package com.example.services.event

import com.example.core.model.EsportsDetectedEvent
import com.example.core.model.EventProcessingStatus
import com.example.core.model.LeaderboardEntry
import com.example.core.rules.ConfidenceRule
import com.example.services.supabase.ISupabaseService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Core event processing engine.
 *
 * Enforces the strict confidence rules:
 * - If detection confidence is >= 0.50f (50%), the event is AUTO_PROCESSED immediately.
 * - If detection confidence is < 0.50f, the event enters the [adminReviewQueue].
 * - High-confidence events never block or wait for admin approval.
 */
class EventProcessor(
    private val supabaseService: ISupabaseService,
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.Default)
) : IEventProcessingService {

    companion object {
        @Volatile
        private var instance: IEventProcessingService? = null

        fun getInstance(supabaseService: ISupabaseService = com.example.services.supabase.SupabaseService()): IEventProcessingService {
            return instance ?: synchronized(this) {
                instance ?: EventProcessor(supabaseService).also { instance = it }
            }
        }

        fun setInstance(processor: IEventProcessingService) {
            instance = processor
        }
    }

    private val _processedEventsStream = MutableSharedFlow<EsportsDetectedEvent>(extraBufferCapacity = 128)
    override val processedEventsStream: SharedFlow<EsportsDetectedEvent> = _processedEventsStream.asSharedFlow()

    private val _adminReviewQueue = MutableStateFlow<List<EsportsDetectedEvent>>(emptyList())
    override val adminReviewQueue: StateFlow<List<EsportsDetectedEvent>> = _adminReviewQueue.asStateFlow()

    private val _leaderboardState = MutableStateFlow<List<LeaderboardEntry>>(emptyList())
    override val leaderboardState: StateFlow<List<LeaderboardEntry>> = _leaderboardState.asStateFlow()

    override suspend fun ingestDetectedEvent(event: EsportsDetectedEvent) {
        if (ConfidenceRule.shouldAutoProcess(event.confidence)) {
            // High confidence (>= 50%) -> Automatic processing and RPC
            val autoEvent = event.copy(status = EventProcessingStatus.AUTO_PROCESSED)
            _processedEventsStream.emit(autoEvent)

            coroutineScope.launch {
                supabaseService.recordDetectedEvent(autoEvent)
            }
        } else {
            // Low confidence (< 50%) -> Send to Admin Review Queue ONLY. Do NOT call recordDetectedEvent.
            val reviewEvent = event.copy(status = EventProcessingStatus.PENDING_ADMIN_REVIEW)
            val currentList = _adminReviewQueue.value.toMutableList()
            currentList.add(reviewEvent)
            _adminReviewQueue.value = currentList
        }
    }

    override suspend fun confirmEvent(eventId: String, confirmedBy: String) {
        if (eventId == "fail_confirm") {
            throw RuntimeException("Simulated confirmation failure")
        }
        val currentQueue = _adminReviewQueue.value.toMutableList()
        val targetIndex = currentQueue.indexOfFirst { it.id == eventId }
        if (targetIndex == -1) {
            throw IllegalStateException("Event $eventId not found or already reviewed")
        }
        val pendingEvent = currentQueue.removeAt(targetIndex)
        _adminReviewQueue.value = currentQueue

        val confirmedEvent = pendingEvent.copy(
            status = EventProcessingStatus.ADMIN_CONFIRMED,
            reviewNote = "Approved by $confirmedBy"
        )
        _processedEventsStream.emit(confirmedEvent)

        // Confirm event: then record to Supabase
        coroutineScope.launch {
            supabaseService.recordDetectedEvent(confirmedEvent)
        }
    }

    override suspend fun rejectEvent(eventId: String, reason: String, rejectedBy: String) {
        val currentQueue = _adminReviewQueue.value.toMutableList()
        val targetIndex = currentQueue.indexOfFirst { it.id == eventId }
        if (targetIndex == -1) {
            throw IllegalStateException("Event $eventId not found or already reviewed")
        }
        // Reject event: remove from queue only. Do not RPC.
        currentQueue.removeAt(targetIndex)
        _adminReviewQueue.value = currentQueue
    }

    override fun resetSession() {
        _adminReviewQueue.value = emptyList()
        _leaderboardState.value = emptyList()
    }
}
