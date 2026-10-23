package com.readingmarathon.app.fake

import android.app.Activity
import com.readingmarathon.app.data.Account
import com.readingmarathon.app.data.AdminMarathon
import com.readingmarathon.app.data.AdminOverview
import com.readingmarathon.app.data.AdminUser
import com.readingmarathon.app.data.Backend
import com.readingmarathon.app.data.MarathonStore
import com.readingmarathon.app.data.MarathonSummary
import com.readingmarathon.app.data.Role
import com.readingmarathon.core.AchievementType
import com.readingmarathon.core.Ids
import com.readingmarathon.core.Marathon
import com.readingmarathon.core.MarathonData
import com.readingmarathon.core.Member
import com.readingmarathon.core.RuleViolation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.time.LocalDate

/** Accounts and marathons in memory, for driving the real UI in tests. */
class FakeBackend : Backend {
    override val configured = true
    private val _account = MutableStateFlow<Account?>(null)
    override val account: StateFlow<Account?> = _account
    override val restoring: StateFlow<Boolean> = MutableStateFlow(false)

    private class Entry(val store: InMemoryStore, val roles: MutableMap<String, Role>)
    private val marathons = LinkedHashMap<String, Entry>()
    val users = LinkedHashMap<String, Account>()

    fun signIn(a: Account) { users[a.uid] = a; _account.value = a }

    /** Adds a marathon with [roles] (uid → role). */
    fun seed(data: MarathonData, roles: Map<String, Role>) {
        marathons[data.marathon.id] = Entry(InMemoryStore(data) { _account.value?.uid.orEmpty() }, roles.toMutableMap())
    }

    override suspend fun signInWithGoogle(activity: Activity) = signIn(Account("google", "reader@gmail.com", "قارئ", false))
    override suspend fun signInAsGuest() = signIn(Account(Ids.newId("g_"), null, null, true))
    override suspend fun signOut() { _account.value = null }

    private fun me() = _account.value?.uid ?: throw RuleViolation("سجّل الدخول أولاً.")

    override suspend fun myMarathons() = marathons.mapNotNull { (id, e) ->
        e.roles[me()]?.let { MarathonSummary(id, e.store.data.value!!.marathon.name, it) }
    }

    override suspend fun createMarathon(name: String, organizerName: String): Pair<String, String> {
        val id = Ids.newId("mr_")
        val memberId = Ids.newId("m_")
        seed(
            MarathonData(Marathon(id, name, Ids.inviteCode()), members = listOf(Member(memberId, organizerName, linkedUid = me()))),
            mapOf(me() to Role.ORGANIZER),
        )
        return id to memberId
    }

    override suspend fun joinMarathon(code: String): String {
        val e = marathons.values.firstOrNull { it.store.data.value!!.marathon.inviteCode == Ids.normalizeCode(code) }
            ?: throw RuleViolation("لم أجد ماراثوناً بهذا الرمز.")
        e.roles.putIfAbsent(me(), Role.MEMBER)
        return e.store.data.value!!.marathon.id
    }

    override suspend fun roleIn(marathonId: String) = marathons[marathonId]?.roles?.get(me())
    override fun openStore(marathonId: String): MarathonStore = marathons.getValue(marathonId).store

    override suspend fun adminOverview(): AdminOverview {
        val today = LocalDate.now()
        val week = today.minusDays(6)
        val all = marathons.values.map { it to it.store.data.value!! }
        val entries = all.flatMap { it.second.entries }
        return AdminOverview(
            marathons = all.map { (e, d) ->
                AdminMarathon(
                    id = d.marathon.id, name = d.marathon.name, inviteCode = d.marathon.inviteCode, createdAt = d.marathon.createdAt,
                    organizers = e.roles.filterValues { it == Role.ORGANIZER }.keys.map { users[it]?.email ?: "ضيف" },
                    accounts = e.roles.size, members = d.members.size, activeMembers = d.activeMembers.size,
                    challenges = d.challenges.size, readingDays = d.entries.count { it.isReadingDay },
                    minutes = d.entries.sumOf { it.totalMinutes.toLong() },
                    minutesLast7 = d.entries.filter { !it.date.isBefore(week) }.sumOf { it.totalMinutes.toLong() },
                    lastActivity = d.entries.filter { it.isReadingDay }.maxOfOrNull { it.date }?.toString(),
                )
            },
            users = users.values.map { AdminUser(it.uid, it.email, it.name, it.isGuest, 0, System.currentTimeMillis(), myCount(it.uid)) },
            totalMembers = all.sumOf { it.second.members.size },
            totalEntries = entries.count { it.isReadingDay },
            totalMinutes = entries.sumOf { it.totalMinutes.toLong() },
            minutesLast7 = entries.filter { !it.date.isBefore(week) }.sumOf { it.totalMinutes.toLong() },
            finishedBooks = all.sumOf { (_, d) -> d.achievements.count { it.type != AchievementType.ATTENDED_DISCUSSION } },
            daily = (29 downTo 0).map { back -> entries.filter { it.date == today.minusDays(back.toLong()) }.sumOf { it.totalMinutes.toLong() } },
        )
    }

    private fun myCount(uid: String) = marathons.values.count { uid in it.roles }
}
