package com.readingmarathon.core

import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Import of the legacy Google Form responses ("Form Responses 1" exported as CSV).
 *
 * Columns are found by their Arabic question titles (both legacy variants), dates may be
 * DD/MM/YYYY, D/M/YYYY, YYYY-MM-DD or M/D/YYYY (US locale sheets), durations are H:MM:SS.
 * Rows for the same member and day are merged into one entry (minutes summed, quotes OR-ed).
 */
object CsvImport {

    data class Row(
        val name: String,
        val date: LocalDate,
        val commonMinutes: Int,
        val otherMinutes: Int,
        val quoteCommon: Boolean,
        val quoteOther: Boolean,
        val finishedCommon: Boolean,
        val finishedOther: Boolean,
        val attended: Boolean,
        val timestamp: String,
    )

    data class Parsed(val rows: List<Row>, val skipped: List<String>)

    data class Plan(
        val entries: List<Entry>,
        val achievements: List<Achievement>,
        val newMembers: List<Member>,
        val outsideChallenges: Int,
        val skipped: List<String>,
    )

    fun parse(csv: String): Parsed {
        val table = parseCsv(csv)
        if (table.isEmpty()) return Parsed(emptyList(), listOf("الملف فارغ."))
        val header = table.first().map { it.trim() }
        fun col(vararg starts: String): Int = header.indexOfFirst { h -> starts.any { h.startsWith(it) } }
        val iName = col("اسمك")
        val iDate = col("تاريخ القراءة")
        val iCommon = col("مدة قراءة الكتاب المشترك")
        val iOther = col("مدة قراءة كتاب آخر")
        val iQuotes = col("ما هي الاقتباسات")
        val iAch = col("إنجازات الكتب والنقاش")
        val iTs = header.indexOfFirst { it.equals("Timestamp", true) || it.startsWith("الطابع الزمني") }
        if (iName < 0 || iDate < 0) {
            return Parsed(emptyList(), listOf("لم أجد عمودي «اسمك» و«تاريخ القراءة». تأكد أن الملف مُصدَّر من تبويب Form Responses 1."))
        }
        val rows = mutableListOf<Row>()
        val skipped = mutableListOf<String>()
        table.drop(1).forEachIndexed { i, r ->
            fun cell(idx: Int) = if (idx in r.indices) r[idx].trim() else ""
            val line = i + 2
            val name = cell(iName)
            if (name.isEmpty()) { skipped += "السطر $line: بلا اسم"; return@forEachIndexed }
            val date = parseDate(cell(iDate))
            if (date == null) { skipped += "السطر $line: تاريخ غير مفهوم «${cell(iDate)}»"; return@forEachIndexed }
            val quotes = cell(iQuotes)
            val ach = cell(iAch)
            rows += Row(
                name = name,
                date = date,
                commonMinutes = parseDuration(cell(iCommon)),
                otherMinutes = parseDuration(cell(iOther)),
                quoteCommon = quotes.contains("الكتاب المشترك"),
                quoteOther = quotes.contains("كتاب آخر"),
                finishedCommon = ach.contains("أنهيت الكتاب المشترك"),
                finishedOther = ach.contains("أنهيت كتاباً آخر") || ach.contains("أنهيت كتابا آخر"),
                attended = ach.contains("حضرت جلسة النقاش"),
                timestamp = cell(iTs),
            )
        }
        return Parsed(rows, skipped)
    }

    /** Turns parsed rows into entries/achievements for [data], creating members for unknown names. */
    fun plan(parsed: Parsed, data: MarathonData, newId: () -> String = { Ids.newId("m_") }): Plan {
        val byName = data.members.associateBy { Text.normalize(it.name) }.toMutableMap()
        val newMembers = mutableListOf<Member>()
        fun memberFor(name: String): Member = byName.getOrPut(Text.normalize(name)) {
            Member(newId(), name.trim()).also { newMembers += it }
        }
        val merged = LinkedHashMap<String, Entry>()
        val achievements = mutableListOf<Achievement>()
        var outside = 0
        val otherCount = HashMap<String, Int>()
        for (r in parsed.rows.sortedBy { it.timestamp }) {
            val m = memberFor(r.name)
            val key = Entry.entryId(m.id, r.date)
            val prev = merged[key]
            merged[key] = Entry(
                memberId = m.id,
                date = r.date,
                commonMinutes = (prev?.commonMinutes ?: 0) + r.commonMinutes,
                otherMinutes = (prev?.otherMinutes ?: 0) + r.otherMinutes,
                quoteCommon = (prev?.quoteCommon ?: false) || r.quoteCommon,
                quoteOther = (prev?.quoteOther ?: false) || r.quoteOther,
            ).let { it.copy(commonMinutes = minOf(it.commonMinutes, Limits.MAX_MINUTES_PER_DAY), otherMinutes = minOf(it.otherMinutes, Limits.MAX_MINUTES_PER_DAY - minOf(it.commonMinutes, Limits.MAX_MINUTES_PER_DAY))) }
            val c = data.challenges.firstOrNull { it.acceptsAchievementOn(r.date) }
            if (c == null) {
                if (r.finishedCommon || r.attended || r.finishedOther) outside++
                continue
            }
            fun has(type: AchievementType) = (data.achievements + achievements).any { it.memberId == m.id && it.challengeId == c.id && it.type == type }
            if (r.finishedCommon && !has(AchievementType.FINISHED_COMMON_BOOK)) {
                achievements += Achievement(Ids.newId("a_"), m.id, c.id, AchievementType.FINISHED_COMMON_BOOK, r.date)
            }
            if (r.attended && !has(AchievementType.ATTENDED_DISCUSSION)) {
                achievements += Achievement(Ids.newId("a_"), m.id, c.id, AchievementType.ATTENDED_DISCUSSION, r.date)
            }
            if (r.finishedOther) {
                // Legacy forms never asked for the title; number them so each finish stays unique.
                val n = otherCount.merge(m.id, 1, Int::plus)!!
                achievements += Achievement(Ids.newId("a_"), m.id, c.id, AchievementType.FINISHED_OTHER_BOOK, r.date, "كتاب آخر (مستورد) $n")
            }
        }
        val existing = data.entries.associateBy { it.id }
        val entries = merged.values.filter { it.id !in existing }
        return Plan(entries, achievements, newMembers, outside, parsed.skipped)
    }

    fun parseDuration(s: String): Int {
        if (s.isBlank()) return 0
        val parts = s.trim().split(":").map { it.trim().toIntOrNull() ?: return 0 }
        return when (parts.size) {
            3 -> parts[0] * 60 + parts[1] + (if (parts[2] >= 30) 1 else 0)
            2 -> parts[0] * 60 + parts[1]
            1 -> parts[0]
            else -> 0
        }.coerceAtLeast(0)
    }

    private val formats = listOf("d/M/yyyy", "yyyy-MM-dd", "d-M-yyyy", "yyyy/M/d").map { DateTimeFormatter.ofPattern(it) }

    fun parseDate(raw: String): LocalDate? {
        val s = raw.trim().substringBefore(' ')
        if (s.isEmpty()) return null
        for (f in formats) runCatching { return LocalDate.parse(s, f) }
        return null
    }

    /** RFC 4180 CSV parser (quotes, escaped quotes, newlines in cells, BOM). */
    fun parseCsv(text: String): List<List<String>> {
        val src = text.removePrefix("﻿")
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val cell = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < src.length) {
            val ch = src[i]
            if (inQuotes) {
                if (ch == '"') {
                    if (i + 1 < src.length && src[i + 1] == '"') { cell.append('"'); i++ } else inQuotes = false
                } else cell.append(ch)
            } else when (ch) {
                '"' -> inQuotes = true
                ',' -> { row += cell.toString(); cell.clear() }
                '\r' -> {}
                '\n' -> { row += cell.toString(); cell.clear(); if (row.any { it.isNotBlank() }) rows += row; row = mutableListOf() }
                else -> cell.append(ch)
            }
            i++
        }
        row += cell.toString()
        if (row.any { it.isNotBlank() }) rows += row
        return rows
    }
}
