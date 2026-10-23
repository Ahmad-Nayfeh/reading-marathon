package com.readingmarathon.app.data

import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.readingmarathon.app.BuildConfig
import com.readingmarathon.core.Ids
import com.readingmarathon.core.Rules
import com.readingmarathon.core.RuleViolation
import kotlinx.coroutines.tasks.await

/** Creating and joining shared marathons (Firebase). Only used when the build has a real config. */
object SharedSetup {

    val available: Boolean
        get() {
            val context = appContextOrNull() ?: return false
            return BuildConfig.FIREBASE_CONFIGURED && FirebaseApp.getApps(context).isNotEmpty()
        }

    private fun appContextOrNull() = com.readingmarathon.app.MarathonApp.instance

    private suspend fun uid(): String {
        val auth = FirebaseAuth.getInstance()
        auth.currentUser?.let { return it.uid }
        return auth.signInAnonymously().await().user?.uid ?: throw RuleViolation("تعذّر تسجيل الدخول. تحقق من الاتصال بالإنترنت.")
    }

    /** Creates the marathon with the organiser as its first member. Returns (marathonId, organiserMemberId). */
    suspend fun create(name: String, organizerName: String): Pair<String, String> {
        if (name.isBlank()) throw RuleViolation("اكتب اسم الماراثون.")
        if (organizerName.isBlank()) throw RuleViolation("اكتب اسمك.")
        val uid = uid()
        val db = FirebaseFirestore.getInstance()
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
                "roles" to mapOf(uid to "organizer"),
            ),
        ).await()
        val batch = db.batch()
        batch.set(db.collection("invites").document(code), mapOf("marathonId" to id, "createdBy" to uid))
        batch.set(root.collection("members").document(memberId), mapOf("name" to organizerName.trim(), "active" to true, "linkedUid" to uid))
        batch.commit().await()
        return id to memberId
    }

    /** Joins as a member using an invite code. Returns the marathon id. */
    suspend fun join(codeInput: String): String {
        val code = Ids.normalizeCode(codeInput)
        if (code.length != 6) throw RuleViolation("رمز الدعوة من 6 أحرف وأرقام.")
        val uid = uid()
        val db = FirebaseFirestore.getInstance()
        val invite = db.collection("invites").document(code).get().await()
        val marathonId = invite.getString("marathonId") ?: throw RuleViolation("لم أجد ماراثوناً بهذا الرمز. تأكد منه مع المشرف.")
        // Adding our own uid as a member is the only change the rules allow a non-member to make.
        db.collection("marathons").document(marathonId)
            .update("roles.$uid", "member")
            .await()
        return marathonId
    }

    suspend fun roleIn(marathonId: String): Role? {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return null
        val snap = FirebaseFirestore.getInstance().collection("marathons").document(marathonId).get().await()
        return when ((snap.get("roles") as? Map<*, *>)?.get(uid)) {
            "organizer" -> Role.ORGANIZER
            "member" -> Role.MEMBER
            else -> null
        }
    }
}
