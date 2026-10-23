package com.readingmarathon.app.data

import com.readingmarathon.core.Achievement
import com.readingmarathon.core.Challenge
import com.readingmarathon.core.CsvImport
import com.readingmarathon.core.Entry
import com.readingmarathon.core.Ids
import com.readingmarathon.core.MarathonData
import com.readingmarathon.core.MarathonJson
import com.readingmarathon.core.Member
import com.readingmarathon.core.Ops
import com.readingmarathon.core.Rules
import com.readingmarathon.core.RuleViolation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate

/**
 * A marathon kept on this device as one JSON file (written atomically). With [file] = null it is
 * the in-memory demo. Every change goes through [Ops], so the rules are identical to shared mode.
 */
class LocalStore(private val file: File?, initial: MarathonData) : MarathonStore {

    private val state = MutableStateFlow<MarathonData?>(initial)
    override val data: StateFlow<MarathonData?> = state
    private val lock = Mutex()

    private suspend fun mutate(block: (MarathonData) -> MarathonData) = lock.withLock {
        val current = state.value ?: throw RuleViolation("لم يُحمَّل الماراثون بعد.")
        val next = block(current)
        if (file != null) withContext(Dispatchers.IO) { save(file, next) }
        state.value = next
    }

    override suspend fun renameMarathon(name: String) = mutate {
        val n = name.trim()
        if (n.isEmpty()) throw RuleViolation("اكتب اسم الماراثون.")
        it.copy(marathon = it.marathon.copy(name = n))
    }

    override suspend fun setDefaultRules(rules: Rules) = mutate { Ops.setDefaultRules(it, rules) }

    override suspend fun addMember(name: String): Member {
        val id = Ids.newId("m_")
        mutate { Ops.addMember(it, id, name) }
        return state.value!!.member(id)!!
    }

    override suspend fun renameMember(id: String, name: String) = mutate { Ops.renameMember(it, id, name) }
    override suspend fun setMemberActive(id: String, active: Boolean) = mutate { Ops.setMemberActive(it, id, active) }
    override suspend fun upsertChallenge(challenge: Challenge) = mutate { Ops.upsertChallenge(it, challenge) }
    override suspend fun deleteChallenge(id: String) = mutate { Ops.deleteChallenge(it, id) }

    override suspend fun upsertEntry(entry: Entry, asOrganizer: Boolean) = mutate {
        Ops.upsertEntry(it, entry.copy(updatedAt = System.currentTimeMillis()), LocalDate.now(), asOrganizer)
    }

    override suspend fun deleteEntry(entryId: String) = mutate { Ops.deleteEntry(it, entryId) }
    override suspend fun addAchievement(achievement: Achievement) = mutate { Ops.addAchievement(it, achievement) }
    override suspend fun deleteAchievement(id: String) = mutate { Ops.deleteAchievement(it, id) }

    override suspend fun applyImport(plan: CsvImport.Plan) = mutate { d ->
        val existing = d.entries.associateBy { it.id }
        d.copy(
            members = d.members + plan.newMembers,
            entries = d.entries + plan.entries.filter { it.id !in existing },
            achievements = d.achievements + plan.achievements,
        )
    }

    companion object {
        fun load(file: File): MarathonData? =
            if (!file.exists()) null else runCatching { MarathonJson.decodeFromString(MarathonData.serializer(), file.readText()) }.getOrNull()

        fun save(file: File, data: MarathonData) {
            file.parentFile?.mkdirs()
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText(MarathonJson.encodeToString(MarathonData.serializer(), data))
            if (!tmp.renameTo(file)) {
                file.delete()
                tmp.renameTo(file)
            }
        }
    }
}
