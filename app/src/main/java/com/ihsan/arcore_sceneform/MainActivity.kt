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
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.maps.model.LatLng
import com.google.ar.core.Config
import com.google.ar.core.Pose
import com.google.ar.sceneform.AnchorNode
import com.google.ar.sceneform.rendering.PlaneRenderer
import com.ihsan.arcore_sceneform.api.ApiResponse
import com.ihsan.arcore_sceneform.api.ApiService
import com.ihsan.arcore_sceneform.ar.PathNode
import com.ihsan.arcore_sceneform.ar.PlaceNode
import com.ihsan.arcore_sceneform.ar.PlacesArFragment
import com.ihsan.arcore_sceneform.data.Common.geoapifyApiService
import com.ihsan.arcore_sceneform.models.Coordinate
import com.ihsan.arcore_sceneform.models.Poi
import com.ihsan.arcore_sceneform.models.getPositionVector
import com.ihsan.arcore_sceneform.repository.PoiRepository
import com.ihsan.arcore_sceneform.sensorservice.Compass
import com.ihsan.arcore_sceneform.sensorservice.CompassListener
import com.ihsan.arcore_sceneform.utils.ArUtils
import com.ihsan.arcore_sceneform.viewmodel.PoiViewModel
import com.ihsan.arcore_sceneform.viewmodel.PoiViewModelFactory
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
    private lateinit var viewmodel:PoiViewModel
    private val au= ArUtils()

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
        viewmodel = ViewModelProvider(this, PoiViewModelFactory(PoiRepository(geoapifyApiService)))
            .get(PoiViewModel::class.java)

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
                        au.placeAnchorNodeForCompass(this@MainActivity, arFragment.arSceneView, azimuthInDegrees)
                        apiService = ApiService( object : ApiResponse {
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

//            val (distance, bearing) = au.calculateDistanceAndBearing(
//                currentLocation!!.latitude, currentLocation!!.longitude, poi.latitude, poi.longitude, azimuthInDegrees
//            )

            val testBearing = (atan2(
                poi.latitude - currentLocation!!.latitude,
                poi.longitude - currentLocation!!.longitude
            ))
            val testDistance = (sqrt(
                (poi.latitude - currentLocation!!.latitude).pow(2) + (poi.longitude - currentLocation!!.longitude).pow(
                    2
                )
            )) * 100

            val pose = au.translateToARCoreCoordinates(testDistance, testBearing.toFloat(), azimuthInDegrees)
            pose.let { validPose ->
                val anchor = arFragment.arSceneView.session!!.createAnchor(validPose)
                anchorNode = AnchorNode(anchor).apply {
                    parent = arFragment.arSceneView.scene
                }
                // Add the place in AR
                val placeNode = PlaceNode(this@MainActivity, poi.name)
                placeNode.localPosition = currentLocation.let { currLocation ->
                    poi.getPositionVector(
                        orientationAngles[0], currLocation!!.latLng
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
            val (distance, bearing) = au.calculateDistanceAndBearing(
                currentLocation!!.latitude, currentLocation!!.longitude, poiPath.lat, poiPath.lon, azimuthInDegrees
            )

            val pose = au.translateToARCoreCoordinates(distance, bearing.toFloat(),azimuthInDegrees)
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

}//MainActivity

val Location.latLng: LatLng
    get() = LatLng(this.latitude, this.longitude)