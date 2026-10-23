package com.readingmarathon.core

import java.time.LocalDate

data class NewsItem(val mark: Mark, val text: String, val date: LocalDate?)

/** Short Arabic news lines, generated from the data (the legacy "آخر الأخبار" ticker, with real points). */
object News {

    fun forChallenge(data: MarathonData, c: Challenge, today: LocalDate): List<NewsItem> {
        val out = mutableListOf<NewsItem>()
        val active = data.activeMembers.associateBy { it.id }
        if (today.isBefore(c.start)) {
            out += NewsItem(Mark.UPCOMING, "الاستعدادات جارية: ينطلق تحدي «${c.book.title}» في ${Dates.long(c.start)}.", c.start)
            return out
        }
        val finishers = data.achievements
            .filter { it.challengeId == c.id && it.type == AchievementType.FINISHED_COMMON_BOOK && it.memberId in active }
            .sortedBy { it.date }
        if (finishers.isEmpty()) {
            out += NewsItem(Mark.RACE, "السباق محتدم: لم يُنهِ أحد «${c.book.title}» بعد. من سيكون أول المنجزين؟", null)
        } else {
            val first = finishers.first()
            out += NewsItem(Mark.FIRST, "${active.getValue(first.memberId).name} أول من أنهى «${c.book.title}».", first.date)
            val lastDay = finishers.last().date
            val sameDay = finishers.filter { it.date == lastDay }.map { active.getValue(it.memberId).name }
            if (finishers.size > 1) {
                out += if (sameDay.size > 1) {
                    NewsItem(Mark.GROUP, "إنجاز جماعي: ${Names.join(sameDay)} أنهوا الكتاب في اليوم نفسه.", lastDay)
                } else {
                    NewsItem(Mark.JOINED, "${sameDay.first()} ينضم إلى قائمة المنجزين.", lastDay)
                }
            }
            out += NewsItem(
                Mark.PROGRESS,
                if (finishers.size == 1) "منجز واحد وصل إلى خط النهاية حتى الآن." else "${finishers.size} منجزين أتموا الكتاب حتى الآن.",
                null,
            )
        }
        val standings = Scoring.standings(data, Scope.OfChallenge(c))
        val leaders = standings.filter { it.rank == 1 && it.score.totalPoints > 0 }
        if (leaders.isNotEmpty()) {
            val names = Names.join(leaders.map { it.score.member.name })
            out += NewsItem(Mark.LEADER, "$names في الصدارة بـ ${leaders.first().score.totalPoints} نقطة.", null)
        }
        if (today.isAfter(c.end)) {
            val attendees = data.achievements
                .filter { it.challengeId == c.id && it.type == AchievementType.ATTENDED_DISCUSSION && it.memberId in active }
                .map { active.getValue(it.memberId).name }
            out += if (attendees.isNotEmpty()) {
                NewsItem(Mark.DISCUSSION, "جلسة نقاش مثمرة بحضور ${Names.join(attendees)}.", c.end)
            } else {
                NewsItem(Mark.INFO, "لم يُسجَّل حضور لجلسة النقاش الختامية بعد.", c.end)
            }
        }
        return out
    }

    /** Who took a hall-of-fame title in the last 7 days. */
    fun weekly(data: MarathonData, today: LocalDate): List<NewsItem> {
        val weekAgo = today.minusDays(7)
        if (data.entries.none { it.date.isBefore(weekAgo) }) return emptyList()
        val past = data.copy(
            entries = data.entries.filter { !it.date.isAfter(weekAgo) },
            achievements = data.achievements.filter { !it.date.isAfter(weekAgo) },
        )
        val now = HallOfFame.heroes(data, Scope.AllTime)
        val before = HallOfFame.heroes(past, Scope.AllTime).associateBy { it.title }
        return now.mapNotNull { h ->
            val prev = before[h.title]?.names.orEmpty().toSet()
            val newcomers = h.names.filter { it !in prev }
            when {
                newcomers.isEmpty() -> null
                prev.isEmpty() -> NewsItem(h.mark, "${Names.join(newcomers)} أول من ينال لقب «${h.title}».", today)
                h.names.any { it in prev } -> NewsItem(h.mark, "${Names.join(newcomers)} ينضم إلى الصدارة في «${h.title}».", today)
                else -> NewsItem(h.mark, "${Names.join(newcomers)} يتصدر «${h.title}» هذا الأسبوع.", today)
            }
        }
    }
}

object Names {
    fun join(names: List<String>): String = when (names.size) {
        0 -> ""
        1 -> names[0]
        else -> names.dropLast(1).joinToString("، ") + " و" + names.last()
    }
}

object Dates {
    private val MONTHS = listOf(
        "يناير", "فبراير", "مارس", "أبريل", "مايو", "يونيو",
        "يوليو", "أغسطس", "سبتمبر", "أكتوبر", "نوفمبر", "ديسمبر",
    )

    fun long(d: LocalDate): String = "${d.dayOfMonth} ${MONTHS[d.monthValue - 1]} ${d.year}"
    fun short(d: LocalDate): String = "${d.dayOfMonth} ${MONTHS[d.monthValue - 1]}"
    fun month(d: LocalDate): String = MONTHS[d.monthValue - 1]
    fun weekday(d: LocalDate): String = Analytics.WEEKDAY_AR.getValue(d.dayOfWeek)

    /** "اليوم", "أمس" or the weekday + date. */
    fun relative(d: LocalDate, today: LocalDate): String = when (today.toEpochDay() - d.toEpochDay()) {
        0L -> "اليوم"
        1L -> "أمس"
        else -> "${weekday(d)} ${short(d)}"
    }
}
