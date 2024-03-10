package com.ihsan.ar_navigation.utils

import android.content.Context
import android.location.Location
import android.util.Log
import android.widget.Toast
import com.google.android.gms.maps.model.LatLng
import com.google.ar.core.Pose
import com.google.ar.sceneform.AnchorNode
import com.google.ar.sceneform.ArSceneView
import com.google.ar.sceneform.math.Vector3
import com.google.maps.android.ktx.utils.sphericalHeading
import com.ihsan.ar_navigation.ar.PlaceNode
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

private const val TAG = "ArUtils"
class ArUtils {
    // Constants
    private val MAX_SCALE_FACTOR = 5.0f
    private val kilometerToMeter = 50.0

    //calibrate bearing to real world north
    fun placeAnchorNodeForCompass(context: Context, arSceneView:ArSceneView, azimuthInDegrees: Float) {

        val poseListSurrounding = listOf(
            Pair(translateToARCoreCoordinatesRelativeToVirtualNorth(2.0, 0.0), "virtualNorth"),
            Pair(translateToARCoreCoordinates(2.0, 0f,azimuthInDegrees), "North + 0"),
            Pair(
                translateToARCoreCoordinates(2.5, 45f,azimuthInDegrees),
                "NorthEast + 45"
            ),
            Pair(
                translateToARCoreCoordinates(2.0, 90f,azimuthInDegrees),
                "East + 90"
            ),
            Pair(
                translateToARCoreCoordinates(2.5, 135f,azimuthInDegrees),
                "SouthEast + 135"
            ),
            Pair(
                translateToARCoreCoordinates(2.0, 180f,azimuthInDegrees),
                "South + 180"
            ),
            Pair(
                translateToARCoreCoordinates(2.5, 225f,azimuthInDegrees),
                "SouthWest + 225"
            ),
            Pair(
                translateToARCoreCoordinates(2.0, 270f,azimuthInDegrees),
                "West + 270"
            ),
            Pair(
                translateToARCoreCoordinates(2.5, 315f,azimuthInDegrees),
                "NorthWest + 315"
            )
        )

        poseListSurrounding.map { validPose ->
            val anchor = arSceneView.session!!.createAnchor(validPose.first)
            val anchorNode = AnchorNode(anchor).apply {
                parent = arSceneView.scene
            }

            // Add the place in AR
            val placeNode = PlaceNode(context, validPose.second)
            Log.d(
                TAG,
                "placeAnchorNodeForNorth:bearing valid pose ${validPose.first}, ${validPose.second}"
            )

            placeNode.parent = anchorNode
        }

        Toast.makeText(context, " virtual compass", Toast.LENGTH_SHORT).show()
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

    private fun makePositiveRange(azimuth: Float): Float {
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

    private fun calibrateBearingToWorldNorth(bearing: Float, azimuthInDegrees: Float): Double {
        var trueNorth = azimuthInDegrees+180//ar 180 calibrate with true north
        trueNorth = makePositiveRange(trueNorth)
        Log.d(TAG, "calibrateBearingToWorldNorth: raw value: bearing $bearing azimuthInDegrees $azimuthInDegrees  trueNorth $trueNorth ")
        val positiveBearing = makePositiveRange(bearing)
        val bearingToTrueNorth = positiveBearing - trueNorth

        val clockwiseBearing = 360 - makePositiveRange(bearingToTrueNorth)

        Log.d(
            TAG,
            "calibrateBearingToWorldNorth: bearing ($positiveBearing) - trueNorth ($trueNorth) = (360-) ($clockwiseBearing)"
        )
        return clockwiseBearing.toDouble()
    }

    fun translateToARCoreCoordinates(
        distance: Double, bearing: Float,azimuthInDegrees:Float
    ): Pose {

        val calibrateBearing = calibrateBearingToWorldNorth(bearing, azimuthInDegrees)

        val x = distance * sin(Math.toRadians(calibrateBearing))
        val y = .4f
        val z = distance * cos(Math.toRadians(calibrateBearing))
        Log.d(TAG, "translateToARCoreCoordinates: x: $x, y: $y, z: $z")

        return Pose.makeTranslation(x.toFloat(), y, z.toFloat())
    }

    fun getPositionVector(azimuth: Float, currLatLng: LatLng, placeLatLng:LatLng): Vector3 {
        val heading = currLatLng.sphericalHeading(placeLatLng)
        val r = -2f
        val x = r * sin(azimuth + heading+ 180).toFloat()
        val y = 1f
        val z = r * cos(azimuth + heading).toFloat()
        return Vector3(x, y, z)
    }

    private fun translateToARCoreCoordinatesRelativeToVirtualNorth(
        distance: Double, bearing: Double
    ): Pose {

        val x = distance * sin(Math.toRadians(bearing))
        val y = .4f
        val z = distance * cos(Math.toRadians(bearing))
        Log.d(TAG, "translateToARCoreCoordinates: x: $x, y: $y, z: $z")

        return Pose.makeTranslation(x.toFloat(), y, z.toFloat())
    }

    // https://stackoverflow.com/questions/639695/how-to-convert-latitude-or-longitude-to-meters
    fun calculateDistanceAndBearing(
        userLat: Double, userLon: Double, poiLat: Double, poiLon: Double, azimuthInDegrees: Float
    ): Pair<Double, Double> {
        //user lat lon
        val lat1 = Math.toRadians(userLat)
        val lon1 = Math.toRadians(userLon)
        //poi lat lon
        val lat2 = Math.toRadians(poiLat)
        val lon2 = Math.toRadians(poiLon)
        //delta lat lon
//        val dLat = lat2 - lat1
//        val dLon = lon2 - lon1

        val dLat = Math.toRadians(poiLat - userLat)
        val dLon = Math.toRadians(poiLon - userLon)

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
        val bearing = calibrateBearingToWorldNorth(bearingFromTrueNorth.toFloat(),azimuthInDegrees)

        //get true north and calculate bearing
        Log.d(TAG, "calculateDistanceAndBearing: distance $distanceInMeters bearing $bearing")

        return Pair(distanceInMeters, bearing)
    }

    //calibrate bearing to real world north
    private fun calibrateBearingToWorldNorthV2(poiLatLng: LatLng, currentLocation:Location, orientationAngles:FloatArray): Double {
        val heading =
            LatLng(currentLocation.latitude, currentLocation.longitude).sphericalHeading(
                poiLatLng
            )
        val bearingToTrueNorth = orientationAngles[0] + heading
        Log.d(
            TAG,
            "calibrateBearingToWorldNorthV2: bearing (${orientationAngles[0]}) + trueNorth ($heading) = ($bearingToTrueNorth)"
        )
        return bearingToTrueNorth
    }

    private fun calculateScaleFactor1(distance: Double): Float {
        // You can adjust this formula as needed
        val scaleFactor = 1.0f / distance.toFloat()

        // Ensure a minimum scale to prevent the node from becoming too large
        return scaleFactor.coerceAtMost(MAX_SCALE_FACTOR)
    }

    fun calculateScaleFactor(distance: Double): Float {
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
}