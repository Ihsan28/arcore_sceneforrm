package com.ihsan.arcore_sceneform.data

import com.ihsan.arcore_sceneform.data.api.ApiResponse
import com.ihsan.arcore_sceneform.data.api.ApiService
import com.ihsan.arcore_sceneform.models.googleApimodels.Results

object Common {
    var currentResult: Results? = null

    private val GOOGLE_API_URL="https://maps.googleapis.com/"
    private val GEOAPIFY_API_URL="https://api.geoapify.com/"
    val googleApiService: ApiResponse
        //interfce
        get()=RetrofitClient.getClient(GOOGLE_API_URL).create(ApiResponse::class.java)
    val geoapifyApiService: ApiService
        //interface
        get()=RetrofitClient.getClient(GEOAPIFY_API_URL).create(ApiService::class.java)
}