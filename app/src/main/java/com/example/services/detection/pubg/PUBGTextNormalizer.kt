package com.example.services.detection.pubg

/**
 * Text normalizer and flag stripper for PUBG Mobile kill-feed recognition.
 *
 * MANDATORY RULE (from Specification & PDF):
 * Country flags may appear on BOTH the killer side and victim side.
 * The detector MUST COMPLETELY IGNORE these flags.
 * Country flags, clan badges and decorative badges must never be used
 * to determine killer, victim, team, kill or points.
 *
 * Example:
 * [FLAG] KillerName [WEAPON] [KNOCK?] [FLAG] VictimName
 * parsed as:
 * LEFT_TEXT = KillerName
 * ICONS = [WEAPON]
 * RIGHT_TEXT = VictimName
 * FLAGS = ignored
 */
object PUBGTextNormalizer {

    // Regex for 2-letter Unicode Regional Indicator symbols (Flags) e.g., 🇮🇩, 🇵🇭, 🇲🇲, 🇹🇷, 🇺🇸, 🇨🇭, 🇭🇰
    private val REGIONAL_INDICATOR_REGEX = Regex("[\\uD83C][\\uDDE6-\\uDDFF][\\uD83C][\\uDDE6-\\uDDFF]")

    // Extended emoji & decorative icons regex (swords, shields, stars, crowns, etc., excluding mathematical alphanumerics)
    private val DECORATIVE_EMOJI_REGEX = Regex("[★✪⚡✦✧☬亗彡ツ✗]+|[\\u2600-\\u27BF]+")

    // Text token flag markers that OCR might produce
    private val TEXT_FLAG_TOKEN_REGEX = Regex("\\[(FLAG|FLAG_[A-Z0-9]+|[A-Z]{2,3})\\]", RegexOption.IGNORE_CASE)

    // Common decorative brackets and decorative punctuation
    private val DECORATIVE_BRACKETS_REGEX = Regex("[【】《》「」『』\\[\\](){}]")

    // Environmental / System killer words
    private val SYSTEM_IDENTIFIERS = setOf(
        "playzone",
        "play zone",
        "bluezone",
        "blue zone",
        "zone",
        "redzone",
        "red zone",
        "airstrike",
        "air strike",
        "fall",
        "fall damage",
        "drown",
        "drowning",
        "water",
        "fire",
        "fire_env",
        "environment",
        "system",
        "trap",
        "mine",
        "suicide",
        "bleed",
        "bleeding",
        "vehicle explosion"
    )

    /**
     * Models Team Number + Player Number identity extracted from kill feed OCR.
     * Example: T3 P2 -> teamNumber = 3, playerNumber = 2
     */
    data class TeamPlayerIdentity(
        val teamNumber: Int,
        val playerNumber: Int,
        val rawText: String
    ) {
        val formattedKey: String get() = "T${teamNumber} P${playerNumber}"
    }

    // Pattern for Team Number + Player Number e.g. T3 P2, T3P2, Team 3 Player 2, T3-P2, T1 4, 1 / 4, 1-4, 1/4
    private val TEAM_PLAYER_REGEX = Regex(
        "(?i)(?:(?:TEAM|T)\\s*0*([1-9][0-9]?)\\s*(?:PLAYER|P)\\s*0*([1-4]))|" +
        "(?i)(?:(?:TEAM|T)\\s*0*([1-9][0-9]?)\\s+([1-4]))|" +
        "(?i)\\b0*([1-9][0-9]?)\\s*[/\\-_]\\s*0*([1-4])\\b"
    )

    // Unclear team/player marker regex e.g. T3 P?, T? P2, T_ P_, T3 P, T P2
    private val UNCLEAR_TEAM_PLAYER_REGEX = Regex(
        "(?i)(?:TEAM|T)\\s*[\\?_A-Za-z0-9]*\\s*(?:PLAYER|P)\\s*[\\?_A-Za-z0-9]*"
    )

    /**
     * Extracts structured Team Number + Player Number identity from raw text if present.
     */
    fun extractTeamPlayerIdentity(rawText: String?): TeamPlayerIdentity? {
        if (rawText.isNullOrBlank()) return null
        val stripped = stripFlagsAndDecorations(rawText)
        val match = TEAM_PLAYER_REGEX.find(stripped) ?: return null
        val teamNum = (match.groupValues[1].toIntOrNull()
            ?: match.groupValues[3].toIntOrNull()
            ?: match.groupValues[5].toIntOrNull()) ?: return null
        val playerNum = (match.groupValues[2].toIntOrNull()
            ?: match.groupValues[4].toIntOrNull()
            ?: match.groupValues[6].toIntOrNull()) ?: return null
        return TeamPlayerIdentity(teamNum, playerNum, match.value)
    }

    /**
     * Detects if raw text contains an incomplete or corrupted team/player token (e.g. T3 P?, T? P2, T3P_).
     */
    fun hasUnclearTeamPlayerToken(rawText: String?): Boolean {
        if (rawText.isNullOrBlank()) return false
        val stripped = stripFlagsAndDecorations(rawText)
        if (!stripped.contains(Regex("(?i)\\b(?:TEAM|T)[0-9\\?_]*\\s*(?:PLAYER|P)?[0-9\\?_]*\\b"))) return false
        
        // If it matches an unclear pattern but valid TeamPlayerIdentity is null, it is unclear!
        val identity = extractTeamPlayerIdentity(stripped)
        return identity == null && UNCLEAR_TEAM_PLAYER_REGEX.containsMatchIn(stripped)
    }

    /**
     * Completely strips country flags, clan badges, and decorative symbols from raw OCR text.
     */
    fun stripFlagsAndDecorations(raw: String?): String {
        if (raw.isNullOrBlank()) return ""

        val sb = StringBuilder()
        var i = 0
        while (i < raw.length) {
            val codePoint = raw.codePointAt(i)
            val charCount = Character.charCount(codePoint)
            
            // Regional Indicator Symbols (Flags) 0x1F1E6..0x1F1FF
            val isFlag = codePoint in 0x1F1E6..0x1F1FF
            // Decorative Emojis & Symbols (excluding weapons/combat characters where needed)
            val isEmojiDecoration = (codePoint in 0x1F300..0x1F5FA && codePoint !in setOf(0x1F52B)) ||
                    (codePoint in 0x1F600..0x1F64F) ||
                    (codePoint in 0x1F680..0x1F6FF) ||
                    (codePoint in 0x1F900..0x1F9FF) ||
                    (codePoint in 0x2600..0x27BF) ||
                    (codePoint in 0xFE00..0xFE0F)

            if (!isFlag && !isEmojiDecoration) {
                sb.appendCodePoint(codePoint)
            } else {
                sb.append(' ')
            }
            i += charCount
        }

        var cleaned = sb.toString()
            // Remove [FLAG] tokens
            .replace(TEXT_FLAG_TOKEN_REGEX, " ")
            // Remove decorative glyphs
            .replace(DECORATIVE_EMOJI_REGEX, " ")
            // Remove leading/trailing decorative quotes or arrows like 》, », «
            .replace(Regex("^[»«›‹>><\\-]+\\s*"), "")
            .replace(Regex("\\s*[»«›‹>><\\-]+$"), "")

        // Normalize spaces
        return cleaned.trim().replace(Regex("\\s+"), " ")
    }

    /**
     * Standardizes a player name for strict identity comparison.
     */
    fun normalizePlayerName(raw: String?): String {
        val stripped = stripFlagsAndDecorations(raw)
        // Remove special wrapper brackets if any
        val withoutBrackets = stripped.replace(DECORATIVE_BRACKETS_REGEX, " ")
        return withoutBrackets.trim().lowercase().replace(Regex("[^a-z0-9_]"), "")
    }

    /**
     * Returns a human-friendly display name (preserving case, with flags stripped and team prefixes separated).
     */
    fun extractCleanDisplayName(raw: String?): String {
        val stripped = stripFlagsAndDecorations(raw)
        if (stripped.isBlank()) return "UNKNOWN_PLAYER"

        // Remove leading bracketed tag e.g. [SOUL] Mortal or 【SOUL】Mortal
        var cleaned = stripped.replace(Regex("^[【\\[][A-Za-z0-9_\\-]{2,8}[】\\]]\\s*"), "")

        // Remove pipe separators e.g. SOUL | Mortal -> Mortal
        if (cleaned.contains("|")) {
            val parts = cleaned.split("|", limit = 2)
            if (parts.size == 2 && parts[1].trim().isNotBlank()) {
                cleaned = parts[1].trim()
            }
        }

        return cleaned.trim().ifBlank { stripped }
    }

    /**
     * Extracts team tag / clan prefix if present (e.g. "TEAM | 99" -> "TEAM 99", "NEMO" -> "NEMO", "[KING]" -> "KING").
     */
    fun extractTeamTag(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val stripped = stripFlagsAndDecorations(raw)

        // Pattern 1: TEAM | XX
        val teamPipeMatch = Regex("TEAM\\s*\\|?\\s*([A-Za-z0-9]+)", RegexOption.IGNORE_CASE).find(stripped)
        if (teamPipeMatch != null) {
            return "TEAM_${teamPipeMatch.groupValues[1].uppercase()}"
        }

        // Pattern 2: [TAG] or 【TAG】
        val bracketMatch = Regex("[【\\[]([A-Za-z0-9_\\-]{2,8})[】\\]]").find(raw)
        if (bracketMatch != null) {
            return bracketMatch.groupValues[1].trim().uppercase()
        }

        // Pattern 3: Prefix like "NEMO " or "SOUL "
        val prefixMatch = Regex("^([A-Za-z0-9]{2,6})\\s+").find(stripped)
        if (prefixMatch != null) {
            val potentialTag = prefixMatch.groupValues[1].uppercase()
            // Ignore system words
            if (!isSystemText(potentialTag)) {
                return potentialTag
            }
        }

        return null
    }

    /**
     * Determines whether the given text indicates an environmental/system cause.
     */
    fun isSystemText(raw: String?): Boolean {
        if (raw.isNullOrBlank()) return false
        val cleaned = stripFlagsAndDecorations(raw).trim().lowercase()
        if (SYSTEM_IDENTIFIERS.contains(cleaned)) return true

        // Check if any system identifier is a dominant prefix or word
        return SYSTEM_IDENTIFIERS.any { cleaned.startsWith(it) || cleaned == it }
    }

    /**
     * Checks if two names represent the exact same player (Self Kill detection).
     */
    fun isSamePlayerName(leftRaw: String?, rightRaw: String?): Boolean {
        val leftNorm = normalizePlayerName(leftRaw)
        val rightNorm = normalizePlayerName(rightRaw)
        if (leftNorm.isBlank() || rightNorm.isBlank()) return false
        return leftNorm == rightNorm
    }

    /**
     * Checks if two team tags represent the exact same team (Team Kill detection).
     */
    fun isSameTeam(leftTeam: String?, rightTeam: String?): Boolean {
        if (leftTeam.isNullOrBlank() || rightTeam.isNullOrBlank()) return false
        val leftNorm = leftTeam.trim().uppercase().replace(Regex("[^A-Z0-9]"), "")
        val rightNorm = rightTeam.trim().uppercase().replace(Regex("[^A-Z0-9]"), "")
        if (leftNorm.isBlank() || rightNorm.isBlank()) return false
        return leftNorm == rightNorm
    }

    /**
     * Normalizes stylized PUBG names (handling mathematical fonts, unicode variants, fullwidth, spacing, symbols).
     */
    fun normalizeStylizedName(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        val stripped = stripFlagsAndDecorations(raw)
        val sb = StringBuilder()
        val codePoints = stripped.codePoints().toArray()
        for (code in codePoints) {
            val mapped = when {
                code in 0xFF21..0xFF3A -> code - 0xFF21 + 'A'.code
                code in 0xFF41..0xFF5A -> code - 0xFF41 + 'a'.code
                code in 0xFF10..0xFF19 -> code - 0xFF10 + '0'.code
                code in 0x1D400..0x1D419 -> code - 0x1D400 + 'A'.code
                code in 0x1D41A..0x1D433 -> code - 0x1D41A + 'a'.code
                code in 0x1D434..0x1D44D -> code - 0x1D434 + 'A'.code
                code in 0x1D44E..0x1D467 -> code - 0x1D44E + 'a'.code
                code in 0x1D504..0x1D51D -> code - 0x1D504 + 'A'.code
                code in 0x1D51E..0x1D537 -> code - 0x1D51E + 'a'.code
                code in 0x1D5A0..0x1D5B9 -> code - 0x1D5A0 + 'A'.code
                code in 0x1D5BA..0x1D5D3 -> code - 0x1D5BA + 'a'.code
                code in 0x1D56C..0x1D585 -> code - 0x1D56C + 'A'.code
                code in 0x1D586..0x1D59F -> code - 0x1D586 + 'a'.code
                else -> code
            }
            val ch = mapped.toChar()
            if (Character.isLetterOrDigit(ch) || ch == '_') {
                sb.append(Character.toLowerCase(ch))
            }
        }
        return sb.toString()
    }

    /**
     * Computes similarity score between two player names (0.0 to 1.0).
     */
    fun fuzzyMatchScore(name1: String?, name2: String?): Float {
        if (name1.isNullOrBlank() || name2.isNullOrBlank()) return 0.0f
        val n1 = normalizeStylizedName(name1)
        val n2 = normalizeStylizedName(name2)
        if (n1 == n2) return 1.0f
        if (n1.isBlank() || n2.isBlank()) return 0.0f

        val maxLen = maxOf(n1.length, n2.length)
        if (maxLen == 0) return 1.0f
        val distance = computeLevenshteinDistance(n1, n2)
        return 1.0f - (distance.toFloat() / maxLen.toFloat())
    }

    private fun computeLevenshteinDistance(lhs: CharSequence, rhs: CharSequence): Int {
        val lhsLength = lhs.length
        val rhsLength = rhs.length
        var cost = IntArray(lhsLength + 1) { it }
        var newCost = IntArray(lhsLength + 1)
        for (i in 1..rhsLength) {
            newCost[0] = i
            for (j in 1..lhsLength) {
                val match = if (lhs[j - 1] == rhs[i - 1]) 0 else 1
                newCost[j] = minOf(
                    newCost[j - 1] + 1,
                    cost[j] + 1,
                    cost[j - 1] + match
                )
            }
            val swap = cost
            cost = newCost
            newCost = swap
        }
        return cost[lhsLength]
    }
}
