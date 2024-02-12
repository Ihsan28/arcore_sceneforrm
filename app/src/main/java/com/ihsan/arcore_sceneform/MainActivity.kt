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
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.getSystemService
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.model.LatLng
import com.google.ar.core.Config
import com.google.ar.core.Pose
import com.google.ar.sceneform.AnchorNode
import com.google.ar.sceneform.ux.TransformableNode
import com.google.maps.android.ktx.utils.sphericalHeading
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
    private lateinit var compassView:ImageView
    private lateinit var azimuthView: TextView

    // Location
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    // Sensor
    private lateinit var sensorManager: SensorManager
    private val accelerometerReading = FloatArray(3)
    private val magnetometerReading = FloatArray(3)
    private val rotationMatrix = FloatArray(9)
    private val orientationAngles = FloatArray(3)

    private val lastAccelerometer = FloatArray(3)
    private val lastMagnetometer = FloatArray(3)
    private val lastAccelerometerSet = false
    private val lastMagnetometerSet = false

    private var anchorNode: AnchorNode? = null
    private var currentLocation: Location? = null
    private var azimuthInDegrees = 0.0

    private lateinit var apiService: ApiService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        arFragment = supportFragmentManager.findFragmentById(R.id.ar_fragment) as PlacesArFragment
        compassView = findViewById(R.id.compass_view)
        azimuthView = findViewById(R.id.azimuth)

        arFragment.let {
            it.arSceneView.planeRenderer.isEnabled = false
            it.arSceneView.session.let { session ->
                session?.configure(session.config.apply {

                    planeFindingMode = Config.PlaneFindingMode.DISABLED
                    updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE
                    lightEstimationMode = Config.LightEstimationMode.DISABLED
                    focusMode = Config.FocusMode.AUTO

                    onTrackballEvent(null)
                })
            }
            it.arSceneView.scene.camera.nearClipPlane = 0.1f
            it.arSceneView.scene.camera.farClipPlane = 5000f
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
                    Log.d(TAG, "onPoiResponse: ${poiList.size}")
                    lifecycleScope.launch {
                        placeAnchorNodeForNorth()
                        makeAnchorNode(poiList)
                    }
                }

                override fun onPoiDirectionResponse(coordinates: List<Coordinate>) {
                    Log.d(TAG, "onPoiDirectionResponse: $coordinates")
                    lifecycleScope.launch {
                        makeAnchorNodeForPoiDirections(coordinates)
                    }
                }
            })
        }
    }

    override fun onResume() {
        super.onResume()
        sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)?.also {
            sensorManager.registerListener(
                this, it, SensorManager.SENSOR_DELAY_NORMAL
            )
        }
        sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.also {
            sensorManager.registerListener(
                this, it, SensorManager.SENSOR_DELAY_NORMAL
            )
        }
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
    }

    private fun makeAnchorNode(poiList: List<Poi>) {
        if (currentLocation == null) {
            Log.e(TAG, "makeAnchorNode: currentLocation is null")
            return
        }

        poiList.forEach { poi ->

            val (distance, bearing) = calculateDistanceAndBearing(
                currentLocation!!.latitude, currentLocation!!.longitude, poi.latitude, poi.longitude
            )

            Log.d(TAG, "makeAnchorNode: ${orientationAngles[0]}")
            val test=(atan2(poi.latitude - currentLocation!!.latitude, poi.longitude - currentLocation!!.longitude))-orientationAngles[0]
            val testDistance=(sqrt((poi.latitude - currentLocation!!.latitude).pow(2) + (poi.longitude - currentLocation!!.longitude).pow(2)))*100
            val testX=cos(test)*testDistance
            val testY=sin(test)*testDistance

            val pose=Pose.makeTranslation(testX.toFloat(), 0.4f, testY.toFloat())
            //val pose = translateToARCoreCoordinates(distance, bearing)
            pose.let { validPose ->
                val anchor = arFragment.arSceneView.session!!.createAnchor(validPose)
                anchorNode = AnchorNode(anchor).apply {
                    parent = arFragment.arSceneView.scene
                }
                // Add the place in AR
                val placeNode = PlaceNode(this@MainActivity, poi.name)
                placeNode.localPosition = currentLocation.let { it1 ->
                    poi.getPositionVector(
                        orientationAngles[0], it1!!.latLng
                    )
                }
                placeNode.parent = anchorNode
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
                currentLocation!!.latitude, currentLocation!!.longitude, poiPath.lat, poiPath.lon
            )

            val pose = translateToARCoreCoordinates(distance, bearing)
            pose.let { validPose ->
                val anchor = arFragment.arSceneView.session!!.createAnchor(validPose)
                anchorNode = AnchorNode(anchor).apply {
                    parent = arFragment.arSceneView.scene
                }
                val poi = Poi("", "$i", poiPath.lat, poiPath.lon, 0.0)

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
                andy.parent = anchorNode
                andy.renderable = andyRenderable
                andy.select()
                pathNode.parent = anchorNode
            }
        }
    }

    //calibrate bearing to real world north
    private fun placeAnchorNodeForNorth() {
        if (currentLocation == null) {
            Log.e(TAG, "makeAnchorNode: currentLocation is null")
            return
        }
        val poseListSurrounding = listOf(
            Pair(translateToARCoreCoordinates(2.0, 0.0),"virtualNorth"),
            Pair(translateToARCoreCoordinates(2.0, calibrateBearingToWorldNorth(0.0)),"North + 0"),
            Pair(translateToARCoreCoordinates(2.0, calibrateBearingToWorldNorth(45.0)),"NorthEast + 45"),
            Pair(translateToARCoreCoordinates(2.0, calibrateBearingToWorldNorth(90.0)),"East + 90"),
            Pair(translateToARCoreCoordinates(2.0, calibrateBearingToWorldNorth(135.0)),"SouthEast + 135"),
            Pair(translateToARCoreCoordinates(2.0, calibrateBearingToWorldNorth(180.0)),"South + 180"),
            Pair(translateToARCoreCoordinates(2.0, calibrateBearingToWorldNorth(225.0)),"SouthWest + 225"),
            Pair(translateToARCoreCoordinates(2.0, calibrateBearingToWorldNorth(270.0)),"West + 270"),
            Pair(translateToARCoreCoordinates(2.0, calibrateBearingToWorldNorth(315.0)),"NorthWest + 315")
        )

        poseListSurrounding.map { validPose ->
            val anchor = arFragment.arSceneView.session!!.createAnchor(validPose.first)
            anchorNode = AnchorNode(anchor).apply {
                parent = arFragment.arSceneView.scene
            }

            // Add the place in AR
            val placeNode = PlaceNode(this@MainActivity, validPose.second)

            placeNode.parent = anchorNode

            Toast.makeText(this, "N", Toast.LENGTH_SHORT).show()
        }
    }
    private fun calibrateBearingToWorldNorth(bearing: Double, trueNorth: Double=getTrueNorth()): Double {
//        var bearingToTrueNorth = bearing - trueNorth - 90.0
        val calibrateVirtualNorth = 180.0
        val bearingToTrueNorth = (bearing + calibrateVirtualNorth) + trueNorth
        Log.d(TAG, "calibrateBearingToWorldNorth: bearing ($bearing) - trueNorth ($trueNorth) - 180.0 = ($bearingToTrueNorth)")
        return bearingToTrueNorth
    }

    private val kilometerToMeter = 50.0
    private fun translateToARCoreCoordinates(
        distance: Double, bearing: Double
    ): Pose {

//        val x = distance * cos(toRadians(bearing))
//        val y = .4f
//        val z = distance * sin(toRadians(bearing))

        val x = distance * sin(toRadians(bearing))
        val y = .4f
        val z = distance * cos(toRadians(bearing))
        Log.d(TAG, "translateToARCoreCoordinates: x: $x, y: $y, z: $z")

        return Pose.makeTranslation(x.toFloat(), y.toFloat(), z.toFloat())
    }

    //use sensor to get true north and calibrate bearing
    private fun getTrueNorth(): Double {
        //round to 3 decimal places
        return -((azimuthInDegrees * 1000).toInt() / 1000.0)
    }

    //calibrate bearing to real world north
    private fun calibrateBearingToWorldNorthV2(poiLatLng: LatLng): Double {
        val heading = LatLng(currentLocation!!.latitude, currentLocation!!.longitude).sphericalHeading(poiLatLng)
        val bearingToTrueNorth = orientationAngles[0] + heading
        Log.d(TAG, "calibrateBearingToWorldNorthV2: bearing (${orientationAngles[0]}) + trueNorth ($heading) = ($bearingToTrueNorth)")
        return bearingToTrueNorth
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
//        val dLat = lat2 - lat1
//        val dLon = lon2 - lon1

        val dLat = toRadians(poiLat - userLat)
        val dLon = toRadians(poiLon - userLon)

        //haversine formula
        val a = sin(dLat / 2).pow(2) + cos(lat1) * cos(lat2) * sin(dLon / 2).pow(2)
        //central angle
        val c = 2 * asin(sqrt(a))
        //distance
        val distance = 6371.0 * c// Earth's radius in kilometers
        val distanceInMeters = distance * kilometerToMeter// multiply by 1000 to get meters

        val y = sin(dLon) * cos(lat2)
        val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLon)

        val bearingFromTrueNorth = Math.toDegrees(atan2(y, x))
        //calibrate bearing to real world north
        val bearing = calibrateBearingToWorldNorth(bearingFromTrueNorth)

//        val bearing = calibrateBearingToWorldNorthV2(poiLatLng = LatLng(poiLat, poiLon))

        //get true north and calculate bearing
        Log.d(TAG, "calculateDistanceAndBearing: distance $distanceInMeters bearing $bearing")

        return Pair(distanceInMeters, bearing)
    }

    private fun getCurrentLocation(onSuccess: (Location) -> Unit) {
        if (ActivityCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            currentLocation = location
            onSuccess(location)
            Log.d(TAG, "getlocation: CurrentLocation ${location.latitude} ${location.longitude}")
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
        //Log.d(TAG, "onAccuracyChanged: $sensor, $accuracy")
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) {
            return
        }
        if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            System.arraycopy(event.values, 0, accelerometerReading, 0, accelerometerReading.size)
            //Log.d(TAG, "Accelerometer: ${event.values[0]}, ${event.values[1]}, ${event.values[2]}")
        } else if (event.sensor.type == Sensor.TYPE_MAGNETIC_FIELD) {
            System.arraycopy(event.values, 0, magnetometerReading, 0, magnetometerReading.size)
            //Log.d(TAG, "Magnetometer: ${event.values[0]}, ${event.values[1]}, ${event.values[2]}")
        }

        // Update rotation matrix, which is needed to update orientation angles.
        SensorManager.getRotationMatrix(
            rotationMatrix, null, accelerometerReading, magnetometerReading
        )
        SensorManager.getOrientation(rotationMatrix, orientationAngles)

        azimuthInDegrees = Math.toDegrees(orientationAngles[0].toDouble())*1000/1000.0
        compassView.rotation = -azimuthInDegrees.toFloat()
        if (azimuthInDegrees < 0) {
            azimuthInDegrees += 360.0
        }

        azimuthView.text = "azimuth: ${orientationAngles[0]}°"
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

}//MainActivity

val Location.latLng: LatLng
    get() = LatLng(this.latitude, this.longitude)