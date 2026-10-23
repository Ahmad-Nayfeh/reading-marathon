package com.readingmarathon.app.data

import android.app.Activity
import kotlinx.coroutines.flow.StateFlow

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
    val label: String get() = name?.takeIf { it.isNotBlank() } ?: email ?: "ضيف"
}

/** A marathon this account belongs to, for the "my marathons" list. */
data class MarathonSummary(val id: String, val name: String, val role: Role)

/** One marathon as the platform admin sees it. */
data class AdminMarathon(
    val id: String,
    val name: String,
    val inviteCode: String,
    val createdAt: Long,
    val organizers: List<String>,
    val accounts: Int,
    val members: Int,
    val activeMembers: Int,
    val challenges: Int,
    val readingDays: Int,
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
    val marathons: Int,
)

data class AdminOverview(
    val marathons: List<AdminMarathon>,
    val users: List<AdminUser>,
    val totalMembers: Int,
    val totalEntries: Int,
    val totalMinutes: Long,
    val minutesLast7: Long,
    val finishedBooks: Int,
    /** Minutes per day for the last 30 days, oldest first. */
    val daily: List<Long>,
)

/**
 * Accounts and everything above a single marathon. The app talks only to this interface; the
 * Firebase implementation ships, tests use an in-memory one.
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

    suspend fun myMarathons(): List<MarathonSummary>
    /** Creates a marathon with the caller as organiser and first member. Returns (marathonId, memberId). */
    suspend fun createMarathon(name: String, organizerName: String): Pair<String, String>
    /** Joins with an invite code. Returns the marathon id. */
    suspend fun joinMarathon(code: String): String
    suspend fun roleIn(marathonId: String): Role?
    fun openStore(marathonId: String): MarathonStore

    /** Platform admin only. */
    suspend fun adminOverview(): AdminOverview
}
