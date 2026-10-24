package com.readingmarathon.app.data

import com.readingmarathon.core.Book
import com.readingmarathon.core.BookBrief
import com.readingmarathon.core.Challenge
import com.readingmarathon.core.Day
import com.readingmarathon.core.Entry
import com.readingmarathon.core.Finish
import com.readingmarathon.core.Group
import com.readingmarathon.core.Member
import com.readingmarathon.core.Role
import com.readingmarathon.core.Rules
import com.readingmarathon.core.Visibility
import java.time.LocalDate

/**
 * Firestore documents ⇄ core types.
 *
 *   users/{uid}                        email, name, guest, goal, groups[], createdAt, lastSeen
 *   users/{uid}/days/{yyyy-MM-dd}      date, minutes, updatedAt
 *   groups/{gid}                       name, about, visibility, inviteCode, roles{uid: owner|admin|member},
 *                                      rules{minutesPerPoint, finishBonus}, createdAt, current{…}|null
 *   groups/{gid}/members/{id}          name, linkedUid, active, joinedAt[, code]
 *   groups/{gid}/challenges/{cid}      title, author, start, end
 *   groups/{gid}/entries/{id_date}     memberId, date, minutes, updatedAt
 *   groups/{gid}/finishes/{id_cid}     memberId, challengeId, date
 *   invites/{CODE}                     groupId, createdBy
 */
object Docs {

    fun role(raw: Any?): Role? = when (raw) {
        "owner" -> Role.OWNER
        "admin" -> Role.ADMIN
        "member" -> Role.MEMBER
        else -> null
    }

    fun roleName(r: Role): String = r.name.lowercase()

    fun visibility(raw: Any?): Visibility = if (raw == "public") Visibility.PUBLIC else Visibility.PRIVATE
    fun visibilityName(v: Visibility): String = v.name.lowercase()

    fun rulesMap(r: Rules): Map<String, Any> = mapOf("minutesPerPoint" to r.minutesPerPoint, "finishBonus" to r.finishBonus)

    @Suppress("UNCHECKED_CAST")
    fun rules(raw: Any?): Rules {
        val m = raw as? Map<String, Any?> ?: return Rules.DEFAULT
        return Rules(
            (m["minutesPerPoint"] as? Number)?.toInt() ?: Rules.DEFAULT.minutesPerPoint,
            (m["finishBonus"] as? Number)?.toInt() ?: Rules.DEFAULT.finishBonus,
        )
    }

    fun briefMap(b: BookBrief?): Map<String, Any>? = b?.let {
        mapOf("title" to it.title, "author" to it.author, "start" to it.start.toString(), "end" to it.end.toString())
    }

    @Suppress("UNCHECKED_CAST")
    fun brief(raw: Any?): BookBrief? {
        val m = raw as? Map<String, Any?> ?: return null
        return runCatching {
            BookBrief(m["title"] as String, m["author"] as? String ?: "", LocalDate.parse(m["start"] as String), LocalDate.parse(m["end"] as String))
        }.getOrNull()
    }

    fun group(id: String, m: Map<String, Any?>): Group = Group(
        id = id,
        name = m["name"] as? String ?: "",
        about = m["about"] as? String ?: "",
        visibility = visibility(m["visibility"]),
        inviteCode = m["inviteCode"] as? String ?: "",
        roles = (m["roles"] as? Map<*, *>).orEmpty().mapNotNull { (k, v) -> role(v)?.let { (k as String) to it } }.toMap(),
        rules = rules(m["rules"]),
        createdAt = (m["createdAt"] as? Number)?.toLong() ?: 0,
        current = brief(m["current"]),
    )

    fun preview(id: String, m: Map<String, Any?>): GroupPreview {
        val g = group(id, m)
        return GroupPreview(g.id, g.name, g.about, g.visibility, g.memberCount, g.current)
    }

    fun member(id: String, m: Map<String, Any?>) = Member(
        id = id,
        name = m["name"] as? String ?: "",
        linkedUid = m["linkedUid"] as? String,
        active = m["active"] as? Boolean ?: true,
    )

    fun challengeMap(c: Challenge): Map<String, Any> =
        mapOf("title" to c.book.title, "author" to c.book.author, "start" to c.start.toString(), "end" to c.end.toString())

    fun challenge(id: String, m: Map<String, Any?>) = Challenge(
        id = id,
        book = Book(m["title"] as String, m["author"] as? String ?: ""),
        start = LocalDate.parse(m["start"] as String),
        end = LocalDate.parse(m["end"] as String),
    )

    fun entryMap(e: Entry): Map<String, Any> =
        mapOf("memberId" to e.memberId, "date" to e.date.toString(), "minutes" to e.minutes, "updatedAt" to e.updatedAt)

    fun entry(m: Map<String, Any?>) = Entry(
        memberId = m["memberId"] as String,
        date = LocalDate.parse(m["date"] as String),
        minutes = (m["minutes"] as? Number)?.toInt() ?: 0,
        updatedAt = (m["updatedAt"] as? Number)?.toLong() ?: 0,
    )

    fun finishMap(f: Finish): Map<String, Any> = mapOf("memberId" to f.memberId, "challengeId" to f.challengeId, "date" to f.date.toString())

    fun finish(m: Map<String, Any?>) = Finish(m["memberId"] as String, m["challengeId"] as String, LocalDate.parse(m["date"] as String))

    fun dayMap(d: Day): Map<String, Any> = mapOf("date" to d.date.toString(), "minutes" to d.minutes, "updatedAt" to d.updatedAt)

    fun day(m: Map<String, Any?>) = Day(LocalDate.parse(m["date"] as String), (m["minutes"] as? Number)?.toInt() ?: 0, (m["updatedAt"] as? Number)?.toLong() ?: 0)

    fun profile(m: Map<String, Any?>?) = Profile(
        name = m?.get("name") as? String ?: "",
        goal = (m?.get("goal") as? Number)?.toInt() ?: 30,
        groups = (m?.get("groups") as? List<*>)?.filterIsInstance<String>().orEmpty(),
    )
}
