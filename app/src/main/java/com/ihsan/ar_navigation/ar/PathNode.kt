package com.ihsan.ar_navigation.ar

import android.content.Context
import android.util.Log
import android.view.View
import android.view.animation.Transformation
import android.widget.TextView
import com.google.ar.core.Anchor
import com.google.ar.sceneform.Node
import com.google.ar.sceneform.rendering.ModelRenderable
import com.google.ar.sceneform.rendering.ViewRenderable
import com.google.ar.sceneform.ux.FootprintSelectionVisualizer
import com.google.ar.sceneform.ux.TransformableNode
import com.google.ar.sceneform.ux.TransformationSystem
import com.ihsan.ar_navigation.R
import com.ihsan.ar_navigation.models.Poi


private const val TAG = "PathNode"

class PathNode(
    private val context: Context,
    private val place: Poi?
) : Node() {
    var modelRenderable: ModelRenderable? = null
    private var placeRenderable: ViewRenderable? = null
    private var textViewPlace: TextView? = null
    private var root=this

    override fun onActivate() {
        super.onActivate()

        if (scene == null) {
            return
        }

        if (modelRenderable != null) {
            return
        }

        ModelRenderable.builder()
            .setSource(context, R.raw.arrow_greater_than)
            .setIsFilamentGltf(true)
            .build()
            .thenAccept { renderable: ModelRenderable ->
                //render with transformable node and add title
                addPlaceObjectTitle(renderable)
//                setRenderable(renderable)
//                modelRenderable = renderable
            }
            .exceptionally { throwable: Throwable? ->
                Log.e(TAG, "Unable to load Renderable.", throwable)
                null
            }
    }
    private fun makeTransformationSystem(): TransformationSystem {
        val selectionVisualizer = FootprintSelectionVisualizer()
        return TransformationSystem(context.resources.displayMetrics, selectionVisualizer)
    }
    fun addPlaceObjectTitle(renderable: ModelRenderable){
        val transformationSystem = makeTransformationSystem()
        TransformableNode(transformationSystem).apply {
            rotationController.isEnabled = true
            scaleController.isEnabled = true
            translationController.isEnabled = false // not support
            setParent(root)
            this.renderable = renderable // Build using CompletableFuture
            select()
        }
        ViewRenderable.builder()
            .setView(context, R.layout.place_view)
            .build()
            .thenAccept { renderable ->
                setRenderable(renderable)
                placeRenderable = renderable
                place?.let {
                    textViewPlace = renderable.view.findViewById(R.id.placeName)
                    textViewPlace?.text = it.name
                    Log.d(TAG, "onActivate: ${it.name}")
                    showInfoWindow()
                }
                parent=root
            }
    }
    fun showInfoWindow() {
        // Show text
        textViewPlace?.let {
            it.visibility = if (it.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }

        // Hide text for other nodes
        this.parent?.children?.filter {
            it is PathNode && it != this
        }?.forEach {
            (it as PathNode).textViewPlace?.visibility = View.VISIBLE
        }
    }
}