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
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.getSystemService
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.model.LatLng
import com.google.ar.core.Pose
import com.google.ar.sceneform.AnchorNode
import com.google.ar.sceneform.ux.TransformableNode
import com.ihsan.arcore_sceneform.api.ApiResponse
import com.ihsan.arcore_sceneform.api.ApiService
import com.ihsan.arcore_sceneform.ar.PathNode
import com.ihsan.arcore_sceneform.ar.PlaceNode
import com.ihsan.arcore_sceneform.ar.PlacesArFragment
import com.ihsan.arcore_sceneform.models.Coordinate
import com.ihsan.arcore_sceneform.models.Poi
import com.ihsan.arcore_sceneform.models.getPositionVector
import kotlinx.coroutines.launch
import java.lang.Math.toRadians
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

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

    private lateinit var apiService: ApiService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        arFragment = supportFragmentManager.findFragmentById(R.id.ar_fragment) as PlacesArFragment
        arFragment.let {
            it.planeDiscoveryController.hide()
            it.planeDiscoveryController.setInstructionView(null)
            it.arSceneView.planeRenderer.isEnabled = false
            it.arSceneView.scene.camera.nearClipPlane = 0.1f
            it.arSceneView.scene.camera.farClipPlane = 5000f
            it.arSceneView.scene.setOnTouchListener { hitTestResult, motionEvent ->
                if (hitTestResult.node != null) {
                    Log.d(TAG, "onCreate: ${hitTestResult.node}")

                }
                true
            }
        }

        sensorManager = getSystemService()!!
        //placesService = PlacesService.create()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        getCurrentLocation {
            apiService = ApiService(it, object : ApiResponse {
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

                override fun onPoiDirectionResponse(coordinates: List<Coordinate>) {
                    Log.d(TAG, "onPoiDirectionResponse: $coordinates")
                    lifecycleScope.launch {
                        //makeAnchorNodeForPoiDirections(coordinates)
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
    private fun calculateScaleFactor1(distance: Double): Float {
        // You can adjust this formula as needed
        val scaleFactor = 1.0f / distance.toFloat()

        // Ensure a minimum scale to prevent the node from becoming too large
        return scaleFactor.coerceAtMost(MAX_SCALE_FACTOR)
    }

    private fun calculateScaleFactor(distance: Double): Float {
        val minDistance = 100.0 // Minimum distance for base scale
        val maxDistance = 5000.0 // Maximum distance (5 km)

        val baseScale = 5.0f // Scale at minDistance
        val minScale = 0.5f // Scale at maxDistance

        // Linear scaling formula
        val scaleFactor = if (distance < minDistance) {
            baseScale
        } else {
            // Scale down as distance increases
            val scale =
                baseScale - ((distance - minDistance) / (maxDistance - minDistance)) * (baseScale - minScale)
            scale.coerceAtLeast(minScale.toDouble()).toFloat()
        }

        return scaleFactor
    }

    private fun makeAnchorNode(poiList: List<Poi>) {
        if (currentLocation == null) {
            Log.e(TAG, "makeAnchorNode: currentLocation is null")
            return
        }
        poiList.forEach { poi ->
            val (distance, bearing) = calculateDistanceAndBearing(
                currentLocation!!.latitude,
                currentLocation!!.longitude,
                poi.latitude,
                poi.longitude
            )

            val pose = translateToARCoreCoordinates(distance, bearing)
            pose.let { validPose ->
                val anchor = arFragment.arSceneView.session!!.createAnchor(validPose)
                anchorNode = AnchorNode(anchor).apply {
                    setParent(arFragment.arSceneView.scene)
                }
                // Add the place in AR
                val placeNode = PlaceNode(this@MainActivity, poi)
                placeNode.localPosition = currentLocation.let { it1 ->
                    poi.getPositionVector(
                        orientationAngles[0], it1!!.latLng
                    )
                }
                placeNode.setParent(anchorNode)
            }
        }
    }

    private fun makeAnchorNodeForPoiDirections(coordinate: List<Coordinate>) {
        if (currentLocation == null) {
            Log.e(TAG, "makeAnchorNode: currentLocation is null")
            return
        }
        var i = 0
        coordinate.forEach { poiPath ->
            Log.d(TAG, "makeAnchorNodeForPoiDirections: Path ${++i} coordinates")
            val (distance, bearing) = calculateDistanceAndBearing(
                currentLocation!!.latitude,
                currentLocation!!.longitude,
                poiPath.lat,
                poiPath.lon
            )

            val pose = translateToARCoreCoordinates(distance, bearing)
            pose.let { validPose ->
                val anchor = arFragment.arSceneView.session!!.createAnchor(validPose)
                anchorNode = AnchorNode(anchor).apply {
                    setParent(arFragment.arSceneView.scene)
                }
                val poi = Poi("", "routing", poiPath.lat, poiPath.lon, 0.0)

                // Add the place in AR
                val pathNode = PathNode(this@MainActivity, poi)
                val positionVector = currentLocation.let { it1 ->
                    poi.getPositionVector(
                        orientationAngles[0], it1!!.latLng
                    )
                }
                Log.d(TAG, "makeAnchorNodeForPoiDirections: positionVector $positionVector")
                pathNode.worldPosition = positionVector

                // Apply scaling based on distance
                /*val scaleFactor = calculateScaleFactor(distance)
                pathNode.localScale = Vector3(scaleFactor, scaleFactor, scaleFactor)*/

                val andyRenderable = pathNode.modelRenderable
                val andy = TransformableNode(arFragment.getTransformationSystem())
                andy.setParent(anchorNode)
                andy.renderable = andyRenderable
                andy.select()
                pathNode.setParent(anchorNode)
            }

        }
    }

    val kilometerToMeter = 20
    private fun translateToARCoreCoordinates(
        distance: Double, bearing: Double
    ): Pose {

        val x = distance * cos(toRadians(bearing))
        val y = .5f
        val z = distance * sin(toRadians(bearing))
        Log.d(TAG, "translateToARCoreCoordinates: x: $x, y: $y, z: $z")
        //anchorPose.compose(cameraPose)

        return Pose.makeTranslation(x.toFloat(), y.toFloat(), z.toFloat())
    }

    // https://stackoverflow.com/questions/639695/how-to-convert-latitude-or-longitude-to-meters
    private fun calculateDistanceAndBearing(
        userLat: Double, userLon: Double, poiLat: Double, poiLon: Double
    ): Pair<Double, Double> {
        //user lat lon
        val lat1 = toRadians(userLat)
        val lon1 = toRadians(userLon)
        //poi lat lon
        val lat2 = toRadians(poiLat)
        val lon2 = toRadians(poiLon)
        //delta lat lon
        val dLat = lat2 - lat1
        val dLon = lon2 - lon1

        //haversine formula
        val a = sin(dLat / 2).pow(2) + cos(lat1) * cos(lat2) * sin(dLon / 2).pow(2)
        val c = 2 * asin(sqrt(a))
        val distance = 6371.0 * c// Earth's radius in kilometers
        val distanceInMeters = distance * kilometerToMeter// multiply by 1000

        val y = sin(dLon) * cos(lat2)
        val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLon)
        val bearing = Math.toDegrees(atan2(y, x))
        Log.d(TAG, "calculateDistanceAndBearing: distance $distanceInMeters bearing $bearing")

        return Pair(distanceInMeters, bearing)
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