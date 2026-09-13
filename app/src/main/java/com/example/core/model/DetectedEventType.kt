package com.example.core.model

/**
 * Platform-independent enum representing detected in-game PUBG Mobile events.
 *
 * Supported event types for Phase 4C:
 * - KNOCK: In-game player knock down
 * - KILL: In-game kill
 * - ELIMINATION: Total player elimination
 * - REVIVE: Player revived by teammate
 * - POINTS_ADJUSTMENT: Manual/rule-based score point adjustment
 * - KILL_ADJUSTMENT: Kill score adjustment
 * - PLACEMENT: Team placement confirmation
 * - WINNER: Match winner / Chicken Dinner
 * - OTHER: Auxiliary / unclassified event
 */
enum class DetectedEventType {
    KNOCK,
    KILL,
    ELIMINATION,
    REVIVE,
    POINTS_ADJUSTMENT,
    KILL_ADJUSTMENT,
    PLACEMENT,
    WINNER,
    OTHER
}
