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
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.WriteBatch
import com.readingmarathon.core.BookBrief
import com.readingmarathon.core.Challenge
import com.readingmarathon.core.ChallengeStatus
import com.readingmarathon.core.CsvImport
import com.readingmarathon.core.Day
import com.readingmarathon.core.Entry
import com.readingmarathon.core.Finish
import com.readingmarathon.core.Group
import com.readingmarathon.core.GroupData
import com.readingmarathon.core.Ids
import com.readingmarathon.core.Limits
import com.readingmarathon.core.Member
import com.readingmarathon.core.Role
import com.readingmarathon.core.RuleViolation
import com.readingmarathon.core.Rules
import com.readingmarathon.core.Validation
import com.readingmarathon.core.Visibility
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.tasks.await
import java.time.LocalDate

/** Firebase: Google or anonymous (guest) sign-in, Firestore for everything else. Schema in [Docs]. */
class FirebaseBackend(private val context: Context, override val configured: Boolean) : Backend {

    private val auth: FirebaseAuth? = if (configured && FirebaseApp.getApps(context).isNotEmpty()) FirebaseAuth.getInstance() else null
    private val db: FirebaseFirestore? = auth?.let { FirebaseFirestore.getInstance() }

    private val _account = MutableStateFlow<Account?>(null)
    override val account: StateFlow<Account?> = _account
    private val _restoring = MutableStateFlow(auth != null)
    override val restoring: StateFlow<Boolean> = _restoring
    private val _profile = MutableStateFlow<Profile?>(null)
    override val profile: StateFlow<Profile?> = _profile
    private val _days = MutableStateFlow<List<Day>>(emptyList())
    override val days: StateFlow<List<Day>> = _days
    private val _groups = MutableStateFlow<Map<String, GroupData>>(emptyMap())
    override val groups: StateFlow<Map<String, GroupData>> = _groups

    private var uidLoaded: String? = null
    private val userListeners = mutableListOf<ListenerRegistration>()
    private val watchers = HashMap<String, GroupWatcher>()
    private val peeked = HashSet<String>()

    init {
        auth?.addAuthStateListener { a ->
            val user = a.currentUser
            _account.value = user?.toAccount()
            _restoring.value = false
            if (user?.uid != uidLoaded) load(user?.uid)
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
        _groups.value = emptyMap()
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
        }

        fun stop() {
            regs.forEach { it.remove() }
            regs.clear()
        }
    }

    private fun loaded(groupId: String): GroupData =
        _groups.value[groupId] ?: throw RuleViolation("لم تُحمَّل المجموعة بعد. تحقق من الاتصال.")

    // ---- Accounts ----

    override suspend fun signInWithGoogle(activity: Activity) {
        val a = requireAuth()
        val clientId = activity.resources.getIdentifier("default_web_client_id", "string", activity.packageName)
            .takeIf { it != 0 }?.let(activity::getString)
            ?: throw RuleViolation("الدخول بحساب Google غير مفعّل في هذه النسخة.")
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(GetSignInWithGoogleOption.Builder(clientId).build())
            .build()
        val result = try {
            CredentialManager.create(activity).getCredential(activity, request)
        } catch (e: GetCredentialCancellationException) {
            return
        } catch (e: NoCredentialException) {
            throw RuleViolation("لا يوجد حساب Google على هذا الهاتف. أضف حساباً من إعدادات الهاتف.")
        }
        val c = result.credential
        if (c !is CustomCredential || c.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            throw RuleViolation("تعذّر الدخول بحساب Google.")
        }
        val credential = GoogleAuthProvider.getCredential(GoogleIdTokenCredential.createFrom(c.data).idToken, null)
        val current = a.currentUser
        if (current != null && current.isAnonymous) {
            // A guest keeps everything by linking; if this Google account already exists, switch to it.
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

    override suspend fun signInAsGuest() {
        val user = requireAuth().signInAnonymously().await().user ?: throw RuleViolation("تعذّر الدخول. تحقق من الاتصال بالإنترنت.")
        touchUser(user)
    }

    override suspend fun signOut() {
        auth?.signOut()
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

    override suspend fun setDay(date: LocalDate, minutes: Int) {
        Validation.day(date, minutes, today())
        val me = uid()
        val now = System.currentTimeMillis()
        // Not awaited: the local cache updates at once and syncs when online.
        users().document(me).collection("days").document(date.toString())
            .set(Docs.dayMap(Day(date, minutes, now)))
            .addOnFailureListener { Log.w(TAG, "day", it) }
        for (gid in groupIds()) {
            val ref = groupRef(gid).collection("entries").document(Entry.entryId(me, date))
            val write = if (minutes == 0) ref.delete() else ref.set(Docs.entryMap(Entry(me, date, minutes, now)))
            write.addOnFailureListener { Log.w(TAG, "entry $gid", it) }
        }
    }

    override suspend fun addMinutes(date: LocalDate, minutes: Int): Int {
        val current = _days.value.firstOrNull { it.date == date }?.minutes
            ?: runCatching {
                (users().document(uid()).collection("days").document(date.toString()).get().await().getLong("minutes") ?: 0).toInt()
            }.getOrDefault(0)
        val total = (current + minutes).coerceIn(0, Limits.MAX_MINUTES_PER_DAY)
        setDay(date, total)
        return total
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
            .set(users().document(me), mapOf("groups" to FieldValue.arrayUnion(id)), SetOptions.merge())
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
                .set(users().document(me), mapOf("groups" to FieldValue.arrayUnion(groupId)), SetOptions.merge())
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
        val days = _days.value.filter { it.minutes > 0 && c.contains(it.date) && !it.date.isAfter(today) }
        days.forEach { d ->
            batch.set(groupRef(groupId).collection("entries").document(Entry.entryId(me, d.date)), Docs.entryMap(Entry(me, d.date, d.minutes, d.updatedAt)))
        }
        if (days.isNotEmpty()) batch.commit().await()
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

    override suspend fun setFinished(groupId: String, challengeId: String, memberId: String, date: LocalDate?) {
        val d = loaded(groupId)
        val me = uid()
        if (memberId != me) Checks.manager(d, me, isAdmin())
        val c = d.challenge(challengeId) ?: throw RuleViolation("لم يعد هذا التحدي موجوداً.")
        val ref = groupRef(groupId).collection("finishes").document(Finish.finishId(memberId, challengeId))
        if (date == null) {
            ref.delete().await()
        } else {
            Validation.finish(c, date)
            ref.set(Docs.finishMap(Finish(memberId, challengeId, date))).await()
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

    override suspend fun addReader(groupId: String, name: String): Member {
        val d = loaded(groupId)
        Checks.manager(d, uid(), isAdmin())
        Validation.memberName(name, d.members)
        val m = Member(Ids.newId("r_"), name.trim())
        groupRef(groupId).collection("members").document(m.id)
            .set(mapOf("name" to m.name, "linkedUid" to null, "active" to true, "joinedAt" to System.currentTimeMillis())).await()
        return m
    }

    override suspend fun renameReader(groupId: String, memberId: String, name: String) {
        val d = loaded(groupId)
        Checks.manager(d, uid(), isAdmin())
        Checks.reader(d, memberId)
        Validation.memberName(name, d.members, selfId = memberId)
        groupRef(groupId).collection("members").document(memberId).update("name", name.trim()).await()
    }

    override suspend fun setReaderDay(groupId: String, memberId: String, date: LocalDate, minutes: Int) {
        val d = loaded(groupId)
        Checks.manager(d, uid(), isAdmin())
        Checks.reader(d, memberId)
        if (date.isAfter(today())) throw RuleViolation("لا يمكن تسجيل قراءة ليوم لم يأتِ بعد.")
        if (minutes !in 0..Limits.MAX_MINUTES_PER_DAY) throw RuleViolation("الدقائق بين 0 و960.")
        val ref = groupRef(groupId).collection("entries").document(Entry.entryId(memberId, date))
        if (minutes == 0) ref.delete().await() else ref.set(Docs.entryMap(Entry(memberId, date, minutes, System.currentTimeMillis()))).await()
    }

    override suspend fun mergeReader(groupId: String, fromId: String, intoId: String) {
        val d = loaded(groupId)
        Checks.manager(d, uid(), isAdmin())
        Checks.reader(d, fromId)
        val into = d.member(intoId)?.takeIf { it.active } ?: throw RuleViolation("العضو غير موجود.")
        val now = System.currentTimeMillis()
        val root = groupRef(groupId)
        val writes = mutableListOf<(WriteBatch) -> Unit>()
        val theirs = d.entries.filter { it.memberId == into.id }.associateBy { it.date }
        d.entries.filter { it.memberId == fromId }.forEach { e ->
            val sum = ((theirs[e.date]?.minutes ?: 0) + e.minutes).coerceAtMost(Limits.MAX_MINUTES_PER_DAY)
            writes += { b -> b.set(root.collection("entries").document(Entry.entryId(into.id, e.date)), Docs.entryMap(Entry(into.id, e.date, sum, now))) }
            writes += { b -> b.delete(root.collection("entries").document(e.id)) }
        }
        d.finishes.filter { it.memberId == fromId }.forEach { f ->
            if (d.finished(into.id, f.challengeId) == null) {
                writes += { b -> b.set(root.collection("finishes").document(Finish.finishId(into.id, f.challengeId)), Docs.finishMap(f.copy(memberId = into.id))) }
            }
            writes += { b -> b.delete(root.collection("finishes").document(f.id)) }
        }
        writes += { b -> b.update(root.collection("members").document(fromId), "active", false) }
        commitAll(writes)
    }

    override suspend fun applyImport(groupId: String, plan: CsvImport.Plan) {
        val d = loaded(groupId)
        Checks.manager(d, uid(), isAdmin())
        val root = groupRef(groupId)
        val now = System.currentTimeMillis()
        val writes = mutableListOf<(WriteBatch) -> Unit>()
        plan.newMembers.forEach { m ->
            writes += { b -> b.set(root.collection("members").document(m.id), mapOf("name" to m.name, "linkedUid" to null, "active" to true, "joinedAt" to now)) }
        }
        plan.entries.forEach { e -> writes += { b -> b.set(root.collection("entries").document(e.id), Docs.entryMap(e)) } }
        plan.finishes.forEach { f -> writes += { b -> b.set(root.collection("finishes").document(f.id), Docs.finishMap(f)) } }
        commitAll(writes)
    }

    override suspend fun deleteGroup(groupId: String) {
        val d = loaded(groupId)
        Checks.owner(d, uid(), isAdmin())
        val db = requireDb()
        val root = groupRef(groupId)
        val writes = mutableListOf<(WriteBatch) -> Unit>()
        // Delete everything the group holds, including history older than what the app loads.
        for (sub in listOf("members", "challenges", "entries", "finishes")) {
            root.collection(sub).get().await().documents.forEach { doc -> writes += { b -> b.delete(doc.reference) } }
        }
        if (d.group.inviteCode.isNotBlank()) writes += { b -> b.delete(db.collection("invites").document(d.group.inviteCode)) }
        commitAll(writes)
        db.batch()
            .delete(root)
            .set(users().document(uid()), mapOf("groups" to FieldValue.arrayRemove(groupId)), SetOptions.merge())
            .commit().await()
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
        val challenges = challengesQ.await().documents.groupingBy { parentId(it.reference) }.eachCount()

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
        )
    }

    private companion object {
        const val TAG = "Marathon"
    }
}
