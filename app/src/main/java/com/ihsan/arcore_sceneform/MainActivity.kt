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
import com.google.ar.sceneform.AnchorNode
import com.google.ar.sceneform.rendering.ViewRenderable
import com.google.codelabs.findnearbyplacesar.ar.PlacesArFragment
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.ihsan.arcore_sceneform.ar.PlaceNode
import com.ihsan.arcore_sceneform.models.Poi
import com.ihsan.arcore_sceneform.models.getPositionVector
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request


class MainActivity : AppCompatActivity(), SensorEventListener {

    private val TAG = "MainActivity"

    //private lateinit var placesService: PlacesService
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
    private var markers: MutableList<Marker> = emptyList<Marker>().toMutableList()
    private var currentLocation: Location? = null
    //private lateinit var andyRenderable: ModelNode

    private lateinit var poiList: List<Poi>
    private var placeRenderable: ViewRenderable? = null
    private var textViewPlace: TextView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        arFragment = supportFragmentManager.findFragmentById(R.id.ar_fragment) as PlacesArFragment

        sensorManager = getSystemService()!!
        //placesService = PlacesService.create()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        getCurrentLocation {
            val pos = CameraPosition.fromLatLngZoom(it.latLng, 13f)
            Log.d(TAG, "entering setUpMaps $pos")
            fetchPoi(it.latitude, it.longitude)
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

    private fun fetchPoi(lat: Double, lng: Double, radius: Int = 5000) {
        val api = "037eff850f2e4b11b0ffacb5a381c7f2"

        val url =
            "https://api.geoapify.com/v2/places?categories=religion&filter=circle:$lng,$lat,$radius&bias=proximity:$lng,$lat&limit=20&apiKey=$api"
        Log.d(TAG, "fetchPoi: $url")
        val client = OkHttpClient()
        val request = Request.Builder()
            .url(url)
            .build()

        client.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                if (response.isSuccessful) {
                    val responseBody = response.body?.string()
                    // Parse the response and cache the POIs
                    poiList = parsePoiResponse(responseBody ?: "")
                    Log.d(TAG, "onResponse: $poiList")
                    // Now, you can use the poiList in your activity
                    lifecycleScope.launch {


                        poiList.forEach {
//                            val anchor: Anchor = arFragment.arSceneView.session!!.createAnchor(
//                                Pose.makeTranslation(
//                                    it.latitude.toFloat(),
//                                    it.longitude.toFloat(),
//                                    0.0f
//                                )
//                            )
//                            val anchorNode = AnchorNode(anchor)
//                            anchorNode.setParent(arFragment.arSceneView.scene)
//
//                            val node = PlaceNode(this@MainActivity, it)
//                            node.setParent(anchorNode)
//                            arFragment.arSceneView.scene.addChild(anchorNode)

                            val anchor = arFragment.arSceneView.session!!.createAnchor(
                                Pose.makeTranslation(
                                    arFragment.arSceneView.arFrame!!.camera.pose.translation
                                )
                            )
                            anchorNode = AnchorNode(anchor)
                            anchorNode?.setParent(arFragment.arSceneView.scene)

                            // Add the place in AR
                            val placeNode = PlaceNode(this@MainActivity, it)
                            placeNode.setParent(anchorNode)
                            placeNode.localPosition = currentLocation?.let { it1 ->
                                it.getPositionVector(orientationAngles[0],
                                    it1.latLng
                                )
                            }
//                            placeNode.setOnTapListener { _, _ ->
//                                showInfoWindow(poi)
//                            }
                        }
                        //setupArScene(poiList)
                    }
                } else {
                    Log.e(TAG, "fetchPoi unsuccessful: ${response.code}")
                }
            }

            override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                Log.e(TAG, "fetchPoi failed: ${e.message}")
            }
        })
    }

    private fun showInfoWindow(poi: Poi) {
        // Show in AR
        val matchingPlaceNode = anchorNode?.children?.filter {
            it is PlaceNode
        }?.first {
            val otherPlace = (it as PlaceNode).place ?: return@first false
            return@first otherPlace == poi
        } as? PlaceNode
        matchingPlaceNode?.showInfoWindow()

        // Show as marker
        val matchingMarker = markers.firstOrNull {
            val placeTag = (it.tag as? Poi) ?: return@firstOrNull false
            return@firstOrNull placeTag == poi
        }
        matchingMarker?.showInfoWindow()
    }

    fun parsePoiResponse(jsonResponse: String): List<Poi> {
        val poiList = mutableListOf<Poi>()

        try {
            val jsonObject = Gson().fromJson(jsonResponse, JsonObject::class.java)
            val featuresArray: JsonArray? = jsonObject.getAsJsonArray("features")

            featuresArray?.forEach { feature ->
                val properties = feature?.asJsonObject?.getAsJsonObject("properties")
                val geometry = feature?.asJsonObject?.getAsJsonObject("geometry")
                val coordinates = geometry?.getAsJsonArray("coordinates")

                val id = properties?.get("place_id")?.asString
                val name = properties?.get("name")?.asString
                val latitude = coordinates?.get(1)?.asDouble
                val longitude = coordinates?.get(0)?.asDouble
                val distance = properties?.get("distance")?.asDouble

                if (name != null && latitude != null && longitude != null && distance != null && id != null) {
                    val poi = Poi(id,name, latitude, longitude, distance)
                    poiList.add(poi)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing POI response: ${e.message}")
        }

        return poiList
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
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
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