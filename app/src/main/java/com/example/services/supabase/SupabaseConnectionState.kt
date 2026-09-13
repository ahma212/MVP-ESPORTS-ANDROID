package com.example.services.supabase

/**
 * Connection and synchronization state with Supabase.
 */
sealed class SupabaseConnectionState {
    object NotConnected : SupabaseConnectionState()
    object Connecting : SupabaseConnectionState()
    data class Connected(val projectUrl: String, val activeSessionId: String? = null) : SupabaseConnectionState()
    data class SyncError(val message: String, val isRecoverable: Boolean = true) : SupabaseConnectionState()
}
