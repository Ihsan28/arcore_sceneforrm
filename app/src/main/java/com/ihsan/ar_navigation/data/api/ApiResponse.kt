package com.ihsan.ar_navigation.data.api

import com.ihsan.ar_navigation.models.Coordinate
import com.ihsan.ar_navigation.models.Poi


interface ApiResponse {
    fun onResponse(response: String)
    fun onError(error: String)
    fun onLoading(isLoading: Boolean)
    fun onPoiResponse(poiList: List<Poi>)
    fun onPoiDirectionResponse(coordinates: List<Coordinate>)
}