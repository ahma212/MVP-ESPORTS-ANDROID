package com.example.services.detection.pubg

import com.example.core.model.DetectionEvidence

/**
 * Visual evidence extractor for PUBG Mobile kill feed crops.
 *
 * Extracts:
 * - LEFT_TEXT (Player A / Clan / System)
 * - ICONS[] (Weapon, Grenade, Vehicle, Molotov, Environment, Knock, Helmet, etc.)
 * - HAS_KNOCK (Boolean)
 * - HAS_FINISH_HELMET (Boolean)
 * - RIGHT_TEXT (Player B / Victim)
 * - FLAGS[] (Flags to be ignored)
 * - CLARITY & TRANSITION status
 */
object PUBGKillFeedVisualParser {

    private var registeredVisionEngine: IPUBGVisionEngine? = null

    fun registerVisionEngine(engine: IPUBGVisionEngine?) {
        registeredVisionEngine = engine
    }

    fun getRegisteredVisionEngine(): IPUBGVisionEngine? = registeredVisionEngine

    data class VisualExtraction(
        val rawLeft: String,
        val rawRight: String,
        val cause: KillFeedCause,
        val hasKnock: Boolean,
        val hasFinishHelmet: Boolean,
        val hasTeamKillIcon: Boolean,
        val hasReviveIcon: Boolean,
        val flagsExtracted: List<String>,
        val clarityScore: Float,
        val isTransitioning: Boolean,
        val detectedIcon: KillFeedVisualIcon = cause.toVisualIcon(),
        val iconConfidence: Float = 0.90f,
        val lineIndex: Int = 0,
        val rawLineText: String = "",
        val iconMethod: String = "PARSER_DEFAULT",
        val iconReason: String = ""
    )

    /**
     * Parses all visible kill-feed lines independently from [evidence].
     * Implements Requirement 1 & 5 of K4-A.
     */
    suspend fun parseMultipleLines(evidence: DetectionEvidence): List<VisualExtraction> {
        val lineSlices = PUBGKillFeedLineSegmenter.segmentLines(evidence)
        if (lineSlices.isEmpty()) return emptyList()

        return lineSlices.mapNotNull { slice ->
            parseLineSlice(slice, evidence)
        }
    }

    /**
     * Parses a single segmented line slice into a structured [VisualExtraction].
     */
    fun parseLineSlice(
        slice: PUBGKillFeedLineSegmenter.KillFeedLineSlice,
        parentEvidence: DetectionEvidence? = null
    ): VisualExtraction? {
        val raw = slice.rawText.trim()
        val meta = slice.metadata

        val rawLeft = meta["left_text"]?.toString() ?: meta["raw_left"]?.toString() ?: ""
        val rawRight = meta["right_text"]?.toString() ?: meta["raw_right"]?.toString() ?: ""
        val metaCause = meta["cause"]?.toString() ?: meta["icon"]?.toString() ?: meta["weapon"]?.toString()
        val metaKnock = meta["has_knock"]?.toString()?.toBooleanStrictOrNull() ?: false
        val metaFinish = meta["has_finish"]?.toString()?.toBooleanStrictOrNull() ?: false
        val metaClarity = meta["clarity"]?.toString()?.toFloatOrNull() ?: 0.90f
        val transitioning = meta["transitioning"]?.toString()?.toBooleanStrictOrNull() ?: false

        // Run multi-modal icon detector
        val iconResult = PUBGVisualIconDetector.detectIcon(
            rawText = raw.ifBlank { "${rawLeft} -> ${rawRight}" },
            pixelBuffer = slice.subBuffer ?: parentEvidence?.croppedBuffer,
            bufferWidth = slice.subWidth.takeIf { it > 0 } ?: (parentEvidence?.croppedWidth ?: 0),
            bufferHeight = slice.subHeight.takeIf { it > 0 } ?: (parentEvidence?.croppedHeight ?: 0),
            iconSubRect = slice.subRect,
            metadataCause = metaCause,
            metadataHasKnock = metaKnock,
            metadataHasFinish = metaFinish
        )

        // Parse left and right tokens if not explicitly given
        val parsedExtraction = if (rawLeft.isNotBlank() || rawRight.isNotBlank()) {
            val flags = extractFlags(rawLeft) + extractFlags(rawRight)
            val effectiveCause = if (iconResult.isKnock) {
                // If specific weapon/cause was provided (e.g. GRENADE, GUN), preserve it; otherwise KNOCK
                if (iconResult.icon != KillFeedVisualIcon.KNOCK && iconResult.icon != KillFeedVisualIcon.UNKNOWN) {
                    iconResult.icon.toKillFeedCause()
                } else {
                    KillFeedCause.KNOCK
                }
            } else if (iconResult.isFinish) {
                KillFeedCause.FINISH_FROM_KNOCK
            } else {
                iconResult.icon.toKillFeedCause()
            }

            VisualExtraction(
                rawLeft = rawLeft,
                rawRight = rawRight,
                cause = effectiveCause,
                hasKnock = iconResult.isKnock,
                hasFinishHelmet = iconResult.isFinish,
                hasTeamKillIcon = iconResult.isTeamKill,
                hasReviveIcon = iconResult.isRevive,
                flagsExtracted = flags,
                clarityScore = metaClarity,
                isTransitioning = transitioning,
                detectedIcon = iconResult.icon,
                iconConfidence = iconResult.confidence,
                lineIndex = slice.lineIndex,
                rawLineText = raw,
                iconMethod = iconResult.detectionMethod,
                iconReason = iconResult.reason
            )
        } else if (raw.isNotBlank()) {
            val fromLine = parseRawFeedLine(raw, metaClarity, transitioning)
            fromLine.copy(
                detectedIcon = iconResult.icon,
                iconConfidence = iconResult.confidence,
                hasKnock = iconResult.isKnock || fromLine.hasKnock,
                hasFinishHelmet = iconResult.isFinish || fromLine.hasFinishHelmet,
                lineIndex = slice.lineIndex,
                rawLineText = raw,
                iconMethod = iconResult.detectionMethod,
                iconReason = iconResult.reason
            )
        } else {
            null
        }

        return parsedExtraction
    }

    /**
     * Parses visual evidence metadata or pixel crop attributes into a structured [VisualExtraction].
     */
    suspend fun parseEvidence(evidence: DetectionEvidence): VisualExtraction? {
        val lines = parseMultipleLines(evidence)
        return lines.firstOrNull()
    }

    /**
     * Parses a composite raw OCR line containing names, icons, knock tokens, and flags.
     * Example formats:
     * - "🇮🇩 Mortal 🔫 🇭🇰 Scout"
     * - "[FLAG] Mortal [M416] [KNOCK] [FLAG] Scout"
     * - "Playzone [ZONE] 🇵🇭 Runner"
     * - "PlayerA [GRENADE] PlayerA"
     */
    fun parseRawFeedLine(
        line: String,
        clarity: Float = 0.90f,
        isTransitioning: Boolean = false
    ): VisualExtraction {
        var remaining = line.substringBefore("\n").substringBefore("\r").trim()

        val hasKnock = remaining.contains("[KNOCK]", ignoreCase = true) ||
                remaining.contains("KNOCK ICON", ignoreCase = true) ||
                remaining.contains("knocked", ignoreCase = true) ||
                remaining.contains("downed", ignoreCase = true)

        val hasFinishHelmet = remaining.contains("[FINISH]", ignoreCase = true) ||
                remaining.contains("[HELMET]", ignoreCase = true) ||
                remaining.contains("FINISH (helmet)", ignoreCase = true)

        val hasTeamKill = remaining.contains("[TEAM_KILL]", ignoreCase = true) ||
                remaining.contains("TEAM KILL ICON", ignoreCase = true) ||
                remaining.contains("FRIENDLY FIRE", ignoreCase = true)

        val hasRevive = remaining.contains("[REVIVE]", ignoreCase = true) ||
                remaining.contains("REVIVE ICON", ignoreCase = true) ||
                remaining.contains("revived", ignoreCase = true)

        val cause = when {
            hasRevive -> KillFeedCause.REVIVE
            hasFinishHelmet -> KillFeedCause.FINISH_FROM_KNOCK
            hasTeamKill -> KillFeedCause.TEAM_KILL
            remaining.contains("GRENADE", ignoreCase = true) || remaining.contains("💣") -> KillFeedCause.GRENADE
            remaining.contains("VEHICLE", ignoreCase = true) || remaining.contains("🚗") -> KillFeedCause.VEHICLE
            remaining.contains("MOLOTOV", ignoreCase = true) || remaining.contains("🔥") || remaining.contains("FIRE") -> KillFeedCause.MOLOTOV
            remaining.contains("RED ZONE", ignoreCase = true) || remaining.contains("REDZONE", ignoreCase = true) -> KillFeedCause.RED_ZONE
            remaining.contains("BLUE ZONE", ignoreCase = true) || remaining.contains("BLUEZONE", ignoreCase = true) -> KillFeedCause.BLUE_ZONE
            remaining.contains("PLAYZONE", ignoreCase = true) || remaining.contains("ZONE", ignoreCase = true) -> KillFeedCause.PLAYZONE
            remaining.contains("AIRSTRIKE", ignoreCase = true) || remaining.contains("AIR STRIKE", ignoreCase = true) -> KillFeedCause.AIRSTRIKE
            remaining.contains("WATER", ignoreCase = true) || remaining.contains("DROWN", ignoreCase = true) -> KillFeedCause.WATER
            remaining.contains("FALL", ignoreCase = true) -> KillFeedCause.FALL
            remaining.contains("TRAP", ignoreCase = true) || remaining.contains("MINE", ignoreCase = true) -> KillFeedCause.TRAP_MINE
            remaining.contains("SUICIDE", ignoreCase = true) -> KillFeedCause.SUICIDE
            remaining.contains("MELEE", ignoreCase = true) || remaining.contains("PAN", ignoreCase = true) || remaining.contains("PUNCH", ignoreCase = true) -> KillFeedCause.MELEE
            remaining.contains("HEADSHOT", ignoreCase = true) -> KillFeedCause.HEADSHOT
            remaining.contains("GUN", ignoreCase = true) || remaining.contains("🔫") || remaining.contains("M416") || remaining.contains("AKM") || remaining.contains("AWM") -> KillFeedCause.GUN
            hasKnock -> KillFeedCause.KNOCK
            else -> KillFeedCause.GUN
        }

        // Split tokens around weapon / icon markers
        val separatorRegex = Regex("\\[(GUN|GRENADE|VEHICLE|MOLOTOV|RED_ZONE|PLAYZONE|BLUE_ZONE|ZONE|AIRSTRIKE|WATER|FALL|TRAP|MINE|SUICIDE|KNOCK|FINISH|TEAM_KILL|REVIVE|HELMET|PAN|MELEE|M416|AKM|AWM|SCAR-L|UMP45)[^\\]]*\\]|[🔫💣🚗🔥☠️]", RegexOption.IGNORE_CASE)
        val parts = remaining.split(separatorRegex, limit = 2)

        val rawLeft: String
        val rawRight: String

        if (parts.size >= 2) {
            rawLeft = parts[0].trim()
            // In case right side still contains [KNOCK] or [FINISH] tokens, strip them for name extraction
            rawRight = parts[1]
                .replace(Regex("\\[(KNOCK|FINISH|HELMET|TEAM_KILL|REVIVE)[^\\]]*\\]", RegexOption.IGNORE_CASE), "")
                .trim()
        } else {
            // Fallback space splitting
            val words = remaining.split("\\s+".toRegex())
            if (words.size >= 2) {
                rawLeft = words.first()
                rawRight = words.last()
            } else {
                rawLeft = remaining
                rawRight = ""
            }
        }

        val flags = extractFlags(line)

        return VisualExtraction(
            rawLeft = rawLeft,
            rawRight = rawRight,
            cause = cause,
            hasKnock = hasKnock,
            hasFinishHelmet = hasFinishHelmet,
            hasTeamKillIcon = hasTeamKill,
            hasReviveIcon = hasRevive,
            flagsExtracted = flags,
            clarityScore = clarity,
            isTransitioning = isTransitioning
        )
    }

    private fun parseCause(
        causeStr: String,
        hasKnock: Boolean,
        hasFinish: Boolean,
        hasRevive: Boolean
    ): KillFeedCause {
        if (hasRevive) return KillFeedCause.REVIVE
        if (hasFinish) return KillFeedCause.FINISH_FROM_KNOCK
        if (hasKnock && causeStr.isBlank()) return KillFeedCause.KNOCK

        val norm = causeStr.trim().uppercase().replace("[^A-Z_]".toRegex(), "")
        return when {
            norm.contains("GRENADE") || norm.contains("NADE") -> KillFeedCause.GRENADE
            norm.contains("VEHICLE") || norm.contains("CAR") || norm.contains("BUGGY") || norm.contains("UAZ") -> KillFeedCause.VEHICLE
            norm.contains("MOLOTOV") || norm.contains("FIRE") -> KillFeedCause.MOLOTOV
            norm.contains("RED_ZONE") || norm.contains("REDZONE") -> KillFeedCause.RED_ZONE
            norm.contains("BLUE_ZONE") || norm.contains("BLUEZONE") -> KillFeedCause.BLUE_ZONE
            norm.contains("PLAYZONE") || norm.contains("ZONE") -> KillFeedCause.PLAYZONE
            norm.contains("AIRSTRIKE") || norm.contains("AIR_STRIKE") -> KillFeedCause.AIRSTRIKE
            norm.contains("WATER") || norm.contains("DROWN") -> KillFeedCause.WATER
            norm.contains("FALL") -> KillFeedCause.FALL
            norm.contains("TRAP") || norm.contains("MINE") -> KillFeedCause.TRAP_MINE
            norm.contains("SUICIDE") -> KillFeedCause.SUICIDE
            norm.contains("TEAM_KILL") || norm.contains("TEAMKILL") -> KillFeedCause.TEAM_KILL
            norm.contains("FRIENDLY_FIRE") -> KillFeedCause.FRIENDLY_FIRE
            norm.contains("REVIVE") -> KillFeedCause.REVIVE
            norm.contains("FINISH") || norm.contains("HELMET") -> KillFeedCause.FINISH_FROM_KNOCK
            norm.contains("KNOCK") -> KillFeedCause.KNOCK
            norm.contains("MELEE") || norm.contains("PAN") || norm.contains("PUNCH") -> KillFeedCause.MELEE
            norm.contains("HEADSHOT") -> KillFeedCause.HEADSHOT
            norm.contains("GUN") || norm.contains("M416") || norm.contains("AKM") || norm.contains("AWM") -> KillFeedCause.GUN
            hasKnock -> KillFeedCause.KNOCK
            else -> KillFeedCause.GUN
        }
    }

    private fun extractFlags(text: String): List<String> {
        val flags = mutableListOf<String>()
        val flagRegex = Regex("[\uD83C][\uDDE6-\uDDFF][\uD83C][\uDDE6-\uDDFF]|\\[FLAG[^\\]]*\\]")
        flagRegex.findAll(text).forEach { match ->
            flags.add(match.value)
        }
        return flags
    }
}
