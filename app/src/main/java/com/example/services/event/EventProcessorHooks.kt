package com.example.services.event

import com.example.services.streaming.LocalLiveRuntimeManager
import com.example.services.supabase.ISupabaseService
import com.example.services.supabase.SupabaseConnectionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Utility to hook up LocalLiveRuntimeManager to EventProcessor.
 *
 * Rules:
 * - When Supabase is Connected, do NOT call LocalLiveRuntimeManager.processDetectedEvent.
 * - Local runtime must not change overlay scores if Supabase teams exist.
 */
object EventProcessorHooks {
    fun attachTo(
        processor: IEventProcessingService,
        supabaseService: ISupabaseService? = null,
        scope: CoroutineScope = CoroutineScope(Dispatchers.Default)
    ) {
        scope.launch {
            processor.processedEventsStream.collect { event ->
                val isConnected = supabaseService?.connectionState?.value is SupabaseConnectionState.Connected
                val hasSupabaseTeams = (supabaseService?.teamsCount?.value ?: 0) > 0 ||
                        (supabaseService?.liveLeaderboardStream?.value?.isNotEmpty() == true)

                if (isConnected || hasSupabaseTeams) {
                    // Supabase is the single source of truth for scores and standings.
                    // Local runtime must not change overlay scores when Supabase is connected or teams exist.
                    return@collect
                }

                LocalLiveRuntimeManager.processDetectedEvent(event)
            }
        }
    }
}
