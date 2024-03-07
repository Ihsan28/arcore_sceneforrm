package com.ihsan.arcore_sceneform.ui

import android.annotation.SuppressLint
import android.location.Location
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.google.ar.core.Config
import com.google.ar.core.TrackingState
import com.google.ar.sceneform.AnchorNode
import com.google.ar.sceneform.rendering.PlaneRenderer
import com.google.ar.sceneform.ux.ArFragment
import com.ihsan.arcore_sceneform.R
import com.ihsan.arcore_sceneform.ar.PathNode
import com.ihsan.arcore_sceneform.ar.PlaceNode
import com.ihsan.arcore_sceneform.latLng
import com.ihsan.arcore_sceneform.models.Coordinate
import com.ihsan.arcore_sceneform.models.Poi
import com.ihsan.arcore_sceneform.models.getPositionVector
import com.ihsan.arcore_sceneform.utils.ArUtils
import com.ihsan.arcore_sceneform.utils.sensorservice.Compass
import com.ihsan.arcore_sceneform.utils.sensorservice.CompassListener
import com.ihsan.arcore_sceneform.viewmodel.PoiViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.atan2
import kotlin.math.pow
import kotlin.math.sqrt

private const val TAG = "ArNavigationFragment"

class ArNavigationFragment : Fragment() {

    private lateinit var arFragment: ArFragment
    private lateinit var compassView: ImageView
    private lateinit var azimuthView: TextView

    //    private val viewmodel: PoiViewModel by viewModels()
    private lateinit var viewmodel: PoiViewModel
    private val au = ArUtils()

    private var anchorNode: AnchorNode? = null
    private var currentLocation: Location? = null
    private var azimuthInDegrees = 0.0f

    private var orientationAngles = FloatArray(3)
//    private lateinit var apiService: ApiService

    //compass
    private lateinit var compass: Compass

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_ar_navigation, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewmodel = ViewModelProvider(requireActivity())[PoiViewModel::class.java]

        arFragment = childFragmentManager.findFragmentById(R.id.arFragment) as ArFragment
        compassView = view.findViewById(R.id.compass_view)
        azimuthView = view.findViewById(R.id.azimuth)

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
                })
            }
            it.arSceneView.scene.camera.nearClipPlane = 0.1f
            it.arSceneView.scene.camera.farClipPlane = 5000f
        }
        var size=0

        arFragment.setOnTapArPlaneListener { hitResult, plane, motionEvent ->
            // Do something with the tap
            size = arFragment.arSceneView.session?.allAnchors?.size!!
            Toast.makeText(context, "size: $size", Toast.LENGTH_SHORT).show()
        }

        //get compass
        compass = Compass(requireActivity().application, object : CompassListener {
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
                Toast.makeText(requireContext(), "location : $location", Toast.LENGTH_SHORT).show()
                lifecycleScope.launch {
                    // Wait for ARCore to be ready
                    while (arFragment.arSceneView.arFrame!!.camera.trackingState != TrackingState.TRACKING) {
                        Log.d(TAG, "getCurrentLocation: waiting for plane")
                        delay(500)
                    }
                    // Place the anchor node
                    if (currentLocation == null) {
                        currentLocation = location
                        Log.d(TAG, "getCurrentLocation: placeAnchorNodeForNorth")
                        au.placeAnchorNodeForCompass(
                            context!!,
                            arFragment.arSceneView,
                            azimuthInDegrees
                        )

                        viewmodel.poiList.observe(viewLifecycleOwner) {
                            it?.let {
                                size = arFragment.arSceneView.session?.allAnchors?.size!!
                                if (size<20){
                                    makeAnchorNode(it)
                                }

                            }
                        }

                        viewmodel.poiDirection.observe(viewLifecycleOwner) {
                            it?.let {
                                makeAnchorNodeForPoiDirections(it)
                            }
                        }
                    }
                }
            }

        })

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
            ))*180/Math.PI

            val testDistance = (sqrt(
                (poi.latitude - currentLocation!!.latitude).pow(2) + (poi.longitude - currentLocation!!.longitude).pow(
                    2
                )
            )) * 500

            Log.d(TAG, "makeAnchorNode: testBearing $testBearing testDistance $testDistance azimuthInDegrees $azimuthInDegrees")

            val pose = au.translateToARCoreCoordinates(
                testDistance,
                testBearing.toFloat(),
                azimuthInDegrees
            )
            pose.let { validPose ->
                val anchor = arFragment.arSceneView.session!!.createAnchor(validPose)
                anchorNode = AnchorNode(anchor).apply {
                    parent = arFragment.arSceneView.scene
                }
                // Add the place in AR
                val placeNode = PlaceNode(requireContext(), poi.name)
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
                currentLocation!!.latitude,
                currentLocation!!.longitude,
                poiPath.lat,
                poiPath.lon,
                azimuthInDegrees
            )

            val pose =
                au.translateToARCoreCoordinates(distance, bearing.toFloat(), azimuthInDegrees)
            pose.let { validPose ->
                val anchor = arFragment.arSceneView.session!!.createAnchor(validPose)
                anchorNode = AnchorNode(anchor).apply {
                    parent = arFragment.arSceneView.scene
                }
                val poi = Poi("", "$i", poiPath.lat, poiPath.lon, 0.0)

                // Add the place in AR
                val pathNode = PathNode(requireContext(), poi)
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

    override fun onResume() {
        super.onResume()
        compass.registerListeners()
    }

    override fun onPause() {
        super.onPause()
        compass.unregisterListeners()
    }
//
//    override fun getAdditionalPermissions(): Array<String> =
//        listOf(Manifest.permission.ACCESS_FINE_LOCATION).toTypedArray()
}