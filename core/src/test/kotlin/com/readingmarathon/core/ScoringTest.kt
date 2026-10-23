package com.readingmarathon.core

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ScoringTest {
    private val d0 = LocalDate.of(2026, 3, 1)
    private val c = Challenge("c1", Book("كتاب"), d0, d0.plusDays(29))
    private val a = Member("a", "أحمد")
    private val b = Member("b", "بدر")

    private fun data(entries: List<Entry> = emptyList(), ach: List<Achievement> = emptyList(), members: List<Member> = listOf(a, b)) =
        MarathonData(Marathon("m", "م"), members, listOf(c), entries, ach)

    @Test
    fun `legacy worked example gives 12 then 87`() {
        // common 0:45, other 0:22, both quotes → 4 + 4 + 3 + 1 = 12 (docs/LEGACY_SPEC.md §4.5)
        val e = Entry("a", d0, 45, 22, quoteCommon = true, quoteOther = true)
        assertEquals(12, Scoring.entryPoints(e, Rules.DEFAULT).total)
        val ach = listOf(
            Achievement("x", "a", "c1", AchievementType.FINISHED_COMMON_BOOK, d0.plusDays(3)),
            Achievement("y", "a", "c1", AchievementType.ATTENDED_DISCUSSION, d0.plusDays(29)),
        )
        val s = Scoring.memberScore(data(listOf(e), ach), a, Scope.OfChallenge(c))
        assertEquals(87, s.totalPoints)
    }

    @Test
    fun `rounding is per daily entry in every scope`() {
        val entries = (0..2).map { Entry("a", d0.plusDays(it.toLong()), commonMinutes = 9) }
        val d = data(entries)
        assertEquals(0, Scoring.memberScore(d, a, Scope.OfChallenge(c)).totalPoints)
        assertEquals(0, Scoring.memberScore(d, a, Scope.AllTime).totalPoints)
    }

    @Test
    fun `entries outside every challenge count hours but no points`() {
        val e = Entry("a", d0.minusDays(5), commonMinutes = 120)
        val s = Scoring.memberScore(data(listOf(e)), a, Scope.AllTime)
        assertEquals(0, s.totalPoints)
        assertEquals(120, s.totalMinutes)
    }

    @Test
    fun `zero minutes per point disables minute points without crashing`() {
        val rules = Rules(minutesPerPointCommon = 0)
        assertEquals(0, Scoring.entryPoints(Entry("a", d0, commonMinutes = 300), rules).readingCommon)
    }

    @Test
    fun `ties share a rank and archived members are hidden`() {
        val entries = listOf(Entry("a", d0, 50), Entry("b", d0, 50), Entry("z", d0, 500))
        val z = Member("z", "زياد", active = false)
        val st = Scoring.standings(data(entries, members = listOf(a, b, z)), Scope.OfChallenge(c))
        assertEquals(listOf(1, 1), st.map { it.rank })
        assertTrue(st.none { it.score.member.id == "z" })
    }

    @Test
    fun `discussion inside the grace window is accepted, after it is refused`() {
        val ok = Achievement("x", "a", "c1", AchievementType.ATTENDED_DISCUSSION, c.end.plusDays(3))
        Validation.achievement(ok, data())
        val late = ok.copy(id = "y", date = c.end.plusDays(4))
        assertFailsWith<RuleViolation> { Validation.achievement(late, data()) }
    }

    @Test
    fun `finish common book only once per challenge, other books need unique titles`() {
        val first = Achievement("x", "a", "c1", AchievementType.FINISHED_COMMON_BOOK, d0)
        val d = data(ach = listOf(first))
        assertFailsWith<RuleViolation> { Validation.achievement(first.copy(id = "x2"), d) }
        val o1 = Achievement("o1", "a", "c1", AchievementType.FINISHED_OTHER_BOOK, d0, "عالم صوفي")
        val d2 = data(ach = listOf(o1))
        assertFailsWith<RuleViolation> { Validation.achievement(o1.copy(id = "o2", bookTitle = " عالم  صوفي "), d2) }
        assertFailsWith<RuleViolation> { Validation.achievement(o1.copy(id = "o3", bookTitle = ""), d) }
        Validation.achievement(o1.copy(id = "o4", bookTitle = "الأيام"), d2)
    }

    @Test
    fun `entries in the future or above 16 hours are refused`() {
        val today = d0.plusDays(10)
        assertFailsWith<RuleViolation> { Validation.entry(Entry("a", today.plusDays(1), 10), today, false) }
        assertFailsWith<RuleViolation> { Validation.entry(Entry("a", today, 17 * 60), today, false) }
        assertFailsWith<RuleViolation> { Validation.entry(Entry("a", today.minusDays(20), 10), today, false) }
        Validation.entry(Entry("a", today.minusDays(20), 10), today, asOrganizer = true)
    }

    @Test
    fun `overlapping challenges are refused`() {
        val clash = Challenge("c2", Book("ب"), d0.plusDays(29), d0.plusDays(40))
        assertFailsWith<RuleViolation> { Validation.challenge(clash, listOf(c)) }
        Validation.challenge(clash.copy(start = d0.plusDays(30)), listOf(c))
    }

    @Test
    fun `deleting a challenge removes its achievements and points`() {
        val ach = listOf(Achievement("x", "a", "c1", AchievementType.FINISHED_COMMON_BOOK, d0))
        val d = Ops.deleteChallenge(data(ach = ach), "c1")
        assertTrue(d.achievements.isEmpty())
        assertEquals(0, Scoring.memberScore(d, a, Scope.AllTime).totalPoints)
    }

    @Test
    fun `an all-zero entry deletes the day`() {
        val today = d0.plusDays(1)
        val d1 = Ops.upsertEntry(data(), Entry("a", d0, 30), today, false)
        assertEquals(1, d1.entries.size)
        val d2 = Ops.upsertEntry(d1, Entry("a", d0, 0), today, false)
        assertEquals(0, d2.entries.size)
    }
}
