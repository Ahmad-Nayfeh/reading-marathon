package com.readingmarathon.app.data

import com.readingmarathon.core.Achievement
import com.readingmarathon.core.Challenge
import com.readingmarathon.core.CsvImport
import com.readingmarathon.core.Entry
import com.readingmarathon.core.MarathonData
import com.readingmarathon.core.Member
import com.readingmarathon.core.Rules
import kotlinx.coroutines.flow.StateFlow

enum class Mode { LOCAL, SHARED, DEMO }

enum class Role { ORGANIZER, MEMBER }

/** Who is using the app and against which marathon. */
data class Session(
    val mode: Mode,
    val marathonId: String,
    val role: Role,
    /** The member this device reads as (null for an organiser who doesn't read in the marathon). */
    val myMemberId: String?,
) {
    val isOrganizer: Boolean get() = role == Role.ORGANIZER
}

/**
 * The one data interface the UI talks to. Every write is validated by the same pure functions in
 * `:core` (Ops/Validation) before it is applied, whatever the backend. Failures throw
 * [com.readingmarathon.core.RuleViolation] with an Arabic message.
 */
interface MarathonStore {
    val data: StateFlow<MarathonData?>

    suspend fun renameMarathon(name: String)
    suspend fun setDefaultRules(rules: Rules)

    suspend fun addMember(name: String): Member
    suspend fun renameMember(id: String, name: String)
    suspend fun setMemberActive(id: String, active: Boolean)

    suspend fun upsertChallenge(challenge: Challenge)
    suspend fun deleteChallenge(id: String)

    suspend fun upsertEntry(entry: Entry, asOrganizer: Boolean)
    suspend fun deleteEntry(entryId: String)

    suspend fun addAchievement(achievement: Achievement)
    suspend fun deleteAchievement(id: String)

    suspend fun applyImport(plan: CsvImport.Plan)

    /** Shared mode: bind this device's account to a member. No-op locally. */
    suspend fun linkMember(memberId: String) {}

    fun close() {}
}
