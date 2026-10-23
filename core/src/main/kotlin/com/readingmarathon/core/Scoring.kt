package com.readingmarathon.core

/** Where a member's points came from (the 7 buckets of the legacy reader card). */
data class PointsBreakdown(
    val readingCommon: Int = 0,
    val readingOther: Int = 0,
    val quotesCommon: Int = 0,
    val quotesOther: Int = 0,
    val finishCommon: Int = 0,
    val finishOther: Int = 0,
    val discussion: Int = 0,
) {
    val total: Int
        get() = readingCommon + readingOther + quotesCommon + quotesOther + finishCommon + finishOther + discussion

    operator fun plus(o: PointsBreakdown) = PointsBreakdown(
        readingCommon + o.readingCommon, readingOther + o.readingOther,
        quotesCommon + o.quotesCommon, quotesOther + o.quotesOther,
        finishCommon + o.finishCommon, finishOther + o.finishOther, discussion + o.discussion,
    )

    /** Non-zero buckets with their Arabic labels, in a stable order. */
    fun labelled(): List<Pair<String, Int>> = listOf(
        "قراءة الكتاب المشترك" to readingCommon,
        "قراءة كتب أخرى" to readingOther,
        "اقتباسات (الكتاب المشترك)" to quotesCommon,
        "اقتباسات (كتب أخرى)" to quotesOther,
        "إنهاء الكتاب المشترك" to finishCommon,
        "إنهاء كتب أخرى" to finishOther,
        "حضور النقاش" to discussion,
    ).filter { it.second > 0 }
}

/** A member's totals within a scope. */
data class MemberScore(
    val member: Member,
    val points: PointsBreakdown,
    val commonMinutes: Int,
    val otherMinutes: Int,
    val quotes: Int,
    val readingDays: Int,
    val booksFinished: Int,
    val discussions: Int,
) {
    val totalPoints: Int get() = points.total
    val totalMinutes: Int get() = commonMinutes + otherMinutes
    val hours: Double get() = totalMinutes / 60.0
}

/** A ranked row. Ties share the same rank (1, 2, 2, 4 …). */
data class Standing(val rank: Int, val score: MemberScore)

/**
 * The single scoring engine. Rounding is applied per daily entry, everywhere — the legacy app
 * rounded per log in one place and per challenge sum in another, so the same member could show
 * two different totals for the same challenge.
 */
object Scoring {

    fun entryPoints(entry: Entry, rules: Rules): PointsBreakdown = PointsBreakdown(
        readingCommon = if (rules.minutesPerPointCommon > 0) entry.commonMinutes / rules.minutesPerPointCommon else 0,
        readingOther = if (rules.minutesPerPointOther > 0) entry.otherMinutes / rules.minutesPerPointOther else 0,
        quotesCommon = if (entry.quoteCommon) rules.quoteCommonPoints else 0,
        quotesOther = if (entry.quoteOther) rules.quoteOtherPoints else 0,
    )

    fun achievementPoints(a: Achievement, rules: Rules): PointsBreakdown = when (a.type) {
        AchievementType.FINISHED_COMMON_BOOK -> PointsBreakdown(finishCommon = rules.finishCommonPoints)
        AchievementType.FINISHED_OTHER_BOOK -> PointsBreakdown(finishOther = rules.finishOtherPoints)
        AchievementType.ATTENDED_DISCUSSION -> PointsBreakdown(discussion = rules.attendDiscussionPoints)
    }

    /** Entries inside the scope. All-time includes entries outside every challenge (0 points, but hours count). */
    fun entriesIn(data: MarathonData, scope: Scope): List<Entry> = when (scope) {
        Scope.AllTime -> data.entries
        is Scope.OfChallenge -> data.entries.filter { scope.challenge.contains(it.date) }
    }

    /** Achievements that count in the scope: their challenge must still exist. */
    fun achievementsIn(data: MarathonData, scope: Scope): List<Achievement> {
        val ids = data.challenges.map { it.id }.toSet()
        val valid = data.achievements.filter { it.challengeId in ids }
        return when (scope) {
            Scope.AllTime -> valid
            is Scope.OfChallenge -> valid.filter { it.challengeId == scope.challenge.id }
        }
    }

    fun memberScore(data: MarathonData, member: Member, scope: Scope): MemberScore {
        val entries = entriesIn(data, scope).filter { it.memberId == member.id }
        val achievements = achievementsIn(data, scope).filter { it.memberId == member.id }
        var points = PointsBreakdown()
        for (e in entries) {
            val challenge = when (scope) {
                is Scope.OfChallenge -> scope.challenge
                Scope.AllTime -> data.challengeOn(e.date)
            } ?: continue
            points += entryPoints(e, challenge.rules)
        }
        for (a in achievements) {
            val challenge = data.challenge(a.challengeId) ?: continue
            points += achievementPoints(a, challenge.rules)
        }
        return MemberScore(
            member = member,
            points = points,
            commonMinutes = entries.sumOf { it.commonMinutes },
            otherMinutes = entries.sumOf { it.otherMinutes },
            quotes = entries.sumOf { it.quotes },
            readingDays = entries.count { it.isReadingDay },
            booksFinished = achievements.count {
                it.type == AchievementType.FINISHED_COMMON_BOOK || it.type == AchievementType.FINISHED_OTHER_BOOK
            },
            discussions = achievements.count { it.type == AchievementType.ATTENDED_DISCUSSION },
        )
    }

    /**
     * Ranked standings. Archived members are excluded. For a challenge, only members with any
     * activity in it are ranked (an achievement alone counts, unlike the legacy podium).
     */
    fun standings(data: MarathonData, scope: Scope): List<Standing> {
        val scores = data.activeMembers.map { memberScore(data, it, scope) }
            .filter { it.totalMinutes > 0 || it.totalPoints > 0 || scope == Scope.AllTime }
            .sortedWith(compareByDescending<MemberScore> { it.totalPoints }.thenByDescending { it.totalMinutes }.thenBy { it.member.name })
        val out = ArrayList<Standing>(scores.size)
        scores.forEachIndexed { i, s ->
            val rank = if (i > 0 && scores[i - 1].totalPoints == s.totalPoints) out[i - 1].rank else i + 1
            out += Standing(rank, s)
        }
        return out
    }
}
