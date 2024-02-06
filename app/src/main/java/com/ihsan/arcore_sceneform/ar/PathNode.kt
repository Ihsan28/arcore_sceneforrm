package com.ihsan.arcore_sceneform.ar

import android.content.Context
import android.util.Log
import android.widget.TextView
import com.google.ar.sceneform.Node
import com.google.ar.sceneform.rendering.ModelRenderable
import com.google.ar.sceneform.rendering.ViewRenderable
import com.ihsan.arcore_sceneform.R
import com.ihsan.arcore_sceneform.models.Poi


private const val TAG = "PathNode"

class PathNode(
    val context: Context,
    val place: Poi?
) : Node() {
    var modelRenderable: ModelRenderable? = null
    private var placeRenderable: ViewRenderable? = null
    private var textViewPlace: TextView? = null

    override fun onActivate() {
        super.onActivate()

        if (scene == null) {
            return
        }

        if (modelRenderable != null) {
            return
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
                }
            }

        ModelRenderable.builder()
            .setSource(context, R.raw.arch_round)
            .setIsFilamentGltf(true)
            .build()
            .thenAccept { renderable: ModelRenderable ->
                setRenderable(renderable)
                modelRenderable = renderable
            }
            .exceptionally { throwable: Throwable? ->
                Log.e(TAG, "Unable to load Renderable.", throwable)
                null
            }
    }
}