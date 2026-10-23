package com.readingmarathon.app.fake

import com.readingmarathon.app.data.MarathonStore
import com.readingmarathon.core.Achievement
import com.readingmarathon.core.Challenge
import com.readingmarathon.core.CsvImport
import com.readingmarathon.core.Entry
import com.readingmarathon.core.Ids
import com.readingmarathon.core.MarathonData
import com.readingmarathon.core.Member
import com.readingmarathon.core.Ops
import com.readingmarathon.core.Rules
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.time.LocalDate

/** A marathon held in memory, applying the same Ops as the real store. For tests. */
class InMemoryStore(initial: MarathonData, private val uid: () -> String) : MarathonStore {
    private val state = MutableStateFlow<MarathonData?>(initial)
    override val data: StateFlow<MarathonData?> = state
    private fun mutate(f: (MarathonData) -> MarathonData) { state.value = f(state.value!!) }

    override suspend fun renameMarathon(name: String) = mutate { it.copy(marathon = it.marathon.copy(name = name.trim())) }
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
    override suspend fun upsertEntry(entry: Entry, asOrganizer: Boolean) = mutate { Ops.upsertEntry(it, entry, LocalDate.now(), asOrganizer) }
    override suspend fun deleteEntry(entryId: String) = mutate { Ops.deleteEntry(it, entryId) }
    override suspend fun addAchievement(achievement: Achievement) = mutate { Ops.addAchievement(it, achievement) }
    override suspend fun deleteAchievement(id: String) = mutate { Ops.deleteAchievement(it, id) }
    override suspend fun applyImport(plan: CsvImport.Plan) = mutate {
        it.copy(members = it.members + plan.newMembers, entries = it.entries + plan.entries, achievements = it.achievements + plan.achievements)
    }
    override suspend fun linkMember(memberId: String) = mutate { d ->
        d.copy(members = d.members.map { if (it.id == memberId) it.copy(linkedUid = uid()) else it })
    }
}
