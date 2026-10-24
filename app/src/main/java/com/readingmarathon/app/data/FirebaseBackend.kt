package com.readingmarathon.app.data

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.gms.tasks.Task
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.AuthCredential
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.AggregateSource
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.WriteBatch
import com.readingmarathon.core.BookBrief
import com.readingmarathon.core.Challenge
import com.readingmarathon.core.ShelfStatus
import com.readingmarathon.core.ShelfBook
import com.readingmarathon.core.Shelf
import com.readingmarathon.core.Read
import com.readingmarathon.core.Text
import com.readingmarathon.core.SuggestionType
import com.readingmarathon.core.SuggestionStatus
import com.readingmarathon.core.Suggestion
import com.readingmarathon.core.CatalogBook
import com.readingmarathon.core.Catalog
import com.readingmarathon.core.BookStatus
import com.readingmarathon.core.ChallengeStatus
import com.readingmarathon.core.Day
import com.readingmarathon.core.Entry
import com.readingmarathon.core.Finish
import com.readingmarathon.core.Group
import com.readingmarathon.core.GroupData
import com.readingmarathon.core.Export
import com.readingmarathon.core.Ids
import com.readingmarathon.core.Limits
import com.readingmarathon.core.Member
import com.readingmarathon.core.Role
import com.readingmarathon.core.RuleViolation
import com.readingmarathon.core.Rules
import com.readingmarathon.core.Validation
import com.readingmarathon.core.Visibility
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.tasks.await
import java.time.LocalDate

/** Firebase: Google sign-in, Firestore for everything else. Schema in [Docs]. */
class FirebaseBackend(private val context: Context, override val configured: Boolean) : Backend {

    private val auth: FirebaseAuth? = if (configured && FirebaseApp.getApps(context).isNotEmpty()) FirebaseAuth.getInstance() else null
    private val db: FirebaseFirestore? = auth?.let { FirebaseFirestore.getInstance() }
    private val bundled = BundledCatalog(context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _account = MutableStateFlow<Account?>(null)
    override val account: StateFlow<Account?> = _account
    private val _restoring = MutableStateFlow(auth != null)
    override val restoring: StateFlow<Boolean> = _restoring
    private val _profile = MutableStateFlow<Profile?>(null)
    override val profile: StateFlow<Profile?> = _profile
    private val _days = MutableStateFlow<List<Day>>(emptyList())
    override val days: StateFlow<List<Day>> = _days
    private val _reads = MutableStateFlow<List<Read>>(emptyList())
    override val reads: StateFlow<List<Read>> = _reads
    private val _shelf = MutableStateFlow<List<ShelfBook>>(emptyList())
    override val shelf: StateFlow<List<ShelfBook>> = _shelf
    private var readsLoaded = false
    private var shelfLoaded = false
    private val _groups = MutableStateFlow<Map<String, GroupData>>(emptyMap())
    override val groups: StateFlow<Map<String, GroupData>> = _groups

    private val _announcements = MutableStateFlow<List<Announcement>>(emptyList())
    override val announcements: StateFlow<List<Announcement>> = _announcements
    private val _banned = MutableStateFlow(false)
    override val banned: StateFlow<Boolean> = _banned

    private var uidLoaded: String? = null
    private val userListeners = mutableListOf<ListenerRegistration>()
    private val watchers = HashMap<String, GroupWatcher>()
    private val peeked = HashSet<String>()

    init {
        // Read the bundled catalog off the main thread, before the first search needs it.
        scope.launch { bundled.books }
        auth?.addAuthStateListener { a ->
            val user = a.currentUser
            _account.value = user?.toAccount()
            _restoring.value = false
            if (user?.uid != uidLoaded) load(user?.uid)
            if (isAdmin()) seedCatalog()
        }
    }

    private fun FirebaseUser.toAccount() = Account(
        uid = uid,
        email = email ?: providerData.firstNotNullOfOrNull { it.email },
        name = displayName ?: providerData.firstNotNullOfOrNull { it.displayName },
        isGuest = isAnonymous,
    )

    private fun requireAuth(): FirebaseAuth = auth ?: throw RuleViolation("هذه النسخة من التطبيق غير مهيأة للاتصال بالخادم.")
    private fun requireDb(): FirebaseFirestore = db ?: throw RuleViolation("هذه النسخة من التطبيق غير مهيأة للاتصال بالخادم.")
    private fun uid(): String = auth?.currentUser?.uid ?: throw RuleViolation("سجّل الدخول أولاً.")
    private fun isAdmin(): Boolean = _account.value?.isAdmin == true
    private fun users() = requireDb().collection("users")
    private fun groupRef(id: String) = requireDb().collection("groups").document(id)
    private fun today(): LocalDate = LocalDate.now()

    // ---- Live data ----

    private fun load(uid: String?) {
        userListeners.forEach { it.remove() }
        userListeners.clear()
        watchers.values.forEach { it.stop() }
        watchers.clear()
        peeked.clear()
        _profile.value = null
        _days.value = emptyList()
        _reads.value = emptyList()
        _shelf.value = emptyList()
        readsLoaded = false
        shelfLoaded = false
        _groups.value = emptyMap()
        _announcements.value = emptyList()
        _banned.value = false
        uidLoaded = uid
        val db = db ?: return
        if (uid == null) return
        val me = db.collection("users").document(uid)
        // "Last seen" for the admin panel: once per app start is enough.
        me.set(mapOf("lastSeen" to System.currentTimeMillis()), SetOptions.merge()).addOnFailureListener { Log.w(TAG, "seen", it) }
        userListeners += me.addSnapshotListener { snap, err ->
            if (err != null) { Log.w(TAG, "profile", err); return@addSnapshotListener }
            if (snap == null) return@addSnapshotListener
            // A missing document from the cache only means "not synced yet"; wait for the server.
            if (!snap.exists() && snap.metadata.isFromCache) return@addSnapshotListener
            _profile.value = Docs.profile(snap.data)
            reconcile()
        }
        userListeners += me.collection("days")
            .whereGreaterThanOrEqualTo("date", today().minusDays(Limits.HISTORY_DAYS).toString())
            .addSnapshotListener { s, err ->
                if (err != null) { Log.w(TAG, "days", err); return@addSnapshotListener }
                if (s != null) _days.value = s.documents.mapNotNull { d -> runCatching { Docs.day(d.data.orEmpty()) }.getOrNull() }
            }
        userListeners += me.collection("reads")
            .whereGreaterThanOrEqualTo("date", today().minusDays(Limits.HISTORY_DAYS).toString())
            .addSnapshotListener { s, err ->
                if (err != null) { Log.w(TAG, "reads", err); return@addSnapshotListener }
                if (s != null) { _reads.value = s.documents.mapNotNull { d -> runCatching { Docs.read(d.data.orEmpty()) }.getOrNull() }; readsLoaded = true }
            }
        userListeners += me.collection("shelf").addSnapshotListener { s, err ->
            if (err != null) { Log.w(TAG, "shelf", err); return@addSnapshotListener }
            if (s != null) { _shelf.value = s.documents.map { Docs.shelf(it.id, it.data.orEmpty()) }; shelfLoaded = true; autoShelve() }
        }
        userListeners += db.collection("banned").document(uid).addSnapshotListener { snap, _ ->
            if (snap != null && !snap.metadata.isFromCache) _banned.value = snap.exists()
        }
        userListeners += db.collection("announcements").whereEqualTo("active", true).addSnapshotListener { s, err ->
            if (err != null) { Log.w(TAG, "announcements", err); return@addSnapshotListener }
            if (s != null) _announcements.value = s.documents.map { Docs.announcement(it.id, it.data.orEmpty()) }.sortedByDescending { it.createdAt }
        }
    }

    /** Watches exactly the reader's groups plus any peeked ones. */
    private fun reconcile() {
        val wanted = _profile.value?.groups.orEmpty().toSet() + peeked
        (watchers.keys - wanted).forEach { id ->
            watchers.remove(id)?.stop()
            _groups.value = _groups.value - id
        }
        (wanted - watchers.keys).forEach { id -> watchers[id] = GroupWatcher(id) }
    }

    override fun watch(groupId: String) {
        peeked += groupId
        reconcile()
    }

    override fun unwatch(groupId: String) {
        peeked -= groupId
        reconcile()
    }

    /** The group was deleted, or the reader was removed from it: drop it from their list. */
    private fun gone(groupId: String) {
        val uid = uidLoaded ?: return
        if (groupId in _profile.value?.groups.orEmpty()) {
            users().document(uid).update("groups", FieldValue.arrayRemove(groupId))
                .addOnFailureListener { Log.w(TAG, "prune $groupId", it) }
        }
        peeked -= groupId
        watchers.remove(groupId)?.stop()
        _groups.value = _groups.value - groupId
    }

    private inner class GroupWatcher(val id: String) {
        private var group: Group? = null
        private var members: List<Member>? = null
        private var challenges: List<Challenge>? = null
        private var entries: List<Entry>? = null
        private var finishes: List<Finish>? = null
        private val regs = mutableListOf<ListenerRegistration>()

        init {
            val root = groupRef(id)
            regs += root.addSnapshotListener { snap, err ->
                if (err != null) {
                    if (err.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) gone(id)
                    return@addSnapshotListener
                }
                if (snap == null) return@addSnapshotListener
                if (!snap.exists()) { if (!snap.metadata.isFromCache) gone(id); return@addSnapshotListener }
                val g = Docs.group(id, snap.data.orEmpty())
                // Removed by an admin: the group still exists, but no longer lists this account.
                val me = uidLoaded
                if (me != null && me !in g.roles && id !in peeked && !isAdmin() && !snap.metadata.hasPendingWrites()) { gone(id); return@addSnapshotListener }
                group = g
                publish()
            }
            regs += root.collection("members").addSnapshotListener { s, _ ->
                if (s != null) { members = s.documents.map { Docs.member(it.id, it.data.orEmpty()) }; publish() }
            }
            regs += root.collection("challenges").addSnapshotListener { s, _ ->
                if (s != null) { challenges = s.documents.mapNotNull { runCatching { Docs.challenge(it.id, it.data.orEmpty()) }.getOrNull() }; publish() }
            }
            regs += root.collection("entries")
                .whereGreaterThanOrEqualTo("date", today().minusDays(Limits.HISTORY_DAYS).toString())
                .addSnapshotListener { s, _ ->
                    if (s != null) { entries = s.documents.mapNotNull { runCatching { Docs.entry(it.data.orEmpty()) }.getOrNull() }; publish() }
                }
            regs += root.collection("finishes").addSnapshotListener { s, _ ->
                if (s != null) { finishes = s.documents.mapNotNull { runCatching { Docs.finish(it.data.orEmpty()) }.getOrNull() }; publish() }
            }
        }

        private fun publish() {
            val d = GroupData(group ?: return, members ?: return, challenges ?: return, entries ?: return, finishes ?: return)
            if (id in watchers) _groups.value = _groups.value + (id to d)
            autoShelve()
        }

        fun stop() {
            regs.forEach { it.remove() }
            regs.clear()
        }
    }

    private fun loaded(groupId: String): GroupData =
        _groups.value[groupId] ?: throw RuleViolation("لم تُحمَّل المجموعة بعد. تحقق من الاتصال.")

    // ---- Accounts ----

    /** Asks the phone for a Google account; null when the reader cancels. */
    private suspend fun googleCredential(activity: Activity): AuthCredential? {
        val clientId = activity.resources.getIdentifier("default_web_client_id", "string", activity.packageName)
            .takeIf { it != 0 }?.let(activity::getString)
            ?: throw RuleViolation("الدخول بحساب Google غير مفعّل في هذه النسخة.")
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(GetSignInWithGoogleOption.Builder(clientId).build())
            .build()
        val result = try {
            CredentialManager.create(activity).getCredential(activity, request)
        } catch (e: GetCredentialCancellationException) {
            return null
        } catch (e: NoCredentialException) {
            throw RuleViolation("لا يوجد حساب Google على هذا الهاتف. أضف حساباً من إعدادات الهاتف.")
        }
        val c = result.credential
        if (c !is CustomCredential || c.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            throw RuleViolation("تعذّر الدخول بحساب Google.")
        }
        return GoogleAuthProvider.getCredential(GoogleIdTokenCredential.createFrom(c.data).idToken, null)
    }

    override suspend fun signInWithGoogle(activity: Activity) {
        val a = requireAuth()
        val credential = googleCredential(activity) ?: return
        val current = a.currentUser
        if (current != null && current.isAnonymous) {
            // An old guest account keeps everything by linking; if this Google account already exists, switch to it.
            try {
                current.linkWithCredential(credential).await()
            } catch (e: FirebaseAuthUserCollisionException) {
                a.signInWithCredential(credential).await()
            }
        } else {
            a.signInWithCredential(credential).await()
        }
        a.currentUser?.reload()?.await()
        // Fresh token, so the security rules see the newly linked email.
        runCatching { a.currentUser?.getIdToken(true)?.await() }
        a.currentUser?.let { _account.value = it.toAccount(); touchUser(it) }
    }

    override suspend fun signOut() {
        auth?.signOut()
        runCatching { CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest()) }
    }

    override suspend fun exportMyData(): MyData {
        val me = users().document(uid())
        // The whole history from the server, not just the year the app keeps loaded.
        val reads = me.collection("reads").get().await().documents.mapNotNull { runCatching { Docs.read(it.data.orEmpty()) }.getOrNull() }
        val shelf = me.collection("shelf").get().await().documents.map { Docs.shelf(it.id, it.data.orEmpty()) }
        val titles = bundled.books.associate { it.id to it.title }
        return MyData(Export.myReads(reads, shelf) { titles[it] }, Export.myShelf(shelf))
    }

    override suspend fun deleteAccount(activity: Activity) {
        val a = requireAuth()
        val user = a.currentUser ?: throw RuleViolation("سجّل الدخول أولاً.")
        // Google confirms it is really the owner, before anything is deleted.
        val credential = googleCredential(activity) ?: throw RuleViolation("لم يُحذف شيء.")
        try {
            user.reauthenticate(credential).await()
        } catch (e: FirebaseAuthException) {
            throw RuleViolation("اختر حساب Google نفسه الذي تدخل به، ثم أعد المحاولة. لم يُحذف شيء.")
        }
        val me = user.uid
        val db = requireDb()
        val mine = users().document(me)
        // Every group the reader is in, or ever was in (their past reading stays there otherwise).
        val profile = mine.get(Source.SERVER).await()
        val gids = (listOf("groups", "joined").flatMap { (profile.get(it) as? List<*>).orEmpty() }.filterIsInstance<String>() + _profile.value?.groups.orEmpty()).distinct()
        for (gid in gids) {
            val g = groupRef(gid)
            val snap = runCatching { g.get(Source.SERVER).await() }.getOrNull() ?: continue
            if (!snap.exists()) continue
            val group = Docs.group(gid, snap.data.orEmpty())
            val role = group.roleOf(me)
            if (role == Role.OWNER) {
                val heir = group.roles.entries.filter { it.key != me }.sortedBy { if (it.value == Role.ADMIN) 0 else 1 }.firstOrNull()?.key
                if (heir == null) { deleteGroupFully(gid, group.inviteCode); continue }
                // The group goes on: ownership passes to an admin (or a member), then the reader leaves it.
                g.update(mapOf("roles.$heir" to "owner", "roles.$me" to "admin")).await()
            }
            val writes = mutableListOf<(WriteBatch) -> Unit>()
            g.collection("entries").whereEqualTo("memberId", me).get().await().documents.forEach { d -> writes += { b -> b.delete(d.reference) } }
            g.collection("finishes").whereEqualTo("memberId", me).get().await().documents.forEach { d -> writes += { b -> b.delete(d.reference) } }
            writes += { b -> b.delete(g.collection("members").document(me)) }
            commitAll(writes)
            if (role != null) g.update("roles.$me", FieldValue.delete()).await()
        }
        val writes = mutableListOf<(WriteBatch) -> Unit>()
        for (sub in listOf("reads", "shelf", "days")) mine.collection(sub).get().await().documents.forEach { d -> writes += { b -> b.delete(d.reference) } }
        db.collection("suggestions").whereEqualTo("by", me).get().await().documents.forEach { d -> writes += { b -> b.delete(d.reference) } }
        writes += { b -> b.delete(mine) }
        commitAll(writes)
        user.delete().await()
        runCatching { CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest()) }
    }

    /** Keeps users/{uid} current: email and guest flag follow the account; the name is the reader's choice. */
    private suspend fun touchUser(user: FirebaseUser) {
        val acc = user.toAccount()
        val ref = users().document(user.uid)
        val now = System.currentTimeMillis()
        val exists = runCatching { ref.get().await().exists() }.getOrDefault(true)
        val fields = mutableMapOf<String, Any?>("email" to acc.email, "guest" to acc.isGuest, "lastSeen" to now)
        if (!exists) { fields["createdAt"] = now; fields["groups"] = emptyList<String>(); fields["goal"] = 30 }
        runCatching { ref.set(fields, SetOptions.merge()).await() }.onFailure { Log.w(TAG, "touch", it) }
    }

    override suspend fun saveProfile(name: String, goal: Int) {
        Validation.personName(name)
        if (goal !in 5..600) throw RuleViolation("الهدف اليومي بين 5 و600 دقيقة.")
        val me = uid()
        val acc = _account.value
        val db = requireDb()
        val batch = db.batch()
        batch.set(
            users().document(me),
            mapOf("name" to name.trim(), "goal" to goal, "email" to acc?.email, "guest" to (acc?.isGuest ?: true), "lastSeen" to System.currentTimeMillis()),
            SetOptions.merge(),
        )
        // The group lists show the same name.
        _profile.value?.groups.orEmpty().forEach { gid ->
            if (_groups.value[gid]?.member(me)?.name != name.trim()) batch.update(groupRef(gid).collection("members").document(me), "name", name.trim())
        }
        batch.commit().await()
    }

    // ---- The reader's log ----

    private fun shelfRef(bookId: String) = users().document(uid()).collection("shelf").document(bookId)

    /** The reads of one day, from the live list or (before it has loaded, e.g. from a notification) from the server. */
    private suspend fun readsOn(date: LocalDate): List<Read> =
        if (readsLoaded) _reads.value.filter { it.date == date }
        else runCatching {
            users().document(uid()).collection("reads").whereEqualTo("date", date.toString()).get().await()
                .documents.mapNotNull { runCatching { Docs.read(it.data.orEmpty()) }.getOrNull() }
        }.getOrDefault(emptyList())

    private suspend fun shelfNow(): List<ShelfBook> =
        if (shelfLoaded) _shelf.value
        else runCatching { users().document(uid()).collection("shelf").get().await().documents.map { Docs.shelf(it.id, it.data.orEmpty()) } }.getOrDefault(emptyList())

    override suspend fun setRead(date: LocalDate, bookId: String, minutes: Int) {
        val me = uid()
        val now = System.currentTimeMillis()
        val day = readsOn(date)
        val before = day.firstOrNull { it.bookId == bookId }?.minutes ?: 0
        val after = day.filter { it.bookId != bookId } + Read(date, bookId, minutes, now)
        Validation.day(date, after.sumOf { it.minutes }, today())
        if (minutes < 0) throw RuleViolation("الدقائق لا تكون سالبة.")
        val mine = users().document(me)
        // Not awaited: the local cache updates at once and syncs when online.
        fun Task<*>.logged(what: String) = addOnFailureListener { Log.w(TAG, what, it) }
        val readRef = mine.collection("reads").document(Read.readId(date, bookId))
        (if (minutes == 0) readRef.delete() else readRef.set(Docs.readMap(Read(date, bookId, minutes, now)))).logged("read")
        val total = after.sumOf { it.minutes }
        mine.collection("days").document(date.toString()).set(Docs.dayMap(Day(date, total, now))).logged("day")
        if (minutes != before) {
            shelfRef(bookId).set(mapOf("minutes" to FieldValue.increment((minutes - before).toLong()), "lastReadAt" to now), SetOptions.merge()).logged("shelf")
        }
        val reads = after.filter { it.minutes > 0 }
        for (gid in groupIds()) {
            val challenges = _groups.value[gid]?.challenges ?: runCatching {
                groupRef(gid).collection("challenges").get().await().documents.mapNotNull { runCatching { Docs.challenge(it.id, it.data.orEmpty()) }.getOrNull() }
            }.getOrDefault(emptyList())
            val e = Shelf.groupEntry(me, date, reads, challenges, now)
            val ref = groupRef(gid).collection("entries").document(Entry.entryId(me, date))
            (if (e.minutes == 0) ref.delete() else ref.set(Docs.entryMap(e))).logged("entry $gid")
        }
    }

    override suspend fun addRead(date: LocalDate, bookId: String?, minutes: Int): Pair<ShelfBook, Int> {
        val shelf = shelfNow()
        val book = (if (bookId != null) shelf.firstOrNull { it.bookId == bookId } else Shelf.reading(shelf).firstOrNull())
            ?: throw RuleViolation("اختر الكتاب الذي تقرؤه أولاً.")
        val current = readsOn(date).firstOrNull { it.bookId == book.bookId }?.minutes ?: 0
        val dayTotal = readsOn(date).sumOf { it.minutes }
        val add = minutes.coerceAtMost(Limits.MAX_MINUTES_PER_DAY - dayTotal)
        if (add <= 0) throw RuleViolation("وصلت إلى 16 ساعة في هذا اليوم.")
        setRead(date, book.bookId, current + add)
        return book to current + add
    }

    override suspend fun shelve(book: CatalogBook, status: ShelfStatus) {
        val now = System.currentTimeMillis()
        val existing = shelfNow().firstOrNull { it.bookId == book.id }
        val fields = mutableMapOf<String, Any>("title" to book.title, "author" to book.author, "status" to status.name.lowercase())
        if (existing == null) { fields["startedAt"] = now; fields["minutes"] = 0; fields["lastReadAt"] = 0L; fields["finishedAt"] = 0L }
        shelfRef(book.id).set(fields, SetOptions.merge()).await()
        if (status == ShelfStatus.FINISHED) setShelfStatus(book.id, ShelfStatus.FINISHED)
    }

    override suspend fun setShelfStatus(bookId: String, status: ShelfStatus) {
        val me = uid()
        val today = today()
        shelfRef(bookId).set(
            mapOf("status" to status.name.lowercase(), "finishedAt" to if (status == ShelfStatus.FINISHED) System.currentTimeMillis() else 0L),
            SetOptions.merge(),
        ).await()
        // Finishing (or un-finishing) a book counts in every group reading it as its shared book.
        for (d in _groups.value.values) {
            if (d.member(me)?.active != true) continue
            val c = d.challenges.firstOrNull { it.bookId == bookId && it.acceptsFinishOn(today) } ?: continue
            val ref = groupRef(d.group.id).collection("finishes").document(Finish.finishId(me, c.id))
            runCatching {
                if (status == ShelfStatus.FINISHED) { if (d.finished(me, c.id) == null) ref.set(Docs.finishMap(Finish(me, c.id, today))).await() }
                else if (d.finished(me, c.id) != null) ref.delete().await()
            }.onFailure { Log.w(TAG, "finish", it) }
        }
    }

    override suspend fun removeFromShelf(bookId: String) {
        shelfRef(bookId).delete().await()
    }

    /** The book a group is reading now goes on each member's shelf by itself. */
    private val autoShelved = HashSet<String>()
    private fun autoShelve() {
        val me = uidLoaded ?: return
        if (!shelfLoaded) return
        val have = _shelf.value.map { it.bookId }.toSet()
        val today = today()
        for (d in _groups.value.values) {
            if (d.member(me)?.active != true) continue
            val c = d.challenges.firstOrNull { it.status(today) == ChallengeStatus.ACTIVE } ?: continue
            val bid = c.bookId ?: continue
            if (bid in have || !autoShelved.add(bid)) continue
            val now = System.currentTimeMillis()
            users().document(me).collection("shelf").document(bid).set(
                mapOf("title" to c.book.title, "author" to c.book.author, "status" to "reading", "minutes" to 0, "startedAt" to now, "lastReadAt" to 0L, "finishedAt" to 0L),
                SetOptions.merge(),
            ).addOnFailureListener { Log.w(TAG, "autoShelve", it) }
        }
    }

    private suspend fun groupIds(): List<String> = _profile.value?.groups
        ?: runCatching { Docs.profile(users().document(uid()).get().await().data).groups }.getOrDefault(emptyList())

    // ---- Groups ----

    override suspend fun createGroup(name: String, about: String, visibility: Visibility): String {
        Validation.group(name, about, Rules.DEFAULT)
        val me = uid()
        val profile = _profile.value ?: throw RuleViolation("لم يكتمل تحميل حسابك بعد.")
        if (profile.groups.size >= Limits.MAX_GROUPS) throw RuleViolation("وصلت إلى الحد الأقصى (${Limits.MAX_GROUPS} مجموعة).")
        val db = requireDb()
        val id = Ids.newId("g_")
        val code = Ids.inviteCode()
        val now = System.currentTimeMillis()
        db.batch()
            .set(
                groupRef(id),
                mapOf(
                    "name" to name.trim(), "about" to about.trim(), "visibility" to Docs.visibilityName(visibility),
                    "inviteCode" to code, "roles" to mapOf(me to "owner"), "rules" to Docs.rulesMap(Rules.DEFAULT),
                    "createdAt" to now, "current" to null,
                ),
            )
            .set(db.collection("invites").document(code), mapOf("groupId" to id, "createdBy" to me))
            .commit().await()
        db.batch()
            .set(groupRef(id).collection("members").document(me), mapOf("name" to profile.name, "linkedUid" to me, "active" to true, "joinedAt" to now))
            .set(users().document(me), mapOf("groups" to FieldValue.arrayUnion(id), "joined" to FieldValue.arrayUnion(id)), SetOptions.merge())
            .commit().await()
        return id
    }

    override suspend fun findByCode(code: String): GroupPreview {
        val c = Ids.normalizeCode(code)
        if (c.length != 6) throw RuleViolation("رمز الدعوة 6 أحرف وأرقام.")
        val invite = requireDb().collection("invites").document(c).get().await()
        val id = invite.getString("groupId") ?: throw RuleViolation("لا توجد مجموعة بهذا الرمز. تأكد منه مع من دعاك.")
        val snap = groupRef(id).get().await()
        if (!snap.exists() || snap.getString("inviteCode") != c) throw RuleViolation("انتهت صلاحية هذا الرمز. اطلب رمزاً جديداً.")
        return Docs.preview(id, snap.data.orEmpty())
    }

    override suspend fun publicGroups(): List<GroupPreview> =
        requireDb().collection("groups").whereEqualTo("visibility", "public").limit(50).get().await()
            .documents.map { Docs.preview(it.id, it.data.orEmpty()) }
            .sortedByDescending { it.memberCount }

    override suspend fun joinGroup(groupId: String, code: String?) {
        val me = uid()
        val profile = _profile.value ?: throw RuleViolation("لم يكتمل تحميل حسابك بعد.")
        val db = requireDb()
        val snap = groupRef(groupId).get().await()
        if (!snap.exists()) throw RuleViolation("لم تعد هذه المجموعة موجودة.")
        val group = Docs.group(groupId, snap.data.orEmpty())
        if (me !in group.roles) {
            if (profile.groups.size >= Limits.MAX_GROUPS) throw RuleViolation("وصلت إلى الحد الأقصى (${Limits.MAX_GROUPS} مجموعة).")
            if (group.visibility == Visibility.PRIVATE && Ids.normalizeCode(code.orEmpty()) != group.inviteCode) {
                throw RuleViolation("هذه مجموعة خاصة، تحتاج رمز دعوة صالحاً.")
            }
            val member = mutableMapOf<String, Any?>("name" to profile.name, "linkedUid" to me, "active" to true, "joinedAt" to System.currentTimeMillis())
            if (group.visibility == Visibility.PRIVATE) member["code"] = group.inviteCode
            db.batch()
                .set(groupRef(groupId).collection("members").document(me), member)
                .update(groupRef(groupId), "roles.$me", "member")
                .set(users().document(me), mapOf("groups" to FieldValue.arrayUnion(groupId), "joined" to FieldValue.arrayUnion(groupId)), SetOptions.merge())
                .commit().await()
            backfill(groupId)
        } else if (groupId !in profile.groups) {
            users().document(me).set(mapOf("groups" to FieldValue.arrayUnion(groupId)), SetOptions.merge()).await()
        }
    }

    /** A reader joining mid-challenge brings the days they already logged for it. */
    private suspend fun backfill(groupId: String) = runCatching {
        val me = uid()
        val today = today()
        val challenges = groupRef(groupId).collection("challenges").get().await().documents
            .mapNotNull { runCatching { Docs.challenge(it.id, it.data.orEmpty()) }.getOrNull() }
        val c = challenges.firstOrNull { it.status(today) == ChallengeStatus.ACTIVE } ?: return@runCatching
        val batch = requireDb().batch()
        val reads = _reads.value.filter { it.minutes > 0 && c.contains(it.date) && !it.date.isAfter(today) }
        val now = System.currentTimeMillis()
        reads.map { it.date }.distinct().forEach { date ->
            val e = Shelf.groupEntry(me, date, reads, challenges, now)
            batch.set(groupRef(groupId).collection("entries").document(e.id), Docs.entryMap(e))
        }
        if (reads.isNotEmpty()) batch.commit().await()
    }.onFailure { Log.w(TAG, "backfill", it) }

    override suspend fun leaveGroup(groupId: String) {
        val me = uid()
        val d = loaded(groupId)
        if (d.group.roleOf(me) == Role.OWNER) {
            if (d.group.memberCount > 1) throw RuleViolation("أنت مالك المجموعة. انقل الملكية إلى عضو آخر أولاً، أو احذف المجموعة.")
            deleteGroup(groupId)
            return
        }
        requireDb().batch()
            .update(groupRef(groupId), "roles.$me", FieldValue.delete())
            .update(groupRef(groupId).collection("members").document(me), "active", false)
            .update(users().document(me), "groups", FieldValue.arrayRemove(groupId))
            .commit().await()
    }

    // ---- Group admins ----

    override suspend fun updateGroup(groupId: String, name: String, about: String, visibility: Visibility, rules: Rules) {
        val d = loaded(groupId)
        Checks.manager(d, uid(), isAdmin())
        Validation.group(name, about, rules)
        groupRef(groupId).update(
            mapOf("name" to name.trim(), "about" to about.trim(), "visibility" to Docs.visibilityName(visibility), "rules" to Docs.rulesMap(rules)),
        ).await()
    }

    override suspend fun newInviteCode(groupId: String) {
        val d = loaded(groupId)
        Checks.manager(d, uid(), isAdmin())
        val db = requireDb()
        val code = Ids.inviteCode()
        val batch = db.batch()
            .set(db.collection("invites").document(code), mapOf("groupId" to groupId, "createdBy" to uid()))
            .update(groupRef(groupId), "inviteCode", code)
        if (d.group.inviteCode.isNotBlank()) batch.delete(db.collection("invites").document(d.group.inviteCode))
        batch.commit().await()
    }

    /** The group card shows the current book; refresh it whenever the books change. */
    private fun briefAfter(challenges: List<Challenge>) =
        GroupData(Group("", ""), challenges = challenges).currentChallenge(today())?.let { BookBrief(it.book.title, it.book.author, it.start, it.end) }

    override suspend fun saveChallenge(groupId: String, challenge: Challenge) {
        val d = loaded(groupId)
        Checks.manager(d, uid(), isAdmin())
        val c = challenge.copy(book = challenge.book.copy(title = challenge.book.title.trim(), author = challenge.book.author.trim()))
        Checks.challenge(d, c)
        val after = d.challenges.filter { it.id != c.id } + c
        requireDb().batch()
            .set(groupRef(groupId).collection("challenges").document(c.id), Docs.challengeMap(c))
            .update(groupRef(groupId), "current", Docs.briefMap(briefAfter(after)))
            .commit().await()
    }

    override suspend fun deleteChallenge(groupId: String, challengeId: String) {
        val d = loaded(groupId)
        Checks.manager(d, uid(), isAdmin())
        val batch = requireDb().batch()
        d.finishes.filter { it.challengeId == challengeId }.forEach { batch.delete(groupRef(groupId).collection("finishes").document(it.id)) }
        batch.delete(groupRef(groupId).collection("challenges").document(challengeId))
        batch.update(groupRef(groupId), "current", Docs.briefMap(briefAfter(d.challenges.filter { it.id != challengeId })))
        batch.commit().await()
    }

    override suspend fun setFinished(groupId: String, challengeId: String, date: LocalDate?) {
        val d = loaded(groupId)
        val me = uid()
        val memberId = me
        val c = d.challenge(challengeId) ?: throw RuleViolation("لم يعد هذا التحدي موجوداً.")
        val ref = groupRef(groupId).collection("finishes").document(Finish.finishId(memberId, challengeId))
        if (date == null) {
            ref.delete().await()
        } else {
            Validation.finish(c, date)
            ref.set(Docs.finishMap(Finish(memberId, challengeId, date))).await()
        }
        // The reader's shelf follows: finished here means finished there.
        val bid = c.bookId
        if (bid != null) {
            runCatching {
                shelfRef(bid).set(
                    mapOf(
                        "title" to c.book.title, "author" to c.book.author,
                        "status" to if (date == null) "reading" else "finished",
                        "finishedAt" to if (date == null) 0L else System.currentTimeMillis(),
                    ),
                    SetOptions.merge(),
                ).await()
            }.onFailure { Log.w(TAG, "shelf finish", it) }
        }
    }

    override suspend fun setRole(groupId: String, uid: String, role: Role) {
        val d = loaded(groupId)
        val me = uid()
        Checks.owner(d, me, isAdmin())
        if (uid !in d.group.roles) throw RuleViolation("هذا العضو لا يستخدم التطبيق.")
        if (uid == me) throw RuleViolation("لا يمكنك تغيير دورك بنفسك.")
        val updates = mutableMapOf<String, Any>("roles.$uid" to Docs.roleName(role))
        // Handing over ownership: the old owner stays as an admin.
        if (role == Role.OWNER) d.group.roles.filterValues { it == Role.OWNER }.keys.forEach { updates["roles.$it"] = "admin" }
        groupRef(groupId).update(updates).await()
    }

    override suspend fun removeMember(groupId: String, memberId: String) {
        val d = loaded(groupId)
        val me = uid()
        if (memberId == me) throw RuleViolation("لمغادرة المجموعة استخدم «مغادرة المجموعة».")
        Checks.canRemove(d, memberId, me, isAdmin())
        val batch = requireDb().batch()
        if (memberId in d.group.roles) batch.update(groupRef(groupId), "roles.$memberId", FieldValue.delete())
        batch.update(groupRef(groupId).collection("members").document(memberId), "active", false)
        batch.commit().await()
    }

    override suspend fun deleteGroup(groupId: String) {
        val d = loaded(groupId)
        Checks.owner(d, uid(), isAdmin())
        deleteGroupFully(groupId, d.group.inviteCode)
        users().document(uid()).set(mapOf("groups" to FieldValue.arrayRemove(groupId)), SetOptions.merge()).await()
    }

    /** Everything the group holds, including history older than what the app loads. */
    private suspend fun deleteGroupFully(groupId: String, inviteCode: String) {
        val db = requireDb()
        val root = groupRef(groupId)
        val writes = mutableListOf<(WriteBatch) -> Unit>()
        for (sub in listOf("members", "challenges", "entries", "finishes")) {
            root.collection(sub).get().await().documents.forEach { doc -> writes += { b -> b.delete(doc.reference) } }
        }
        if (inviteCode.isNotBlank()) writes += { b -> b.delete(db.collection("invites").document(inviteCode)) }
        commitAll(writes)
        root.delete().await()
    }

    /** Firestore batches hold at most 500 writes. */
    private suspend fun commitAll(writes: List<(WriteBatch) -> Unit>) {
        writes.chunked(450).forEach { chunk ->
            val b = requireDb().batch()
            chunk.forEach { it(b) }
            b.commit().await()
        }
    }

    // ---- Platform admin ----

    override suspend fun adminOverview(): AdminOverview = coroutineScope {
        if (!isAdmin()) throw RuleViolation("هذه اللوحة لمدير المنصة فقط.")
        val db = requireDb()
        val groupsQ = async { db.collection("groups").get().await() }
        val usersQ = async { db.collection("users").get().await() }
        val membersQ = async { db.collectionGroup("members").get().await() }
        val entriesQ = async { db.collectionGroup("entries").get().await() }
        val daysQ = async { db.collectionGroup("days").get().await() }
        val challengesQ = async { db.collectionGroup("challenges").get().await() }
        val finishesQ = async { db.collectionGroup("finishes").get().await() }
        val booksAll = async { db.collection("books").count().get(AggregateSource.SERVER).await().count }
        val booksMerged = async { db.collection("books").whereEqualTo("status", "merged").count().get(AggregateSource.SERVER).await().count }
        val booksPending = async { db.collection("books").whereEqualTo("status", "pending").count().get(AggregateSource.SERVER).await().count }
        val openQ = async { db.collection("suggestions").whereEqualTo("status", "open").get().await() }
        val bannedQ = async { db.collection("banned").get().await() }

        val today = today()
        val weekAgo = today.minusDays(6).toString()
        val monthAgo = today.minusDays(29)
        fun parentId(ref: DocumentReference) = ref.path.split("/").getOrNull(1).orEmpty() // groups|users/{id}/sub/{doc}

        data class Row(val owner: String, val date: String, val minutes: Long)
        val days = daysQ.await().documents.map { Row(parentId(it.reference), it.getString("date").orEmpty(), it.getLong("minutes") ?: 0) }
        val daysBy = days.groupBy { it.owner }
        val users = usersQ.await().documents.map { d ->
            AdminUser(
                uid = d.id,
                email = d.getString("email"),
                name = d.getString("name"),
                isGuest = d.getBoolean("guest") ?: false,
                createdAt = d.getLong("createdAt") ?: 0,
                lastSeen = d.getLong("lastSeen") ?: 0,
                groups = (d.get("groups") as? List<*>)?.size ?: 0,
                minutes = daysBy[d.id].orEmpty().sumOf { it.minutes },
            )
        }
        val label = users.associate { it.uid to (it.email ?: it.name ?: "ضيف") }

        val entries = entriesQ.await().documents.map { Row(parentId(it.reference), it.getString("date").orEmpty(), it.getLong("minutes") ?: 0) }
        val entriesBy = entries.groupBy { it.owner }
        val members = membersQ.await().documents.groupBy { parentId(it.reference) }
        val challengeDocs = challengesQ.await().documents
        val challenges = challengeDocs.groupingBy { parentId(it.reference) }.eachCount()
        // Books chosen by the most groups (by catalog id when there is one, else by title).
        val topBooks = challengeDocs.groupBy { it.getString("bookId") ?: Text.normalize(it.getString("title").orEmpty()) }
            .map { (_, list) -> list.first().getString("title").orEmpty() to list.map { parentId(it.reference) }.distinct().size }
            .sortedByDescending { it.second }.take(10)

        val groups = groupsQ.await().documents.map { d ->
            val g = Docs.group(d.id, d.data.orEmpty())
            val mine = entriesBy[d.id].orEmpty()
            AdminGroup(
                id = g.id,
                name = g.name.ifBlank { "—" },
                visibility = g.visibility,
                inviteCode = g.inviteCode,
                createdAt = g.createdAt,
                owners = g.roles.filterValues { it == Role.OWNER }.keys.map { label[it] ?: "حساب محذوف" },
                accounts = g.memberCount,
                readers = members[d.id].orEmpty().count { it.getBoolean("active") != false },
                challenges = challenges[d.id] ?: 0,
                minutes = mine.sumOf { it.minutes },
                minutesLast7 = mine.filter { it.date >= weekAgo }.sumOf { it.minutes },
                lastActivity = mine.filter { it.minutes > 0 }.maxOfOrNull { it.date },
            )
        }.sortedWith(compareByDescending<AdminGroup> { it.lastActivity ?: "" }.thenByDescending { it.createdAt })

        // Platform totals come from personal logs: they count every reader once, in a group or not.
        val byDay = days.groupBy { it.date }.mapValues { (_, v) -> v.sumOf { it.minutes } }
        AdminOverview(
            groups = groups,
            users = users.sortedByDescending { it.lastSeen },
            totalMinutes = days.sumOf { it.minutes },
            minutesLast7 = days.filter { it.date >= weekAgo }.sumOf { it.minutes },
            readingDays = days.count { it.minutes > 0 },
            finishedBooks = finishesQ.await().size(),
            daily = (0L until 30L).map { byDay[monthAgo.plusDays(it).toString()] ?: 0L },
            activeReaders = (0L until 30L).map { off -> val d = monthAgo.plusDays(off).toString(); days.count { it.date == d && it.minutes > 0 } },
            topBooks = topBooks,
            catalogBooks = (booksAll.await() - booksMerged.await()).toInt(),
            pendingBooks = booksPending.await().toInt(),
            openSuggestions = openQ.await().size(),
            banned = bannedQ.await().documents.map { it.id }.toSet(),
        )
    }

    private fun requireAdmin() { if (!isAdmin()) throw RuleViolation("هذا لمدير المنصة فقط.") }

    /** Every admin change is written to the audit log (best effort). */
    private fun log(action: String, target: String = "") {
        db?.collection("adminLog")?.add(mapOf("at" to System.currentTimeMillis(), "action" to action, "target" to target))
            ?.addOnFailureListener { Log.w(TAG, "log", it) }
    }

    override suspend fun adminBooks(): List<CatalogBook> {
        requireAdmin()
        return requireDb().collection("books").get().await().documents.map { Docs.book(it.id, it.data.orEmpty()) }.sortedBy { Text.normalize(it.title) }
    }

    /** Groups reading [bookId] get its current title and author. */
    private suspend fun propagate(bookId: String, to: CatalogBook) {
        val db = requireDb()
        val writes = mutableListOf<(WriteBatch) -> Unit>()
        db.collectionGroup("challenges").get().await().documents.filter { it.getString("bookId") == bookId }.forEach { d ->
            writes += { b -> b.update(d.reference, mapOf("bookId" to to.id, "title" to to.title, "author" to to.author)) }
        }
        db.collection("groups").get().await().documents.forEach { g ->
            val brief = Docs.brief(g.get("current"))
            if (brief?.bookId == bookId) writes += { b -> b.update(g.reference, "current", Docs.briefMap(brief.copy(title = to.title, author = to.author, bookId = to.id))) }
        }
        commitAll(writes)
    }

    override suspend fun adminSaveBook(book: CatalogBook): CatalogBook {
        requireAdmin()
        Catalog.validate(book.title, book.author)
        val db = requireDb()
        val b = book.copy(
            id = book.id.ifBlank { Ids.newId("b_") },
            title = book.title.trim(), author = book.author.trim(),
            aliases = book.aliases.map { it.trim() }.filter { it.isNotEmpty() && Text.normalize(it) != Text.normalize(book.title) }.distinctBy { Text.normalize(it) },
            status = if (book.status == BookStatus.MERGED) BookStatus.MERGED else BookStatus.VERIFIED,
            createdBy = book.createdBy ?: uid(), createdAt = book.createdAt.takeIf { it > 0 } ?: System.currentTimeMillis(),
        )
        db.collection("books").document(b.id).set(Docs.bookMap(b)).await()
        if (book.id.isNotBlank()) propagate(b.id, b)
        log(if (book.id.isBlank()) "أضاف كتاباً" else "عدّل كتاباً", "«${b.title}» · ${b.author}")
        return b
    }

    override suspend fun adminDeleteBook(id: String) {
        requireAdmin()
        val db = requireDb()
        val b = db.collection("books").document(id).get().await()
        db.collection("books").document(id).delete().await()
        log("حذف كتاباً", "«${b.getString("title").orEmpty()}»")
    }

    override suspend fun adminMergeBooks(fromId: String, intoId: String) {
        requireAdmin()
        if (fromId == intoId) throw RuleViolation("اختر كتاباً آخر.")
        val db = requireDb()
        val from = db.collection("books").document(fromId).get().await().let { Docs.book(it.id, it.data ?: throw RuleViolation("الكتاب غير موجود.")) }
        val into = db.collection("books").document(intoId).get().await().let { Docs.book(it.id, it.data ?: throw RuleViolation("الكتاب غير موجود.")) }
        val kept = into.copy(aliases = Catalog.mergedAliases(into, from))
        db.batch()
            .set(db.collection("books").document(intoId), Docs.bookMap(kept))
            .set(db.collection("books").document(fromId), Docs.bookMap(from.copy(status = BookStatus.MERGED, mergedInto = intoId)))
            .commit().await()
        propagate(fromId, kept)
        log("دمج كتابين", "«${from.title}» ← «${into.title}»")
    }

    override suspend fun adminImportBooks(books: List<CatalogBook>): Int {
        requireAdmin()
        val existing = adminBooks().toMutableList()
        val now = System.currentTimeMillis()
        val writes = mutableListOf<(WriteBatch) -> Unit>()
        var added = 0
        for (x in books) {
            if (Catalog.findSame(x.title, x.author, existing) != null) continue
            val b = x.copy(id = Ids.newId("b_"), status = BookStatus.VERIFIED, createdBy = uid(), createdAt = now)
            existing += b
            added++
            writes += { batch -> batch.set(requireDb().collection("books").document(b.id), Docs.bookMap(b)) }
        }
        commitAll(writes)
        log("استورد قائمة كتب", "$added كتاباً")
        return added
    }

    override suspend fun adminSuggestions(): List<Suggestion> {
        requireAdmin()
        return requireDb().collection("suggestions").get().await().documents.map { Docs.suggestion(it.id, it.data.orEmpty()) }
            .sortedWith(compareBy<Suggestion> { it.status != SuggestionStatus.OPEN }.thenByDescending { it.createdAt })
    }

    override suspend fun adminResolve(id: String, accepted: Boolean, reply: String) {
        requireAdmin()
        val db = requireDb()
        val sg = db.collection("suggestions").document(id).get().await().let { Docs.suggestion(it.id, it.data ?: throw RuleViolation("الاقتراح غير موجود.")) }
        if (accepted) when (sg.type) {
            // A new book becomes a verified one (the admin edits it first if needed).
            SuggestionType.NEW_BOOK -> sg.bookId?.let { bid ->
                val b = db.collection("books").document(bid).get().await()
                if (b.exists()) db.collection("books").document(bid).update("status", "verified").await()
            }
            SuggestionType.DUPLICATE -> {
                val from = sg.bookId
                val into = sg.otherBookId
                if (from != null && into != null) adminMergeBooks(from, into)
            }
            // A fix is applied by editing the book; accepting records it.
            SuggestionType.FIX -> Unit
        }
        db.collection("suggestions").document(id)
            .update(mapOf("status" to if (accepted) "accepted" else "rejected", "reply" to reply.trim(), "resolvedAt" to System.currentTimeMillis())).await()
        log(if (accepted) "قبل اقتراحاً" else "رفض اقتراحاً", "«${sg.title}» من ${sg.byName}")
    }

    override suspend fun adminUser(uid: String): AdminUserDetail = coroutineScope {
        requireAdmin()
        val db = requireDb()
        val profile = async { db.collection("users").document(uid).get().await() }
        val days = async { db.collection("users").document(uid).collection("days").get().await() }
        val ban = async { db.collection("banned").document(uid).get().await() }
        val shelfQ = async { db.collection("users").document(uid).collection("shelf").get().await() }
        val p = profile.await()
        val ds = days.await().documents.mapNotNull { runCatching { Docs.day(it.data.orEmpty()) }.getOrNull() }.sortedByDescending { it.date }
        val gids = (p.get("groups") as? List<*>)?.filterIsInstance<String>().orEmpty()
        val names = gids.map { gid -> async { gid to (runCatching { db.collection("groups").document(gid).get().await().getString("name") }.getOrNull() ?: "مجموعة محذوفة") } }.map { it.await() }
        AdminUserDetail(
            user = AdminUser(uid, p.getString("email"), p.getString("name"), p.getBoolean("guest") ?: false, p.getLong("createdAt") ?: 0, p.getLong("lastSeen") ?: 0, gids.size, ds.sumOf { it.minutes.toLong() }),
            goal = (p.getLong("goal") ?: 30).toInt(),
            days = ds,
            groups = names,
            shelf = shelfQ.await().documents.map { Docs.shelf(it.id, it.data.orEmpty()) }.sortedByDescending { it.lastReadAt },
            banned = ban.await().exists(),
        )
    }

    override suspend fun adminRenameUser(uid: String, name: String) {
        requireAdmin()
        Validation.personName(name)
        val db = requireDb()
        val gids = (db.collection("users").document(uid).get().await().get("groups") as? List<*>)?.filterIsInstance<String>().orEmpty()
        val batch = db.batch().update(db.collection("users").document(uid), "name", name.trim())
        gids.forEach { gid -> batch.set(groupRef(gid).collection("members").document(uid), mapOf("name" to name.trim()), SetOptions.merge()) }
        batch.commit().await()
        log("غيّر اسم حساب", name.trim())
    }

    override suspend fun adminSetBanned(uid: String, banned: Boolean) {
        requireAdmin()
        if (uid == uid()) throw RuleViolation("لا يمكنك إيقاف حسابك.")
        val ref = requireDb().collection("banned").document(uid)
        if (banned) ref.set(mapOf("at" to System.currentTimeMillis())).await() else ref.delete().await()
        log(if (banned) "أوقف حساباً" else "أعاد تفعيل حساب", uid)
    }

    override suspend fun adminDeleteUserData(uid: String) {
        requireAdmin()
        if (uid == uid()) throw RuleViolation("لا يمكنك حذف بيانات حسابك من هنا.")
        val db = requireDb()
        val user = db.collection("users").document(uid)
        val snap = user.get().await()
        val gids = listOf("groups", "joined").flatMap { (snap.get(it) as? List<*>).orEmpty() }.filterIsInstance<String>().distinct()
        val writes = mutableListOf<(WriteBatch) -> Unit>()
        for (sub in listOf("days", "reads", "shelf")) user.collection(sub).get().await().documents.forEach { d -> writes += { b -> b.delete(d.reference) } }
        for (gid in gids) {
            val g = groupRef(gid)
            val snap = runCatching { g.get().await() }.getOrNull() ?: continue
            if (!snap.exists()) continue
            if (Docs.group(gid, snap.data.orEmpty()).roleOf(uid) == Role.OWNER) {
                throw RuleViolation("هذا الحساب يملك «${snap.getString("name")}». انقل ملكيتها أو احذفها أولاً.")
            }
            writes += { b -> b.update(g, "roles.$uid", FieldValue.delete()) }
            writes += { b -> b.delete(g.collection("members").document(uid)) }
            g.collection("entries").whereEqualTo("memberId", uid).get().await().documents.forEach { d -> writes += { b -> b.delete(d.reference) } }
            g.collection("finishes").whereEqualTo("memberId", uid).get().await().documents.forEach { d -> writes += { b -> b.delete(d.reference) } }
        }
        writes += { b -> b.delete(user) }
        commitAll(writes)
        log("حذف بيانات حساب", uid)
    }

    override suspend fun adminAnnouncements(): List<Announcement> {
        requireAdmin()
        return requireDb().collection("announcements").get().await().documents.map { Docs.announcement(it.id, it.data.orEmpty()) }.sortedByDescending { it.createdAt }
    }

    override suspend fun adminSaveAnnouncement(a: Announcement) {
        requireAdmin()
        if (a.title.isBlank() && a.body.isBlank()) throw RuleViolation("اكتب نص الإعلان.")
        val id = a.id.ifBlank { Ids.newId("a_") }
        requireDb().collection("announcements").document(id)
            .set(mapOf("title" to a.title.trim(), "body" to a.body.trim(), "active" to a.active, "createdAt" to a.createdAt.takeIf { it > 0 }.let { it ?: System.currentTimeMillis() })).await()
        log(if (a.id.isBlank()) "نشر إعلاناً" else "عدّل إعلاناً", a.title.ifBlank { a.body.take(40) })
    }

    override suspend fun adminDeleteAnnouncement(id: String) {
        requireAdmin()
        requireDb().collection("announcements").document(id).delete().await()
        log("حذف إعلاناً", id)
    }

    override suspend fun adminLog(): List<LogEntry> {
        requireAdmin()
        return requireDb().collection("adminLog").orderBy("at", com.google.firebase.firestore.Query.Direction.DESCENDING).limit(200).get().await()
            .documents.map { LogEntry(it.id, it.getLong("at") ?: 0, it.getString("action").orEmpty(), it.getString("target").orEmpty()) }
    }

    // ---- Book catalog ----

    /** The shared catalog (as the admin keeps it) plus the catalog bundled in the app; the shared copy wins. */
    override suspend fun searchBooks(query: String): List<CatalogBook> {
        val key = Catalog.queryKey(query) ?: return emptyList()
        val local = withContext(Dispatchers.Default) { bundled.search(query, 30) }
        val shared = runCatching {
            requireDb().collection("books").whereArrayContains("keys", key).limit(40).get().await()
                .documents.map { Docs.book(it.id, it.data.orEmpty()) }
        }.getOrElse { if (local.isEmpty()) throw it else emptyList() }
        val ids = shared.map { it.id }.toSet()
        return Catalog.rank(query, shared + local.filter { it.id !in ids }).take(20)
    }

    /** The admin's app copies the bundled catalog into Firestore once (marked in the audit log). */
    private var seeding = false
    private fun seedCatalog() {
        if (seeding) return
        seeding = true
        scope.launch {
            runCatching {
                val db = requireDb()
                val marker = db.collection("adminLog").document("seed-catalog-1")
                if (marker.get(Source.SERVER).await().exists()) return@launch
                val existing = db.collection("books").get(Source.SERVER).await().documents.map { Docs.book(it.id, it.data.orEmpty()) }
                val ids = existing.map { it.id }.toHashSet()
                val names = existing.flatMap { b -> (listOf(b.title) + b.aliases).map { Text.normalize(it) to Text.normalize(b.author) } }.toHashSet()
                val now = System.currentTimeMillis()
                val me = uid()
                val fresh = bundled.books.filter { b -> b.id !in ids && (Text.normalize(b.title) to Text.normalize(b.author)) !in names }
                commitAll(fresh.map { b -> { batch: WriteBatch -> batch.set(db.collection("books").document(b.id), Docs.bookMap(b.copy(status = BookStatus.VERIFIED, createdBy = me, createdAt = now))) } })
                marker.set(mapOf("at" to now, "action" to "أضاف فهرس الكتب الأولي", "target" to "${fresh.size} كتاباً")).await()
            }.onFailure { Log.w(TAG, "seed", it); seeding = false }
        }
    }

    override suspend fun book(id: String): CatalogBook? =
        requireDb().collection("books").document(id).get().await().takeIf { it.exists() }?.let { Docs.book(it.id, it.data.orEmpty()) }
            ?: bundled.books.firstOrNull { it.id == id }

    override suspend fun proposeBook(title: String, author: String, pages: Int?): CatalogBook {
        Catalog.validate(title, author)
        val me = uid()
        val db = requireDb()
        // Already there under this title or another name of it? Use that one.
        Catalog.findSame(title, author, searchBooks(title))?.let { return it }
        val b = CatalogBook(Ids.newId("b_"), title.trim(), author.trim(), pages = pages, status = BookStatus.PENDING, createdBy = me, createdAt = System.currentTimeMillis())
        val sid = Ids.newId("s_")
        db.batch()
            .set(db.collection("books").document(b.id), Docs.bookMap(b))
            .set(
                db.collection("suggestions").document(sid),
                Docs.suggestionMap(Suggestion(sid, SuggestionType.NEW_BOOK, b.id, title = b.title, author = b.author, by = me, byName = _profile.value?.name.orEmpty(), createdAt = b.createdAt)),
            )
            .commit().await()
        return b
    }

    override suspend fun suggest(suggestion: Suggestion) {
        val me = uid()
        if (suggestion.type == SuggestionType.FIX && suggestion.title.isBlank() && suggestion.author.isBlank() && suggestion.note.isBlank()) {
            throw RuleViolation("اكتب التصحيح المقترح.")
        }
        if (suggestion.note.length > 500) throw RuleViolation("الملاحظة طويلة جداً.")
        val id = Ids.newId("s_")
        requireDb().collection("suggestions").document(id)
            .set(Docs.suggestionMap(suggestion.copy(id = id, by = me, byName = _profile.value?.name.orEmpty(), status = SuggestionStatus.OPEN, createdAt = System.currentTimeMillis()))).await()
    }

    override suspend fun mySuggestions(): List<Suggestion> =
        requireDb().collection("suggestions").whereEqualTo("by", uid()).get().await().documents
            .map { Docs.suggestion(it.id, it.data.orEmpty()) }.sortedByDescending { it.createdAt }

    private companion object {
        const val TAG = "Marathon"
    }
}
