package com.readingmarathon.app.data

import android.app.Activity
import android.content.Context
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
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.readingmarathon.core.Ids
import com.readingmarathon.core.Rules
import com.readingmarathon.core.RuleViolation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.tasks.await
import java.time.LocalDate

/**
 * Firebase: Google or anonymous (guest) sign-in, Firestore for every marathon.
 * Schema: users/{uid}, invites/{CODE}, marathons/{id} + members, challenges, entries, achievements.
 */
class FirebaseBackend(private val context: Context, override val configured: Boolean) : Backend {

    private val auth: FirebaseAuth? = if (configured && FirebaseApp.getApps(context).isNotEmpty()) FirebaseAuth.getInstance() else null
    private val db: FirebaseFirestore? = auth?.let { FirebaseFirestore.getInstance() }

    private val _account = MutableStateFlow<Account?>(null)
    override val account: StateFlow<Account?> = _account
    private val _restoring = MutableStateFlow(auth != null)
    override val restoring: StateFlow<Boolean> = _restoring

    init {
        auth?.addAuthStateListener { a ->
            _account.value = a.currentUser?.toAccount()
            _restoring.value = false
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
        val user = if (current != null && current.isAnonymous) {
            // A guest keeps their marathons by linking; if this Google account already exists, switch to it.
            try {
                current.linkWithCredential(credential).await().user
            } catch (e: FirebaseAuthUserCollisionException) {
                a.signInWithCredential(credential).await().user
            }
        } else {
            a.signInWithCredential(credential).await().user
        }
        user?.reload()?.await()
        a.currentUser?.let { _account.value = it.toAccount(); touchUser(it) }
    }

    override suspend fun signInAsGuest() {
        val user = requireAuth().signInAnonymously().await().user ?: throw RuleViolation("تعذّر الدخول. تحقق من الاتصال بالإنترنت.")
        touchUser(user)
    }

    override suspend fun signOut() {
        auth?.signOut()
        runCatching { androidx.credentials.ClearCredentialStateRequest().let { CredentialManager.create(context).clearCredentialState(it) } }
    }

    /** Keeps users/{uid} current: the admin panel lists accounts from it. */
    private suspend fun touchUser(user: FirebaseUser) {
        val acc = user.toAccount()
        val ref = requireDb().collection("users").document(user.uid)
        val now = System.currentTimeMillis()
        val exists = runCatching { ref.get().await().exists() }.getOrDefault(true)
        val fields = mutableMapOf<String, Any?>(
            "email" to acc.email, "name" to acc.name, "guest" to acc.isGuest, "lastSeen" to now,
        )
        if (!exists) { fields["createdAt"] = now; fields["marathons"] = emptyList<String>() }
        runCatching { ref.set(fields, SetOptions.merge()).await() }
    }

    private suspend fun rememberMarathon(id: String) {
        requireDb().collection("users").document(uid())
            .set(mapOf("marathons" to FieldValue.arrayUnion(id), "lastSeen" to System.currentTimeMillis()), SetOptions.merge())
            .await()
    }

    // ---- Marathons ----

    override suspend fun myMarathons(): List<MarathonSummary> = coroutineScope {
        val db = requireDb()
        val me = uid()
        val ids = (db.collection("users").document(me).get().await().get("marathons") as? List<*>)?.filterIsInstance<String>().orEmpty()
        ids.map { id ->
            async {
                runCatching {
                    val snap = db.collection("marathons").document(id).get().await()
                    val role = when ((snap.get("roles") as? Map<*, *>)?.get(me)) {
                        "organizer" -> Role.ORGANIZER
                        "member" -> Role.MEMBER
                        else -> null
                    } ?: return@runCatching null
                    MarathonSummary(id, snap.getString("name") ?: "ماراثون", role)
                }.getOrNull()
            }
        }.mapNotNull { it.await() }
    }

    override suspend fun createMarathon(name: String, organizerName: String): Pair<String, String> {
        if (name.isBlank()) throw RuleViolation("اكتب اسم الماراثون.")
        if (organizerName.isBlank()) throw RuleViolation("اكتب اسمك.")
        val db = requireDb()
        val me = uid()
        val id = Ids.newId("mr_")
        val code = Ids.inviteCode()
        val memberId = Ids.newId("m_")
        val root = db.collection("marathons").document(id)
        root.set(
            mapOf(
                "name" to name.trim(),
                "inviteCode" to code,
                "defaultRules" to FirestoreStore.rulesMap(Rules.DEFAULT),
                "createdAt" to System.currentTimeMillis(),
                "roles" to mapOf(me to "organizer"),
            ),
        ).await()
        val batch = db.batch()
        batch.set(db.collection("invites").document(code), mapOf("marathonId" to id, "createdBy" to me))
        batch.set(root.collection("members").document(memberId), mapOf("name" to organizerName.trim(), "active" to true, "linkedUid" to me))
        batch.commit().await()
        rememberMarathon(id)
        return id to memberId
    }

    override suspend fun joinMarathon(code: String): String {
        val c = Ids.normalizeCode(code)
        if (c.length != 6) throw RuleViolation("رمز الدعوة من 6 أحرف وأرقام.")
        val db = requireDb()
        val me = uid()
        val invite = db.collection("invites").document(c).get().await()
        val id = invite.getString("marathonId") ?: throw RuleViolation("لم أجد ماراثوناً بهذا الرمز. تأكد منه مع المشرف.")
        // Already in it (e.g. the organiser tapping their own link): nothing to change.
        if (roleIn(id) == null) {
            db.collection("marathons").document(id).update("roles.$me", "member").await()
        }
        rememberMarathon(id)
        return id
    }

    override suspend fun roleIn(marathonId: String): Role? {
        val me = auth?.currentUser?.uid ?: return null
        val snap = runCatching { requireDb().collection("marathons").document(marathonId).get().await() }.getOrNull() ?: return null
        return when ((snap.get("roles") as? Map<*, *>)?.get(me)) {
            "organizer" -> Role.ORGANIZER
            "member" -> Role.MEMBER
            else -> null
        }
    }

    override fun openStore(marathonId: String): MarathonStore = FirestoreStore(requireDb(), marathonId)

    // ---- Platform admin ----

    override suspend fun adminOverview(): AdminOverview = coroutineScope {
        if (_account.value?.isAdmin != true) throw RuleViolation("هذه اللوحة لمدير المنصة فقط.")
        val db = requireDb()
        val marathonsQ = async { db.collection("marathons").get().await() }
        val usersQ = async { db.collection("users").get().await() }
        val membersQ = async { db.collectionGroup("members").get().await() }
        val entriesQ = async { db.collectionGroup("entries").get().await() }
        val challengesQ = async { db.collectionGroup("challenges").get().await() }
        val achievementsQ = async { db.collectionGroup("achievements").get().await() }

        val today = LocalDate.now()
        val weekAgo = today.minusDays(6).toString()
        val monthAgo = today.minusDays(29)
        fun parentId(path: String) = path.split("/").getOrNull(1).orEmpty() // marathons/{id}/sub/{doc}

        val users = usersQ.await().documents.map { d ->
            AdminUser(
                uid = d.id,
                email = d.getString("email"),
                name = d.getString("name"),
                isGuest = d.getBoolean("guest") ?: false,
                createdAt = d.getLong("createdAt") ?: 0,
                lastSeen = d.getLong("lastSeen") ?: 0,
                marathons = (d.get("marathons") as? List<*>)?.size ?: 0,
            )
        }
        val emails = users.associate { it.uid to (it.email ?: it.name ?: "ضيف") }

        data class E(val marathon: String, val date: String, val minutes: Long)
        val entries = entriesQ.await().documents.map { d ->
            E(
                parentId(d.reference.path),
                d.getString("date").orEmpty(),
                (d.getLong("commonMinutes") ?: 0) + (d.getLong("otherMinutes") ?: 0),
            )
        }
        val members = membersQ.await().documents.groupBy { parentId(it.reference.path) }
        val challenges = challengesQ.await().documents.groupingBy { parentId(it.reference.path) }.eachCount()
        val entriesBy = entries.groupBy { it.marathon }

        val marathons = marathonsQ.await().documents.map { d ->
            val roles = (d.get("roles") as? Map<*, *>).orEmpty()
            val mine = entriesBy[d.id].orEmpty()
            val ms = members[d.id].orEmpty()
            AdminMarathon(
                id = d.id,
                name = d.getString("name") ?: "—",
                inviteCode = d.getString("inviteCode").orEmpty(),
                createdAt = d.getLong("createdAt") ?: 0,
                organizers = roles.filterValues { it == "organizer" }.keys.map { emails[it] ?: "حساب محذوف" },
                accounts = roles.size,
                members = ms.size,
                activeMembers = ms.count { it.getBoolean("active") != false },
                challenges = challenges[d.id] ?: 0,
                readingDays = mine.count { it.minutes > 0 },
                minutes = mine.sumOf { it.minutes },
                minutesLast7 = mine.filter { it.date >= weekAgo }.sumOf { it.minutes },
                lastActivity = mine.filter { it.minutes > 0 }.maxOfOrNull { it.date },
            )
        }.sortedWith(compareByDescending<AdminMarathon> { it.lastActivity ?: "" }.thenByDescending { it.createdAt })

        val byDay = entries.groupBy { it.date }.mapValues { (_, v) -> v.sumOf { it.minutes } }
        AdminOverview(
            marathons = marathons,
            users = users.sortedByDescending { it.lastSeen },
            totalMembers = members.values.sumOf { it.size },
            totalEntries = entries.count { it.minutes > 0 },
            totalMinutes = entries.sumOf { it.minutes },
            minutesLast7 = entries.filter { it.date >= weekAgo }.sumOf { it.minutes },
            finishedBooks = achievementsQ.await().documents.count {
                val t = it.getString("type")
                t == "FINISHED_COMMON_BOOK" || t == "FINISHED_OTHER_BOOK"
            },
            daily = (0L until 30L).map { byDay[monthAgo.plusDays(it).toString()] ?: 0L },
        )
    }
}
