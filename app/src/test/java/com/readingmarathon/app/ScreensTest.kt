package com.readingmarathon.app

import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ActivityScenario
import com.github.takahirom.roborazzi.captureRoboImage
import com.readingmarathon.app.data.ADMIN_EMAIL
import com.readingmarathon.app.data.Account
import com.readingmarathon.app.data.Profile
import com.readingmarathon.app.fake.FakeBackend
import com.readingmarathon.core.Book
import com.readingmarathon.core.Challenge
import com.readingmarathon.core.Day
import com.readingmarathon.core.Entry
import com.readingmarathon.core.Finish
import com.readingmarathon.core.Group
import com.readingmarathon.core.GroupData
import com.readingmarathon.core.Member
import com.readingmarathon.core.Role
import com.readingmarathon.core.Visibility
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

/**
 * Drives the real app on an in-memory backend through every screen and captures each one, so
 * layout problems (RTL, clipping, overflow) show without a device.
 * Run: ./gradlew :app:recordRoborazziDebug — images land in app/build/shots.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class ScreensTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    private val backend = FakeBackend()
    private val today = LocalDate.now()
    private val sara = Account("u_sara", "sara@gmail.com", "Sara", isGuest = false)
    private lateinit var scenario: ActivityScenario<MainActivity>

    /** A club two weeks into a shared book. Minutes follow a fixed pattern so shots are stable. */
    private fun club(): GroupData {
        val names = listOf("u_sara" to "سارة", "u_khalid" to "خالد", "u_mona" to "منى", "u_omar" to "عمر", "u_huda" to "هدى", "r_abu" to "أبو أحمد")
        val members = names.map { (id, n) -> Member(id, n, if (id.startsWith("u_")) id else null) }
        val book = Challenge("c_now", Book("الأيام", "طه حسين"), today.minusDays(11), today.plusDays(16))
        val past = Challenge("c_past", Book("موسم الهجرة إلى الشمال", "الطيب صالح"), today.minusDays(45), today.minusDays(18))
        val pattern = listOf(30, 45, 0, 20, 60, 15, 40, 25, 0, 50, 35, 30)
        val entries = members.flatMapIndexed { i, m ->
            (0..40).mapNotNull { back ->
                val mins = pattern[(back + i * 3) % pattern.size] * (6 - i) / 5
                if (mins == 0 || (m.id == "u_sara" && back == 0)) null else Entry(m.id, today.minusDays(back.toLong()), mins, updatedAt = 1_000_000L - back * 1000L - i)
            }
        }
        return GroupData(
            Group(
                "g_club", "نادي كتاب الحي", "نقرأ كتاباً كل شهر ونلتقي في آخر جمعة لنتحدث عنه.", Visibility.PRIVATE, "KTB7Q2",
                mapOf("u_sara" to Role.OWNER, "u_khalid" to Role.ADMIN, "u_mona" to Role.MEMBER, "u_omar" to Role.MEMBER, "u_huda" to Role.MEMBER),
            ),
            members, listOf(book, past), entries,
            listOf(Finish("u_khalid", "c_now", today.minusDays(2)), Finish("u_sara", "c_past", today.minusDays(20)), Finish("u_mona", "c_past", today.minusDays(19))),
        )
    }

    @Before
    fun setUp() {
        backend.profiles["u_sara"] = Profile("سارة", goal = 30)
        val d = club()
        backend.seed(d)
        backend.seed(GroupData(Group("g_family", "قراءة العائلة", visibility = Visibility.PRIVATE, inviteCode = "FAM234", roles = mapOf("u_sara" to Role.MEMBER, "u_dad" to Role.OWNER)), listOf(Member("u_sara", "سارة", "u_sara"), Member("u_dad", "أبي", "u_dad"))))
        backend.seed(GroupData(Group("g_public", "قرّاء الرياض", "مجموعة مفتوحة لكل من يحب القراءة في الرياض.", Visibility.PUBLIC, "RYD456", mapOf("u_x" to Role.OWNER, "u_y" to Role.MEMBER, "u_z" to Role.MEMBER))))
        backend.logs["u_sara"] = d.entries.filter { it.memberId == "u_sara" }.associate { it.date to Day(it.date, it.minutes) }.toMutableMap()
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

    private fun waitFor(text: String) = compose.waitUntil(10_000) { compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty() }

    private fun tap(text: String, index: Int = 0) {
        waitFor(text)
        compose.onAllNodesWithText(text)[index].apply { runCatching { performScrollTo() } }.performClick()
        compose.waitForIdle()
    }

    private fun tapIcon(description: String) {
        compose.waitUntil(10_000) { compose.onAllNodesWithContentDescription(description).fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodesWithContentDescription(description)[0].performClick()
        compose.waitForIdle()
    }

    @Test
    fun tour() {
        shot("01-sign-in")
        backend.signIn(sara)
        waitFor("مجموعاتي")
        shot("02-home")
        tap("\u2066+30\u2069")
        assertEquals(30, backend.logs.getValue("u_sara")[today]?.minutes)
        assertTrue(backend.all.getValue("g_club").entries.any { it.memberId == "u_sara" && it.date == today && it.minutes == 30 })
        shot("03-home-logged")
        tap("تعديل")
        waitFor("قراءة يوم")
        shot("04-day-sheet")
        tap("حفظ اليوم")
        compose.waitUntil(10_000) { compose.onAllNodesWithText("قراءة يوم").fetchSemanticsNodes().isEmpty() }
        compose.mainClock.advanceTimeBy(12_000) // let the snackbar go
        tap("نادي كتاب الحي")
        waitFor("نقرأ الآن")
        shot("05-group")
        tap("النشاط")
        shot("06-group-activity")
        tap("الأعضاء")
        shot("07-group-members")
        tap("الكتب")
        shot("08-group-books")
        tapIcon("إعدادات المجموعة")
        waitFor("رمز الدعوة")
        shot("09-group-settings")
    }

    @Test
    fun tabs() {
        backend.signIn(sara)
        waitFor("مجموعاتي")
        tap("المجموعات", index = 0)
        waitFor("مجموعات عامة")
        shot("10-groups")
        tap("أنا")
        waitFor("قراءتي")
        shot("11-me")
    }

    @Test
    fun newReader() {
        backend.signIn(Account("u_new", null, null, isGuest = true))
        waitFor("أهلاً بك")
        shot("12-name")
    }

    @Test
    fun admin() {
        val admin = Account("u_admin", ADMIN_EMAIL, "الإدارة", false)
        backend.profiles["u_admin"] = Profile("الإدارة")
        backend.signIn(admin)
        waitFor("مجموعاتي")
        tap("أنا")
        tap("لوحة إدارة المنصة")
        waitFor("الحسابات")
        shot("13-admin")
        tap("نادي كتاب الحي")
        waitFor("نقرأ الآن")
        shot("14-admin-opened-group")
    }
}
