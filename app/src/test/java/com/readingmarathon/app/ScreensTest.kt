package com.readingmarathon.app

import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.onAllNodesWithText
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Drives the real app through the demo marathon and captures every main screen, so layout
 * problems (RTL, clipping, overflow) are visible without a device. Run: ./gradlew :app:recordRoborazziDebug
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class ScreensTest {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private fun shot(name: String) {
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(2_000)
        compose.onRoot().captureRoboImage("build/shots/$name.png")
    }

    private fun tap(text: String) {
        compose.onAllNodesWithText(text)[0].performClick()
        compose.waitForIdle()
    }

    @Test
    fun tour() {
        shot("01-welcome")
        tap("جرّب التطبيق ببيانات تجريبية")
        compose.waitUntil(10_000) { compose.onAllNodesWithText("الرئيسية").fetchSemanticsNodes().isNotEmpty() }
        shot("02-home")
        tap("المتصدرون")
        shot("03-leaderboard")
        tap("التحديات")
        shot("04-challenges")
        compose.onAllNodesWithText("جارٍ الآن")[0].performClick()
        shot("05-challenge-detail")
        compose.activity.onBackPressedDispatcher.onBackPressed()
        tap("بطاقتي")
        shot("06-my-card")
        tap("سجّل قراءة")
        shot("07-log-sheet")
    }
}
