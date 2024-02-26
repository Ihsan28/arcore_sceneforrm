package com.ihsan.arcore_sceneform.repository

import android.location.Location
import com.ihsan.arcore_sceneform.api.ApiService
import com.ihsan.arcore_sceneform.models.Coordinate
import com.ihsan.arcore_sceneform.models.Poi

class PoiRepository(private val apiService: ApiService) {

    suspend fun fetchPoi(location: Location): List<Poi>{
        val poiList = apiService.fetchPoi(location.latitude, location.longitude,location)
        return poiList
    }

    suspend fun fetchPoiDirection(start: Coordinate, end: Coordinate): List<Coordinate> {
        val poiDirectionResponse = apiService.fetchPoiDirection(start, end)
        return poiDirectionResponse

    }
}
