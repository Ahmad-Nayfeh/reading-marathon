package com.readingmarathon.app.fake

import android.app.Activity
import com.readingmarathon.app.data.Account
import com.readingmarathon.app.data.AdminGroup
import com.readingmarathon.app.data.AdminOverview
import com.readingmarathon.app.data.AdminUser
import com.readingmarathon.app.data.Backend
import com.readingmarathon.app.data.Checks
import com.readingmarathon.app.data.AdminUserDetail
import com.readingmarathon.app.data.Announcement
import com.readingmarathon.app.data.LogEntry
import com.readingmarathon.core.BookStatus
import com.readingmarathon.core.Catalog
import com.readingmarathon.core.CatalogBook
import com.readingmarathon.core.Suggestion
import com.readingmarathon.core.SuggestionStatus
import com.readingmarathon.core.SuggestionType
import com.readingmarathon.app.data.GroupPreview
import com.readingmarathon.app.data.Profile
import com.readingmarathon.core.BookBrief
import com.readingmarathon.core.Challenge
import com.readingmarathon.app.data.MyData
import com.readingmarathon.core.Export
import com.readingmarathon.core.Day
import com.readingmarathon.core.Entry
import com.readingmarathon.core.Finish
import com.readingmarathon.core.GroupData
import com.readingmarathon.core.Group
import com.readingmarathon.core.Ids
import com.readingmarathon.core.ShelfStatus
import com.readingmarathon.core.ShelfBook
import com.readingmarathon.core.Shelf
import com.readingmarathon.core.Read
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
        emitExtras()
    }

    /** Re-emits what the signed-in account sees. */
    fun publish() {
        val uid = _account.value?.uid
        _profile.value = uid?.let { profiles[it] ?: Profile("") }
        _days.value = uid?.let { logs[it]?.values?.toList() }.orEmpty()
        _reads.value = uid?.let { readsBy[it]?.values?.toList() }.orEmpty()
        _shelf.value = uid?.let { shelves[it]?.values?.toList() }.orEmpty()
        val wanted = _profile.value?.groups.orEmpty().toSet() + peeked
        _groups.value = all.filterKeys { it in wanted }
    }

    private fun edit(id: String, f: (GroupData) -> GroupData) {
        all[id] = f(all[id] ?: throw RuleViolation("لم تعد هذه المجموعة موجودة."))
        publish()
    }

    private fun loaded(id: String) = all[id] ?: throw RuleViolation("لم تعد هذه المجموعة موجودة.")

    override suspend fun signInWithGoogle(activity: Activity) = signIn(Account("google", "reader@gmail.com", "قارئ", false))
    override suspend fun signOut() { _account.value = null; publish() }

    override suspend fun exportMyData(): MyData {
        val shelf = shelves[me()].orEmpty().values.toList()
        return MyData(Export.myReads(readsBy[me()].orEmpty().values.toList(), shelf) { null }, Export.myShelf(shelf))
    }

    override suspend fun deleteAccount(activity: Activity) {
        val me = me()
        all.keys.toList().forEach { gid ->
            val d = all.getValue(gid)
            if (d.group.roleOf(me) == Role.OWNER && d.group.roles.size == 1) all.remove(gid)
            else all[gid] = d.copy(
                group = d.group.copy(roles = (d.group.roles - me).let { r -> if (r.none { it.value == Role.OWNER } && r.isNotEmpty()) r + (r.keys.first() to Role.OWNER) else r }),
                members = d.members.filter { it.id != me }, entries = d.entries.filter { it.memberId != me }, finishes = d.finishes.filter { it.memberId != me },
            )
        }
        profiles.remove(me); readsBy.remove(me); shelves.remove(me); logs.remove(me)
        signOut()
    }

    override suspend fun saveProfile(name: String, goal: Int) {
        Validation.personName(name)
        val me = me()
        val p = (profiles[me] ?: Profile("")).copy(name = name.trim(), goal = goal)
        profiles[me] = p
        p.groups.forEach { gid -> all[gid]?.let { d -> all[gid] = d.copy(members = d.members.map { if (it.id == me) it.copy(name = name.trim()) else it }) } }
        publish()
    }

    val readsBy = LinkedHashMap<String, MutableMap<String, Read>>()
    val shelves = LinkedHashMap<String, LinkedHashMap<String, ShelfBook>>()
    private val _reads = MutableStateFlow<List<Read>>(emptyList())
    override val reads: StateFlow<List<Read>> = _reads
    private val _shelf = MutableStateFlow<List<ShelfBook>>(emptyList())
    override val shelf: StateFlow<List<ShelfBook>> = _shelf

    override suspend fun setRead(date: LocalDate, bookId: String, minutes: Int) {
        val me = me()
        val now = System.currentTimeMillis()
        val mine = readsBy.getOrPut(me) { LinkedHashMap() }
        val before = mine[Read.readId(date, bookId)]?.minutes ?: 0
        val after = mine.values.filter { it.date == date && it.bookId != bookId } + Read(date, bookId, minutes, now)
        Validation.day(date, after.sumOf { it.minutes }, today())
        if (minutes == 0) mine.remove(Read.readId(date, bookId)) else mine[Read.readId(date, bookId)] = Read(date, bookId, minutes, now)
        logs.getOrPut(me) { LinkedHashMap() }[date] = Day(date, after.sumOf { it.minutes }, now)
        shelves[me]?.get(bookId)?.let { shelves.getValue(me)[bookId] = it.copy(minutes = it.minutes + minutes - before, lastReadAt = now) }
        profiles[me]?.groups.orEmpty().forEach { gid ->
            all[gid]?.let { d ->
                val e = Shelf.groupEntry(me, date, after.filter { it.minutes > 0 }, d.challenges, now)
                val others = d.entries.filter { !(it.memberId == me && it.date == date) }
                all[gid] = d.copy(entries = if (e.minutes == 0) others else others + e)
            }
        }
        publish()
    }

    override suspend fun addRead(date: LocalDate, bookId: String?, minutes: Int): Pair<ShelfBook, Int> {
        val me = me()
        val shelf = shelves[me]?.values?.toList().orEmpty()
        val book = (if (bookId != null) shelf.firstOrNull { it.bookId == bookId } else Shelf.reading(shelf).firstOrNull())
            ?: throw RuleViolation("اختر الكتاب الذي تقرؤه أولاً.")
        val current = readsBy[me]?.get(Read.readId(date, book.bookId))?.minutes ?: 0
        setRead(date, book.bookId, current + minutes)
        return book to current + minutes
    }

    override suspend fun shelve(book: CatalogBook, status: ShelfStatus) {
        val mine = shelves.getOrPut(me()) { LinkedHashMap() }
        mine[book.id] = (mine[book.id] ?: ShelfBook(book.id, book.title, book.author, startedAt = System.currentTimeMillis())).copy(status = status)
        publish()
    }

    override suspend fun setShelfStatus(bookId: String, status: ShelfStatus) {
        val mine = shelves.getOrPut(me()) { LinkedHashMap() }
        mine[bookId]?.let { mine[bookId] = it.copy(status = status, finishedAt = if (status == ShelfStatus.FINISHED) System.currentTimeMillis() else 0) }
        publish()
    }

    override suspend fun removeFromShelf(bookId: String) { shelves[me()]?.remove(bookId); publish() }

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

    override suspend fun setFinished(groupId: String, challengeId: String, date: LocalDate?) {
        val d = loaded(groupId)
        val memberId = me()
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

    override suspend fun deleteGroup(groupId: String) {
        Checks.owner(loaded(groupId), me(), isAdmin())
        all.remove(groupId)
        profiles.keys.toList().forEach { k -> profiles[k] = profiles.getValue(k).let { it.copy(groups = it.groups - groupId) } }
        publish()
    }

    // ---- Catalog ----

    val books = LinkedHashMap<String, CatalogBook>()
    val suggestions = LinkedHashMap<String, Suggestion>()
    val news = LinkedHashMap<String, Announcement>()
    val bannedSet = HashSet<String>()
    val log = mutableListOf<LogEntry>()
    private val _announcements = MutableStateFlow<List<Announcement>>(emptyList())
    override val announcements: StateFlow<List<Announcement>> = _announcements
    private val _banned = MutableStateFlow(false)
    override val banned: StateFlow<Boolean> = _banned

    private fun emitExtras() {
        _announcements.value = news.values.filter { it.active }.sortedByDescending { it.createdAt }
        _banned.value = _account.value?.uid in bannedSet
    }

    private fun requireAdmin() { if (!isAdmin()) throw RuleViolation("هذا لمدير المنصة فقط.") }
    private fun record(action: String, target: String = "") { log += LogEntry(Ids.newId("l_"), System.currentTimeMillis(), action, target) }

    override suspend fun searchBooks(query: String) = Catalog.rank(query, books.values.toList()).take(20)
    override suspend fun book(id: String) = books[id]

    override suspend fun proposeBook(title: String, author: String, pages: Int?): CatalogBook {
        Catalog.validate(title, author)
        Catalog.findSame(title, author, books.values.toList())?.let { return it }
        val b = CatalogBook(Ids.newId("b_"), title.trim(), author.trim(), pages = pages, status = BookStatus.PENDING, createdBy = me(), createdAt = System.currentTimeMillis())
        books[b.id] = b
        val sid = Ids.newId("s_")
        suggestions[sid] = Suggestion(sid, SuggestionType.NEW_BOOK, b.id, title = b.title, author = b.author, by = me(), byName = profiles[me()]?.name.orEmpty(), createdAt = b.createdAt)
        return b
    }

    override suspend fun suggest(suggestion: Suggestion) {
        val id = Ids.newId("s_")
        suggestions[id] = suggestion.copy(id = id, by = me(), byName = profiles[me()]?.name.orEmpty(), status = SuggestionStatus.OPEN, createdAt = System.currentTimeMillis())
    }

    override suspend fun mySuggestions() = suggestions.values.filter { it.by == me() }.sortedByDescending { it.createdAt }

    override suspend fun adminBooks(): List<CatalogBook> { requireAdmin(); return books.values.sortedBy { it.title } }

    override suspend fun adminSaveBook(book: CatalogBook): CatalogBook {
        requireAdmin()
        Catalog.validate(book.title, book.author)
        val b = book.copy(id = book.id.ifBlank { Ids.newId("b_") }, title = book.title.trim(), author = book.author.trim(), status = BookStatus.VERIFIED)
        books[b.id] = b
        all.keys.toList().forEach { gid ->
            all[gid] = all.getValue(gid).let { d -> d.copy(challenges = d.challenges.map { c -> if (c.bookId == b.id) c.copy(book = b.book) else c }) }
        }
        record("حفظ كتاباً", b.title)
        publish()
        return b
    }

    override suspend fun adminDeleteBook(id: String) { requireAdmin(); books.remove(id); record("حذف كتاباً", id) }

    override suspend fun adminMergeBooks(fromId: String, intoId: String) {
        requireAdmin()
        val from = books.getValue(fromId)
        val into = books.getValue(intoId)
        val kept = into.copy(aliases = Catalog.mergedAliases(into, from))
        books[intoId] = kept
        books[fromId] = from.copy(status = BookStatus.MERGED, mergedInto = intoId)
        all.keys.toList().forEach { gid ->
            all[gid] = all.getValue(gid).let { d -> d.copy(challenges = d.challenges.map { c -> if (c.bookId == fromId) c.copy(bookId = intoId, book = kept.book) else c }) }
        }
        record("دمج كتابين", "${from.title} ← ${into.title}")
        publish()
    }

    override suspend fun adminImportBooks(books: List<CatalogBook>): Int {
        requireAdmin()
        var n = 0
        books.forEach { x ->
            if (Catalog.findSame(x.title, x.author, this.books.values.toList()) == null) {
                val b = x.copy(id = Ids.newId("b_"), status = BookStatus.VERIFIED)
                this.books[b.id] = b
                n++
            }
        }
        return n
    }

    override suspend fun adminSuggestions(): List<Suggestion> { requireAdmin(); return suggestions.values.sortedByDescending { it.createdAt } }

    override suspend fun adminResolve(id: String, accepted: Boolean, reply: String) {
        requireAdmin()
        val sg = suggestions.getValue(id)
        if (accepted && sg.type == SuggestionType.NEW_BOOK) sg.bookId?.let { b -> books[b]?.let { books[b] = it.copy(status = BookStatus.VERIFIED) } }
        if (accepted && sg.type == SuggestionType.DUPLICATE) adminMergeBooks(sg.bookId!!, sg.otherBookId!!)
        suggestions[id] = sg.copy(status = if (accepted) SuggestionStatus.ACCEPTED else SuggestionStatus.REJECTED, reply = reply, resolvedAt = System.currentTimeMillis())
    }

    override suspend fun adminUser(uid: String): AdminUserDetail {
        requireAdmin()
        val a = accounts[uid]
        val p = profiles[uid] ?: Profile("")
        val days = logs[uid]?.values?.sortedByDescending { it.date }.orEmpty()
        return AdminUserDetail(
            AdminUser(uid, a?.email, p.name, a?.isGuest ?: true, 0, System.currentTimeMillis(), p.groups.size, days.sumOf { it.minutes.toLong() }),
            p.goal, days, p.groups.map { it to (all[it]?.group?.name ?: "—") }, shelves[uid]?.values?.toList().orEmpty(), banned = uid in bannedSet,
        )
    }

    override suspend fun adminRenameUser(uid: String, name: String) { requireAdmin(); profiles[uid]?.let { profiles[uid] = it.copy(name = name.trim()) }; publish() }
    override suspend fun adminSetBanned(uid: String, banned: Boolean) { requireAdmin(); if (banned) bannedSet += uid else bannedSet -= uid; emitExtras() }

    override suspend fun adminDeleteUserData(uid: String) {
        requireAdmin()
        logs.remove(uid)
        profiles[uid]?.groups?.forEach { gid ->
            all[gid]?.let { d -> all[gid] = d.copy(group = d.group.copy(roles = d.group.roles - uid), members = d.members.filter { it.id != uid }, entries = d.entries.filter { it.memberId != uid }) }
        }
        profiles.remove(uid)
        publish()
    }

    override suspend fun adminAnnouncements(): List<Announcement> { requireAdmin(); return news.values.sortedByDescending { it.createdAt } }

    override suspend fun adminSaveAnnouncement(a: Announcement) {
        requireAdmin()
        val id = a.id.ifBlank { Ids.newId("a_") }
        news[id] = a.copy(id = id, createdAt = a.createdAt.takeIf { it > 0 } ?: System.currentTimeMillis())
        emitExtras()
    }

    override suspend fun adminDeleteAnnouncement(id: String) { requireAdmin(); news.remove(id); emitExtras() }
    override suspend fun adminLog(): List<LogEntry> { requireAdmin(); return log.reversed() }

    /** Test setup: publish announcements/bans that were put in the maps directly. */
    fun refreshExtras() = emitExtras()

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
            activeReaders = (29 downTo 0).map { back -> logs.values.count { m -> (m[today.minusDays(back.toLong())]?.minutes ?: 0) > 0 } },
            topBooks = all.values.flatMap { d -> d.challenges.map { it.book.title to d.group.id } }.groupBy({ it.first }, { it.second }).map { (t, g) -> t to g.distinct().size }.sortedByDescending { it.second },
            catalogBooks = books.values.count { it.status != BookStatus.MERGED },
            pendingBooks = books.values.count { it.status == BookStatus.PENDING },
            openSuggestions = suggestions.values.count { it.status == SuggestionStatus.OPEN },
            banned = bannedSet.toSet(),
        )
    }
}
