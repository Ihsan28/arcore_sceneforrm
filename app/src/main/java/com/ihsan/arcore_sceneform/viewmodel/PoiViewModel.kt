package com.ihsan.arcore_sceneform.viewmodel

import android.annotation.SuppressLint
import android.location.Location
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ihsan.arcore_sceneform.MainActivity
import com.ihsan.arcore_sceneform.api.ApiResponse
import com.ihsan.arcore_sceneform.api.ApiService
import com.ihsan.arcore_sceneform.models.Coordinate
import com.ihsan.arcore_sceneform.models.Poi
import com.ihsan.arcore_sceneform.repository.PoiRepository
import com.ihsan.arcore_sceneform.sensorservice.Compass
import com.ihsan.arcore_sceneform.sensorservice.CompassListener
import kotlinx.coroutines.launch
import okhttp3.internal.wait

private const val TAG = "PoiViewModel"

class PoiViewModel() : ViewModel() {
    private val _poiList = MutableLiveData<List<Poi>>()
    val poiList: LiveData<List<Poi>>
        get() = _poiList

    private val _poiDirection = MutableLiveData<List<Coordinate>>()
    val poiDirection: LiveData<List<Coordinate>>
        get() = _poiDirection

    private lateinit var viewmodel: PoiViewModel

    private lateinit var currentLocation: Location
    private var azimuthInDegrees = 0.0f

    private var orientationAngles = FloatArray(3)

    //compass
    private lateinit var compass: Compass

    private lateinit var apiResponse: ApiResponse
    private var apiService: ApiService
    private var repository: PoiRepository

    init {
        init().wait()
        apiService = ApiService(apiResponse)
        repository = PoiRepository(apiService)
    }

    private fun init(): Unit {
        this.viewmodel = PoiViewModel()
        this.compass = Compass(MainActivity(), object : CompassListener {
            @SuppressLint("SetTextI18n")
            override fun onNewAzimuth(
                aziInDeg: Float,
                magneticDeclination: Float,
                orientation: FloatArray
            ) {
                orientationAngles = orientation

                azimuthInDegrees = aziInDeg
            }

            override fun getCurrentLocation(location: Location) {
                // Place the anchor node
                if (currentLocation == null) {
                    currentLocation = location
                    apiService = ApiService(object : ApiResponse {
                        override fun onResponse(response: String) {
                            Log.d(TAG, "onResponse: $response")
                        }

                        override fun onError(error: String) {
                            Log.d(TAG, "onError: $error")
                        }

                        override fun onLoading(isLoading: Boolean) {
                            Log.d(TAG, "onLoading: $isLoading")
                        }

                        override fun onPoiResponse(poiList: List<Poi>) {
                            Log.d(TAG, "onPoiResponse: ${poiList.size}")

                        }

                        override fun onPoiDirectionResponse(coordinates: List<Coordinate>) {
                            Log.d(TAG, "onPoiDirectionResponse: $coordinates")

                        }
                    })
                }
                return
            }
        })
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
