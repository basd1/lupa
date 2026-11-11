package dev.bastian.lupa.domain

import dev.bastian.lupa.app
import dev.bastian.lupa.granted
import dev.bastian.lupa.requested
import org.junit.Test
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RiskScoreTest {
    private fun p(name: String) = "android.permission.$name"

    @Test
    fun appWithoutPermissionsFromAStoreScoresZero() {
        val score = RiskScorer.score(app("a"))
        assertEquals(0, score.value)
        assertEquals(RiskLevel.LOW, score.level)
        assertTrue(score.items.isEmpty())
    }

    @Test
    fun grantedCategoryAddsItsWeightAndRequestedOnlyAddsAQuarter() {
        val granted = RiskScorer.score(app("a", permissions = listOf(granted(p("CAMERA")))))
        assertEquals(RiskWeights.categoryGranted.getValue(PermissionCategory.CAMERA), granted.value)
        assertEquals(listOf(ScoreReason.Granted(PermissionCategory.CAMERA)), granted.items.map { it.reason })

        val onlyRequested = RiskScorer.score(app("a", permissions = listOf(requested(p("CAMERA")))))
        assertEquals(6 / RiskWeights.REQUESTED_ONLY_DIVISOR, onlyRequested.value)
        assertEquals(listOf(ScoreReason.RequestedOnly(PermissionCategory.CAMERA)), onlyRequested.items.map { it.reason })
        assertTrue(onlyRequested.value < granted.value)
    }

    @Test
    fun severalPermissionsOfOneCategoryCountOnce() {
        val score = RiskScorer.score(
            app("a", permissions = listOf(granted(p("ACCESS_FINE_LOCATION")), granted(p("ACCESS_COARSE_LOCATION")))),
        )
        assertEquals(RiskWeights.categoryGranted.getValue(PermissionCategory.LOCATION), score.value)
    }

    @Test
    fun aCategoryWithOneGrantedAndOneRequestedPermissionCountsAsGranted() {
        val score = RiskScorer.score(
            app("a", permissions = listOf(granted(p("ACCESS_COARSE_LOCATION")), requested(p("ACCESS_FINE_LOCATION")))),
        )
        assertEquals(listOf(ScoreReason.Granted(PermissionCategory.LOCATION)), score.items.map { it.reason })
    }

    @Test
    fun oldTargetSdkAddsPointsByAge() {
        assertEquals(15, RiskScorer.score(app("a", targetSdk = 22)).value)
        assertEquals(8, RiskScorer.score(app("a", targetSdk = 26)).value)
        assertEquals(4, RiskScorer.score(app("a", targetSdk = 30)).value)
        assertEquals(0, RiskScorer.score(app("a", targetSdk = 31)).value)
        assertEquals(ScoreReason.OldTargetSdk(22), RiskScorer.score(app("a", targetSdk = 22)).items.single().reason)
    }

    @Test
    fun unverifiedOriginAddsPointsButStoresAndSystemAppsDoNot() {
        assertEquals(RiskWeights.UNVERIFIED_ORIGIN_POINTS, RiskScorer.score(app("a", installer = null)).value)
        assertEquals(
            RiskWeights.UNVERIFIED_ORIGIN_POINTS,
            RiskScorer.score(app("a", installer = "com.google.android.packageinstaller")).value,
        )
        assertEquals(0, RiskScorer.score(app("a", installer = "com.android.vending")).value)
        assertEquals(0, RiskScorer.score(app("a", installer = null, isSystem = true)).value)
        assertEquals(0, RiskScorer.score(app("a", installer = "com.some.mdm")).value)
    }

    @Test
    fun sensitiveCombinationAddsItsOwnPoints() {
        val score = RiskScorer.score(
            app("a", permissions = listOf(granted(p("RECORD_AUDIO")), granted(p("ACCESS_BACKGROUND_LOCATION")))),
        )
        val expected = RiskWeights.categoryGranted.getValue(PermissionCategory.MICROPHONE) +
            RiskWeights.categoryGranted.getValue(PermissionCategory.BACKGROUND_LOCATION) +
            RiskWeights.combinationPoints.getValue(Combination.MICROPHONE_AND_BACKGROUND_LOCATION)
        assertEquals(expected, score.value)
        assertTrue(score.items.any { it.reason == ScoreReason.Together(Combination.MICROPHONE_AND_BACKGROUND_LOCATION) })
    }

    @Test
    fun combinationNeedsBothSidesGranted() {
        val score = RiskScorer.score(
            app("a", permissions = listOf(granted(p("RECORD_AUDIO")), requested(p("ACCESS_BACKGROUND_LOCATION")))),
        )
        assertTrue(score.items.none { it.reason is ScoreReason.Together })
    }

    @Test
    fun accessibilityAndOverlayTogetherAreTheWorstPair() {
        val score = RiskScorer.score(
            app("a", permissions = listOf(granted(p("SYSTEM_ALERT_WINDOW"))), declaresAccessibilityService = true),
        )
        assertTrue(score.items.any { it.reason == ScoreReason.Together(Combination.OVERLAY_AND_ACCESSIBILITY) })
    }

    @Test
    fun appWithEverythingGrantedIsCappedAtOneHundredAndTheItemsExplainTheUncappedSum() {
        val everything = PermissionCatalogSamples.all.map { granted(it) }
        val score = RiskScorer.score(
            app(
                "a",
                targetSdk = 19,
                installer = null,
                permissions = everything,
                declaresAccessibilityService = true,
                declaresDeviceAdmin = true,
            ),
        )
        assertEquals(100, score.value)
        assertEquals(RiskLevel.HIGH, score.level)
        assertTrue(score.uncapped > 100)
        assertEquals(score.uncapped, score.items.sumOf { it.points })
    }

    @Test
    fun theScoreAlwaysStaysBetweenZeroAndOneHundredAndItemsAddUpToIt() {
        val random = Random(42)
        val names = PermissionCatalogSamples.all
        repeat(500) {
            val permissions = names.filter { random.nextInt(3) == 0 }.map {
                if (random.nextBoolean()) granted(it) else requested(it)
            }
            val score = RiskScorer.score(
                app(
                    "p$it",
                    targetSdk = random.nextInt(1, 37),
                    installer = listOf(null, "com.android.vending", "x.y").random(random),
                    isSystem = random.nextBoolean(),
                    permissions = permissions,
                    declaresAccessibilityService = random.nextBoolean(),
                    declaresDeviceAdmin = random.nextBoolean(),
                ),
            )
            assertTrue(score.value in 0..100, "score ${score.value}")
            assertEquals(minOf(100, score.uncapped), score.value)
            assertEquals(score.uncapped, score.items.sumOf { it.points })
            assertEquals(RiskWeights.levelOf(score.value), score.level)
        }
    }

    @Test
    fun theSameAppAlwaysGetsTheSameScore() {
        val a = app("a", permissions = listOf(granted(p("CAMERA")), granted(p("READ_SMS"))), targetSdk = 25)
        assertEquals(RiskScorer.score(a), RiskScorer.score(a))
    }

    @Test
    fun levelsSplitAtTheDocumentedThresholds() {
        assertEquals(RiskLevel.LOW, RiskWeights.levelOf(19))
        assertEquals(RiskLevel.MEDIUM, RiskWeights.levelOf(20))
        assertEquals(RiskLevel.MEDIUM, RiskWeights.levelOf(49))
        assertEquals(RiskLevel.HIGH, RiskWeights.levelOf(50))
        assertEquals(RiskLevel.HIGH, RiskWeights.levelOf(100))
    }

    @Test
    fun everyCategoryHasAWeight() {
        assertEquals(PermissionCategory.entries.toSet(), RiskWeights.categoryGranted.keys)
    }
}

/** Every permission name the catalog knows, taken from the catalog itself through its categories. */
internal object PermissionCatalogSamples {
    val all: List<String> = listOf(
        "ACCESS_FINE_LOCATION", "ACCESS_COARSE_LOCATION", "ACCESS_BACKGROUND_LOCATION", "CAMERA", "RECORD_AUDIO",
        "READ_CONTACTS", "READ_CALENDAR", "READ_SMS", "READ_PHONE_STATE", "READ_CALL_LOG", "READ_EXTERNAL_STORAGE",
        "BODY_SENSORS", "ACTIVITY_RECOGNITION", "BLUETOOTH_SCAN", "POST_NOTIFICATIONS", "SYSTEM_ALERT_WINDOW",
        "REQUEST_INSTALL_PACKAGES", "MANAGE_EXTERNAL_STORAGE", "PACKAGE_USAGE_STATS",
    ).map { "android.permission.$it" }
}
