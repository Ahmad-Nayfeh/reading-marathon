package com.readingmarathon.core

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class CsvImportTest {
    private val header =
        "Timestamp,اسمك,تاريخ القراءة,مدة قراءة الكتاب المشترك (اختياري),مدة قراءة كتاب آخر (اختياري)," +
            "ما هي الاقتباسات التي أرسلتها اليوم؟ (اختياري),إنجازات الكتب والنقاش (اختر فقط عند حدوثه لأول مرة)"

    private val c = Challenge("c1", Book("كتاب"), LocalDate.of(2025, 7, 1), LocalDate.of(2025, 7, 31))
    private val base = GroupData(Group("g", "م"), listOf(Member("u1", "سارة", "u1")), listOf(c), listOf(Entry("u1", LocalDate.of(2025, 7, 9), 5)))

    @Test
    fun `parses durations, dates and the finish answer`() {
        val csv = """
            $header
            01/07/2025 10:00:00,سارة,02/07/2025,0:45:00,0:20:00,"أرسلت اقتباساً","أنهيت الكتاب المشترك, حضرت جلسة النقاش"
            01/07/2025 11:00:00,خالد,2025-07-03,1:05:40,,,
            bad,سارة,not a date,0:10:00,,,
        """.trimIndent()
        val p = CsvImport.parse(csv)
        assertEquals(2, p.rows.size)
        assertEquals(1, p.skipped.size)
        assertEquals(65, p.rows[0].minutes)
        assertEquals(true, p.rows[0].finishedCommon)
        assertEquals(66, p.rows[1].minutes) // 1:05:40 rounds the 40 s up
    }

    @Test
    fun `plan merges same-day rows, adds unknown names and keeps existing days`() {
        val csv = """
            $header
            a1,ساره,02/07/2025,0:30:00,,,"أنهيت الكتاب المشترك"
            a2,سارة,02/07/2025,0:15:00,,,"أنهيت الكتاب المشترك"
            a3,خالد,10/08/2025,0:40:00,,,"أنهيت الكتاب المشترك"
            a4,سارة,09/07/2025,0:40:00,,,
        """.trimIndent()
        val plan = CsvImport.plan(CsvImport.parse(csv), base) { "new_1" }
        assertEquals(listOf("خالد"), plan.newMembers.map { it.name })
        assertEquals(45, plan.entries.first { it.memberId == "u1" }.minutes)
        assertEquals(1, plan.finishes.size) // Sara once; Khalid's is past the end + grace
        assertEquals(1, plan.existingDays)
        assertEquals(2, plan.entries.size)
    }

    @Test
    fun `csv parser handles quotes, commas and newlines`() {
        val rows = CsvImport.parseCsv("a,\"b,c\",\"d \"\"e\"\"\"\n\"multi\nline\",x\n")
        assertEquals(listOf("a", "b,c", "d \"e\""), rows[0])
        assertEquals(listOf("multi\nline", "x"), rows[1])
    }
}
