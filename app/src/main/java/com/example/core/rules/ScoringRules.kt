package com.example.core.rules

/**
 * Standard esports PUBG Mobile scoring matrix (Official Esports point system).
 */
object ScoringRules {
    const val POINTS_PER_KILL = 1

    /**
     * Official PUBG Mobile Placement Points Matrix (16 Teams):
     * 1st: 10 pts
     * 2nd: 6 pts
     * 3rd: 5 pts
     * 4th: 4 pts
     * 5th: 3 pts
     * 6th: 2 pts
     * 7th: 1 pt
     * 8th: 1 pt
     * 9th - 16th: 0 pts
     */
    fun getPlacementPoints(placement: Int): Int {
        return when (placement) {
            1 -> 10
            2 -> 6
            3 -> 5
            4 -> 4
            5 -> 3
            6 -> 2
            7 -> 1
            8 -> 1
            else -> 0
        }
    }

    /**
     * Calculates total tournament points for a team.
     */
    fun calculateTotalPoints(kills: Int, placement: Int?): Int {
        val killPoints = kills * POINTS_PER_KILL
        val placementPoints = placement?.let { getPlacementPoints(it) } ?: 0
        return killPoints + placementPoints
    }
}
