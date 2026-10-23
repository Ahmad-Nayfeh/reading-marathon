package com.readingmarathon.app

import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import com.github.takahirom.roborazzi.captureRoboImage
import com.readingmarathon.app.data.Account
import com.readingmarathon.app.data.ADMIN_EMAIL
import com.readingmarathon.app.data.Role
import com.readingmarathon.app.fake.FakeBackend
import com.readingmarathon.core.Marathon
import com.readingmarathon.core.MarathonData
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

/**
 * Drives the real app (with an in-memory backend) through every main screen and captures each one,
 * so layout problems (RTL, clipping, overflow) are visible without a device.
 * Run: ./gradlew :app:recordRoborazziDebug — images land in app/build/shots.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class ScreensTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    private val backend = FakeBackend()
    private val reader = Account("u_sara", "sara@gmail.com", "سارة", isGuest = false)
    private lateinit var scenario: ActivityScenario<MainActivity>

    @Before
    fun setUp() {
        val today = LocalDate.now()
        val group = SampleData.build(today)
        // Sara reads in the sample marathon; her member is linked to her account.
        backend.seed(
            group.copy(members = group.members.mapIndexed { i, m -> if (i == 0) m.copy(linkedUid = reader.uid) else m }),
            mapOf(reader.uid to Role.ORGANIZER),
        )
        backend.seed(MarathonData(Marathon("mr_second", "ماراثون العائلة", "FAM123")), mapOf("u_other" to Role.ORGANIZER))
        backend.users[reader.uid] = reader
        backend.users["u_other"] = Account("u_other", "family@gmail.com", "أبو أحمد", false)
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
    fun tour() {
        shot("01-sign-in")
        backend.signIn(reader)
        tap("ماراثون نادي الكتاب")
        compose.waitUntil(10_000) { compose.onAllNodesWithText("الرئيسية").fetchSemanticsNodes().isNotEmpty() }
        shot("02-home")
        tap("المتصدرون")
        shot("03-leaderboard")
        tap("التحديات")
        shot("04-challenges")
        tap("جارٍ الآن")
        shot("05-challenge-detail")
        scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        tap("بطاقتي")
        shot("06-my-card")
        tap("سجّل قراءة")
        shot("07-log-sheet")
    }

    @Test
    fun marathonsAndAdmin() {
        backend.signIn(Account("u_admin", ADMIN_EMAIL, "الإدارة", false))
        compose.waitUntil(10_000) { compose.onAllNodesWithText("ماراثوناتي").fetchSemanticsNodes().isNotEmpty() }
        shot("08-marathons")
        tap("لوحة إدارة المنصة")
        compose.waitUntil(10_000) { compose.onAllNodesWithText("الحسابات", substring = true).fetchSemanticsNodes().isNotEmpty() }
        shot("09-admin")
        tap("ماراثون نادي الكتاب")
        compose.waitUntil(20_000) { compose.onAllNodesWithContentDescription("الإدارة").fetchSemanticsNodes().isNotEmpty() }
        shot("10-admin-opened-marathon")
    }
}
