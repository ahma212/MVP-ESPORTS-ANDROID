package com.example.services.roi

import com.example.core.model.RoiRegion
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * In-memory implementation of IRoiConfigurationService.
 * Pre-seeds default esports ROIs (KILL_FEED, PLAYER_ID, TEAM_NUMBER) and supports
 * dynamic addition, deletion, and normalized editing.
 */
class InMemoryRoiConfigurationService : IRoiConfigurationService {

    private val defaultRois = listOf(
        RoiRegion.DEFAULT_KILL_FEED,
        RoiRegion.DEFAULT_PLAYER_ID,
        RoiRegion.DEFAULT_TEAM_NUMBER
    )

    private val _rois = MutableStateFlow<List<RoiRegion>>(defaultRois)
    override val rois: StateFlow<List<RoiRegion>> = _rois.asStateFlow()

    private val _selectedRoi = MutableStateFlow<RoiRegion?>(defaultRois.firstOrNull())
    override val selectedRoi: StateFlow<RoiRegion?> = _selectedRoi.asStateFlow()

    override fun selectRoi(id: String) {
        val found = _rois.value.find { it.id == id }
        if (found != null) {
            _selectedRoi.value = found
        }
    }

    override suspend fun saveRoi(roi: RoiRegion) {
        val currentList = _rois.value.toMutableList()
        val index = currentList.indexOfFirst { it.id == roi.id }
        if (index >= 0) {
            currentList[index] = roi
        } else {
            currentList.add(roi)
        }
        _rois.value = currentList
        if (_selectedRoi.value?.id == roi.id || _selectedRoi.value == null) {
            _selectedRoi.value = roi
        }
    }

    override suspend fun updateRoi(roi: RoiRegion) {
        saveRoi(roi)
    }

    override suspend fun deleteRoi(id: String) {
        val currentList = _rois.value.filter { it.id != id }
        _rois.value = currentList
        if (_selectedRoi.value?.id == id) {
            _selectedRoi.value = currentList.firstOrNull()
        }
    }

    override suspend fun toggleRoiEnabled(id: String) {
        val currentList = _rois.value.map {
            if (it.id == id) it.copy(enabled = !it.enabled, updatedAtMs = System.currentTimeMillis()) else it
        }
        _rois.value = currentList
        val updatedSelected = currentList.find { it.id == _selectedRoi.value?.id }
        if (updatedSelected != null) {
            _selectedRoi.value = updatedSelected
        }
    }

    override suspend fun resetToDefaults() {
        _rois.value = defaultRois
        _selectedRoi.value = defaultRois.firstOrNull()
    }

    companion object {
        @Volatile
        private var instance: InMemoryRoiConfigurationService? = null

        fun getInstance(): InMemoryRoiConfigurationService {
            return instance ?: synchronized(this) {
                instance ?: InMemoryRoiConfigurationService().also { instance = it }
            }
        }
    }
}
