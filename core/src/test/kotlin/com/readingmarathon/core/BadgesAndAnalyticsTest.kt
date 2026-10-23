package com.readingmarathon.core

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BadgesAndAnalyticsTest {
    private val d0 = LocalDate.of(2026, 1, 1)
    private val c = Challenge("c1", Book("كتاب"), d0, d0.plusDays(29))
    private val a = Member("a", "أحمد")

    private fun data(entries: List<Entry>, ach: List<Achievement> = emptyList()) =
        MarathonData(Marathon("m", "م"), listOf(a), listOf(c), entries, ach)

    @Test
    fun `streaks count reading days only`() {
        val days = (0..6).map { d0.plusDays(it.toLong()) }
        assertEquals(7, Badges.longestStreak(days))
        assertEquals(3, Badges.longestStreak(listOf(d0, d0.plusDays(1), d0.plusDays(2), d0.plusDays(4))))
        val entries = days.map { Entry("a", it, 10) } + Entry("a", d0.plusDays(7), 0, quoteCommon = true)
        val kinds = Badges.forMember(data(entries), a, Scope.AllTime).map { it.kind }
        assertTrue(BadgeKind.PERSISTENCE in kinds)
    }

    @Test
    fun `current streak survives until today's log`() {
        val today = d0.plusDays(5)
        val dates = listOf(d0.plusDays(2), d0.plusDays(3), d0.plusDays(4))
        assertEquals(3, Badges.currentStreak(dates, today))
        assertEquals(0, Badges.currentStreak(dates, today.plusDays(1)))
    }

    @Test
    fun `runner badge needs a finish within the first 7 days`() {
        val day7 = Achievement("x", "a", "c1", AchievementType.FINISHED_COMMON_BOOK, d0.plusDays(6))
        assertTrue(Badges.forMember(data(emptyList(), listOf(day7)), a, Scope.AllTime).any { it.kind == BadgeKind.RUNNER })
        val day8 = day7.copy(date = d0.plusDays(7))
        assertTrue(Badges.forMember(data(emptyList(), listOf(day8)), a, Scope.AllTime).none { it.kind == BadgeKind.RUNNER })
    }

    @Test
    fun `philosopher needs more than 10 quotes`() {
        val ten = (0..9).map { Entry("a", d0.plusDays(it.toLong()), 5, quoteCommon = true) }
        assertTrue(Badges.forMember(data(ten), a, Scope.AllTime).none { it.kind == BadgeKind.PHILOSOPHER })
        val eleven = ten + Entry("a", d0.plusDays(10), 5, quoteOther = true)
        assertTrue(Badges.forMember(data(eleven), a, Scope.AllTime).any { it.kind == BadgeKind.PHILOSOPHER })
    }

    @Test
    fun `progress stops at the end date`() {
        val p = Analytics.progress(c, c.end.plusDays(40))
        assertEquals(30, p.daysPassed)
        assertEquals(30, p.totalDays)
        assertEquals(0, p.daysLeft)
        assertEquals(1, Analytics.progress(c, c.start).daysPassed)
        assertEquals(0, Analytics.progress(c, c.start.minusDays(1)).daysPassed)
    }

    @Test
    fun `heatmap weeks start on Saturday and span year boundaries in order`() {
        val from = LocalDate.of(2025, 12, 25)
        val to = LocalDate.of(2026, 1, 10)
        val weeks = Analytics.heatmap(listOf(Entry("a", LocalDate.of(2026, 1, 2), 30)), from, to)
        assertTrue(weeks.all { it.first().date.dayOfWeek == java.time.DayOfWeek.SATURDAY })
        val all = weeks.flatten().map { it.date }
        assertEquals(all.sorted(), all)
        assertEquals(30, weeks.flatten().first { it.date == LocalDate.of(2026, 1, 2) }.minutes)
    }

    @Test
    fun `weekday shares sum to 100`() {
        val entries = (0..13).map { Entry("a", d0.plusDays(it.toLong()), 10 + it) }
        val total = Analytics.weekdayShare(entries).sumOf { it.second }
        assertEquals(100.0, total, 0.0001)
        assertEquals("السبت", Analytics.weekdayShare(entries).first().first)
    }

    @Test
    fun `hall of fame lists ties and skips zero`() {
        val b = Member("b", "بدر")
        val d = MarathonData(Marathon("m", "م"), listOf(a, b), listOf(c), listOf(Entry("a", d0, 60), Entry("b", d0, 60)))
        val hours = HallOfFame.heroes(d, Scope.AllTime).first { it.title == "سيد الساعات" }
        assertEquals(listOf("أحمد", "بدر"), hours.names)
        assertEquals("1 ساعة", hours.value)
        val books = HallOfFame.heroes(d, Scope.AllTime).first { it.title == "الدودة القارئة" }
        assertTrue(books.names.isEmpty())
    }

    @Test
    fun `demo data is consistent with the rules`() {
        val today = LocalDate.of(2026, 10, 23)
        val demo = DemoData.build(today)
        demo.entries.forEach { Validation.entry(it, today, asOrganizer = true) }
        demo.challenges.forEach { Validation.challenge(it, demo.challenges) }
        val st = Scoring.standings(demo, Scope.OfChallenge(demo.currentChallenge(today)!!))
        assertTrue(st.isNotEmpty())
        assertEquals(1, st.first().rank)
    }
}
