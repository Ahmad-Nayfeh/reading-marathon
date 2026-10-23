package com.readingmarathon.app.data

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import com.readingmarathon.core.Achievement
import com.readingmarathon.core.AchievementType
import com.readingmarathon.core.Book
import com.readingmarathon.core.Challenge
import com.readingmarathon.core.CsvImport
import com.readingmarathon.core.Entry
import com.readingmarathon.core.Ids
import com.readingmarathon.core.Marathon
import com.readingmarathon.core.MarathonData
import com.readingmarathon.core.Member
import com.readingmarathon.core.Ops
import com.readingmarathon.core.Rules
import com.readingmarathon.core.RuleViolation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.tasks.await
import java.time.LocalDate

/**
 * A shared marathon in Firestore:
 *
 *   marathons/{id}                 name, inviteCode, defaultRules{}, createdAt, roles{uid: organizer|member}
 *   marathons/{id}/members/{mid}   name, active, linkedUid
 *   marathons/{id}/challenges/{cid}
 *   marathons/{id}/entries/{memberId_date}
 *   marathons/{id}/achievements/{aid}
 *   invites/{CODE}                 marathonId
 *
 * Access is enforced by firestore.rules; this class validates with the same :core rules first so
 * errors come back as clear Arabic messages instead of permission failures.
 */
class FirestoreStore(
    private val db: FirebaseFirestore,
    private val marathonId: String,
) : MarathonStore {

    private val root = db.collection("marathons").document(marathonId)
    private val state = MutableStateFlow<MarathonData?>(null)
    override val data: StateFlow<MarathonData?> = state

    private var marathon: Marathon? = null
    private var members: List<Member>? = null
    private var challenges: List<Challenge>? = null
    private var entries: List<Entry>? = null
    private var achievements: List<Achievement>? = null
    private val listeners = mutableListOf<ListenerRegistration>()

    init {
        listeners += root.addSnapshotListener { snap, _ ->
            if (snap != null && snap.exists()) {
                marathon = Marathon(
                    id = marathonId,
                    name = snap.getString("name").orEmpty(),
                    inviteCode = snap.getString("inviteCode").orEmpty(),
                    defaultRules = rulesFrom(snap.get("defaultRules")),
                    createdAt = snap.getLong("createdAt") ?: 0,
                )
                publish()
            }
        }
        listeners += root.collection("members").addSnapshotListener { s, _ ->
            if (s != null) { members = s.documents.map { d -> Member(d.id, d.getString("name").orEmpty(), d.getBoolean("active") ?: true, d.getString("linkedUid")) }; publish() }
        }
        listeners += root.collection("challenges").addSnapshotListener { s, _ ->
            if (s != null) { challenges = s.documents.mapNotNull { d -> runCatching { challengeFrom(d.id, d.data.orEmpty()) }.getOrNull() }; publish() }
        }
        listeners += root.collection("entries").addSnapshotListener { s, _ ->
            if (s != null) { entries = s.documents.mapNotNull { d -> runCatching { entryFrom(d.data.orEmpty()) }.getOrNull() }; publish() }
        }
        listeners += root.collection("achievements").addSnapshotListener { s, _ ->
            if (s != null) { achievements = s.documents.mapNotNull { d -> runCatching { achievementFrom(d.id, d.data.orEmpty()) }.getOrNull() }; publish() }
        }
    }

    private fun publish() {
        val m = marathon ?: return
        state.value = MarathonData(m, members ?: return, challenges ?: return, entries ?: return, achievements ?: return)
    }

    private fun current(): MarathonData = state.value ?: throw RuleViolation("لم يُحمَّل الماراثون بعد. تحقق من الاتصال.")

    override suspend fun renameMarathon(name: String) {
        if (name.isBlank()) throw RuleViolation("اكتب اسم الماراثون.")
        root.update("name", name.trim()).await()
    }

    override suspend fun setDefaultRules(rules: Rules) {
        Ops.setDefaultRules(current(), rules)
        root.update("defaultRules", rulesMap(rules)).await()
    }

    override suspend fun addMember(name: String): Member {
        val id = Ids.newId("m_")
        Ops.addMember(current(), id, name)
        val m = Member(id, name.trim())
        root.collection("members").document(id).set(mapOf("name" to m.name, "active" to true, "linkedUid" to null)).await()
        return m
    }

    override suspend fun renameMember(id: String, name: String) {
        Ops.renameMember(current(), id, name)
        root.collection("members").document(id).update("name", name.trim()).await()
    }

    override suspend fun setMemberActive(id: String, active: Boolean) {
        root.collection("members").document(id).update("active", active).await()
    }

    override suspend fun upsertChallenge(challenge: Challenge) {
        Ops.upsertChallenge(current(), challenge)
        root.collection("challenges").document(challenge.id).set(challengeMap(challenge)).await()
    }

    override suspend fun deleteChallenge(id: String) {
        val batch = db.batch()
        current().achievements.filter { it.challengeId == id }.forEach { batch.delete(root.collection("achievements").document(it.id)) }
        batch.delete(root.collection("challenges").document(id))
        batch.commit().await()
    }

    override suspend fun upsertEntry(entry: Entry, asOrganizer: Boolean) {
        val e = entry.copy(updatedAt = System.currentTimeMillis())
        Ops.upsertEntry(current(), e, LocalDate.now(), asOrganizer)
        val doc = root.collection("entries").document(e.id)
        if (e.totalMinutes == 0 && e.quotes == 0) doc.delete().await() else doc.set(entryMap(e)).await()
    }

    override suspend fun deleteEntry(entryId: String) {
        root.collection("entries").document(entryId).delete().await()
    }

    override suspend fun addAchievement(achievement: Achievement) {
        Ops.addAchievement(current(), achievement)
        root.collection("achievements").document(achievement.id).set(achievementMap(achievement)).await()
    }

    override suspend fun deleteAchievement(id: String) {
        root.collection("achievements").document(id).delete().await()
    }

    override suspend fun applyImport(plan: CsvImport.Plan) {
        // Firestore batches hold at most 500 writes.
        val writes = mutableListOf<Pair<com.google.firebase.firestore.DocumentReference, Map<String, Any?>>>()
        plan.newMembers.forEach { writes += root.collection("members").document(it.id) to mapOf("name" to it.name, "active" to true, "linkedUid" to null) }
        plan.entries.forEach { writes += root.collection("entries").document(it.id) to entryMap(it) }
        plan.achievements.forEach { writes += root.collection("achievements").document(it.id) to achievementMap(it) }
        writes.chunked(450).forEach { chunk ->
            val b = db.batch()
            chunk.forEach { (ref, map) -> b.set(ref, map) }
            b.commit().await()
        }
    }

    override suspend fun linkMember(memberId: String) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: throw RuleViolation("لم يكتمل تسجيل الدخول.")
        val m = current().member(memberId) ?: throw RuleViolation("العضو غير موجود.")
        if (m.linkedUid != null && m.linkedUid != uid) throw RuleViolation("هذا الاسم مرتبط بجهاز آخر. اطلب من المشرف فكّ الارتباط.")
        root.collection("members").document(memberId).set(mapOf("linkedUid" to uid), SetOptions.merge()).await()
    }

    suspend fun unlinkMember(memberId: String) {
        root.collection("members").document(memberId).update("linkedUid", null).await()
    }

    override fun close() {
        listeners.forEach { it.remove() }
        listeners.clear()
    }

    companion object {
        fun rulesMap(r: Rules): Map<String, Any> = mapOf(
            "minutesPerPointCommon" to r.minutesPerPointCommon,
            "minutesPerPointOther" to r.minutesPerPointOther,
            "quoteCommonPoints" to r.quoteCommonPoints,
            "quoteOtherPoints" to r.quoteOtherPoints,
            "finishCommonPoints" to r.finishCommonPoints,
            "finishOtherPoints" to r.finishOtherPoints,
            "attendDiscussionPoints" to r.attendDiscussionPoints,
        )

        @Suppress("UNCHECKED_CAST")
        fun rulesFrom(raw: Any?): Rules {
            val m = raw as? Map<String, Any?> ?: return Rules.DEFAULT
            fun i(k: String, def: Int) = (m[k] as? Number)?.toInt() ?: def
            val d = Rules.DEFAULT
            return Rules(
                i("minutesPerPointCommon", d.minutesPerPointCommon), i("minutesPerPointOther", d.minutesPerPointOther),
                i("quoteCommonPoints", d.quoteCommonPoints), i("quoteOtherPoints", d.quoteOtherPoints),
                i("finishCommonPoints", d.finishCommonPoints), i("finishOtherPoints", d.finishOtherPoints),
                i("attendDiscussionPoints", d.attendDiscussionPoints),
            )
        }

        fun challengeMap(c: Challenge): Map<String, Any?> = mapOf(
            "title" to c.book.title, "author" to c.book.author, "year" to c.book.year, "pages" to c.book.pages,
            "start" to c.start.toString(), "end" to c.end.toString(), "graceDays" to c.graceDays, "rules" to rulesMap(c.rules),
        )

        fun challengeFrom(id: String, m: Map<String, Any?>) = Challenge(
            id = id,
            book = Book(m["title"] as String, m["author"] as? String ?: "", (m["year"] as? Number)?.toInt(), (m["pages"] as? Number)?.toInt()),
            start = LocalDate.parse(m["start"] as String),
            end = LocalDate.parse(m["end"] as String),
            rules = rulesFrom(m["rules"]),
            graceDays = (m["graceDays"] as? Number)?.toInt() ?: 3,
        )

        fun entryMap(e: Entry): Map<String, Any?> = mapOf(
            "memberId" to e.memberId, "date" to e.date.toString(), "commonMinutes" to e.commonMinutes,
            "otherMinutes" to e.otherMinutes, "quoteCommon" to e.quoteCommon, "quoteOther" to e.quoteOther, "updatedAt" to e.updatedAt,
        )

        fun entryFrom(m: Map<String, Any?>) = Entry(
            memberId = m["memberId"] as String,
            date = LocalDate.parse(m["date"] as String),
            commonMinutes = (m["commonMinutes"] as? Number)?.toInt() ?: 0,
            otherMinutes = (m["otherMinutes"] as? Number)?.toInt() ?: 0,
            quoteCommon = m["quoteCommon"] as? Boolean ?: false,
            quoteOther = m["quoteOther"] as? Boolean ?: false,
            updatedAt = (m["updatedAt"] as? Number)?.toLong() ?: 0,
        )

        fun achievementMap(a: Achievement): Map<String, Any?> = mapOf(
            "memberId" to a.memberId, "challengeId" to a.challengeId, "type" to a.type.name, "date" to a.date.toString(), "bookTitle" to a.bookTitle,
        )

        fun achievementFrom(id: String, m: Map<String, Any?>) = Achievement(
            id = id,
            memberId = m["memberId"] as String,
            challengeId = m["challengeId"] as String,
            type = AchievementType.valueOf(m["type"] as String),
            date = LocalDate.parse(m["date"] as String),
            bookTitle = m["bookTitle"] as? String,
        )
    }
}
