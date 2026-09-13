package com.example

import com.example.services.detection.pubg.KillFeedCause
import com.example.services.detection.pubg.KillFeedTemporalTracker
import com.example.services.detection.pubg.PUBGDecisionType
import com.example.services.detection.pubg.PUBGKillFeedDecisionEngine
import com.example.services.detection.pubg.PUBGTextNormalizer
import com.example.services.detection.pubg.TournamentRoster
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class Phase4c5TeamPlayerIdentificationTest {

    private lateinit var temporalTracker: KillFeedTemporalTracker

    @Before
    fun setup() {
        temporalTracker = KillFeedTemporalTracker()
        TournamentRoster.clearRoster()
    }

    @Test
    fun test01_T3P2_Kills_T7P4_NoRosterLoaded() {
        val rawLeft = "T3 P2"
        val rawRight = "T7 P4"

        val decision = PUBGKillFeedDecisionEngine.evaluate(
            rawLeft = rawLeft,
            rawRight = rawRight,
            cause = KillFeedCause.GUN,
            hasKnock = false
        )

        assertEquals(PUBGDecisionType.ENEMY_KILL, decision.decisionType)
        assertEquals(true, decision.dead)
        assertEquals(1, decision.killCount)
        assertEquals("T3 P2", decision.pointAwardedTo)
        assertEquals("T3 P2", decision.killerName)
        assertEquals("T7 P4", decision.victimName)
        assertEquals("T3", decision.killerTeam)
        assertEquals("T7", decision.victimTeam)
    }

    @Test
    fun test02_T3P2_Kills_T7P4_WithTournamentRosterMapping() {
        // Register slot booking data
        TournamentRoster.registerPlayer(
            teamNumber = 3,
            playerNumber = 2,
            inGameName = "Jonathan",
            teamName = "GodLike Esports",
            teamTag = "GOD"
        )
        TournamentRoster.registerPlayer(
            teamNumber = 7,
            playerNumber = 4,
            inGameName = "Goblin",
            teamName = "SouL Esports",
            teamTag = "SOUL"
        )

        val rawLeft = "🇮🇩 T3 P2"
        val rawRight = "🇵🇭 T7 P4"

        val decision = PUBGKillFeedDecisionEngine.evaluate(
            rawLeft = rawLeft,
            rawRight = rawRight,
            cause = KillFeedCause.GUN,
            hasKnock = false
        )

        assertEquals(PUBGDecisionType.ENEMY_KILL, decision.decisionType)
        assertEquals(1, decision.killCount)
        assertEquals("Jonathan", decision.pointAwardedTo)
        assertEquals("Jonathan", decision.killerName)
        assertEquals("Goblin", decision.victimName)
        assertEquals("GOD", decision.killerTeam)
        assertEquals("SOUL", decision.victimTeam)
    }

    @Test
    fun test03_T3P2_Knocks_T7P4() {
        val rawLeft = "T3 P2"
        val rawRight = "T7 P4"

        val decision = PUBGKillFeedDecisionEngine.evaluate(
            rawLeft = rawLeft,
            rawRight = rawRight,
            cause = KillFeedCause.KNOCK,
            hasKnock = true
        )

        assertEquals(PUBGDecisionType.KNOCK, decision.decisionType)
        assertEquals(false, decision.dead)
        assertEquals(0, decision.killCount)
        assertNull(decision.pointAwardedTo)
        assertEquals("T3 P2", decision.killerName)
        assertEquals("T7 P4", decision.victimName)
    }

    @Test
    fun test04_T3P2_SelfKill() {
        val rawLeft = "T3 P2"
        val rawRight = "T3 P2"

        val decision = PUBGKillFeedDecisionEngine.evaluate(
            rawLeft = rawLeft,
            rawRight = rawRight,
            cause = KillFeedCause.GRENADE,
            hasKnock = false
        )

        assertEquals(PUBGDecisionType.SELF_KILL, decision.decisionType)
        assertEquals(true, decision.dead)
        assertEquals(1, decision.killCount)
        assertNull(decision.pointAwardedTo) // 0 points awarded for suicide
        assertEquals("T3 P2", decision.killerName)
        assertEquals("T3 P2", decision.victimName)
    }

    @Test
    fun test05_SameTeamKill_T3P1_Kills_T3P2() {
        val rawLeft = "T3 P1"
        val rawRight = "T3 P2"

        val decision = PUBGKillFeedDecisionEngine.evaluate(
            rawLeft = rawLeft,
            rawRight = rawRight,
            cause = KillFeedCause.VEHICLE,
            hasKnock = false
        )

        assertEquals(PUBGDecisionType.TEAM_KILL, decision.decisionType)
        assertEquals(true, decision.dead)
        assertEquals(1, decision.killCount)
        assertNull(decision.pointAwardedTo) // 0 points awarded for team kill
        assertEquals("T3 P1", decision.killerName)
        assertEquals("T3 P2", decision.victimName)
    }

    @Test
    fun test06_EnvironmentDeath_PlayzoneKills_T7P4() {
        val rawLeft = "Playzone"
        val rawRight = "T7 P4"

        val decision = PUBGKillFeedDecisionEngine.evaluate(
            rawLeft = rawLeft,
            rawRight = rawRight,
            cause = KillFeedCause.PLAYZONE,
            hasKnock = false
        )

        assertEquals(PUBGDecisionType.ENV_DEATH, decision.decisionType)
        assertEquals(true, decision.dead)
        assertEquals(0, decision.killCount)
        assertNull(decision.pointAwardedTo) // 0 points awarded
        assertNull(decision.killerName)
        assertEquals("T7 P4", decision.victimName)
    }

    @Test
    fun test07_UnclearTeamPlayerNumber_ReturnsWait() {
        val rawLeft = "T3 P?"
        val rawRight = "T7 P4"

        assertTrue(PUBGTextNormalizer.hasUnclearTeamPlayerToken(rawLeft))

        val decision = PUBGKillFeedDecisionEngine.evaluate(
            rawLeft = rawLeft,
            rawRight = rawRight,
            cause = KillFeedCause.GUN
        )

        assertEquals(PUBGDecisionType.NO_DECISION_WAIT, decision.decisionType)

        val isReady = temporalTracker.isVisualEvidenceReady(
            rawLeft = rawLeft,
            rawRight = rawRight,
            cause = KillFeedCause.GUN,
            isTransitioning = false,
            clarityScore = 0.90f
        )
        assertFalse("Unclear team/player token must cause tracker to wait", isReady)
    }

    @Test
    fun test08_DuplicateFrames_ProduceOnlyOneEvent() {
        val rawLeft = "T3 P2"
        val rawRight = "T7 P4"
        val timestamp = 1000000L

        val isDupFrame1 = temporalTracker.isDuplicateEvent(
            rawLeft = rawLeft,
            rawRight = rawRight,
            cause = KillFeedCause.GUN,
            hasKnock = false,
            currentTimestampMs = timestamp
        )
        assertFalse("First frame is not duplicate", isDupFrame1)

        // Mark finalized
        temporalTracker.markEventFinalized(
            rawLeft = rawLeft,
            rawRight = rawRight,
            cause = KillFeedCause.GUN,
            hasKnock = false,
            timestampMs = timestamp
        )

        // Subsequent frame 500ms later
        val isDupFrame2 = temporalTracker.isDuplicateEvent(
            rawLeft = rawLeft,
            rawRight = rawRight,
            cause = KillFeedCause.GUN,
            hasKnock = false,
            currentTimestampMs = timestamp + 500L
        )
        assertTrue("Subsequent frame within duplicate window must be blocked as duplicate", isDupFrame2)
    }

    @Test
    fun test09_CountryFlagsAndDecorationsIgnoredWithTeamPlayerNumber() {
        val rawLeft = "🇮🇩 [CLAN] T3 P2"
        val rawRight = "🇵🇭 【SOUL】 T7 P4"

        val identityLeft = PUBGTextNormalizer.extractTeamPlayerIdentity(rawLeft)
        val identityRight = PUBGTextNormalizer.extractTeamPlayerIdentity(rawRight)

        assertNotNull(identityLeft)
        assertNotNull(identityRight)

        assertEquals(3, identityLeft?.teamNumber)
        assertEquals(2, identityLeft?.playerNumber)
        assertEquals(7, identityRight?.teamNumber)
        assertEquals(4, identityRight?.playerNumber)

        val decision = PUBGKillFeedDecisionEngine.evaluate(rawLeft, rawRight, KillFeedCause.GUN)
        assertEquals("T3 P2", decision.killerName)
        assertEquals("T7 P4", decision.victimName)
    }
}
