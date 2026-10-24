package com.readingmarathon.app.data

import android.app.Activity
import com.readingmarathon.core.BookBrief
import com.readingmarathon.core.Challenge
import com.readingmarathon.core.CsvImport
import com.readingmarathon.core.Day
import com.readingmarathon.core.GroupData
import com.readingmarathon.core.Member
import com.readingmarathon.core.Role
import com.readingmarathon.core.Rules
import com.readingmarathon.core.Visibility
import kotlinx.coroutines.flow.StateFlow
import java.time.LocalDate

/** The platform owner. Only this Google account sees the platform admin panel (enforced again in firestore.rules). */
const val ADMIN_EMAIL = "readingmarathon.admin@gmail.com"

/** Who is signed in. Guests have no email until they link a Google account. */
data class Account(
    val uid: String,
    val email: String?,
    val name: String?,
    val isGuest: Boolean,
) {
    val isAdmin: Boolean get() = !isGuest && email.equals(ADMIN_EMAIL, ignoreCase = true)
}

/** The reader as the app knows them. An empty name means they haven't chosen one yet. */
data class Profile(
    val name: String,
    /** Daily goal in minutes. */
    val goal: Int = 30,
    val groups: List<String> = emptyList(),
)

/** What anyone can see of a group before joining it. */
data class GroupPreview(
    val id: String,
    val name: String,
    val about: String,
    val visibility: Visibility,
    val memberCount: Int,
    val current: BookBrief?,
)

data class AdminGroup(
    val id: String,
    val name: String,
    val visibility: Visibility,
    val inviteCode: String,
    val createdAt: Long,
    val owners: List<String>,
    val accounts: Int,
    val readers: Int,
    val challenges: Int,
    val minutes: Long,
    val minutesLast7: Long,
    /** ISO date of the latest logged reading, if any. */
    val lastActivity: String?,
)

data class AdminUser(
    val uid: String,
    val email: String?,
    val name: String?,
    val isGuest: Boolean,
    val createdAt: Long,
    val lastSeen: Long,
    val groups: Int,
    val minutes: Long,
)

data class AdminOverview(
    val groups: List<AdminGroup>,
    val users: List<AdminUser>,
    val totalMinutes: Long,
    val minutesLast7: Long,
    val readingDays: Int,
    val finishedBooks: Int,
    /** Minutes per day for the last 30 days, oldest first. */
    val daily: List<Long>,
)

/**
 * Accounts, the reader's own log and their groups. The app talks only to this interface; the
 * Firebase implementation ships, tests use an in-memory one. Every write is checked first with
 * the same rules in `:core`, so a refusal comes back as a clear Arabic [com.readingmarathon.core.RuleViolation].
 */
interface Backend {
    /** False when the build has no real Firebase config (then nothing can be signed into). */
    val configured: Boolean
    val account: StateFlow<Account?>
    /** True until the saved sign-in (if any) has been restored. */
    val restoring: StateFlow<Boolean>

    suspend fun signInWithGoogle(activity: Activity)
    suspend fun signInAsGuest()
    suspend fun signOut()

    /** null while loading or signed out. */
    val profile: StateFlow<Profile?>
    /** The reader's own log (the last year or so). */
    val days: StateFlow<List<Day>>
    /** Loaded groups: the reader's own, plus any opened from the admin panel. */
    val groups: StateFlow<Map<String, GroupData>>

    suspend fun saveProfile(name: String, goal: Int)

    /** Sets the reader's minutes for a day, in their own log and in every group they are in. */
    suspend fun setDay(date: LocalDate, minutes: Int)

    /** Adds minutes to a day, reading the current value first (works before anything is loaded). */
    suspend fun addMinutes(date: LocalDate, minutes: Int): Int

    suspend fun createGroup(name: String, about: String, visibility: Visibility): String
    suspend fun findByCode(code: String): GroupPreview
    suspend fun publicGroups(): List<GroupPreview>
    suspend fun joinGroup(groupId: String, code: String?)
    suspend fun leaveGroup(groupId: String)

    /** Keeps a group that isn't one of the reader's loaded (platform admin). */
    fun watch(groupId: String)
    fun unwatch(groupId: String)

    // Group admins

    suspend fun updateGroup(groupId: String, name: String, about: String, visibility: Visibility, rules: Rules)
    suspend fun newInviteCode(groupId: String)
    suspend fun saveChallenge(groupId: String, challenge: Challenge)
    suspend fun deleteChallenge(groupId: String, challengeId: String)
    /** Marks (or with null, unmarks) that a member finished a challenge's book. */
    suspend fun setFinished(groupId: String, challengeId: String, memberId: String, date: LocalDate?)
    suspend fun setRole(groupId: String, uid: String, role: Role)
    suspend fun removeMember(groupId: String, memberId: String)
    /** A reader without the app, whose reading an admin logs. */
    suspend fun addReader(groupId: String, name: String): Member
    suspend fun renameReader(groupId: String, memberId: String, name: String)
    suspend fun setReaderDay(groupId: String, memberId: String, date: LocalDate, minutes: Int)
    /** Moves a reader-without-the-app's history onto a member's account, then removes the reader. */
    suspend fun mergeReader(groupId: String, fromId: String, intoId: String)
    suspend fun applyImport(groupId: String, plan: CsvImport.Plan)
    suspend fun deleteGroup(groupId: String)

    /** Platform admin only. */
    suspend fun adminOverview(): AdminOverview
}
