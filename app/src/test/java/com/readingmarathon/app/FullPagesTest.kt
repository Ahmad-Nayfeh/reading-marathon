package com.readingmarathon.app

import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import com.readingmarathon.app.data.Account
import com.readingmarathon.app.data.Role
import com.readingmarathon.app.fake.FakeBackend
import org.junit.After
import org.junit.Before
import java.time.LocalDate
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
    val compose = createEmptyComposeRule()

    private val backend = FakeBackend()
    private lateinit var scenario: ActivityScenario<MainActivity>

    @Before
    fun setUp() {
        val group = SampleData.build(LocalDate.now())
        val me = Account("u_sara", "sara@gmail.com", "سارة", isGuest = false)
        backend.seed(group.copy(members = group.members.mapIndexed { i, m -> if (i == 0) m.copy(linkedUid = me.uid) else m }), mapOf(me.uid to Role.ORGANIZER))
        backend.signIn(me)
        MarathonApp.backendOverride = backend
        scenario = ActivityScenario.launch(MainActivity::class.java)
    }

    @After
    fun tearDown() {
        scenario.close()
        MarathonApp.backendOverride = null
    }

    private fun shot(name: String) {
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(2_000)
        compose.onRoot().captureRoboImage("build/shots/$name.png")
    }

    private fun tap(text: String) {
        compose.waitUntil(10_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodesWithText(text)[0].performClick()
        compose.waitForIdle()
    }

    @Test
    fun pages() {
        tap("ماراثون نادي الكتاب")
        compose.waitUntil(10_000) { compose.onAllNodesWithText("الرئيسية").fetchSemanticsNodes().isNotEmpty() }
        shot("10-home-dark")
        tap("التحديات")
        compose.onAllNodesWithText("جارٍ الآن")[0].performClick()
        shot("11-detail-dark")
        scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        tap("بطاقتي")
        shot("12-card-dark")
        tap("الرئيسية")
        compose.onAllNodesWithContentDescription("الإدارة")[0].performClick()
        shot("13-admin-dark")
        scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        compose.onAllNodesWithContentDescription("الإعدادات")[0].performClick()
        shot("14-settings-dark")
    }
}
