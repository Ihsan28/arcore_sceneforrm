package com.ihsan.arcore_sceneform

import android.Manifest
import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.getSystemService
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.ar.core.Pose
import com.google.ar.core.TrackingState
import com.google.ar.sceneform.AnchorNode
import com.google.ar.sceneform.math.Vector3
import com.google.ar.sceneform.rendering.ViewRenderable
import com.google.codelabs.findnearbyplacesar.ar.PlacesArFragment
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.ihsan.arcore_sceneform.api.ApiResponse
import com.ihsan.arcore_sceneform.api.ApiService
import com.ihsan.arcore_sceneform.ar.PlaceNode
import com.ihsan.arcore_sceneform.models.Coordinate
import com.ihsan.arcore_sceneform.models.Poi
import com.ihsan.arcore_sceneform.models.PoiDirectionResponse
import com.ihsan.arcore_sceneform.models.getPositionVector
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.IOException


class MainActivity : AppCompatActivity(), SensorEventListener {

    private val TAG = "MainActivity"

    private lateinit var arFragment: PlacesArFragment

    // Location
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    // Sensor
    private lateinit var sensorManager: SensorManager
    private val accelerometerReading = FloatArray(3)
    private val magnetometerReading = FloatArray(3)
    private val rotationMatrix = FloatArray(9)
    private val orientationAngles = FloatArray(3)

    private var anchorNode: AnchorNode? = null
    private var currentLocation: Location? = null

    private lateinit var poiList: List<Poi>
    private lateinit var apiService: ApiService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        arFragment = supportFragmentManager.findFragmentById(R.id.ar_fragment) as PlacesArFragment
        arFragment.arSceneView.planeRenderer.isEnabled = false

        sensorManager = getSystemService()!!
        //placesService = PlacesService.create()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        getCurrentLocation {
            apiService= ApiService(it,object : ApiResponse {
                override fun onResponse(response: String) {
                    Log.d(TAG, "onResponse: $response")
                }

                override fun onError(error: String) {
                    Log.d(TAG, "onError: $error")
                }

                override fun onLoading(isLoading: Boolean) {
                    Log.d(TAG, "onLoading: $isLoading")
                }

                override fun onPoiResponse(poiList: List<Poi>) {
                    Log.d(TAG, "onPoiResponse: $poiList")
                    lifecycleScope.launch {
                        makeAnchorNode(poiList)
                    }
                }

                override fun onPoiDirectionResponse(coordinates: List<List<Double>>) {
                    Log.d(TAG, "onPoiDirectionResponse: $coordinates")
                    lifecycleScope.launch {
                        coordinates.forEachIndexed { index, coordinate ->
                            if (coordinate.size % 2 == 0) {
                                val anchor = arFragment.arSceneView.session!!.createAnchor(
                                    Pose.makeTranslation(
                                        arFragment.arSceneView.arFrame!!.camera.pose.translation
                                    )
                                )
                                val anchorNode = AnchorNode(anchor)
                                anchorNode.setParent(arFragment.arSceneView.scene)

                                val placeNode = PlaceNode(this@MainActivity, Poi("","routing",coordinate[0], coordinate[1], 0.0))
                                placeNode.setParent(anchorNode)
                                placeNode.localPosition = currentLocation?.let { it1 ->
                                    Poi("","routing",coordinate[0], coordinate[1], 0.0).getPositionVector(
                                        orientationAngles[0],
                                        it1.latLng
                                    )
                                }
                            }
                        }
                    }
                }
            })
        }
    }

    override fun onResume() {
        super.onResume()
        sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)?.also {
            sensorManager.registerListener(
                this,
                it,
                SensorManager.SENSOR_DELAY_NORMAL
            )
        }
        sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.also {
            sensorManager.registerListener(
                this,
                it,
                SensorManager.SENSOR_DELAY_NORMAL
            )
        }
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
    }

    // Constants
    private val MAX_SCALE_FACTOR = 5.0f
    private fun calculateScaleFactor(distance: Double): Float {
        // Define a scaling factor based on your requirements
        // You can adjust this formula as needed
        val scaleFactor = 1.0f / distance.toFloat()

        // Ensure a minimum scale to prevent the node from becoming too large
        return scaleFactor.coerceAtMost(MAX_SCALE_FACTOR)
    }

    private fun makeAnchorNode(poiList: List<Poi>) {
        val frame = arFragment.arSceneView.session?.update()
        if (frame != null) {
            if (frame.camera.trackingState != TrackingState.TRACKING) {
                Toast.makeText(MainActivity(), "Not tracking", Toast.LENGTH_SHORT).show()
                return
            }
        }
        poiList.forEach {
            val anchor = arFragment.arSceneView.session!!.createAnchor(
                Pose.makeTranslation(
                    it.latitude.toFloat(),
                    it.longitude.toFloat(),
                    arFragment.arSceneView.arFrame!!.camera.pose.translation.last()
                )
            )
            val distance = it.distance
            anchorNode?.worldScale = Vector3.one()
            anchorNode = AnchorNode(anchor)
            anchorNode?.setParent(arFragment.arSceneView.scene)

            // Calculate the scale factor based on distance
            val scaleFactor = 1f

            // Apply the scale factor to the anchorNode
            anchorNode?.worldScale = Vector3(scaleFactor, scaleFactor, scaleFactor)

            // Add the place in AR
            val placeNode = PlaceNode(this@MainActivity, it)
            placeNode.setParent(anchorNode)
            placeNode.localPosition = currentLocation?.let { it1 ->
                it.getPositionVector(
                    orientationAngles[0],
                    it1.latLng
                )
            }
        }
//                            placeNode.setOnTapListener { _, _ ->
//                                showInfoWindow(poi)
//                            }

    }

    private fun getCurrentLocation(onSuccess: (Location) -> Unit) {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            currentLocation = location
            onSuccess(location)
            Log.d(TAG, "getlocation ${location.latitude} ${location.longitude}")
        }.addOnFailureListener {
            Log.e(TAG, "Could not get location")
        }
    }

    private fun isSupportedDevice(): Boolean {
        val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val openGlVersionString = activityManager.deviceConfigurationInfo.glEsVersion
        if (openGlVersionString.toDouble() < 3.0) {
            Toast.makeText(this, "Sceneform requires OpenGL ES 3.0 or later", Toast.LENGTH_LONG)
                .show()
            finish()
            return false
        }
        return true
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) {
            return
        }
        if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            System.arraycopy(event.values, 0, accelerometerReading, 0, accelerometerReading.size)
        } else if (event.sensor.type == Sensor.TYPE_MAGNETIC_FIELD) {
            System.arraycopy(event.values, 0, magnetometerReading, 0, magnetometerReading.size)
        }

        // Update rotation matrix, which is needed to update orientation angles.
        SensorManager.getRotationMatrix(
            rotationMatrix,
            null,
            accelerometerReading,
            magnetometerReading
        )
        SensorManager.getOrientation(rotationMatrix, orientationAngles)
    }
}

val Location.latLng: LatLng
    get() = LatLng(this.latitude, this.longitude)