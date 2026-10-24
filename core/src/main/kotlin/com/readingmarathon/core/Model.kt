package com.readingmarathon.core

import java.time.LocalDate

/**
 * Reading is personal: a reader logs minutes for a day once, and the same minutes count in every
 * group they belong to. A group adds a shared book (a challenge), a ranking and an activity feed.
 */

/** One day of the reader's own log. */
data class Day(val date: LocalDate, val minutes: Int, val updatedAt: Long = 0)

/** How a group turns reading into points. */
data class Rules(val minutesPerPoint: Int = 10, val finishBonus: Int = 50) {
    fun isValid(): Boolean = minutesPerPoint in 1..600 && finishBonus in 0..10_000

    companion object {
        val DEFAULT = Rules()
    }
}

enum class Role { OWNER, ADMIN, MEMBER;
    val canManage: Boolean get() = this != MEMBER
}

enum class Visibility { PUBLIC, PRIVATE }

data class Book(val title: String, val author: String = "")

/** A shared book with a reading window (inclusive). */
data class Challenge(
    val id: String,
    val book: Book,
    val start: LocalDate,
    val end: LocalDate,
) {
    fun contains(date: LocalDate): Boolean = !date.isBefore(start) && !date.isAfter(end)
    val lengthDays: Int get() = (end.toEpochDay() - start.toEpochDay() + 1).toInt()

    /** Finishing the book still counts a few days after the end. */
    fun acceptsFinishOn(date: LocalDate): Boolean = !date.isBefore(start) && !date.isAfter(end.plusDays(Limits.FINISH_GRACE_DAYS))

    fun status(today: LocalDate): ChallengeStatus = when {
        today.isBefore(start) -> ChallengeStatus.UPCOMING
        today.isAfter(end) -> ChallengeStatus.FINISHED
        else -> ChallengeStatus.ACTIVE
    }

    /** Day number within the challenge (1-based), clamped to its length. */
    fun dayNumber(today: LocalDate): Int =
        (today.toEpochDay() - start.toEpochDay() + 1).toInt().coerceIn(0, lengthDays)
}

enum class ChallengeStatus { UPCOMING, ACTIVE, FINISHED }

/** The current book as shown on a group card, kept on the group itself so lists need one read. */
data class BookBrief(val title: String, val author: String, val start: LocalDate, val end: LocalDate)

data class Group(
    val id: String,
    val name: String,
    val about: String = "",
    val visibility: Visibility = Visibility.PRIVATE,
    val inviteCode: String = "",
    /** uid → role, for every account in the group. */
    val roles: Map<String, Role> = emptyMap(),
    val rules: Rules = Rules.DEFAULT,
    val createdAt: Long = 0,
    val current: BookBrief? = null,
) {
    fun roleOf(uid: String?): Role? = uid?.let { roles[it] }
    val memberCount: Int get() = roles.size
}

/**
 * A reader inside a group. For an account the id is its uid. A reader without the app (added by an
 * admin, or imported from the old Google Form) has a generated id and no linked account.
 */
data class Member(
    val id: String,
    val name: String,
    val linkedUid: String? = null,
    val active: Boolean = true,
) {
    val hasApp: Boolean get() = linkedUid != null
}

/** A member's minutes on one day, inside one group. */
data class Entry(val memberId: String, val date: LocalDate, val minutes: Int, val updatedAt: Long = 0) {
    val id: String get() = entryId(memberId, date)

    companion object {
        fun entryId(memberId: String, date: LocalDate) = "${memberId}_$date"
    }
}

/** A member finished a challenge's book. */
data class Finish(val memberId: String, val challengeId: String, val date: LocalDate) {
    val id: String get() = finishId(memberId, challengeId)

    companion object {
        fun finishId(memberId: String, challengeId: String) = "${memberId}_$challengeId"
    }
}

/** Everything inside one group. Groups are small, so it is loaded whole. */
data class GroupData(
    val group: Group,
    val members: List<Member> = emptyList(),
    val challenges: List<Challenge> = emptyList(),
    val entries: List<Entry> = emptyList(),
    val finishes: List<Finish> = emptyList(),
) {
    val activeMembers: List<Member> get() = members.filter { it.active }
    fun member(id: String): Member? = members.firstOrNull { it.id == id }
    fun challenge(id: String): Challenge? = challenges.firstOrNull { it.id == id }
    val timeline: List<Challenge> get() = challenges.sortedBy { it.start }

    /** The challenge to show: running now, else the next one, else the latest finished. */
    fun currentChallenge(today: LocalDate): Challenge? =
        timeline.firstOrNull { it.status(today) == ChallengeStatus.ACTIVE }
            ?: timeline.firstOrNull { it.status(today) == ChallengeStatus.UPCOMING }
            ?: timeline.lastOrNull()

    fun finished(memberId: String, challengeId: String): Finish? =
        finishes.firstOrNull { it.memberId == memberId && it.challengeId == challengeId }
}

/** Which slice of a group's history a ranking covers. */
sealed interface Scope {
    data object AllTime : Scope
    data class OfChallenge(val challenge: Challenge) : Scope
}

object Limits {
    const val MAX_MINUTES_PER_DAY = 16 * 60
    /** How far back a reader may log or edit; group admins can import older history. */
    const val BACKDATE_DAYS = 14L
    const val FINISH_GRACE_DAYS = 7L
    const val MAX_NAME = 40
    const val MAX_TITLE = 120
    const val MAX_ABOUT = 300
    const val MAX_GROUPS = 20
    /** How much history the app loads (a year, plus a margin for a challenge that spans the edge). */
    const val HISTORY_DAYS = 400L
}
