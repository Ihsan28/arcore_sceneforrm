package com.ihsan.arcore_sceneform.models

import com.google.android.gms.maps.model.LatLng
import com.google.ar.sceneform.math.Vector3
import com.google.maps.android.ktx.utils.sphericalHeading
import kotlin.math.cos
import kotlin.math.sin

data class Poi(
    val id: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val distance: Double = 0.0
){
    override fun equals(other: Any?): Boolean {
        if (other !is Poi) {
            return false
        }
        return this.id == other.id
    }

    override fun hashCode(): Int {
        return this.id.hashCode()
    }
}

fun Poi.getPositionVector(azimuth: Float, latLng: LatLng): Vector3 {
    val placeLatLng = LatLng(this.latitude, this.longitude)
    val heading = latLng.sphericalHeading(placeLatLng)
    val r = -2f
    val x = r * sin(azimuth + heading+ 180).toFloat()
    val y = 1f
    val z = r * cos(azimuth + heading).toFloat()
    return Vector3(x, y, z)
}