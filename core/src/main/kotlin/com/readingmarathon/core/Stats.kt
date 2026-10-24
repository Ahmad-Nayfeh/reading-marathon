package com.readingmarathon.core

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/** Calendar heatmap cell: one per day, laid out in Saturday-first weeks. */
data class HeatCell(val date: LocalDate, val minutes: Int, val inRange: Boolean)

/** The reader's own numbers, from their personal log. */
data class PersonalStats(
    val today: Int,
    val currentStreak: Int,
    val longestStreak: Int,
    val minutesThisWeek: Int,
    val minutesThisMonth: Int,
    val daysThisMonth: Int,
    val minutesThisYear: Int,
    val totalMinutes: Int,
)

object Stats {

    /** Weeks run Saturday to Friday (the Saudi week). */
    fun weekStart(d: LocalDate): LocalDate = d.with(TemporalAdjusters.previousOrSame(DayOfWeek.SATURDAY))

    val WEEK: List<DayOfWeek> = listOf(
        DayOfWeek.SATURDAY, DayOfWeek.SUNDAY, DayOfWeek.MONDAY, DayOfWeek.TUESDAY,
        DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY,
    )

    fun longestStreak(dates: Collection<LocalDate>): Int {
        var best = 0
        var run = 0
        var prev: Long? = null
        for (d in dates.map { it.toEpochDay() }.toSortedSet()) {
            run = if (prev != null && d == prev + 1) run + 1 else 1
            best = maxOf(best, run)
            prev = d
        }
        return best
    }

    /** Streak ending today, or yesterday if today has no reading yet (so it doesn't break mid-day). */
    fun currentStreak(dates: Collection<LocalDate>, today: LocalDate): Int {
        val days = dates.map { it.toEpochDay() }.toHashSet()
        var cursor = today.toEpochDay()
        if (cursor !in days) cursor -= 1
        var n = 0
        while (cursor in days) {
            n++
            cursor--
        }
        return n
    }

    fun personal(days: List<Day>, today: LocalDate): PersonalStats {
        val read = days.filter { it.minutes > 0 }
        val dates = read.map { it.date }
        val week = weekStart(today)
        val month = today.withDayOfMonth(1)
        val year = today.withDayOfYear(1)
        fun since(from: LocalDate) = read.filter { !it.date.isBefore(from) && !it.date.isAfter(today) }
        return PersonalStats(
            today = read.filter { it.date == today }.sumOf { it.minutes },
            currentStreak = currentStreak(dates, today),
            longestStreak = longestStreak(dates),
            minutesThisWeek = since(week).sumOf { it.minutes },
            minutesThisMonth = since(month).sumOf { it.minutes },
            daysThisMonth = since(month).size,
            minutesThisYear = since(year).sumOf { it.minutes },
            totalMinutes = read.sumOf { it.minutes },
        )
    }

    /** Minutes for each of the last [n] days, oldest first. */
    fun lastDays(days: List<Day>, today: LocalDate, n: Int): List<Pair<LocalDate, Int>> {
        val by = days.associate { it.date to it.minutes }
        return (n - 1 downTo 0).map { back -> today.minusDays(back.toLong()).let { it to (by[it] ?: 0) } }
    }

    /** Calendar heatmap from [from] to [to], padded to whole Saturday–Friday weeks. */
    fun heatmap(minutesByDay: Map<LocalDate, Int>, from: LocalDate, to: LocalDate): List<List<HeatCell>> {
        if (to.isBefore(from)) return emptyList()
        val weeks = mutableListOf<List<HeatCell>>()
        var start = weekStart(from)
        while (!start.isAfter(to)) {
            weeks += (0L until 7L).map { i ->
                val d = start.plusDays(i)
                HeatCell(d, minutesByDay[d] ?: 0, !d.isBefore(from) && !d.isAfter(to))
            }
            start = start.plusWeeks(1)
        }
        return weeks
    }

    /** Group minutes per day across active members. */
    fun groupDaily(data: GroupData): Map<LocalDate, Int> {
        val active = data.activeMembers.map { it.id }.toSet()
        return data.entries.filter { it.memberId in active }.groupBy { it.date }.mapValues { (_, v) -> v.sumOf { it.minutes } }
    }
}
