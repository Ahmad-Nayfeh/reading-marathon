package com.readingmarathon.core

import kotlinx.serialization.Serializable
import java.time.LocalDate

/**
 * Points system. A challenge keeps its own copy, so editing the marathon's default rules never
 * changes a challenge that already exists (same behaviour as the legacy app).
 */
@Serializable
data class Rules(
    val minutesPerPointCommon: Int = 10,
    val minutesPerPointOther: Int = 5,
    val quoteCommonPoints: Int = 3,
    val quoteOtherPoints: Int = 1,
    val finishCommonPoints: Int = 50,
    val finishOtherPoints: Int = 25,
    val attendDiscussionPoints: Int = 25,
) {
    fun isValid(): Boolean = listOf(
        minutesPerPointCommon, minutesPerPointOther, quoteCommonPoints, quoteOtherPoints,
        finishCommonPoints, finishOtherPoints, attendDiscussionPoints,
    ).all { it in 0..10_000 }

    companion object {
        val DEFAULT = Rules()
    }
}

@Serializable
data class Book(
    val title: String,
    val author: String = "",
    val year: Int? = null,
    val pages: Int? = null,
)

@Serializable
data class Member(
    val id: String,
    val name: String,
    val active: Boolean = true,
    /** Account (uid) linked to this member in a shared marathon, if any. */
    val linkedUid: String? = null,
)

/** A challenge (تحدي): one common book, an inclusive date range and a snapshot of the rules. */
@Serializable
data class Challenge(
    val id: String,
    val book: Book,
    @Serializable(with = LocalDateSerializer::class) val start: LocalDate,
    @Serializable(with = LocalDateSerializer::class) val end: LocalDate,
    val rules: Rules = Rules.DEFAULT,
    /** Days after [end] during which finishing the book and the discussion still count. */
    val graceDays: Int = 3,
) {
    fun contains(date: LocalDate): Boolean = !date.isBefore(start) && !date.isAfter(end)

    /** Inclusive length in days. */
    val lengthDays: Int get() = (end.toEpochDay() - start.toEpochDay() + 1).toInt()

    fun acceptsAchievementOn(date: LocalDate): Boolean =
        !date.isBefore(start) && !date.isAfter(end.plusDays(graceDays.toLong()))

    fun status(today: LocalDate): ChallengeStatus = when {
        today.isBefore(start) -> ChallengeStatus.UPCOMING
        today.isAfter(end) -> ChallengeStatus.FINISHED
        else -> ChallengeStatus.ACTIVE
    }
}

enum class ChallengeStatus { UPCOMING, ACTIVE, FINISHED }

/** One reading log per member per day. Logging again on the same day edits this entry. */
@Serializable
data class Entry(
    val memberId: String,
    @Serializable(with = LocalDateSerializer::class) val date: LocalDate,
    val commonMinutes: Int = 0,
    val otherMinutes: Int = 0,
    val quoteCommon: Boolean = false,
    val quoteOther: Boolean = false,
    /** Epoch millis of the last change, for ordering news and conflict resolution. */
    val updatedAt: Long = 0,
) {
    val id: String get() = entryId(memberId, date)
    val totalMinutes: Int get() = commonMinutes + otherMinutes
    val quotes: Int get() = (if (quoteCommon) 1 else 0) + (if (quoteOther) 1 else 0)
    /** A reading day is a day with at least one minute of reading. */
    val isReadingDay: Boolean get() = totalMinutes > 0

    companion object {
        fun entryId(memberId: String, date: LocalDate) = "${memberId}_$date"
    }
}

enum class AchievementType { FINISHED_COMMON_BOOK, ATTENDED_DISCUSSION, FINISHED_OTHER_BOOK }

@Serializable
data class Achievement(
    val id: String,
    val memberId: String,
    val challengeId: String,
    val type: AchievementType,
    @Serializable(with = LocalDateSerializer::class) val date: LocalDate,
    /** Required for FINISHED_OTHER_BOOK (so the same book cannot be counted twice). */
    val bookTitle: String? = null,
)

@Serializable
data class Marathon(
    val id: String,
    val name: String,
    val inviteCode: String = "",
    val defaultRules: Rules = Rules.DEFAULT,
    val createdAt: Long = 0,
)

/** Everything the screens need, loaded at once. Small groups make this cheap. */
@Serializable
data class MarathonData(
    val marathon: Marathon,
    val members: List<Member> = emptyList(),
    val challenges: List<Challenge> = emptyList(),
    val entries: List<Entry> = emptyList(),
    val achievements: List<Achievement> = emptyList(),
) {
    val activeMembers: List<Member> get() = members.filter { it.active }
    fun member(id: String): Member? = members.firstOrNull { it.id == id }
    fun challenge(id: String): Challenge? = challenges.firstOrNull { it.id == id }

    /** Challenges sorted by start date. */
    val timeline: List<Challenge> get() = challenges.sortedBy { it.start }

    fun challengeOn(date: LocalDate): Challenge? = timeline.firstOrNull { it.contains(date) }

    /** The challenge to show by default: active, else the next upcoming, else the latest finished. */
    fun currentChallenge(today: LocalDate): Challenge? =
        timeline.firstOrNull { it.status(today) == ChallengeStatus.ACTIVE }
            ?: timeline.firstOrNull { it.status(today) == ChallengeStatus.UPCOMING }
            ?: timeline.lastOrNull()
}

/** Which slice of history a computation covers. */
sealed interface Scope {
    data object AllTime : Scope
    data class OfChallenge(val challenge: Challenge) : Scope
}
