package com.readingmarathon.core

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CoreTest {
    private val d0 = LocalDate.of(2026, 11, 1)
    private val book = Challenge("c1", Book("الأيام", "طه حسين"), d0, d0.plusDays(27))
    private val members = listOf(Member("u1", "سارة", "u1"), Member("u2", "خالد", "u2"), Member("g1", "منى"), Member("old", "قديم", active = false))

    private fun data(entries: List<Entry> = emptyList(), finishes: List<Finish> = emptyList()) =
        GroupData(Group("g", "نادي الكتاب", rules = Rules(minutesPerPoint = 10, finishBonus = 50)), members, listOf(book), entries, finishes)

    @Test
    fun `points sum minutes before rounding and add the finish bonus`() {
        val d = data(
            listOf(Entry("u1", d0, 15), Entry("u1", d0.plusDays(1), 15), Entry("u2", d0, 40)),
            listOf(Finish("u2", "c1", d0.plusDays(3))),
        )
        val sara = Scoring.memberScore(d, members[0], Scope.OfChallenge(book))
        assertEquals(3, sara.points) // 30 min, not 1 + 1
        assertEquals(2, sara.readingDays)
        assertEquals(54, Scoring.memberScore(d, members[1], Scope.OfChallenge(book)).points)
    }

    @Test
    fun `standings rank ties together and leave archived members out`() {
        val d = data(listOf(Entry("u1", d0, 30), Entry("u2", d0, 30), Entry("g1", d0, 10), Entry("old", d0, 500)))
        val s = Scoring.standings(d, Scope.OfChallenge(book))
        assertEquals(listOf(1, 1, 3), s.map { it.rank })
        assertTrue(s.none { it.score.member.id == "old" })
        assertEquals(3, Scoring.readersOn(d, d0))
    }

    @Test
    fun `challenge scope ignores reading outside its dates`() {
        val d = data(listOf(Entry("u1", d0.minusDays(1), 100), Entry("u1", d0, 20)))
        assertEquals(2, Scoring.memberScore(d, members[0], Scope.OfChallenge(book)).points)
        assertEquals(12, Scoring.memberScore(d, members[0], Scope.AllTime).points)
    }

    @Test
    fun `finish of a deleted challenge does not count`() {
        val d = data(finishes = listOf(Finish("u1", "gone", d0)))
        assertEquals(0, Scoring.memberScore(d, members[0], Scope.AllTime).points)
    }

    @Test
    fun `streaks survive a day not logged yet`() {
        val today = d0.plusDays(10)
        val days = listOf(today.minusDays(1), today.minusDays(2), today.minusDays(3), today.minusDays(6))
        assertEquals(3, Stats.currentStreak(days, today))
        assertEquals(4, Stats.currentStreak(days + today, today))
        assertEquals(0, Stats.currentStreak(listOf(today.minusDays(2)), today))
        assertEquals(3, Stats.longestStreak(days))
    }

    @Test
    fun `personal stats count this week from Saturday`() {
        val friday = LocalDate.of(2026, 11, 6)
        val days = listOf(Day(friday, 30), Day(friday.minusDays(6), 20), Day(friday.minusDays(7), 99), Day(friday.minusDays(40), 10))
        val s = Stats.personal(days, friday)
        assertEquals(30, s.today)
        assertEquals(50, s.minutesThisWeek) // Saturday 31 Oct to Friday 6 Nov
        assertEquals(30, s.minutesThisMonth)
        assertEquals(1, s.currentStreak)
    }

    @Test
    fun `heatmap pads to whole weeks across a year end`() {
        val weeks = Stats.heatmap(mapOf(LocalDate.of(2026, 1, 1) to 30), LocalDate.of(2025, 12, 25), LocalDate.of(2026, 1, 8))
        assertTrue(weeks.all { it.size == 7 })
        assertEquals(java.time.DayOfWeek.SATURDAY, weeks.first().first().date.dayOfWeek)
        assertEquals(30, weeks.flatten().first { it.date == LocalDate.of(2026, 1, 1) }.minutes)
    }

    @Test
    fun `validation rejects future days, old days and overlapping books`() {
        val today = d0.plusDays(20)
        assertFailsWith<RuleViolation> { Validation.day(today.plusDays(1), 10, today) }
        assertFailsWith<RuleViolation> { Validation.day(today.minusDays(15), 10, today) }
        assertFailsWith<RuleViolation> { Validation.day(today, 961, today) }
        Validation.day(today.minusDays(14), 960, today)
        val clash = Challenge("c2", Book("آخر"), d0.plusDays(27), d0.plusDays(40))
        assertFailsWith<RuleViolation> { Validation.challenge(clash, listOf(book)) }
        Validation.challenge(clash.copy(start = d0.plusDays(28)), listOf(book))
        assertFailsWith<RuleViolation> { Validation.finish(book, book.end.plusDays(8)) }
        Validation.finish(book, book.end.plusDays(7))
    }

    @Test
    fun `member names are unique within a group, Arabic-aware`() {
        assertFailsWith<RuleViolation> { Validation.memberName("ساره ", members) }
        Validation.memberName("قديم", members) // only clashes with an archived member
        Validation.memberName("سارة", members, selfId = "u1")
    }

    @Test
    fun `feed lists reading and finishes newest first`() {
        val d = data(listOf(Entry("u1", d0, 30, updatedAt = 10), Entry("u2", d0, 0, updatedAt = 20), Entry("u2", d0.plusDays(1), 15, updatedAt = 5)), listOf(Finish("g1", "c1", d0.plusDays(2))))
        val feed = Feed.of(d)
        assertEquals(3, feed.size)
        assertTrue(feed.first() is Activity.Finished)
        assertNull(feed.firstOrNull { it is Activity.Read && it.minutes == 0 })
    }

    @Test
    fun `arabic counts agree with the number`() {
        assertEquals("دقيقة واحدة", Fmt.minutes(1))
        assertEquals("دقيقتان", Fmt.minutes(2))
        assertEquals("5 دقائق", Fmt.minutes(5))
        assertEquals("45 دقيقة", Fmt.minutes(45))
        assertEquals("1 س 25 د", Fmt.duration(85))
        assertEquals("1.5", Fmt.hours(90))
        assertEquals("1 – 28 نوفمبر", Dates.span(book.start, book.end))
    }

    @Test
    fun `ranking text lists only members who read`() {
        val d = data(listOf(Entry("u1", d0, 60)), listOf(Finish("u1", "c1", d0)))
        val text = Export.rankingText(d, Scope.OfChallenge(book), d0.plusDays(4))
        assertTrue("1. سارة · 56 نقطة · 1 ساعة · أتمّ الكتاب" in text, text)
        assertTrue("خالد" !in text)
        assertTrue("اليوم 5 من 28" in text)
    }
}
