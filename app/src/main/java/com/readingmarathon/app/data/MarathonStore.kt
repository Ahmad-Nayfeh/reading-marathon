package com.readingmarathon.app.data

import com.readingmarathon.core.Achievement
import com.readingmarathon.core.Challenge
import com.readingmarathon.core.CsvImport
import com.readingmarathon.core.Entry
import com.readingmarathon.core.MarathonData
import com.readingmarathon.core.Member
import com.readingmarathon.core.Rules
import kotlinx.coroutines.flow.StateFlow

enum class Role { ORGANIZER, MEMBER }

/** Which marathon is open on this device, and as whom. */
data class Session(
    val marathonId: String,
    val role: Role,
    /** The member this account reads as (null for an organiser who doesn't read in the marathon). */
    val myMemberId: String?,
    /** Opened from the platform admin panel rather than as a member. */
    val asAdmin: Boolean = false,
) {
    val isOrganizer: Boolean get() = role == Role.ORGANIZER || asAdmin
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

    /** Binds the signed-in account to a member. */
    suspend fun linkMember(memberId: String)

    fun close() {}
}
