package com.example.services.detection.pubg

/**
 * Kill feed cause classification according to the authoritative PUBG Kill Feed Detection Rules Table.
 *
 * Rules:
 * 1. GUN: Normal Kill by weapon (Point awarded to Killer)
 * 2. GRENADE: Kill by grenade (Point awarded to Killer)
 * 3. VEHICLE: Kill by vehicle (Point awarded to Killer if valid attacker)
 * 4. MOLOTOV: Kill by fire / molotov (Point awarded to Killer)
 * 5. RED_ZONE: Kill by Red Zone (No player gets point)
 * 6. PLAYZONE: Kill by Playzone (No player gets point)
 * 7. BLUE_ZONE: Kill by Blue Zone (No player gets point)
 * 8. AIRSTRIKE: Kill by Airstrike (No player gets point)
 * 9. WATER: Kill by Drowning (No player gets point)
 * 10. FALL: Kill by Fall Damage (No player gets point)
 * 11. TRAP_MINE: Kill by Trap / Mine (No player gets point)
 * 12. SUICIDE: Self Kill / Suicide (No player gets point)
 * 13. KNOCK_GUN: Player B Knocked by A with gun (Not a kill, 0 points)
 * 14. KNOCK_GRENADE: Player B Knocked by A with grenade (Not a kill, 0 points)
 * 15. KNOCK_GENERIC: Knock with no weapon shown (Not a kill, 0 points)
 * 16. FINISH_FROM_KNOCK: Player B Finished from Knock (Helmet icon, not a new kill)
 * 17. TEAM_KILL: Team Kill (0 points)
 * 18. FRIENDLY_FIRE: Friendly Fire (0 points)
 * 19. KNOCK_ANY: Downed with any icon (Not finished, 0 points)
 * 20. REVIVE: Player B Revived (Not a kill)
 */
enum class KillFeedCause(val ruleNumber: Int, val description: String, val isEnvironment: Boolean) {
    GUN(1, "Normal Kill by weapon", false),
    GRENADE(2, "Kill by grenade", false),
    VEHICLE(3, "Kill by vehicle", false),
    MOLOTOV(4, "Kill by fire / molotov", false),
    MELEE(1, "Kill by melee / punch / pan", false),
    HEADSHOT(1, "Kill by headshot", false),
    RED_ZONE(5, "Kill by Red Zone", true),
    PLAYZONE(6, "Kill by Playzone", true),
    BLUE_ZONE(7, "Kill by Blue Zone", true),
    AIRSTRIKE(8, "Kill by Airstrike", true),
    WATER(9, "Kill by Drowning", true),
    FALL(10, "Kill by Fall Damage", true),
    TRAP_MINE(11, "Kill by Trap / Mine", true),
    SUICIDE(12, "Self Kill (Suicide)", false),
    KNOCK(15, "Player Knocked / Downed", false),
    FINISH_FROM_KNOCK(16, "Player Finished From Knock", false),
    TEAM_KILL(17, "Team Kill", false),
    FRIENDLY_FIRE(18, "Friendly Fire", false),
    REVIVE(20, "Player Revived", false),
    UNKNOWN(0, "Unclassified", false);

    val isKnockEvent: Boolean
        get() = this == KNOCK

    val isReviveEvent: Boolean
        get() = this == REVIVE

    fun toVisualIcon(): KillFeedVisualIcon = when (this) {
        GUN -> KillFeedVisualIcon.GUN
        GRENADE -> KillFeedVisualIcon.GRENADE
        VEHICLE -> KillFeedVisualIcon.VEHICLE
        MOLOTOV -> KillFeedVisualIcon.MOLOTOV
        MELEE -> KillFeedVisualIcon.MELEE
        HEADSHOT -> KillFeedVisualIcon.HEADSHOT
        RED_ZONE -> KillFeedVisualIcon.RED_ZONE
        PLAYZONE -> KillFeedVisualIcon.PLAYZONE
        BLUE_ZONE -> KillFeedVisualIcon.BLUE_ZONE
        AIRSTRIKE -> KillFeedVisualIcon.AIRSTRIKE
        WATER -> KillFeedVisualIcon.WATER
        FALL -> KillFeedVisualIcon.FALL
        TRAP_MINE -> KillFeedVisualIcon.TRAP_MINE
        SUICIDE -> KillFeedVisualIcon.SUICIDE
        KNOCK -> KillFeedVisualIcon.KNOCK
        FINISH_FROM_KNOCK -> KillFeedVisualIcon.FINISH_HELMET
        TEAM_KILL -> KillFeedVisualIcon.TEAM_KILL
        FRIENDLY_FIRE -> KillFeedVisualIcon.FRIENDLY_FIRE
        REVIVE -> KillFeedVisualIcon.REVIVE
        UNKNOWN -> KillFeedVisualIcon.UNKNOWN
    }
}
