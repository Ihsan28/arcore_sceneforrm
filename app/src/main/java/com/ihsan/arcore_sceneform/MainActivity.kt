package com.ihsan.arcore_sceneform

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.content.Context
import android.location.Location
import android.os.Bundle
import android.util.Log
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.maps.model.LatLng
import com.google.ar.core.Config
import com.google.ar.core.Pose
import com.google.ar.sceneform.AnchorNode
import com.google.ar.sceneform.rendering.PlaneRenderer
import com.google.maps.android.ktx.utils.sphericalHeading
import com.ihsan.arcore_sceneform.api.ApiResponse
import com.ihsan.arcore_sceneform.api.ApiService
import com.ihsan.arcore_sceneform.ar.PathNode
import com.ihsan.arcore_sceneform.ar.PlaceNode
import com.ihsan.arcore_sceneform.ar.PlacesArFragment
import com.ihsan.arcore_sceneform.models.Coordinate
import com.ihsan.arcore_sceneform.models.Poi
import com.ihsan.arcore_sceneform.models.getPositionVector
import com.ihsan.arcore_sceneform.sensorservice.Compass
import com.ihsan.arcore_sceneform.sensorservice.CompassListener
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.lang.Math.toRadians
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

class MainActivity : AppCompatActivity() {

    private val TAG = "MainActivity"

    private lateinit var arFragment: PlacesArFragment
    private lateinit var compassView: ImageView
    private lateinit var azimuthView: TextView

    private var anchorNode: AnchorNode? = null
    private var currentLocation: Location? = null
    private var azimuthInDegrees = 0.0f

    private var orientationAngles = FloatArray(3)
    private lateinit var apiService: ApiService

    //compass
    private lateinit var compass: Compass

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        arFragment = supportFragmentManager.findFragmentById(R.id.ar_fragment) as PlacesArFragment
        compassView = findViewById(R.id.compass_view)
        azimuthView = findViewById(R.id.azimuth)

        arFragment.let {
            it.arSceneView.planeRenderer.planeRendererMode =
                PlaneRenderer.PlaneRendererMode.RENDER_TOP_MOST
            it.arSceneView.planeRenderer.isEnabled = false
            it.arSceneView.planeRenderer.isVisible = false

            it.arSceneView.session.let { session ->
                session?.configure(session.config.apply {
                    augmentedFaceMode = Config.AugmentedFaceMode.DISABLED
                    cloudAnchorMode = Config.CloudAnchorMode.DISABLED
                    geospatialMode = Config.GeospatialMode.DISABLED
                    instantPlacementMode = Config.InstantPlacementMode.LOCAL_Y_UP
                    planeFindingMode = Config.PlaneFindingMode.DISABLED
                    updateMode = Config.UpdateMode.BLOCKING
                    lightEstimationMode = Config.LightEstimationMode.DISABLED
                    focusMode = Config.FocusMode.FIXED
                    depthMode = Config.DepthMode.DISABLED

                    setPlaneFindingMode(null)
                    onTrackballEvent(null)
                })
            }
            it.arSceneView.scene.camera.nearClipPlane = 0.1f
            it.arSceneView.scene.camera.farClipPlane = 5000f
        }

        arFragment.setOnTapArPlaneListener { hitResult, plane, motionEvent ->
            // Do something with the tap
            val size=arFragment.arSceneView.session?.allAnchors?.size
            Toast.makeText(this, "size: $size", Toast.LENGTH_SHORT).show()
        }

        //get compass
        compass = Compass(this, object : CompassListener {
            @SuppressLint("SetTextI18n")
            override fun onNewAzimuth(
                aziInDeg: Float,
                magneticDeclination: Float,
                orientation: FloatArray
            ) {
                orientationAngles = orientation
                compassView.rotation = -aziInDeg
                azimuthInDegrees = aziInDeg
                azimuthView.text =
                    "azimuth: $azimuthInDegrees° \nmagneticDeclination: $magneticDeclination° \norientation: ${orientation[0]}°, ${orientation[1]}°, ${orientation[2]}° \nlocation: ${currentLocation?.latitude}, ${currentLocation?.longitude}"
            }

            override fun getCurrentLocation(location: Location) {
                lifecycleScope.launch {

                    // Wait for ARCore to be ready
                    while (arFragment.arSceneView.arFrame!!.camera.trackingState != com.google.ar.core.TrackingState.TRACKING) {
                        Log.d(TAG, "getCurrentLocation: waiting for plane")
                        delay(500)
                    }
                    // Place the anchor node
                    if (currentLocation == null) {
                        currentLocation = location
                        Log.d(TAG, "getCurrentLocation: placeAnchorNodeForNorth")
                        placeAnchorNodeForNorth()
                        apiService = ApiService(location, object : ApiResponse {
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
                                    //makeAnchorNode(poiList)
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
            }
        })

    }

    override fun onResume() {
        super.onResume()
        compass.registerListeners()
    }

    override fun onPause() {
        super.onPause()
        compass.unregisterListeners()
    }

    private fun makeAnchorNode(poiList: List<Poi>) {
        if (currentLocation == null) {
            Log.e(TAG, "makeAnchorNode: currentLocation is null")
            return
        }

        poiList.forEach { poi ->

            //distance and bearing using haversine formula
            val (distance, bearing) = calculateDistanceAndBearing(
                currentLocation!!.latitude, currentLocation!!.longitude, poi.latitude, poi.longitude
            )

            val test = (atan2(
                poi.latitude - currentLocation!!.latitude,
                poi.longitude - currentLocation!!.longitude
            )) - orientationAngles[0]
            val testDistance = (sqrt(
                (poi.latitude - currentLocation!!.latitude).pow(2) + (poi.longitude - currentLocation!!.longitude).pow(
                    2
                )
            )) * 100

            //ar coordinate
            val testX = cos(test) * testDistance
            val testY = 0.4f
            val testZ = sin(test) * testDistance

            val pose = Pose.makeTranslation(testX.toFloat(), testY, testZ.toFloat())
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

            }
        }
    }

    //calibrate bearing to real world north
    private fun placeAnchorNodeForNorth() {
        if (currentLocation == null) {
            Log.e(TAG, "makeAnchorNode: currentLocation is null")
            return
        }
        Toast.makeText(this, " virtual compass", Toast.LENGTH_SHORT).show()
        val poseListSurrounding = listOf(
            Pair(translateToARCoreCoordinates(2.0, 0.0), "virtualNorth"),
            Pair(translateToARCoreCoordinates(2.0, 95.0), "virtualNorth+95"),
            Pair(translateToARCoreCoordinates(2.0, calibrateBearingToWorldNorth(0f)), "North + 0"),
            Pair(
                translateToARCoreCoordinates(2.0, calibrateBearingToWorldNorth(45f)),
                "NorthEast + 45"
            ),
            Pair(
                translateToARCoreCoordinates(2.0, calibrateBearingToWorldNorth(90f)),
                "East + 90"
            ),
            Pair(
                translateToARCoreCoordinates(2.0, calibrateBearingToWorldNorth(135f)),
                "SouthEast + 135"
            ),
            Pair(
                translateToARCoreCoordinates(2.0, calibrateBearingToWorldNorth(180f)),
                "South + 180"
            ),
            Pair(
                translateToARCoreCoordinates(2.0, calibrateBearingToWorldNorth(225f)),
                "SouthWest + 225"
            ),
            Pair(
                translateToARCoreCoordinates(2.0, calibrateBearingToWorldNorth(270f)),
                "West + 270"
            ),
            Pair(
                translateToARCoreCoordinates(2.0, calibrateBearingToWorldNorth(315f)),
                "NorthWest + 315"
            )
        )

        poseListSurrounding.map { validPose ->
            val anchor = arFragment.arSceneView.session!!.createAnchor(validPose.first)
            anchorNode = AnchorNode(anchor).apply {
                parent = arFragment.arSceneView.scene
            }

            // Add the place in AR
            val placeNode = PlaceNode(this@MainActivity, validPose.second)
            Log.d(
                TAG,
                "placeAnchorNodeForNorth:bearing valid pose ${validPose.first}, ${validPose.second}"
            )

            placeNode.parent = anchorNode

            Toast.makeText(this, "N", Toast.LENGTH_SHORT).show()
        }
    }

    fun getCompassDirection(azimuth: Float): String {
        return when (azimuth) {
            in 337.5..360.0 -> "N"
            in 0.0..22.5 -> "N"
            in 22.5..67.5 -> "NE"
            in 67.5..112.5 -> "E"
            in 112.5..157.5 -> "SE"
            in 157.5..202.5 -> "S"
            in 202.5..247.5 -> "SW"
            in 247.5..292.5 -> "W"
            in 292.5..337.5 -> "NW"
            else -> "N"
        }
    }

    fun makePositiveRange(azimuth: Float): Float {
        var positiveAzimuth = azimuth

        if (positiveAzimuth < -360f) {
            positiveAzimuth %= -360f
            positiveAzimuth += 360f
        } else if (positiveAzimuth > 360f) {
            positiveAzimuth %= 360f
        } else if (positiveAzimuth < 0) {
            positiveAzimuth += 360f
        }

        return positiveAzimuth
    }

    private fun calibrateBearingToWorldNorth(bearing: Float): Double {
        var trueNorth = azimuthInDegrees+180//ar 180 calibrate with true north
        trueNorth = makePositiveRange(trueNorth)
        var positiveBearing = makePositiveRange(bearing)
        var bearingToTrueNorth = positiveBearing - trueNorth

        var clockwiseBearing = 360 - makePositiveRange(bearingToTrueNorth)

        when(bearing){
            0f-> Log.d(TAG, "calibrateBearingToWorldNorth: North clockwise $clockwiseBearing \n trueNorth $trueNorth")
            45f-> Log.d(TAG, "calibrateBearingToWorldNorth: Northeast clockwise $clockwiseBearing")
            90f-> Log.d(TAG, "calibrateBearingToWorldNorth: East counterclockwise $clockwiseBearing")
            135f-> Log.d(TAG, "calibrateBearingToWorldNorth: Southeast counterclockwise $clockwiseBearing")
            180f-> Log.d(TAG, "calibrateBearingToWorldNorth: South clockwise $clockwiseBearing")
            225f-> Log.d(TAG, "calibrateBearingToWorldNorth: Southwest counterclockwise $clockwiseBearing")
            270f-> Log.d(TAG, "calibrateBearingToWorldNorth: West counterclockwise $clockwiseBearing")
        }

        Log.d(
            TAG,
            "calibrateBearingToWorldNorth: bearing ($bearing) - trueNorth ($trueNorth) = ($clockwiseBearing)"
        )
        return clockwiseBearing.toDouble()
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

    //calibrate bearing to real world north
    private fun calibrateBearingToWorldNorthV2(poiLatLng: LatLng): Double {
        val heading =
            LatLng(currentLocation!!.latitude, currentLocation!!.longitude).sphericalHeading(
                poiLatLng
            )
        val bearingToTrueNorth = orientationAngles[0] + heading
        Log.d(
            TAG,
            "calibrateBearingToWorldNorthV2: bearing (${orientationAngles[0]}) + trueNorth ($heading) = ($bearingToTrueNorth)"
        )
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
        val bearing = calibrateBearingToWorldNorth(bearingFromTrueNorth.toFloat()).toDouble()

//        val bearing = calibrateBearingToWorldNorthV2(poiLatLng = LatLng(poiLat, poiLon))

        //get true north and calculate bearing
        Log.d(TAG, "calculateDistanceAndBearing: distance $distanceInMeters bearing $bearing")

        return Pair(distanceInMeters, bearing)
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

//    // Constants
//    private val MAX_SCALE_FACTOR = 5.0f
//    private fun calculateScaleFactor1(distance: Double): Float {
//        // You can adjust this formula as needed
//        val scaleFactor = 1.0f / distance.toFloat()
//
//        // Ensure a minimum scale to prevent the node from becoming too large
//        return scaleFactor.coerceAtMost(MAX_SCALE_FACTOR)
//    }
//
//    private fun calculateScaleFactor(distance: Double): Float {
//        val minDistance = 100.0 // Minimum distance for base scale
//        val maxDistance = 5000.0 // Maximum distance (5 km)
//
//        val baseScale = 5.0f // Scale at minDistance
//        val minScale = 0.5f // Scale at maxDistance
//
//        // Linear scaling formula
//        val scaleFactor = if (distance < minDistance) {
//            baseScale
//        } else {
//            // Scale down as distance increases
//            val scale =
//                baseScale - ((distance - minDistance) / (maxDistance - minDistance)) * (baseScale - minScale)
//            scale.coerceAtLeast(minScale.toDouble()).toFloat()
//        }
//
//        return scaleFactor
//    }

}//MainActivity

val Location.latLng: LatLng
    get() = LatLng(this.latitude, this.longitude)