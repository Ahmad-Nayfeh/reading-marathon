package com.readingmarathon.app

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Whole pages in dark mode on a very tall screen, so nothing below the fold goes unchecked. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h2400dp-night-xhdpi")
class FullPagesTest {

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
    fun pages() {
        tap("جرّب التطبيق ببيانات تجريبية")
        compose.waitUntil(10_000) { compose.onAllNodesWithText("الرئيسية").fetchSemanticsNodes().isNotEmpty() }
        shot("10-home-dark")
        tap("التحديات")
        compose.onAllNodesWithText("جارٍ الآن")[0].performClick()
        shot("11-detail-dark")
        compose.activity.onBackPressedDispatcher.onBackPressed()
        tap("بطاقتي")
        shot("12-card-dark")
        tap("الرئيسية")
        compose.onAllNodesWithContentDescription("الإدارة")[0].performClick()
        shot("13-admin-dark")
        compose.activity.onBackPressedDispatcher.onBackPressed()
        compose.onAllNodesWithContentDescription("الإعدادات")[0].performClick()
        shot("14-settings-dark")
    }
}
