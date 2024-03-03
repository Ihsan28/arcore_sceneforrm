package com.ihsan.arcore_sceneform.viewmodel

import android.app.Application
import android.location.Location
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.ihsan.arcore_sceneform.data.api.ApiResponse
import com.ihsan.arcore_sceneform.models.Coordinate
import com.ihsan.arcore_sceneform.models.Poi
import com.ihsan.arcore_sceneform.repository.PoiRepository
import kotlinx.coroutines.launch

private const val TAG = "PoiViewModel"

class PoiViewModel(application: Application) : AndroidViewModel(application) {
    private val _poiList = MutableLiveData<List<Poi>>()
    val poiList: LiveData<List<Poi>>
        get() = _poiList

    private val _poiDirection = MutableLiveData<List<Coordinate>>()
    val poiDirection: LiveData<List<Coordinate>>
        get() = _poiDirection

    private var apiResponse: ApiResponse
    private var repository: PoiRepository

    init {
        apiResponse = object : ApiResponse {
            override fun onResponse(response: String) {
                Log.d(TAG, "onResponse: $response")
            }

            override fun onError(error: String) {
                Log.e(TAG, "onError: $error")
            }

            override fun onLoading(isLoading: Boolean) {
                Log.d(TAG, "onLoading: $isLoading")
            }

            override fun onPoiResponse(poiList: List<Poi>) {
                Log.d(TAG, "onPoiResponse: $poiList")
                _poiList.value = poiList
            }

            override fun onPoiDirectionResponse(coordinates: List<Coordinate>) {
                Log.d(TAG, "onPoiDirectionResponse: $coordinates")
                _poiDirection.value = coordinates
            }
        }

        repository = PoiRepository(apiResponse)
    }

    fun fetchPoi(location: Location) {
        viewModelScope.launch {
            val poiList = repository.fetchPoi(location)
            _poiList.value = poiList
        }
    }

    fun fetchPoiDirection(start: Coordinate, end: Coordinate) {
        viewModelScope.launch {
            val poiDirection = repository.fetchPoiDirection(start, end)
            _poiDirection.value = poiDirection
        }
    }
}
