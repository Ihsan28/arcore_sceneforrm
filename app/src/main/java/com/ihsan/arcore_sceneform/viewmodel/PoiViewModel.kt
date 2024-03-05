package com.ihsan.arcore_sceneform.viewmodel

import android.app.Application
import android.location.Location
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.ihsan.arcore_sceneform.data.api.ApiResponse
import com.ihsan.arcore_sceneform.models.Coordinate
import com.ihsan.arcore_sceneform.models.Poi
import com.ihsan.arcore_sceneform.repository.PoiRepository
import com.ihsan.arcore_sceneform.utils.sensorservice.Compass
import com.ihsan.arcore_sceneform.utils.sensorservice.CompassListener
import kotlinx.coroutines.delay
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

    private val compass: Compass
    private var location: Location? = null

    private val _azimuth = MutableLiveData<Float>()
    val azimuth: LiveData<Float>
        get() = _azimuth
    private val _magneticDeclination = MutableLiveData<Float>()
    val magneticDeclination: LiveData<Float>
        get() = _magneticDeclination
    private val _orientation = MutableLiveData<FloatArray>()
    val orientation: LiveData<FloatArray>
        get() = _orientation

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

        compass = Compass(application, object : CompassListener {
            override fun onNewAzimuth(
                aziInDeg: Float,
                magneticDeclination: Float,
                orientation: FloatArray
            ) {
                _azimuth.value = aziInDeg
                _magneticDeclination.value = magneticDeclination
                _orientation.value = orientation
            }

            override fun getCurrentLocation(location: Location) {
                this@PoiViewModel.location=location
                fetchPoi()
            }
        })
    }

    fun fetchPoi(location:Location?=this.location!!) {
        if (location==null && this.location==null){
            Log.e(TAG, "fetchPoi: location is null")
            viewModelScope.launch {
                var i=0
                val limit=5
                while (this@PoiViewModel.location==null && i<limit){
                    delay(500)
                    i++
                }
                //if location is still null then return
                if (this@PoiViewModel.location==null)
                {
                    Toast.makeText(getApplication(), "Location is null", Toast.LENGTH_SHORT).show()
                    Log.e(TAG, "fetchPoi: location is null")
                    return@launch
                }

                //if location is not null then fetch poi
                fetchPoi()
            }
            return
        }else if (location==null){
            Toast.makeText(getApplication(), "Location is null", Toast.LENGTH_SHORT).show()
            Log.e(TAG, "fetchPoi: location is null")
            return
        }

        viewModelScope.launch {
            val poiList = repository.fetchPoi(location)
            _poiList.value = poiList
            Log.d(TAG, "fetchPoi: $poiList")
        }
    }

    fun fetchPoiDirection(start: Coordinate, end: Coordinate) {
        viewModelScope.launch {
            val poiDirection = repository.fetchPoiDirection(start, end)
            _poiDirection.value = poiDirection
        }
    }
}