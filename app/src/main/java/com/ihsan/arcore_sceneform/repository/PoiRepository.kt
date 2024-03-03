package com.ihsan.arcore_sceneform.repository

import android.location.Location
import com.ihsan.arcore_sceneform.data.api.ApiResponse
import com.ihsan.arcore_sceneform.data.api.ApiService
import com.ihsan.arcore_sceneform.models.Coordinate
import com.ihsan.arcore_sceneform.models.Poi

class PoiRepository(private val apiResponse: ApiResponse) {
    private val apiService= ApiService(apiResponse)

    suspend fun fetchPoi(location: Location): List<Poi> {
        return apiService.fetchPoi(location.latitude, location.longitude, location)
    }

    suspend fun fetchPoiDirection(start: Coordinate, end: Coordinate): List<Coordinate> {
        return apiService.fetchPoiDirection(start, end)
    }
}