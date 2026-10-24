package com.readingmarathon.core

import java.time.LocalDate

/** A member's totals within a scope. */
data class MemberScore(
    val member: Member,
    /** Minutes that count for points: all reading, or the shared book only (group setting). */
    val minutes: Int,
    val readingDays: Int,
    val finishes: Int,
    val points: Int,
    /** Minutes on the shared book, shown next to the total. */
    val sharedMinutes: Int = 0,
    val allMinutes: Int = minutes,
) {
    val hours: Double get() = minutes / 60.0
}

/** A ranked row. Ties share a rank (1, 2, 2, 4 …). */
data class Standing(val rank: Int, val score: MemberScore)

/**
 * Points = whole points for the minutes in the scope (summed first, so two 15-minute days make
 * 3 points, not 2) + a bonus for each finished book.
 */
object Scoring {

    fun entriesIn(data: GroupData, scope: Scope): List<Entry> = when (scope) {
        Scope.AllTime -> data.entries
        is Scope.OfChallenge -> data.entries.filter { scope.challenge.contains(it.date) }
    }

    /** Finishes whose challenge still exists. */
    fun finishesIn(data: GroupData, scope: Scope): List<Finish> {
        val ids = data.challenges.map { it.id }.toSet()
        return data.finishes.filter {
            it.challengeId in ids && (scope !is Scope.OfChallenge || it.challengeId == scope.challenge.id)
        }
    }

    fun memberScore(data: GroupData, member: Member, scope: Scope): MemberScore {
        val entries = entriesIn(data, scope).filter { it.memberId == member.id }
        val rules = data.group.rules
        val all = entries.sumOf { it.minutes }
        val shared = entries.sumOf { it.sharedMinutes.coerceAtMost(it.minutes) }
        val minutes = if (rules.countAll) all else shared
        val finishes = finishesIn(data, scope).count { it.memberId == member.id }
        return MemberScore(
            member = member,
            minutes = minutes,
            readingDays = entries.count { (if (rules.countAll) it.minutes else it.sharedMinutes) > 0 },
            finishes = finishes,
            points = minutes / rules.minutesPerPoint.coerceAtLeast(1) + finishes * rules.finishBonus,
            sharedMinutes = shared,
            allMinutes = all,
        )
    }

    /** Every active member, best first. Members with nothing yet sit at the bottom, sorted by name. */
    fun standings(data: GroupData, scope: Scope): List<Standing> {
        val scores = data.activeMembers.map { memberScore(data, it, scope) }
            .sortedWith(
                compareByDescending<MemberScore> { it.points }
                    .thenByDescending { it.minutes }
                    .thenBy { Text.normalize(it.member.name) },
            )
        val out = ArrayList<Standing>(scores.size)
        scores.forEachIndexed { i, s ->
            val prev = scores.getOrNull(i - 1)
            val rank = if (prev != null && prev.points == s.points && prev.minutes == s.minutes) out[i - 1].rank else i + 1
            out += Standing(rank, s)
        }
        return out
    }

    /** The member's place, or null if they are not in the ranking. */
    fun rankOf(data: GroupData, memberId: String, scope: Scope): Standing? =
        standings(data, scope).firstOrNull { it.score.member.id == memberId }

    /** How many members logged reading on [date]. */
    fun readersOn(data: GroupData, date: LocalDate): Int {
        val active = data.activeMembers.map { it.id }.toSet()
        return data.entries.count { it.date == date && it.minutes > 0 && it.memberId in active }
    }
}

/** One line of a group's activity feed. */
sealed interface Activity {
    val at: Long
    val memberId: String

    data class Read(override val memberId: String, val date: LocalDate, val minutes: Int, override val at: Long) : Activity
    data class Finished(override val memberId: String, val challenge: Challenge, val date: LocalDate, override val at: Long) : Activity
}

object Feed {
    /** Latest reading and finished books, newest first. */
    fun of(data: GroupData, limit: Int = 40): List<Activity> {
        val active = data.activeMembers.map { it.id }.toSet()
        val reads = data.entries.filter { it.minutes > 0 && it.memberId in active }.map {
            Activity.Read(it.memberId, it.date, it.minutes, if (it.updatedAt > 0) it.updatedAt else it.date.toEpochDay() * 86_400_000)
        }
        val finishes = data.finishes.filter { it.memberId in active }.mapNotNull { f ->
            data.challenge(f.challengeId)?.let { Activity.Finished(f.memberId, it, f.date, f.date.toEpochDay() * 86_400_000 + 86_399_000) }
        }
        return (reads + finishes).sortedByDescending { it.at }.take(limit)
    }
}
