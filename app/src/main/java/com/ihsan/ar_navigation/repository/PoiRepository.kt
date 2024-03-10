package com.ihsan.ar_navigation.repository

import android.location.Location
import com.ihsan.ar_navigation.data.api.ApiResponse
import com.ihsan.ar_navigation.data.api.ApiService
import com.ihsan.ar_navigation.models.Coordinate
import com.ihsan.ar_navigation.models.Poi

class PoiRepository(private val apiResponse: ApiResponse) {
    private val apiService= ApiService(apiResponse)

    suspend fun fetchPoi(location: Location): List<Poi> {
        return apiService.fetchPoi(location.latitude, location.longitude, location)
    }

    suspend fun fetchPoiDirection(start: Coordinate, end: Coordinate): List<Coordinate> {
        return apiService.fetchPoiDirection(start, end)
    }
}