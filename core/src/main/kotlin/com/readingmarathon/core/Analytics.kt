package com.readingmarathon.core

import java.time.DayOfWeek
import java.time.LocalDate

data class Kpi(val label: String, val value: String)

/** A value per calendar day, continuous (missing days are 0). */
data class DailyPoint(val date: LocalDate, val minutes: Int)

/** Calendar heatmap cell: one per day, laid out in Saturday-first weeks. */
data class HeatCell(val date: LocalDate, val minutes: Int, val inRange: Boolean)

data class ChallengeProgress(
    val daysPassed: Int,
    val totalDays: Int,
    val daysLeft: Int,
    val fraction: Float,
)

object Analytics {

    /** Saturday-first order used across the app (Saudi week). */
    val WEEK: List<DayOfWeek> = listOf(
        DayOfWeek.SATURDAY, DayOfWeek.SUNDAY, DayOfWeek.MONDAY, DayOfWeek.TUESDAY,
        DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY,
    )

    val WEEKDAY_AR: Map<DayOfWeek, String> = mapOf(
        DayOfWeek.SATURDAY to "السبت", DayOfWeek.SUNDAY to "الأحد", DayOfWeek.MONDAY to "الاثنين",
        DayOfWeek.TUESDAY to "الثلاثاء", DayOfWeek.WEDNESDAY to "الأربعاء", DayOfWeek.THURSDAY to "الخميس",
        DayOfWeek.FRIDAY to "الجمعة",
    )

    fun progress(c: Challenge, today: LocalDate): ChallengeProgress {
        val total = c.lengthDays
        val passed = when {
            today.isBefore(c.start) -> 0
            today.isAfter(c.end) -> total
            else -> (today.toEpochDay() - c.start.toEpochDay() + 1).toInt()
        }
        return ChallengeProgress(passed, total, total - passed, if (total == 0) 0f else passed.toFloat() / total)
    }

    /** Continuous daily series from [from] to [to] inclusive. */
    fun daily(entries: List<Entry>, from: LocalDate, to: LocalDate): List<DailyPoint> {
        if (to.isBefore(from)) return emptyList()
        val byDay = entries.groupBy { it.date }.mapValues { (_, v) -> v.sumOf { it.totalMinutes } }
        val out = ArrayList<DailyPoint>()
        var d = from
        while (!d.isAfter(to)) {
            out += DailyPoint(d, byDay[d] ?: 0)
            d = d.plusDays(1)
        }
        return out
    }

    fun cumulativeHours(series: List<DailyPoint>): List<Pair<LocalDate, Double>> {
        var acc = 0
        return series.map { acc += it.minutes; it.date to acc / 60.0 }
    }

    /** Share of minutes per weekday (Saturday first), in percent. */
    fun weekdayShare(entries: List<Entry>): List<Pair<String, Double>> {
        val total = entries.sumOf { it.totalMinutes }.toDouble()
        val by = entries.groupBy { it.date.dayOfWeek }.mapValues { (_, v) -> v.sumOf { it.totalMinutes } }
        return WEEK.map { d -> WEEKDAY_AR.getValue(d) to if (total == 0.0) 0.0 else (by[d] ?: 0) * 100.0 / total }
    }

    /** Calendar heatmap from [from] to [to], padded to whole Saturday–Friday weeks. Correct across years. */
    fun heatmap(entries: List<Entry>, from: LocalDate, to: LocalDate): List<List<HeatCell>> {
        if (to.isBefore(from)) return emptyList()
        val byDay = entries.groupBy { it.date }.mapValues { (_, v) -> v.sumOf { it.totalMinutes } }
        val first = HallOfFame.weekStart(from)
        val weeks = mutableListOf<List<HeatCell>>()
        var weekStart = first
        while (!weekStart.isAfter(to)) {
            weeks += (0L until 7L).map { i ->
                val d = weekStart.plusDays(i)
                HeatCell(d, byDay[d] ?: 0, !d.isBefore(from) && !d.isAfter(to))
            }
            weekStart = weekStart.plusWeeks(1)
        }
        return weeks
    }

    fun marathonKpis(data: MarathonData, today: LocalDate): List<Kpi> {
        val ids = data.activeMembers.map { it.id }.toSet()
        val entries = data.entries.filter { it.memberId in ids }
        val books = Scoring.achievementsIn(data, Scope.AllTime)
            .count { it.memberId in ids && it.type != AchievementType.ATTENDED_DISCUSSION }
        return listOf(
            Kpi("ساعات القراءة", Fmt.hours(entries.sumOf { it.totalMinutes })),
            Kpi("الكتب المنهَاة", books.toString()),
            Kpi("الاقتباسات", entries.sumOf { it.quotes }.toString()),
            Kpi("الأعضاء النشطون", ids.size.toString()),
            Kpi("أيام القراءة", entries.filter { it.isReadingDay }.map { it.date }.distinct().size.toString()),
            Kpi("التحديات المكتملة", data.challenges.count { it.status(today) == ChallengeStatus.FINISHED }.toString()),
        )
    }

    fun challengeKpis(data: MarathonData, c: Challenge, today: LocalDate): List<Kpi> {
        val ids = data.activeMembers.map { it.id }.toSet()
        val entries = Scoring.entriesIn(data, Scope.OfChallenge(c)).filter { it.memberId in ids }
        val participants = entries.filter { it.isReadingDay }.map { it.memberId }.distinct().size
        val p = progress(c, today)
        // Average per participant per elapsed day; the elapsed days stop at the end date (legacy bug fixed).
        val avg = if (participants == 0 || p.daysPassed == 0) 0.0 else entries.sumOf { it.totalMinutes }.toDouble() / p.daysPassed / participants
        return listOf(
            Kpi("ساعات القراءة", Fmt.hours(entries.sumOf { it.totalMinutes })),
            Kpi("المشاركون", participants.toString()),
            Kpi("الاقتباسات", entries.sumOf { it.quotes }.toString()),
            Kpi("متوسط القارئ يومياً", "${Math.round(avg)} د"),
        )
    }

    /** Minutes split between the common book and other books. */
    fun focus(entries: List<Entry>): Pair<Int, Int> = entries.sumOf { it.commonMinutes } to entries.sumOf { it.otherMinutes }
}
