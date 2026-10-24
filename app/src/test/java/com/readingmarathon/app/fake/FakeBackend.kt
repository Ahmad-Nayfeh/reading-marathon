package com.readingmarathon.app.fake

import android.app.Activity
import com.readingmarathon.app.data.Account
import com.readingmarathon.app.data.AdminGroup
import com.readingmarathon.app.data.AdminOverview
import com.readingmarathon.app.data.AdminUser
import com.readingmarathon.app.data.Backend
import com.readingmarathon.app.data.Checks
import com.readingmarathon.app.data.GroupPreview
import com.readingmarathon.app.data.Profile
import com.readingmarathon.core.BookBrief
import com.readingmarathon.core.Challenge
import com.readingmarathon.core.CsvImport
import com.readingmarathon.core.Day
import com.readingmarathon.core.Entry
import com.readingmarathon.core.Finish
import com.readingmarathon.core.GroupData
import com.readingmarathon.core.Group
import com.readingmarathon.core.Ids
import com.readingmarathon.core.Limits
import com.readingmarathon.core.Member
import com.readingmarathon.core.Role
import com.readingmarathon.core.RuleViolation
import com.readingmarathon.core.Rules
import com.readingmarathon.core.Validation
import com.readingmarathon.core.Visibility
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.time.LocalDate

/** Everything in memory, refusing what the real backend refuses (same [Checks] and :core rules). */
class FakeBackend : Backend {
    override val configured = true
    private val _account = MutableStateFlow<Account?>(null)
    override val account: StateFlow<Account?> = _account
    override val restoring: StateFlow<Boolean> = MutableStateFlow(false)

    val profiles = LinkedHashMap<String, Profile>()
    val logs = LinkedHashMap<String, MutableMap<LocalDate, Day>>()
    val all = LinkedHashMap<String, GroupData>()
    val accounts = LinkedHashMap<String, Account>()
    private val peeked = HashSet<String>()

    private val _profile = MutableStateFlow<Profile?>(null)
    override val profile: StateFlow<Profile?> = _profile
    private val _days = MutableStateFlow<List<Day>>(emptyList())
    override val days: StateFlow<List<Day>> = _days
    private val _groups = MutableStateFlow<Map<String, GroupData>>(emptyMap())
    override val groups: StateFlow<Map<String, GroupData>> = _groups

    private fun me() = _account.value?.uid ?: throw RuleViolation("سجّل الدخول أولاً.")
    private fun isAdmin() = _account.value?.isAdmin == true
    private fun today() = LocalDate.now()

    fun signIn(a: Account) {
        accounts[a.uid] = a
        _account.value = a
        publish()
    }

    /** Re-emits what the signed-in account sees. */
    fun publish() {
        val uid = _account.value?.uid
        _profile.value = uid?.let { profiles[it] ?: Profile("") }
        _days.value = uid?.let { logs[it]?.values?.toList() }.orEmpty()
        val wanted = _profile.value?.groups.orEmpty().toSet() + peeked
        _groups.value = all.filterKeys { it in wanted }
    }

    private fun edit(id: String, f: (GroupData) -> GroupData) {
        all[id] = f(all[id] ?: throw RuleViolation("لم تعد هذه المجموعة موجودة."))
        publish()
    }

    private fun loaded(id: String) = all[id] ?: throw RuleViolation("لم تعد هذه المجموعة موجودة.")

    override suspend fun signInWithGoogle(activity: Activity) = signIn(Account("google", "reader@gmail.com", "قارئ", false))
    override suspend fun signInAsGuest() = signIn(Account(Ids.newId("g_"), null, null, true))
    override suspend fun signOut() { _account.value = null; publish() }

    override suspend fun saveProfile(name: String, goal: Int) {
        Validation.personName(name)
        val me = me()
        val p = (profiles[me] ?: Profile("")).copy(name = name.trim(), goal = goal)
        profiles[me] = p
        p.groups.forEach { gid -> all[gid]?.let { d -> all[gid] = d.copy(members = d.members.map { if (it.id == me) it.copy(name = name.trim()) else it }) } }
        publish()
    }

    override suspend fun setDay(date: LocalDate, minutes: Int) {
        Validation.day(date, minutes, today())
        val me = me()
        logs.getOrPut(me) { LinkedHashMap() }[date] = Day(date, minutes, System.currentTimeMillis())
        profiles[me]?.groups.orEmpty().forEach { gid ->
            all[gid]?.let { d ->
                val others = d.entries.filter { !(it.memberId == me && it.date == date) }
                all[gid] = d.copy(entries = if (minutes == 0) others else others + Entry(me, date, minutes, System.currentTimeMillis()))
            }
        }
        publish()
    }

    override suspend fun addMinutes(date: LocalDate, minutes: Int): Int {
        val total = ((logs[me()]?.get(date)?.minutes ?: 0) + minutes).coerceIn(0, Limits.MAX_MINUTES_PER_DAY)
        setDay(date, total)
        return total
    }

    /** Test setup: a group as it would exist on the server. */
    fun seed(d: GroupData) {
        all[d.group.id] = d
        d.group.roles.keys.forEach { uid -> profiles[uid]?.let { p -> profiles[uid] = p.copy(groups = (p.groups + d.group.id).distinct()) } }
        publish()
    }

    private fun preview(d: GroupData) = GroupPreview(
        d.group.id, d.group.name, d.group.about, d.group.visibility, d.group.memberCount,
        d.currentChallenge(today())?.let { BookBrief(it.book.title, it.book.author, it.start, it.end) },
    )

    override suspend fun createGroup(name: String, about: String, visibility: Visibility): String {
        Validation.group(name, about, Rules.DEFAULT)
        val me = me()
        val id = Ids.newId("g_")
        val p = profiles[me] ?: Profile("")
        all[id] = GroupData(
            Group(id, name.trim(), about.trim(), visibility, Ids.inviteCode(), mapOf(me to Role.OWNER), createdAt = System.currentTimeMillis()),
            members = listOf(Member(me, p.name, me)),
        )
        profiles[me] = p.copy(groups = p.groups + id)
        publish()
        return id
    }

    override suspend fun findByCode(code: String): GroupPreview =
        all.values.firstOrNull { it.group.inviteCode == Ids.normalizeCode(code) }?.let(::preview)
            ?: throw RuleViolation("لا توجد مجموعة بهذا الرمز. تأكد منه مع من دعاك.")

    override suspend fun publicGroups(): List<GroupPreview> = all.values.filter { it.group.visibility == Visibility.PUBLIC }.map(::preview)

    override suspend fun joinGroup(groupId: String, code: String?) {
        val me = me()
        val d = loaded(groupId)
        if (d.group.visibility == Visibility.PRIVATE && Ids.normalizeCode(code.orEmpty()) != d.group.inviteCode) throw RuleViolation("هذه مجموعة خاصة، تحتاج رمز دعوة صالحاً.")
        val p = profiles[me] ?: Profile("")
        all[groupId] = d.copy(
            group = d.group.copy(roles = d.group.roles + (me to (d.group.roles[me] ?: Role.MEMBER))),
            members = d.members.filter { it.id != me } + Member(me, p.name, me),
        )
        profiles[me] = p.copy(groups = (p.groups + groupId).distinct())
        publish()
    }

    override suspend fun leaveGroup(groupId: String) {
        val me = me()
        val d = loaded(groupId)
        if (d.group.roleOf(me) == Role.OWNER) throw RuleViolation("أنت مالك المجموعة. انقل الملكية إلى عضو آخر أولاً، أو احذف المجموعة.")
        all[groupId] = d.copy(group = d.group.copy(roles = d.group.roles - me), members = d.members.map { if (it.id == me) it.copy(active = false) else it })
        profiles[me]?.let { profiles[me] = it.copy(groups = it.groups - groupId) }
        publish()
    }

    override fun watch(groupId: String) { peeked += groupId; publish() }
    override fun unwatch(groupId: String) { peeked -= groupId; publish() }

    override suspend fun updateGroup(groupId: String, name: String, about: String, visibility: Visibility, rules: Rules) {
        Checks.manager(loaded(groupId), me(), isAdmin())
        Validation.group(name, about, rules)
        edit(groupId) { it.copy(group = it.group.copy(name = name.trim(), about = about.trim(), visibility = visibility, rules = rules)) }
    }

    override suspend fun newInviteCode(groupId: String) {
        Checks.manager(loaded(groupId), me(), isAdmin())
        edit(groupId) { it.copy(group = it.group.copy(inviteCode = Ids.inviteCode())) }
    }

    override suspend fun saveChallenge(groupId: String, challenge: Challenge) {
        val d = loaded(groupId)
        Checks.manager(d, me(), isAdmin())
        Checks.challenge(d, challenge)
        edit(groupId) { it.copy(challenges = it.challenges.filter { c -> c.id != challenge.id } + challenge) }
    }

    override suspend fun deleteChallenge(groupId: String, challengeId: String) {
        Checks.manager(loaded(groupId), me(), isAdmin())
        edit(groupId) { it.copy(challenges = it.challenges.filter { c -> c.id != challengeId }, finishes = it.finishes.filter { f -> f.challengeId != challengeId }) }
    }

    override suspend fun setFinished(groupId: String, challengeId: String, memberId: String, date: LocalDate?) {
        val d = loaded(groupId)
        if (memberId != me()) Checks.manager(d, me(), isAdmin())
        val c = d.challenge(challengeId) ?: throw RuleViolation("لم يعد هذا التحدي موجوداً.")
        if (date != null) Validation.finish(c, date)
        edit(groupId) {
            val rest = it.finishes.filter { f -> f.id != Finish.finishId(memberId, challengeId) }
            it.copy(finishes = if (date == null) rest else rest + Finish(memberId, challengeId, date))
        }
    }

    override suspend fun setRole(groupId: String, uid: String, role: Role) {
        val d = loaded(groupId)
        Checks.owner(d, me(), isAdmin())
        var roles = d.group.roles + (uid to role)
        if (role == Role.OWNER) roles = roles.mapValues { (k, v) -> if (k != uid && v == Role.OWNER) Role.ADMIN else v }
        edit(groupId) { it.copy(group = it.group.copy(roles = roles)) }
    }

    override suspend fun removeMember(groupId: String, memberId: String) {
        Checks.canRemove(loaded(groupId), memberId, me(), isAdmin())
        edit(groupId) { it.copy(group = it.group.copy(roles = it.group.roles - memberId), members = it.members.map { m -> if (m.id == memberId) m.copy(active = false) else m }) }
    }

    override suspend fun addReader(groupId: String, name: String): Member {
        val d = loaded(groupId)
        Checks.manager(d, me(), isAdmin())
        Validation.memberName(name, d.members)
        val m = Member(Ids.newId("r_"), name.trim())
        edit(groupId) { it.copy(members = it.members + m) }
        return m
    }

    override suspend fun renameReader(groupId: String, memberId: String, name: String) {
        val d = loaded(groupId)
        Checks.manager(d, me(), isAdmin())
        Checks.reader(d, memberId)
        Validation.memberName(name, d.members, memberId)
        edit(groupId) { it.copy(members = it.members.map { m -> if (m.id == memberId) m.copy(name = name.trim()) else m }) }
    }

    override suspend fun setReaderDay(groupId: String, memberId: String, date: LocalDate, minutes: Int) {
        val d = loaded(groupId)
        Checks.manager(d, me(), isAdmin())
        Checks.reader(d, memberId)
        edit(groupId) {
            val rest = it.entries.filter { e -> e.id != Entry.entryId(memberId, date) }
            it.copy(entries = if (minutes == 0) rest else rest + Entry(memberId, date, minutes))
        }
    }

    override suspend fun mergeReader(groupId: String, fromId: String, intoId: String) {
        val d = loaded(groupId)
        Checks.manager(d, me(), isAdmin())
        Checks.reader(d, fromId)
        edit(groupId) {
            val moved = it.entries.filter { e -> e.memberId == fromId }
            val kept = it.entries.filter { e -> e.memberId != fromId }.associateBy { e -> e.id }.toMutableMap()
            moved.forEach { e ->
                val id = Entry.entryId(intoId, e.date)
                kept[id] = Entry(intoId, e.date, ((kept[id]?.minutes ?: 0) + e.minutes).coerceAtMost(Limits.MAX_MINUTES_PER_DAY))
            }
            it.copy(
                entries = kept.values.toList(),
                finishes = it.finishes.map { f -> if (f.memberId == fromId) f.copy(memberId = intoId) else f }.distinctBy { f -> f.id },
                members = it.members.map { m -> if (m.id == fromId) m.copy(active = false) else m },
            )
        }
    }

    override suspend fun applyImport(groupId: String, plan: CsvImport.Plan) {
        Checks.manager(loaded(groupId), me(), isAdmin())
        edit(groupId) { it.copy(members = it.members + plan.newMembers, entries = it.entries + plan.entries, finishes = it.finishes + plan.finishes) }
    }

    override suspend fun deleteGroup(groupId: String) {
        Checks.owner(loaded(groupId), me(), isAdmin())
        all.remove(groupId)
        profiles.keys.toList().forEach { k -> profiles[k] = profiles.getValue(k).let { it.copy(groups = it.groups - groupId) } }
        publish()
    }

    override suspend fun adminOverview(): AdminOverview {
        if (!isAdmin()) throw RuleViolation("هذه اللوحة لمدير المنصة فقط.")
        val today = today()
        val week = today.minusDays(6)
        val days = logs.values.flatMap { it.values }
        return AdminOverview(
            groups = all.values.map { d ->
                AdminGroup(
                    d.group.id, d.group.name, d.group.visibility, d.group.inviteCode, d.group.createdAt,
                    d.group.roles.filterValues { it == Role.OWNER }.keys.map { accounts[it]?.email ?: "ضيف" },
                    d.group.memberCount, d.activeMembers.size, d.challenges.size,
                    d.entries.sumOf { it.minutes.toLong() }, d.entries.filter { !it.date.isBefore(week) }.sumOf { it.minutes.toLong() },
                    d.entries.filter { it.minutes > 0 }.maxOfOrNull { it.date }?.toString(),
                )
            },
            users = accounts.values.map { a ->
                AdminUser(a.uid, a.email, profiles[a.uid]?.name, a.isGuest, 0, System.currentTimeMillis(), profiles[a.uid]?.groups?.size ?: 0, logs[a.uid]?.values?.sumOf { it.minutes.toLong() } ?: 0)
            },
            totalMinutes = days.sumOf { it.minutes.toLong() },
            minutesLast7 = days.filter { !it.date.isBefore(week) }.sumOf { it.minutes.toLong() },
            readingDays = days.count { it.minutes > 0 },
            finishedBooks = all.values.sumOf { it.finishes.size },
            daily = (29 downTo 0).map { back -> days.filter { it.date == today.minusDays(back.toLong()) }.sumOf { it.minutes.toLong() } },
        )
    }
}
