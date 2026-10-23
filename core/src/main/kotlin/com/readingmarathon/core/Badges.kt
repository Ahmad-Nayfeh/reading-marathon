package com.readingmarathon.core

import java.time.LocalDate

enum class BadgeKind(val emoji: String, val title: String, val rule: String) {
    PHILOSOPHER("✍️", "وسام الفيلسوف", "أكثر من 10 اقتباسات"),
    RUNNER("🏃", "وسام العدّاء", "إنهاء الكتاب المشترك خلال أول 7 أيام من التحدي"),
    PERSISTENCE("💯", "وسام المثابرة", "7 أيام قراءة متتالية أو أكثر"),
    DEVOURER("📚", "وسام الملتهم", "إنهاء 3 كتب أو أكثر في تحدٍ واحد"),
    DEBATER("🗣️", "وسام المحاور", "حضور جلسة النقاش في 3 تحديات"),
}

data class Badge(val kind: BadgeKind, val detail: String, val earnedOn: LocalDate?)

object Badges {

    /** Longest run of consecutive reading days (days with minutes > 0). */
    fun longestStreak(dates: Collection<LocalDate>): Int {
        val days = dates.map { it.toEpochDay() }.toSortedSet()
        var best = 0
        var run = 0
        var prev: Long? = null
        for (d in days) {
            run = if (prev != null && d == prev + 1) run + 1 else 1
            best = maxOf(best, run)
            prev = d
        }
        return best
    }

    /** Current streak ending today or yesterday (so it doesn't break before today's log). */
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

    fun forMember(data: MarathonData, member: Member, scope: Scope): List<Badge> {
        val entries = Scoring.entriesIn(data, scope).filter { it.memberId == member.id }
        val achievements = Scoring.achievementsIn(data, scope).filter { it.memberId == member.id }
        val out = mutableListOf<Badge>()

        val quotes = entries.sumOf { it.quotes }
        if (quotes > 10) {
            out += Badge(BadgeKind.PHILOSOPHER, "$quotes اقتباساً", null)
        }

        val runnerFinish = achievements
            .filter { it.type == AchievementType.FINISHED_COMMON_BOOK }
            .mapNotNull { a -> data.challenge(a.challengeId)?.let { c -> a to c } }
            .filter { (a, c) -> a.date.toEpochDay() - c.start.toEpochDay() in 0..6 }
            .minByOrNull { it.first.date }
        if (runnerFinish != null) {
            val day = runnerFinish.first.date.toEpochDay() - runnerFinish.second.start.toEpochDay() + 1
            out += Badge(BadgeKind.RUNNER, "أنهى «${runnerFinish.second.book.title}» في اليوم $day", runnerFinish.first.date)
        }

        val streak = longestStreak(entries.filter { it.isReadingDay }.map { it.date })
        if (streak >= 7) {
            out += Badge(BadgeKind.PERSISTENCE, "$streak أيام متتالية", null)
        }

        val booksPerChallenge = achievements
            .filter { it.type != AchievementType.ATTENDED_DISCUSSION }
            .groupingBy { it.challengeId }.eachCount()
        val bestBooks = booksPerChallenge.maxByOrNull { it.value }
        if (bestBooks != null && bestBooks.value >= 3) {
            val title = data.challenge(bestBooks.key)?.book?.title.orEmpty()
            out += Badge(BadgeKind.DEVOURER, "${bestBooks.value} كتب في تحدي «$title»", null)
        }

        val discussions = achievements.count { it.type == AchievementType.ATTENDED_DISCUSSION }
        if (discussions >= 3) {
            out += Badge(BadgeKind.DEBATER, "$discussions جلسات نقاش", null)
        }
        return out
    }
}
