package com.ihsan.arcore_sceneform.api

import com.ihsan.arcore_sceneform.models.Poi

interface ApiResponse {
    fun onResponse(response: String)
    fun onError(error: String)
    fun onLoading(isLoading: Boolean)
    fun onPoiResponse(poiList: List<Poi>)
    fun onPoiDirectionResponse(coordinates: List<List<Double>>)
}