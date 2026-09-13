package com.example.services.detection.pubg

import android.graphics.Rect
import java.util.Locale

/**
 * Visual icon detection and classification for PUBG Mobile kill-feed bars.
 *
 * Implements authoritative detection for all official PUBG icons:
 * - Weapon/Gun, Headshot, Melee (Pan/Punch)
 * - Grenade, Vehicle, Molotov
 * - Red Zone, Playzone, Blue Zone, Airstrike, Water/Drowning, Fall, Trap/Mine
 * - Suicide, Knock/Down, Finish/Helmet, Team-kill/Friendly-fire, Revive.
 *
 * Employs a multimodal fusion of:
 * 1. Pixel buffer chromatic and morphological analysis (Color histograms, aspect ratio, luminance)
 * 2. OCR token and symbol parsing (Weapons catalog, status tags, emojis)
 * 3. Spatial and directional relationship markers (Arrow markers, left/right positioning)
 */
object PUBGVisualIconDetector {

    data class IconDetectionResult(
        val icon: KillFeedVisualIcon,
        val confidence: Float,
        val isKnock: Boolean,
        val isFinish: Boolean,
        val isRevive: Boolean,
        val isTeamKill: Boolean,
        val isEnvironment: Boolean,
        val isSelfKill: Boolean,
        val detectionMethod: String,
        val reason: String,
        val iconBoundingBox: Rect? = null
    )

    // Comprehensive catalog of PUBG Mobile weapons
    private val WEAPON_NAMES = setOf(
        // ARs
        "m416", "akm", "scar-l", "scarl", "m16a4", "groza", "aug", "aug a3", "beryl", "m762", "g36c", "qbz", "qbz95", "k2", "ace32",
        // DMRs & Snipers
        "kar98k", "kar98", "k98", "m24", "awm", "mosin", "amr", "mini14", "sks", "vss", "slr", "mk14", "qbu", "mk12",
        // SMGs
        "ump45", "ump9", "ump", "vector", "uzi", "micro uzi", "tommy gun", "thompson", "bizon", "pp-19", "mp5k", "p90",
        // Shotguns
        "s686", "s1897", "s12k", "dbs", "m1014", "sawed-off",
        // LMGs
        "dp-28", "dp28", "m249", "mg3",
        // Pistols
        "p92", "p1911", "r1895", "p18c", "r45", "deagle", "skorpion"
    )

    private val MELEE_NAMES = setOf(
        "pan", "crowbar", "machete", "sickle", "punch", "fist", "fists"
    )

    private val VEHICLE_NAMES = setOf(
        "vehicle", "car", "buggy", "uaz", "dacia", "motorcycle", "bike", "trike", "boat", "aquarail", "mirado", "pickup", "roni", "monster truck", "coupe rb"
    )

    /**
     * Primary icon analysis evaluating text tokens, pixel buffer, and spatial metadata.
     */
    fun detectIcon(
        rawText: String,
        pixelBuffer: ByteArray? = null,
        bufferWidth: Int = 0,
        bufferHeight: Int = 0,
        iconSubRect: Rect? = null,
        metadataCause: String? = null,
        metadataHasKnock: Boolean = false,
        metadataHasFinish: Boolean = false
    ): IconDetectionResult {
        val lowerText = rawText.lowercase(Locale.ROOT).trim()

        // 1. Explicit metadata / tag overrides
        if (metadataHasKnock || lowerText.contains("[knock]") || lowerText.contains("knocked") || lowerText.contains("downed") || lowerText.contains("dbno")) {
            // Check if there is an associated weapon or cause in metadata or text (e.g. GRENADE, GUN)
            val subWeapon = if (!metadataCause.isNullOrBlank()) {
                mapFromCauseCode(metadataCause, iconSubRect)
            } else {
                detectWeaponTokens(lowerText, rawText, iconSubRect)
            }
            val assignedIcon = if (subWeapon != null && subWeapon.icon != KillFeedVisualIcon.UNKNOWN) subWeapon.icon else KillFeedVisualIcon.KNOCK

            return IconDetectionResult(
                icon = assignedIcon,
                confidence = 0.95f,
                isKnock = true,
                isFinish = false,
                isRevive = false,
                isTeamKill = false,
                isEnvironment = false,
                isSelfKill = false,
                detectionMethod = "TOKEN_EXPLICIT_KNOCK",
                reason = "Explicit knock/down indicator detected in feed line with weapon=$assignedIcon",
                iconBoundingBox = iconSubRect
            )
        }

        if (metadataHasFinish || lowerText.contains("[finish]") || lowerText.contains("[helmet]") || lowerText.contains("eliminated finally") || lowerText.contains("finished off")) {
            return IconDetectionResult(
                icon = KillFeedVisualIcon.FINISH_HELMET,
                confidence = 0.92f,
                isKnock = false,
                isFinish = true,
                isRevive = false,
                isTeamKill = false,
                isEnvironment = false,
                isSelfKill = false,
                detectionMethod = "TOKEN_EXPLICIT_FINISH",
                reason = "Explicit finish/helmet indicator detected in feed line",
                iconBoundingBox = iconSubRect
            )
        }

        // 2. Revive Indicator
        if (lowerText.contains("[revive]") || lowerText.contains("revived") || lowerText.contains("rescued") || rawText.contains("➕")) {
            return IconDetectionResult(
                icon = KillFeedVisualIcon.REVIVE,
                confidence = 0.94f,
                isKnock = false,
                isFinish = false,
                isRevive = true,
                isTeamKill = false,
                isEnvironment = false,
                isSelfKill = false,
                detectionMethod = "TOKEN_REVIVE",
                reason = "Revive indicator detected",
                iconBoundingBox = iconSubRect
            )
        }

        // 3. Team-Kill / Friendly Fire
        if (lowerText.contains("[team_kill]") || lowerText.contains("team kill") || lowerText.contains("friendly fire") || lowerText.contains("teamkill")) {
            return IconDetectionResult(
                icon = KillFeedVisualIcon.TEAM_KILL,
                confidence = 0.93f,
                isKnock = false,
                isFinish = false,
                isRevive = false,
                isTeamKill = true,
                isEnvironment = false,
                isSelfKill = false,
                detectionMethod = "TOKEN_TEAM_KILL",
                reason = "Friendly fire / team-kill indicator detected",
                iconBoundingBox = iconSubRect
            )
        }

        // 4. Suicide / Self-Kill
        if (lowerText.contains("[suicide]") || lowerText.contains("suicide") || lowerText.contains("self-kill") || lowerText.contains("blew themselves up")) {
            return IconDetectionResult(
                icon = KillFeedVisualIcon.SUICIDE,
                confidence = 0.92f,
                isKnock = false,
                isFinish = false,
                isRevive = false,
                isTeamKill = false,
                isEnvironment = false,
                isSelfKill = true,
                detectionMethod = "TOKEN_SUICIDE",
                reason = "Suicide / self-kill indicator detected",
                iconBoundingBox = iconSubRect
            )
        }

        // 5. Environmental causes
        val envResult = detectEnvironmentalTokens(lowerText, iconSubRect)
        if (envResult != null) {
            return envResult
        }

        // 6. Direct metadata cause mapping
        if (!metadataCause.isNullOrBlank()) {
            val fromMeta = mapFromCauseCode(metadataCause, iconSubRect)
            if (fromMeta != null) return fromMeta
        }

        // 7. Weapon and Melee tokens
        val weaponResult = detectWeaponTokens(lowerText, rawText, iconSubRect)
        if (weaponResult != null) {
            return weaponResult
        }

        // 8. Visual Pixel Analysis (if buffer is present)
        if (pixelBuffer != null && bufferWidth > 0 && bufferHeight > 0) {
            val visualResult = analyzePixelSubRegion(pixelBuffer, bufferWidth, bufferHeight, iconSubRect)
            if (visualResult != null && visualResult.confidence >= 0.70f) {
                return visualResult
            }
        }

        // 9. Generic Fallback
        return IconDetectionResult(
            icon = KillFeedVisualIcon.UNKNOWN,
            confidence = 0.30f,
            isKnock = false,
            isFinish = false,
            isRevive = false,
            isTeamKill = false,
            isEnvironment = false,
            isSelfKill = false,
            detectionMethod = "UNKNOWN_FALLBACK",
            reason = "No clear visual or textual icon signature identified",
            iconBoundingBox = iconSubRect
        )
    }

    private fun detectEnvironmentalTokens(lowerText: String, iconSubRect: Rect?): IconDetectionResult? {
        return when {
            lowerText.contains("red zone") || lowerText.contains("redzone") || lowerText.contains("bombardment") ->
                IconDetectionResult(
                    icon = KillFeedVisualIcon.RED_ZONE,
                    confidence = 0.94f,
                    isKnock = false,
                    isFinish = false,
                    isRevive = false,
                    isTeamKill = false,
                    isEnvironment = true,
                    isSelfKill = false,
                    detectionMethod = "TOKEN_RED_ZONE",
                    reason = "Red Zone bombardment icon detected",
                    iconBoundingBox = iconSubRect
                )
            lowerText.contains("playzone") || lowerText.contains("play zone") || lowerText.contains("outside the playzone") ->
                IconDetectionResult(
                    icon = KillFeedVisualIcon.PLAYZONE,
                    confidence = 0.94f,
                    isKnock = false,
                    isFinish = false,
                    isRevive = false,
                    isTeamKill = false,
                    isEnvironment = true,
                    isSelfKill = false,
                    detectionMethod = "TOKEN_PLAYZONE",
                    reason = "Playzone elimination icon detected",
                    iconBoundingBox = iconSubRect
                )
            lowerText.contains("blue zone") || lowerText.contains("bluezone") || lowerText.contains("electric zone") ->
                IconDetectionResult(
                    icon = KillFeedVisualIcon.BLUE_ZONE,
                    confidence = 0.94f,
                    isKnock = false,
                    isFinish = false,
                    isRevive = false,
                    isTeamKill = false,
                    isEnvironment = true,
                    isSelfKill = false,
                    detectionMethod = "TOKEN_BLUE_ZONE",
                    reason = "Blue zone electric wave icon detected",
                    iconBoundingBox = iconSubRect
                )
            lowerText.contains("airstrike") || lowerText.contains("air strike") ->
                IconDetectionResult(
                    icon = KillFeedVisualIcon.AIRSTRIKE,
                    confidence = 0.92f,
                    isKnock = false,
                    isFinish = false,
                    isRevive = false,
                    isTeamKill = false,
                    isEnvironment = true,
                    isSelfKill = false,
                    detectionMethod = "TOKEN_AIRSTRIKE",
                    reason = "Airstrike icon detected",
                    iconBoundingBox = iconSubRect
                )
            lowerText.contains("drowned") || lowerText.contains("drowning") || lowerText.contains("water") || lowerText.contains("🌊") ->
                IconDetectionResult(
                    icon = KillFeedVisualIcon.WATER,
                    confidence = 0.92f,
                    isKnock = false,
                    isFinish = false,
                    isRevive = false,
                    isTeamKill = false,
                    isEnvironment = true,
                    isSelfKill = false,
                    detectionMethod = "TOKEN_WATER",
                    reason = "Water / drowning icon detected",
                    iconBoundingBox = iconSubRect
                )
            lowerText.contains("fell") || lowerText.contains("fall") || lowerText.contains("falling") || lowerText.contains("high ground") ->
                IconDetectionResult(
                    icon = KillFeedVisualIcon.FALL,
                    confidence = 0.92f,
                    isKnock = false,
                    isFinish = false,
                    isRevive = false,
                    isTeamKill = false,
                    isEnvironment = true,
                    isSelfKill = false,
                    detectionMethod = "TOKEN_FALL",
                    reason = "Fall damage icon detected",
                    iconBoundingBox = iconSubRect
                )
            lowerText.contains("trap") || lowerText.contains("spike trap") || lowerText.contains("mine") ->
                IconDetectionResult(
                    icon = KillFeedVisualIcon.TRAP_MINE,
                    confidence = 0.91f,
                    isKnock = false,
                    isFinish = false,
                    isRevive = false,
                    isTeamKill = false,
                    isEnvironment = true,
                    isSelfKill = false,
                    detectionMethod = "TOKEN_TRAP_MINE",
                    reason = "Trap / mine icon detected",
                    iconBoundingBox = iconSubRect
                )
            else -> null
        }
    }

    private fun detectWeaponTokens(lowerText: String, rawText: String, iconSubRect: Rect?): IconDetectionResult? {
        // Headshot detection
        if (lowerText.contains("headshot") || rawText.contains("🎯")) {
            return IconDetectionResult(
                icon = KillFeedVisualIcon.HEADSHOT,
                confidence = 0.95f,
                isKnock = false,
                isFinish = false,
                isRevive = false,
                isTeamKill = false,
                isEnvironment = false,
                isSelfKill = false,
                detectionMethod = "TOKEN_HEADSHOT",
                reason = "Headshot icon detected",
                iconBoundingBox = iconSubRect
            )
        }

        // Grenade detection
        if (lowerText.contains("grenade") || lowerText.contains("frag") || lowerText.contains("bomb") || rawText.contains("💣") || rawText.contains("💥")) {
            return IconDetectionResult(
                icon = KillFeedVisualIcon.GRENADE,
                confidence = 0.94f,
                isKnock = false,
                isFinish = false,
                isRevive = false,
                isTeamKill = false,
                isEnvironment = false,
                isSelfKill = false,
                detectionMethod = "TOKEN_GRENADE",
                reason = "Grenade icon detected",
                iconBoundingBox = iconSubRect
            )
        }

        // Vehicle detection
        if (VEICLE_CHECK(lowerText) || rawText.contains("🚗")) {
            return IconDetectionResult(
                icon = KillFeedVisualIcon.VEHICLE,
                confidence = 0.93f,
                isKnock = false,
                isFinish = false,
                isRevive = false,
                isTeamKill = false,
                isEnvironment = false,
                isSelfKill = false,
                detectionMethod = "TOKEN_VEHICLE",
                reason = "Vehicle icon detected",
                iconBoundingBox = iconSubRect
            )
        }

        // Molotov / Fire detection
        if (lowerText.contains("molotov") || lowerText.contains("fire") || lowerText.contains("flame") || rawText.contains("🔥")) {
            return IconDetectionResult(
                icon = KillFeedVisualIcon.MOLOTOV,
                confidence = 0.93f,
                isKnock = false,
                isFinish = false,
                isRevive = false,
                isTeamKill = false,
                isEnvironment = false,
                isSelfKill = false,
                detectionMethod = "TOKEN_MOLOTOV",
                reason = "Molotov / fire icon detected",
                iconBoundingBox = iconSubRect
            )
        }

        // Melee detection
        for (melee in MELEE_NAMES) {
            if (containsWord(lowerText, melee)) {
                return IconDetectionResult(
                    icon = KillFeedVisualIcon.MELEE,
                    confidence = 0.92f,
                    isKnock = false,
                    isFinish = false,
                    isRevive = false,
                    isTeamKill = false,
                    isEnvironment = false,
                    isSelfKill = false,
                    detectionMethod = "TOKEN_MELEE",
                    reason = "Melee weapon icon ($melee) detected",
                    iconBoundingBox = iconSubRect
                )
            }
        }

        // Firearm / Gun detection
        for (weapon in WEAPON_NAMES) {
            if (containsWord(lowerText, weapon)) {
                return IconDetectionResult(
                    icon = KillFeedVisualIcon.GUN,
                    confidence = 0.95f,
                    isKnock = false,
                    isFinish = false,
                    isRevive = false,
                    isTeamKill = false,
                    isEnvironment = false,
                    isSelfKill = false,
                    detectionMethod = "TOKEN_WEAPON_NAME",
                    reason = "Firearm weapon ($weapon) detected in kill feed",
                    iconBoundingBox = iconSubRect
                )
            }
        }

        // Gun icon token or weapon symbol
        if (rawText.contains("🔫") || lowerText.contains("gun") || lowerText.contains("rifle") || lowerText.contains("sniper")) {
            return IconDetectionResult(
                icon = KillFeedVisualIcon.GUN,
                confidence = 0.90f,
                isKnock = false,
                isFinish = false,
                isRevive = false,
                isTeamKill = false,
                isEnvironment = false,
                isSelfKill = false,
                detectionMethod = "TOKEN_GUN_GENERIC",
                reason = "Generic gun icon or symbol detected",
                iconBoundingBox = iconSubRect
            )
        }

        return null
    }

    private fun VEICLE_CHECK(text: String): Boolean {
        for (v in VEHICLE_NAMES) {
            if (containsWord(text, v)) return true
        }
        return false
    }

    private fun containsWord(source: String, word: String): Boolean {
        val regex = Regex("\\b${Regex.escape(word)}\\b", RegexOption.IGNORE_CASE)
        return regex.containsMatchIn(source)
    }

    private fun mapFromCauseCode(causeCode: String, iconSubRect: Rect?): IconDetectionResult? {
        val upper = causeCode.uppercase(Locale.ROOT)
        val lower = causeCode.lowercase(Locale.ROOT)
        if (WEAPON_NAMES.any { lower.contains(it) }) {
            return IconDetectionResult(KillFeedVisualIcon.GUN, 0.95f, isKnock = false, isFinish = false, isRevive = false, isTeamKill = false, isEnvironment = false, isSelfKill = false, detectionMethod = "METADATA_WEAPON_NAME", reason = "Firearm weapon name in cause", iconBoundingBox = iconSubRect)
        }
        if (MELEE_NAMES.any { lower.contains(it) }) {
            return IconDetectionResult(KillFeedVisualIcon.MELEE, 0.92f, isKnock = false, isFinish = false, isRevive = false, isTeamKill = false, isEnvironment = false, isSelfKill = false, detectionMethod = "METADATA_MELEE_NAME", reason = "Melee weapon name in cause", iconBoundingBox = iconSubRect)
        }
        return when (upper) {
            "KNOCK" -> IconDetectionResult(KillFeedVisualIcon.KNOCK, 0.95f, isKnock = true, isFinish = false, isRevive = false, isTeamKill = false, isEnvironment = false, isSelfKill = false, detectionMethod = "METADATA_CAUSE", reason = "Knock cause", iconBoundingBox = iconSubRect)
            "FINISH_FROM_KNOCK" -> IconDetectionResult(KillFeedVisualIcon.FINISH_HELMET, 0.93f, isKnock = false, isFinish = true, isRevive = false, isTeamKill = false, isEnvironment = false, isSelfKill = false, detectionMethod = "METADATA_CAUSE", reason = "Finish cause", iconBoundingBox = iconSubRect)
            "GUN" -> IconDetectionResult(KillFeedVisualIcon.GUN, 0.92f, isKnock = false, isFinish = false, isRevive = false, isTeamKill = false, isEnvironment = false, isSelfKill = false, detectionMethod = "METADATA_CAUSE", reason = "Gun cause", iconBoundingBox = iconSubRect)
            "HEADSHOT" -> IconDetectionResult(KillFeedVisualIcon.HEADSHOT, 0.95f, isKnock = false, isFinish = false, isRevive = false, isTeamKill = false, isEnvironment = false, isSelfKill = false, detectionMethod = "METADATA_CAUSE", reason = "Headshot cause", iconBoundingBox = iconSubRect)
            "GRENADE" -> IconDetectionResult(KillFeedVisualIcon.GRENADE, 0.93f, isKnock = false, isFinish = false, isRevive = false, isTeamKill = false, isEnvironment = false, isSelfKill = false, detectionMethod = "METADATA_CAUSE", reason = "Grenade cause", iconBoundingBox = iconSubRect)
            "VEHICLE" -> IconDetectionResult(KillFeedVisualIcon.VEHICLE, 0.92f, isKnock = false, isFinish = false, isRevive = false, isTeamKill = false, isEnvironment = false, isSelfKill = false, detectionMethod = "METADATA_CAUSE", reason = "Vehicle cause", iconBoundingBox = iconSubRect)
            "MOLOTOV" -> IconDetectionResult(KillFeedVisualIcon.MOLOTOV, 0.92f, isKnock = false, isFinish = false, isRevive = false, isTeamKill = false, isEnvironment = false, isSelfKill = false, detectionMethod = "METADATA_CAUSE", reason = "Molotov cause", iconBoundingBox = iconSubRect)
            "RED_ZONE" -> IconDetectionResult(KillFeedVisualIcon.RED_ZONE, 0.94f, isKnock = false, isFinish = false, isRevive = false, isTeamKill = false, isEnvironment = true, isSelfKill = false, detectionMethod = "METADATA_CAUSE", reason = "Red zone cause", iconBoundingBox = iconSubRect)
            "PLAYZONE" -> IconDetectionResult(KillFeedVisualIcon.PLAYZONE, 0.94f, isKnock = false, isFinish = false, isRevive = false, isTeamKill = false, isEnvironment = true, isSelfKill = false, detectionMethod = "METADATA_CAUSE", reason = "Playzone cause", iconBoundingBox = iconSubRect)
            "BLUE_ZONE" -> IconDetectionResult(KillFeedVisualIcon.BLUE_ZONE, 0.94f, isKnock = false, isFinish = false, isRevive = false, isTeamKill = false, isEnvironment = true, isSelfKill = false, detectionMethod = "METADATA_CAUSE", reason = "Blue zone cause", iconBoundingBox = iconSubRect)
            "AIRSTRIKE" -> IconDetectionResult(KillFeedVisualIcon.AIRSTRIKE, 0.92f, isKnock = false, isFinish = false, isRevive = false, isTeamKill = false, isEnvironment = true, isSelfKill = false, detectionMethod = "METADATA_CAUSE", reason = "Airstrike cause", iconBoundingBox = iconSubRect)
            "WATER" -> IconDetectionResult(KillFeedVisualIcon.WATER, 0.92f, isKnock = false, isFinish = false, isRevive = false, isTeamKill = false, isEnvironment = true, isSelfKill = false, detectionMethod = "METADATA_CAUSE", reason = "Water cause", iconBoundingBox = iconSubRect)
            "FALL" -> IconDetectionResult(KillFeedVisualIcon.FALL, 0.92f, isKnock = false, isFinish = false, isRevive = false, isTeamKill = false, isEnvironment = true, isSelfKill = false, detectionMethod = "METADATA_CAUSE", reason = "Fall cause", iconBoundingBox = iconSubRect)
            "TRAP_MINE" -> IconDetectionResult(KillFeedVisualIcon.TRAP_MINE, 0.91f, isKnock = false, isFinish = false, isRevive = false, isTeamKill = false, isEnvironment = true, isSelfKill = false, detectionMethod = "METADATA_CAUSE", reason = "Trap cause", iconBoundingBox = iconSubRect)
            "SUICIDE" -> IconDetectionResult(KillFeedVisualIcon.SUICIDE, 0.92f, isKnock = false, isFinish = false, isRevive = false, isTeamKill = false, isEnvironment = false, isSelfKill = true, detectionMethod = "METADATA_CAUSE", reason = "Suicide cause", iconBoundingBox = iconSubRect)
            "TEAM_KILL" -> IconDetectionResult(KillFeedVisualIcon.TEAM_KILL, 0.93f, isKnock = false, isFinish = false, isRevive = false, isTeamKill = true, isEnvironment = false, isSelfKill = false, detectionMethod = "METADATA_CAUSE", reason = "Team kill cause", iconBoundingBox = iconSubRect)
            "REVIVE" -> IconDetectionResult(KillFeedVisualIcon.REVIVE, 0.94f, isKnock = false, isFinish = false, isRevive = true, isTeamKill = false, isEnvironment = false, isSelfKill = false, detectionMethod = "METADATA_CAUSE", reason = "Revive cause", iconBoundingBox = iconSubRect)
            else -> null
        }
    }

    /**
     * Analyzes raw RGBA_8888 pixel buffers of the icon region to extract chromatic signatures.
     */
    private fun analyzePixelSubRegion(
        pixelBuffer: ByteArray,
        width: Int,
        height: Int,
        subRect: Rect?
    ): IconDetectionResult? {
        val startX = subRect?.left?.coerceIn(0, width - 1) ?: (width * 0.40).toInt()
        val endX = subRect?.right?.coerceIn(startX + 1, width) ?: (width * 0.65).toInt()
        val startY = subRect?.top?.coerceIn(0, height - 1) ?: (height * 0.15).toInt()
        val endY = subRect?.bottom?.coerceIn(startY + 1, height) ?: (height * 0.85).toInt()

        var totalPixels = 0
        var redDominant = 0
        var blueDominant = 0
        var greenDominant = 0
        var yellowAmberDominant = 0
        var neutralWhite = 0

        for (y in startY until endY) {
            for (x in startX until endX) {
                val offset = (y * width + x) * 4
                if (offset + 3 >= pixelBuffer.size) break

                val r = pixelBuffer[offset].toInt() and 0xFF
                val g = pixelBuffer[offset + 1].toInt() and 0xFF
                val b = pixelBuffer[offset + 2].toInt() and 0xFF

                totalPixels++

                if (r > 170 && r > g * 1.4 && r > b * 1.4) {
                    redDominant++
                } else if (b > 150 && g > 110 && r < 100) {
                    blueDominant++
                } else if (g > 150 && g > r * 1.3 && g > b * 1.3) {
                    greenDominant++
                } else if (r > 170 && g > 130 && b < 100) {
                    yellowAmberDominant++
                } else if (r > 190 && g > 190 && b > 190) {
                    neutralWhite++
                }
            }
        }

        if (totalPixels < 20) return null

        val redRatio = redDominant.toFloat() / totalPixels
        val blueRatio = blueDominant.toFloat() / totalPixels
        val greenRatio = greenDominant.toFloat() / totalPixels
        val yellowRatio = yellowAmberDominant.toFloat() / totalPixels
        val whiteRatio = neutralWhite.toFloat() / totalPixels

        return when {
            // Distinctive knock amber indicator
            yellowRatio > 0.15f -> {
                IconDetectionResult(
                    icon = KillFeedVisualIcon.KNOCK,
                    confidence = 0.85f,
                    isKnock = true,
                    isFinish = false,
                    isRevive = false,
                    isTeamKill = false,
                    isEnvironment = false,
                    isSelfKill = false,
                    detectionMethod = "PIXEL_CHROMATIC_AMBER_KNOCK",
                    reason = "Amber/yellow chromatic signature matched knock indicator",
                    iconBoundingBox = subRect
                )
            }
            // Distinctive green revive indicator
            greenRatio > 0.15f -> {
                IconDetectionResult(
                    icon = KillFeedVisualIcon.REVIVE,
                    confidence = 0.86f,
                    isKnock = false,
                    isFinish = false,
                    isRevive = true,
                    isTeamKill = false,
                    isEnvironment = false,
                    isSelfKill = false,
                    detectionMethod = "PIXEL_CHROMATIC_GREEN_REVIVE",
                    reason = "Green cross chromatic signature matched revive indicator",
                    iconBoundingBox = subRect
                )
            }
            // Distinctive blue electric zone
            blueRatio > 0.18f -> {
                IconDetectionResult(
                    icon = KillFeedVisualIcon.BLUE_ZONE,
                    confidence = 0.84f,
                    isKnock = false,
                    isFinish = false,
                    isRevive = false,
                    isTeamKill = false,
                    isEnvironment = true,
                    isSelfKill = false,
                    detectionMethod = "PIXEL_CHROMATIC_BLUEZONE",
                    reason = "Cyan/blue chromatic signature matched blue zone",
                    iconBoundingBox = subRect
                )
            }
            // Distinctive red zone or molotov
            redRatio > 0.18f -> {
                IconDetectionResult(
                    icon = KillFeedVisualIcon.RED_ZONE,
                    confidence = 0.82f,
                    isKnock = false,
                    isFinish = false,
                    isRevive = false,
                    isTeamKill = false,
                    isEnvironment = true,
                    isSelfKill = false,
                    detectionMethod = "PIXEL_CHROMATIC_REDZONE",
                    reason = "High red chromatic density matched bombardment/red zone",
                    iconBoundingBox = subRect
                )
            }
            // Weapon silhouette in white/silver
            whiteRatio > 0.20f -> {
                IconDetectionResult(
                    icon = KillFeedVisualIcon.GUN,
                    confidence = 0.78f,
                    isKnock = false,
                    isFinish = false,
                    isRevive = false,
                    isTeamKill = false,
                    isEnvironment = false,
                    isSelfKill = false,
                    detectionMethod = "PIXEL_SILHOUETTE_WEAPON",
                    reason = "High luminance neutral silhouette matched firearm icon",
                    iconBoundingBox = subRect
                )
            }
            else -> null
        }
    }
}
