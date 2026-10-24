package com.readingmarathon.core

import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Import of the legacy Google Form responses ("Form Responses 1" exported as CSV).
 *
 * Columns are found by their Arabic question titles (both legacy variants), dates may be
 * D/M/YYYY or YYYY-MM-DD, durations are H:MM:SS. Minutes of the common book and of other books
 * are added together: the app now counts all reading the same way.
 */
object CsvImport {

    data class Row(
        val name: String,
        val date: LocalDate,
        val minutes: Int,
        val finishedCommon: Boolean,
        val timestamp: String,
    )

    data class Parsed(val rows: List<Row>, val skipped: List<String>)

    data class Plan(
        val newMembers: List<Member>,
        val entries: List<Entry>,
        val finishes: List<Finish>,
        /** Days already in the group, left as they are. */
        val existingDays: Int,
        val skipped: List<String>,
    ) {
        val isEmpty: Boolean get() = newMembers.isEmpty() && entries.isEmpty() && finishes.isEmpty()
    }

    fun parse(csv: String): Parsed {
        val table = parseCsv(csv)
        if (table.isEmpty()) return Parsed(emptyList(), listOf("الملف فارغ."))
        val header = table.first().map { it.trim() }
        fun col(vararg starts: String): Int = header.indexOfFirst { h -> starts.any { h.startsWith(it) } }
        val iName = col("اسمك")
        val iDate = col("تاريخ القراءة")
        val iCommon = col("مدة قراءة الكتاب المشترك")
        val iOther = col("مدة قراءة كتاب آخر")
        val iAch = col("إنجازات الكتب والنقاش")
        val iTs = header.indexOfFirst { it.equals("Timestamp", true) || it.startsWith("الطابع الزمني") }
        if (iName < 0 || iDate < 0) {
            return Parsed(emptyList(), listOf("لم أجد عمودي «اسمك» و«تاريخ القراءة». صدّر تبويب Form Responses 1 بصيغة CSV."))
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
            rows += Row(
                name = name,
                date = date,
                minutes = parseDuration(cell(iCommon)) + parseDuration(cell(iOther)),
                finishedCommon = cell(iAch).contains("أنهيت الكتاب المشترك"),
                timestamp = cell(iTs),
            )
        }
        return Parsed(rows, skipped)
    }

    /**
     * Turns parsed rows into group data. Names are matched to members (Arabic-aware); unknown names
     * become readers without the app, whom an admin can later merge into the real account.
     * Rows for the same reader and day are summed. Days the group already has are left alone.
     */
    fun plan(parsed: Parsed, data: GroupData, newId: () -> String = { Ids.newId("m_") }): Plan {
        val byName = data.members.filter { it.active }.associateBy { Text.normalize(it.name) }.toMutableMap()
        val newMembers = mutableListOf<Member>()
        fun memberFor(name: String): Member = byName.getOrPut(Text.normalize(name)) {
            Member(newId(), name.trim().take(Limits.MAX_NAME)).also { newMembers += it }
        }
        val merged = LinkedHashMap<String, Entry>()
        val finishes = LinkedHashMap<String, Finish>()
        for (r in parsed.rows.sortedBy { it.timestamp }) {
            val m = memberFor(r.name)
            val key = Entry.entryId(m.id, r.date)
            val sum = (merged[key]?.minutes ?: 0) + r.minutes
            merged[key] = Entry(m.id, r.date, sum.coerceAtMost(Limits.MAX_MINUTES_PER_DAY))
            if (r.finishedCommon) {
                val c = data.challenges.firstOrNull { it.acceptsFinishOn(r.date) } ?: continue
                val f = Finish(m.id, c.id, r.date)
                if (data.finishes.none { it.id == f.id }) finishes.putIfAbsent(f.id, f)
            }
        }
        val existing = data.entries.map { it.id }.toSet()
        val fresh = merged.values.filter { it.minutes > 0 && it.id !in existing }
        return Plan(newMembers, fresh, finishes.values.toList(), merged.values.count { it.id in existing }, parsed.skipped)
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
