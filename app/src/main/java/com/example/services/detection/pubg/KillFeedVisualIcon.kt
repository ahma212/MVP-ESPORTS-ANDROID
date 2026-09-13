package com.example.services.detection.pubg

/**
 * Visual icons recognized in PUBG Mobile kill feed bars.
 *
 * Implements authoritative recognition for:
 * 1. Weapon / Gun (M416, AKM, AWM, SCAR-L, Kar98k, M24, Groza, UMP45, DBS, etc.)
 * 2. Headshot
 * 3. Melee (Pan, Punch, Crowbar, Fist)
 * 4. Grenade (Frag grenade, sticky bomb)
 * 5. Vehicle (Car, Buggy, UAZ, Dacia, Motorcycle, Boat, explosion, runover)
 * 6. Molotov / Fire
 * 7. Red Zone (Bombardment / artillery marker)
 * 8. Playzone (Safe zone boundary icon)
 * 9. Blue Zone (Electric blue zone wave icon)
 * 10. Airstrike (Jet / airstrike bomb icon)
 * 11. Water / Drowning
 * 12. Fall damage
 * 13. Trap / Mine
 * 14. Suicide / Self-kill
 * 15. Knock / Down (Critical knock indicator between players)
 * 16. Finish (Helmet / elimination confirmation icon)
 * 17. Team-kill / Friendly-fire
 * 18. Revive (Green cross / helping hand)
 * 19. Unknown
 */
enum class KillFeedVisualIcon(
    val code: String,
    val displayName: String,
    val isEnvironment: Boolean = false,
    val isKnockIndicator: Boolean = false,
    val isFinishIndicator: Boolean = false,
    val isReviveIndicator: Boolean = false,
    val isTeamKillIndicator: Boolean = false,
    val isSelfKillIndicator: Boolean = false
) {
    GUN("GUN", "Weapon / Firearm"),
    HEADSHOT("HEADSHOT", "Headshot"),
    MELEE("MELEE", "Melee / Pan / Punch"),
    GRENADE("GRENADE", "Grenade / Frag"),
    VEHICLE("VEHICLE", "Vehicle / Runover"),
    MOLOTOV("MOLOTOV", "Molotov / Fire"),
    RED_ZONE("RED_ZONE", "Red Zone", isEnvironment = true),
    PLAYZONE("PLAYZONE", "Playzone", isEnvironment = true),
    BLUE_ZONE("BLUE_ZONE", "Blue Zone", isEnvironment = true),
    AIRSTRIKE("AIRSTRIKE", "Airstrike", isEnvironment = true),
    WATER("WATER", "Water / Drowning", isEnvironment = true),
    FALL("FALL", "Fall Damage", isEnvironment = true),
    TRAP_MINE("TRAP_MINE", "Trap / Mine", isEnvironment = true),
    SUICIDE("SUICIDE", "Suicide / Self-Kill", isSelfKillIndicator = true),
    KNOCK("KNOCK", "Knock / Down Icon", isKnockIndicator = true),
    FINISH_HELMET("FINISH_HELMET", "Finish / Helmet Icon", isFinishIndicator = true),
    TEAM_KILL("TEAM_KILL", "Team-Kill Icon", isTeamKillIndicator = true),
    FRIENDLY_FIRE("FRIENDLY_FIRE", "Friendly Fire Icon", isTeamKillIndicator = true),
    REVIVE("REVIVE", "Revive Icon", isReviveIndicator = true),
    UNKNOWN("UNKNOWN", "Unidentified Icon");

    fun toKillFeedCause(): KillFeedCause = when (this) {
        GUN -> KillFeedCause.GUN
        HEADSHOT -> KillFeedCause.HEADSHOT
        MELEE -> KillFeedCause.MELEE
        GRENADE -> KillFeedCause.GRENADE
        VEHICLE -> KillFeedCause.VEHICLE
        MOLOTOV -> KillFeedCause.MOLOTOV
        RED_ZONE -> KillFeedCause.RED_ZONE
        PLAYZONE -> KillFeedCause.PLAYZONE
        BLUE_ZONE -> KillFeedCause.BLUE_ZONE
        AIRSTRIKE -> KillFeedCause.AIRSTRIKE
        WATER -> KillFeedCause.WATER
        FALL -> KillFeedCause.FALL
        TRAP_MINE -> KillFeedCause.TRAP_MINE
        SUICIDE -> KillFeedCause.SUICIDE
        KNOCK -> KillFeedCause.KNOCK
        FINISH_HELMET -> KillFeedCause.FINISH_FROM_KNOCK
        TEAM_KILL -> KillFeedCause.TEAM_KILL
        FRIENDLY_FIRE -> KillFeedCause.FRIENDLY_FIRE
        REVIVE -> KillFeedCause.REVIVE
        UNKNOWN -> KillFeedCause.UNKNOWN
    }
}
