package com.readingmarathon.core

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/** One hall-of-fame title with everyone tied for it. */
data class Hero(val mark: Mark, val title: String, val names: List<String>, val value: String, val raw: Double)

object HallOfFame {

    /** Weeks run Saturday–Friday, as in the legacy charts. */
    fun weekStart(d: LocalDate): LocalDate = d.with(TemporalAdjusters.previousOrSame(DayOfWeek.SATURDAY))

    fun heroes(data: MarathonData, scope: Scope): List<Hero> {
        val members = data.activeMembers
        val entries = Scoring.entriesIn(data, scope).filter { e -> members.any { it.id == e.memberId } }
        val scores = members.associateWith { Scoring.memberScore(data, it, scope) }

        fun pick(
            mark: Mark,
            title: String,
            metric: (Member) -> Double,
            fmt: (Double) -> String,
        ): Hero {
            val values = members.associateWith(metric)
            val best = values.values.maxOrNull() ?: 0.0
            val names = if (best <= 0.0) emptyList() else values.filter { it.value == best }.keys.map { it.name }.sorted()
            return Hero(mark, title, names, if (names.isEmpty()) "—" else fmt(best), best)
        }

        val byMember = entries.groupBy { it.memberId }
        fun maxBucket(m: Member, key: (Entry) -> Any): Double =
            (byMember[m.id].orEmpty().groupBy(key).values.maxOfOrNull { list -> list.sumOf { it.totalMinutes } } ?: 0).toDouble()

        return listOf(
            pick(Mark.POINTS, "العقل المدبّر", { scores.getValue(it).totalPoints.toDouble() }) { "${it.toInt()} نقطة" },
            pick(Mark.HOURS, "سيد الساعات", { scores.getValue(it).totalMinutes.toDouble() }) { "${Fmt.hours(it)} ساعة" },
            pick(Mark.BOOKS, "الدودة القارئة", { scores.getValue(it).booksFinished.toDouble() }) { "${it.toInt()} كتب" },
            pick(Mark.QUOTES, "صائد الدرر", { scores.getValue(it).quotes.toDouble() }) { "${it.toInt()} اقتباساً" },
            pick(Mark.DAYS, "صاحب النَفَس الطويل", { scores.getValue(it).readingDays.toDouble() }) { "${it.toInt()} يوم قراءة" },
            pick(Mark.FAST, "العدّاء السريع", { maxBucket(it) { e -> e.date } }) { "${Fmt.hours(it)} ساعة في يوم" },
            pick(Mark.WEEK, "نجم الأسبوع", { maxBucket(it) { e -> weekStart(e.date) } }) { "${Fmt.hours(it)} ساعة في أسبوع" },
            pick(Mark.MONTH, "عملاق الشهر", { maxBucket(it) { e -> e.date.withDayOfMonth(1) } }) { "${Fmt.hours(it)} ساعة في شهر" },
        )
    }
}

object Fmt {
    /** Minutes → hours with one decimal, without a trailing ".0". */
    fun hours(minutes: Double): String {
        val h = minutes / 60.0
        val r = Math.round(h * 10) / 10.0
        return if (r == r.toLong().toDouble()) r.toLong().toString() else r.toString()
    }

    fun hours(minutes: Int): String = hours(minutes.toDouble())

    /** "1 س 25 د" style compact duration. */
    fun duration(minutes: Int): String {
        val h = minutes / 60
        val m = minutes % 60
        return when {
            h == 0 -> "$m د"
            m == 0 -> "$h س"
            else -> "$h س $m د"
        }
    }
}
