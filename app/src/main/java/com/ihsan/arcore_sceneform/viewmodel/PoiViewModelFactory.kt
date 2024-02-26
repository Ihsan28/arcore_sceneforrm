package com.ihsan.arcore_sceneform.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.ihsan.arcore_sceneform.repository.PoiRepository

class PoiViewModelFactory(private val repository: PoiRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(PoiViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return PoiViewModel() as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}