package com.ihsan.arcore_sceneform.api

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.util.Log
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.ihsan.arcore_sceneform.MainActivity
import com.ihsan.arcore_sceneform.models.Coordinate
import com.ihsan.arcore_sceneform.models.Poi
import com.ihsan.arcore_sceneform.models.PoiDirectionResponse
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.IOException

private const val TAG = "ApiService"

class ApiService(val currentLocation:Location,val apiResponse: ApiResponse) {

    val api = "037eff850f2e4b11b0ffacb5a381c7f2"

    init {
            fetchPoi(currentLocation.latitude, currentLocation.longitude)
    }

    private fun fetchPoi(lat: Double, lng: Double, radius: Int = 5000) {
        val url =
            "https://api.geoapify.com/v2/places?categories=religion&filter=circle:$lng,$lat,$radius&bias=proximity:$lng,$lat&limit=20&apiKey=$api"
        Log.d(TAG, "fetchPoi: $url")
        val client = OkHttpClient()
        val request = Request.Builder().url(url).build()

        client.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                if (response.isSuccessful) {
                    val responseBody = response.body?.string()
                    // Parse the response and cache the POIs
                    val poiList = parsePoiResponse(responseBody ?: "")

                    Log.d(TAG, "onResponse: $poiList")
                    apiResponse.onPoiResponse(poiList)

                    fetchPoiDirection(
                        Coordinate(
                            currentLocation!!.latitude, currentLocation!!.longitude
                        ), Coordinate(
                            poiList[0].latitude, poiList[0].longitude
                        )
                    )
                    return
                } else {
                    Log.e(TAG, "fetchPoi unsuccessful: ${response.code}")
                    apiResponse.onError(response.message)
                }
            }

            override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                apiResponse.onError(e.message ?: "")
                Log.e(TAG, "fetchPoi failed: ${e.message}")
            }
        })
    }

    fun parsePoiResponse(jsonResponse: String): List<Poi> {
        val poiList = mutableListOf<Poi>()

        try {
            val jsonObject = Gson().fromJson(jsonResponse, JsonObject::class.java)
            val featuresArray: JsonArray? = jsonObject.getAsJsonArray("features")

            featuresArray?.forEach { feature ->
                val properties = feature?.asJsonObject?.getAsJsonObject("properties")
                val geometry = feature?.asJsonObject?.getAsJsonObject("geometry")
                val coordinates = geometry?.getAsJsonArray("coordinates")

                val id = properties?.get("place_id")?.asString
                val name = properties?.get("name")?.asString
                val latitude = coordinates?.get(1)?.asDouble
                val longitude = coordinates?.get(0)?.asDouble
                val distance = properties?.get("distance")?.asDouble

                if (name != null && latitude != null && longitude != null && distance != null && id != null) {
                    val poi = Poi(id, name, latitude, longitude, distance)
                    poiList.add(poi)
                }
            }
        } catch (e: Exception) {
            apiResponse.onError(e.message ?: "")
            Log.e(TAG, "Error parsing POI response: ${e.message}")
        }

        return poiList
    }

    fun parsePoiDirectionResponse(jsonResponse: String): PoiDirectionResponse {
        val gson = Gson()
        return gson.fromJson(jsonResponse, PoiDirectionResponse::class.java)
    }

    private fun fetchPoiDirection(start: Coordinate, end: Coordinate) {
        Log.d(TAG, "fetchPoiDirection: $start ////// $end")

        val url =
            "https://api.geoapify.com/v1/routing?waypoints=${start.lat},${start.lon}|${end.lat},${end.lon}&mode=walk&apiKey=$api"

        Log.d(TAG, "fetchPoi: $url")
        val client = OkHttpClient()
        val request = Request.Builder().url(url).build()

        client.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                if (response.isSuccessful) {
                    val responseBody = response.body?.string()
                    Log.d(TAG, "fetchPoi direction response: $responseBody")

                    // Parse the response using the new data classes
                    try {
                        val poiResponse = parsePoiDirectionResponse(responseBody ?: "")
                        Log.d(TAG, "fetchPoi direction response: $poiResponse")
                        val legs = poiResponse.features[0].properties.legs
                        val coordinates =
                            poiResponse.features.flatMap { it.geometry.coordinates.flatten() }
                                .map { Coordinate(it[1], it[0]) }

                        apiResponse.onPoiDirectionResponse(coordinates)

                    } catch (e: Exception) {
                        apiResponse.onError(e.message ?: "")
                        Log.e(TAG, "Error parsing POI response: ${e.message}")
                    }

                } else {
                    apiResponse.onError(response.message)
                    Log.e(TAG, "fetchPoi unsuccessful: ${response.code}")
                }
            }

            override fun onFailure(call: okhttp3.Call, e: IOException) {
                apiResponse.onError(e.message ?: "")
                Log.e(TAG, "fetchPoi failed: ${e.message}")
            }
        })
    }
}