package com.readingmarathon.core

import java.time.LocalDate
import kotlin.random.Random

/** A deterministic 10-reader marathon used only by tests. */
object SampleData {

    fun build(today: LocalDate, seed: Int = 7): MarathonData {
        val rnd = Random(seed)
        val names = listOf("سارة", "خالد", "نورة", "فهد", "ريم", "عبدالله", "لمى", "يوسف", "هيا", "ماجد")
        val members = names.mapIndexed { i, n -> Member("m_t$i", n) }
        val past = Challenge(
            id = "c_t1",
            book = Book("الإنسان يبحث عن المعنى", "فيكتور فرانكل", 1946, 180),
            start = today.minusDays(52),
            end = today.minusDays(24),
        )
        val current = Challenge(
            id = "c_t2",
            book = Book("ثلاثية غرناطة", "رضوى عاشور", 1994, 504),
            start = today.minusDays(17),
            end = today.plusDays(12),
        )
        val next = Challenge(
            id = "c_t3",
            book = Book("الأيام", "طه حسين", 1929, 220),
            start = today.plusDays(14),
            end = today.plusDays(44),
        )
        // Each member has a temperament: how often they read and how much.
        val habit = members.associate { it.id to (0.45 + rnd.nextDouble() * 0.5) }
        val pace = members.associate { it.id to (15 + rnd.nextInt(45)) }
        val entries = mutableListOf<Entry>()
        var d = past.start
        while (!d.isAfter(today)) {
            for (m in members) {
                if (rnd.nextDouble() > habit.getValue(m.id)) continue
                val base = pace.getValue(m.id)
                val common = (base + rnd.nextInt(-10, 25)).coerceAtLeast(0)
                val other = if (rnd.nextDouble() < 0.35) 10 + rnd.nextInt(40) else 0
                entries += Entry(
                    memberId = m.id,
                    date = d,
                    commonMinutes = common,
                    otherMinutes = other,
                    quoteCommon = rnd.nextDouble() < 0.3,
                    quoteOther = other > 0 && rnd.nextDouble() < 0.25,
                )
            }
            d = d.plusDays(1)
        }
        val ach = mutableListOf<Achievement>()
        var n = 0
        fun add(m: Member, c: Challenge, t: AchievementType, date: LocalDate, title: String? = null) {
            ach += Achievement("a_t${n++}", m.id, c.id, t, date, title)
        }
        members.take(7).forEachIndexed { i, m -> add(m, past, AchievementType.FINISHED_COMMON_BOOK, past.start.plusDays(5L + i * 3)) }
        members.take(6).forEach { m -> add(m, past, AchievementType.ATTENDED_DISCUSSION, past.end.plusDays(1)) }
        add(members[0], current, AchievementType.FINISHED_COMMON_BOOK, current.start.plusDays(6))
        add(members[2], current, AchievementType.FINISHED_COMMON_BOOK, current.start.plusDays(13))
        add(members[1], past, AchievementType.FINISHED_OTHER_BOOK, past.start.plusDays(20), "عالم صوفي")
        add(members[4], current, AchievementType.FINISHED_OTHER_BOOK, current.start.plusDays(9), "قواعد العشق الأربعون")
        add(members[0], current, AchievementType.FINISHED_OTHER_BOOK, current.start.plusDays(15), "رجال في الشمس")
        return MarathonData(
            marathon = Marathon("sample", "ماراثون نادي الكتاب", inviteCode = "TEST26"),
            members = members,
            challenges = listOf(past, current, next),
            entries = entries,
            achievements = ach,
        )
    }
}
