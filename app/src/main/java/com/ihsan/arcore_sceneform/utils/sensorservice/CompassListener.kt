package com.ihsan.arcore_sceneform.utils.sensorservice

import android.graphics.drawable.GradientDrawable.Orientation
import android.location.Location

interface CompassListener {
    fun onNewAzimuth(aziInDeg: Float, magneticDeclination: Float, orientation: FloatArray)
    fun getCurrentLocation(location: Location)
}