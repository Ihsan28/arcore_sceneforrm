package com.ihsan.arcore_sceneform.ar

import android.content.Context
import android.util.Log
import android.view.View
import android.widget.TextView
import com.google.ar.sceneform.Node
import com.google.ar.sceneform.rendering.ViewRenderable
import com.ihsan.arcore_sceneform.R
import com.ihsan.arcore_sceneform.models.Poi


private const val TAG = "PlaceNode"
class PlaceNode(
    val context: Context,
    val place: Poi?
) : Node() {

    private var placeRenderable: ViewRenderable? = null
    private var textViewPlace: TextView? = null

    override fun onActivate() {
        super.onActivate()

        if (scene == null) {
            return
        }

        if (placeRenderable != null) {
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
                    showInfoWindow()
                }
            }
    }

    fun showInfoWindow() {
        // Show text
        textViewPlace?.let {
            it.visibility = if (it.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }

        // Hide text for other nodes
        this.parent?.children?.filter {
            it is PlaceNode && it != this
        }?.forEach {
            (it as PlaceNode).textViewPlace?.visibility = View.VISIBLE
        }
    }
}