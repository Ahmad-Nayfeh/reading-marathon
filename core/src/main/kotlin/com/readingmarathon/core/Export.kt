package com.readingmarathon.core

import java.time.LocalDate

/** Text a group shares outside the app: a ranking for WhatsApp, and a CSV of every day for Excel. */
object Export {

    fun rankingText(data: GroupData, scope: Scope, today: LocalDate): String = buildString {
        appendLine(data.group.name)
        when (scope) {
            is Scope.OfChallenge -> {
                val c = scope.challenge
                appendLine("«${c.book.title}» · ${Dates.span(c.start, c.end)}")
                if (c.status(today) == ChallengeStatus.ACTIVE) appendLine("اليوم ${c.dayNumber(today)} من ${c.lengthDays}")
            }
            Scope.AllTime -> appendLine("الترتيب منذ البداية")
        }
        appendLine()
        val rows = Scoring.standings(data, scope).filter { it.score.minutes > 0 || it.score.finishes > 0 }
        if (rows.isEmpty()) appendLine("لم يُسجَّل شيء بعد.")
        rows.forEach { s ->
            val sc = s.score
            append("${s.rank}. ${sc.member.name} · ${Fmt.points(sc.points)} · ${Fmt.hours(sc.minutes)} ساعة")
            if (scope is Scope.OfChallenge && sc.finishes > 0) append(" · أتمّ الكتاب")
            appendLine()
        }
        val total = Scoring.entriesIn(data, scope).filter { e -> data.activeMembers.any { it.id == e.memberId } }.sumOf { it.minutes }
        appendLine()
        append("مجموع المجموعة: ${Fmt.hours(total)} ساعة")
    }

    /** One row per member per day, plus finished books. Opens in Excel (UTF-8 with BOM). */
    fun csv(data: GroupData): String {
        val names = data.members.associate { it.id to it.name }
        fun q(s: String) = if (s.any { it == ',' || it == '"' || it == '\n' }) "\"" + s.replace("\"", "\"\"") + "\"" else s
        val finished = data.finishes.groupBy { it.memberId to it.date }
        return buildString {
            append('﻿')
            appendLine("التاريخ,العضو,الدقائق,الكتاب المشترك,أنهى الكتاب")
            data.entries.sortedWith(compareBy<Entry> { it.date }.thenBy { names[it.memberId].orEmpty() }).forEach { e ->
                val c = data.challenges.firstOrNull { it.contains(e.date) }
                val done = finished[e.memberId to e.date].orEmpty().isNotEmpty()
                appendLine(listOf(e.date.toString(), q(names[e.memberId] ?: e.memberId), e.minutes.toString(), q(c?.book?.title.orEmpty()), if (done) "نعم" else "").joinToString(","))
            }
        }
    }

    /** The reader's own log, one row per book per day, for "export my data". Opens in Excel. */
    fun myReads(reads: List<Read>, shelf: List<ShelfBook>, titleOf: (String) -> String?): String {
        val books = shelf.associateBy { it.bookId }
        return buildString {
            append('\uFEFF')
            appendLine("التاريخ,الكتاب,المؤلف,الدقائق")
            reads.filter { it.minutes > 0 }.sortedWith(compareBy<Read> { it.date }.thenBy { it.bookId }).forEach { r ->
                val b = books[r.bookId]
                appendLine(listOf(r.date.toString(), q(b?.title ?: titleOf(r.bookId) ?: r.bookId), q(b?.author.orEmpty()), r.minutes.toString()).joinToString(","))
            }
        }
    }

    /** The reader's shelf, one row per book. */
    fun myShelf(shelf: List<ShelfBook>): String = buildString {
        append('\uFEFF')
        appendLine("الكتاب,المؤلف,الحالة,الدقائق")
        shelf.sortedBy { it.status.ordinal }.forEach { b ->
            val status = when (b.status) { ShelfStatus.READING -> "أقرؤه"; ShelfStatus.FINISHED -> "أتممته"; ShelfStatus.WANT -> "أريد قراءته" }
            appendLine(listOf(q(b.title), q(b.author), status, b.minutes.toString()).joinToString(",")) 
        }
    }

    private fun q(s: String) = if (s.any { it == ',' || it == '"' || it == '\n' }) "\"" + s.replace("\"", "\"\"") + "\"" else s
}
