package com.readingmarathon.core

import java.security.SecureRandom
import java.time.LocalDate

/**
 * Pure, validated mutations on [MarathonData]. The local (on-device) store applies them directly;
 * the shared (Firebase) store runs the same validation before writing.
 */
object Ops {

    fun addMember(d: MarathonData, id: String, name: String): MarathonData {
        Validation.memberName(name, d.members)
        return d.copy(members = d.members + Member(id, name.trim()))
    }

    fun renameMember(d: MarathonData, id: String, name: String): MarathonData {
        Validation.memberName(name, d.members, selfId = id)
        return d.copy(members = d.members.map { if (it.id == id) it.copy(name = name.trim()) else it })
    }

    fun setMemberActive(d: MarathonData, id: String, active: Boolean): MarathonData =
        d.copy(members = d.members.map { if (it.id == id) it.copy(active = active) else it })

    fun upsertChallenge(d: MarathonData, c: Challenge): MarathonData {
        Validation.challenge(c, d.challenges)
        return d.copy(challenges = d.challenges.filter { it.id != c.id } + c)
    }

    /** Deleting a challenge removes its achievements. Entries are kept (they are dated, not owned). */
    fun deleteChallenge(d: MarathonData, id: String): MarathonData = d.copy(
        challenges = d.challenges.filter { it.id != id },
        achievements = d.achievements.filter { it.challengeId != id },
    )

    fun upsertEntry(d: MarathonData, e: Entry, today: LocalDate, asOrganizer: Boolean): MarathonData {
        Validation.entry(e, today, asOrganizer)
        val others = d.entries.filter { it.id != e.id }
        // An all-zero entry is a deletion.
        return d.copy(entries = if (e.totalMinutes == 0 && e.quotes == 0) others else others + e)
    }

    fun deleteEntry(d: MarathonData, entryId: String): MarathonData = d.copy(entries = d.entries.filter { it.id != entryId })

    fun addAchievement(d: MarathonData, a: Achievement): MarathonData {
        Validation.achievement(a, d)
        return d.copy(achievements = d.achievements + a)
    }

    fun deleteAchievement(d: MarathonData, id: String): MarathonData =
        d.copy(achievements = d.achievements.filter { it.id != id })

    fun setDefaultRules(d: MarathonData, rules: Rules): MarathonData {
        if (!rules.isValid()) throw RuleViolation("قيم نظام النقاط يجب أن تكون بين 0 و10000.")
        return d.copy(marathon = d.marathon.copy(defaultRules = rules))
    }
}

object Ids {
    private val rnd = SecureRandom()
    private const val ALPHABET = "abcdefghijkmnopqrstuvwxyz23456789"
    /** Unambiguous alphabet for codes people type (no 0/O, 1/l/I). */
    private const val CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"

    fun newId(prefix: String = ""): String = prefix + (1..12).map { ALPHABET[rnd.nextInt(ALPHABET.length)] }.joinToString("")

    fun inviteCode(): String = (1..6).map { CODE_ALPHABET[rnd.nextInt(CODE_ALPHABET.length)] }.joinToString("")

    fun normalizeCode(input: String): String = input.uppercase().filter { it in CODE_ALPHABET }
}
