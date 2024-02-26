package com.ihsan.arcore_sceneform.data

import com.ihsan.arcore_sceneform.api.ApiResponse
import com.ihsan.arcore_sceneform.api.ApiService
import com.ihsan.arcore_sceneform.data.models.Results

object Common {
    var currentResult: Results? = null

    private val GOOGLE_API_URL="https://maps.googleapis.com/"
    private val GEOAPIFY_API_URL="https://api.geoapify.com/"
    val googleApiService: ApiResponse//interfce
        get()=RetrofitClient.getClient(GOOGLE_API_URL).create(ApiResponse::class.java)
    val geoapifyApiService: ApiService//interface
        get()=RetrofitClient.getClient(GEOAPIFY_API_URL).create(ApiService::class.java)
}