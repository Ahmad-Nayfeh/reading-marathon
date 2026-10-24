package com.readingmarathon.app.data

import android.app.Activity
import com.readingmarathon.core.BookBrief
import com.readingmarathon.core.CatalogBook
import com.readingmarathon.core.Challenge
import com.readingmarathon.core.Day
import com.readingmarathon.core.GroupData
import com.readingmarathon.core.Read
import com.readingmarathon.core.ShelfBook
import com.readingmarathon.core.ShelfStatus
import com.readingmarathon.core.Role
import com.readingmarathon.core.Rules
import com.readingmarathon.core.Suggestion
import com.readingmarathon.core.Visibility
import kotlinx.coroutines.flow.StateFlow
import java.time.LocalDate

/** The platform owner. Only this Google account sees the platform admin panel (enforced again in firestore.rules). */
const val ADMIN_EMAIL = "readingmarathon.admin@gmail.com"

/** Who is signed in. [isGuest] is an old guest account: it must link Google before going on. */
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
    /** Readers who logged anything, per day for the last 30 days, oldest first. */
    val activeReaders: List<Int> = emptyList(),
    /** Books chosen by most groups: (title, groups). */
    val topBooks: List<Pair<String, Int>> = emptyList(),
    val catalogBooks: Int = 0,
    val pendingBooks: Int = 0,
    val openSuggestions: Int = 0,
    val banned: Set<String> = emptySet(),
)

/** The reader's data, ready to save: one CSV for the reading log, one for the shelf. */
data class MyData(val reads: String, val shelf: String)

/** A message every reader sees on the home screen until they dismiss it. */
data class Announcement(val id: String, val title: String, val body: String, val active: Boolean = true, val createdAt: Long = 0)

/** One platform-admin action, for the audit log. */
data class LogEntry(val id: String, val at: Long, val action: String, val target: String = "")

/** Everything about one account, for the admin. */
data class AdminUserDetail(
    val user: AdminUser,
    val goal: Int,
    val days: List<Day>,
    val groups: List<Pair<String, String>>,
    val shelf: List<ShelfBook> = emptyList(),
    val banned: Boolean,
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

    /** Google only. An old guest account is linked, so it keeps its data. */
    suspend fun signInWithGoogle(activity: Activity)
    suspend fun signOut()
    /** Everything the reader logged: their reads (with book names) and their shelf, as CSV. */
    suspend fun exportMyData(): MyData
    /**
     * Deletes the account and everything in it: log, shelf, profile, suggestions, and its place in
     * every group (an owned group passes to another member, or is deleted when no one else is in it).
     * Asks Google to confirm the account first.
     */
    suspend fun deleteAccount(activity: Activity)

    /** null while loading or signed out. */
    val profile: StateFlow<Profile?>
    /** The reader's own log (the last year or so). */
    val days: StateFlow<List<Day>>
    /** Loaded groups: the reader's own, plus any opened from the admin panel. */
    val groups: StateFlow<Map<String, GroupData>>

    suspend fun saveProfile(name: String, goal: Int)

    /** Minutes per book per day (the last year or so). [days] holds their daily totals. */
    val reads: StateFlow<List<Read>>
    /** The reader's own books: reading, finished, want to read. */
    val shelf: StateFlow<List<ShelfBook>>

    /**
     * Sets the minutes the reader spent on [bookId] on [date], in their own log and in every group
     * they are in (each group sees the day's total and its shared-book part).
     */
    suspend fun setRead(date: LocalDate, bookId: String, minutes: Int)

    /**
     * Adds minutes to [bookId] today, or to the book read most recently when null (the notification
     * buttons). Works before anything is loaded. Returns the book and its minutes today.
     */
    suspend fun addRead(date: LocalDate, bookId: String?, minutes: Int): Pair<ShelfBook, Int>

    /** Puts a catalog book on the shelf (or changes its status). Finishing it counts in the groups reading it. */
    suspend fun shelve(book: CatalogBook, status: ShelfStatus)
    suspend fun setShelfStatus(bookId: String, status: ShelfStatus)
    suspend fun removeFromShelf(bookId: String)

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
    /** Marks (or with null, unmarks) that the reader finished a challenge's book. Only for oneself. */
    suspend fun setFinished(groupId: String, challengeId: String, date: LocalDate?)
    suspend fun setRole(groupId: String, uid: String, role: Role)
    suspend fun removeMember(groupId: String, memberId: String)
    suspend fun deleteGroup(groupId: String)

    // Book catalog (every reader)

    /** Books whose title, other names or author match the query, best first. */
    suspend fun searchBooks(query: String): List<CatalogBook>
    suspend fun book(id: String): CatalogBook?
    /** A book the catalog doesn't have yet: usable at once, reviewed by the admin. */
    suspend fun proposeBook(title: String, author: String, pages: Int?): CatalogBook
    /** Report a wrong title or author (FIX), or that a book repeats another (DUPLICATE). */
    suspend fun suggest(suggestion: Suggestion)
    suspend fun mySuggestions(): List<Suggestion>

    /** Active announcements from the platform admin. */
    val announcements: StateFlow<List<Announcement>>
    /** True when the platform admin has suspended this account. */
    val banned: StateFlow<Boolean>

    // Platform admin only

    suspend fun adminOverview(): AdminOverview
    suspend fun adminBooks(): List<CatalogBook>
    /** Creates or edits a catalog book; groups reading it get the new title and author. */
    suspend fun adminSaveBook(book: CatalogBook): CatalogBook
    suspend fun adminDeleteBook(id: String)
    /** [fromId] was a duplicate: its names join [intoId], and every group reading it moves over. */
    suspend fun adminMergeBooks(fromId: String, intoId: String)
    /** Adds the books the catalog doesn't have yet. Returns how many were added. */
    suspend fun adminImportBooks(books: List<CatalogBook>): Int
    suspend fun adminSuggestions(): List<Suggestion>
    suspend fun adminResolve(id: String, accepted: Boolean, reply: String)
    suspend fun adminUser(uid: String): AdminUserDetail
    suspend fun adminRenameUser(uid: String, name: String)
    suspend fun adminSetBanned(uid: String, banned: Boolean)
    /** Removes the account's log, profile and its place in every group. */
    suspend fun adminDeleteUserData(uid: String)
    suspend fun adminAnnouncements(): List<Announcement>
    suspend fun adminSaveAnnouncement(a: Announcement)
    suspend fun adminDeleteAnnouncement(id: String)
    suspend fun adminLog(): List<LogEntry>
}
