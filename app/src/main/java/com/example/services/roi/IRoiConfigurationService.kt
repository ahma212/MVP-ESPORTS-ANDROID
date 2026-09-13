package com.example.services.roi

import com.example.core.model.RoiRegion
import kotlinx.coroutines.flow.StateFlow

/**
 * Persistence and state management interface for Admin-defined Regions of Interest (ROI).
 * Decouples the UI and CV pipelines from any specific database backend.
 */
interface IRoiConfigurationService {
    val rois: StateFlow<List<RoiRegion>>
    val selectedRoi: StateFlow<RoiRegion?>

    fun selectRoi(id: String)
    suspend fun saveRoi(roi: RoiRegion)
    suspend fun updateRoi(roi: RoiRegion)
    suspend fun deleteRoi(id: String)
    suspend fun toggleRoiEnabled(id: String)
    suspend fun resetToDefaults()
}
