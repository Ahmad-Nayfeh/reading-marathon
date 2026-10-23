package com.readingmarathon.core

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CsvImportTest {
    private val header =
        "Timestamp,اسمك,تاريخ القراءة,مدة قراءة الكتاب المشترك (اختياري),مدة قراءة كتاب آخر (اختياري)," +
            "ما هي الاقتباسات التي أرسلتها اليوم؟ (اختياري),إنجازات الكتب والنقاش (اختر فقط عند حدوثه لأول مرة)"

    private val c = Challenge("c1", Book("كتاب"), LocalDate.of(2025, 7, 1), LocalDate.of(2025, 7, 31))
    private val base = MarathonData(Marathon("m", "م"), listOf(Member("m1", "سارة")), listOf(c))

    @Test
    fun `parses durations, dates and checkbox answers`() {
        val csv = """
            $header
            01/07/2025 10:00:00,سارة,02/07/2025,0:45:00,0:20:00,"أرسلت اقتباساً من الكتاب المشترك, أرسلت اقتباساً من كتاب آخر","أنهيت الكتاب المشترك, حضرت جلسة النقاش"
            01/07/2025 11:00:00,خالد,2025-07-03,1:05:40,,,
            bad,سارة,not a date,0:10:00,,,
        """.trimIndent()
        val p = CsvImport.parse(csv)
        assertEquals(2, p.rows.size)
        assertEquals(1, p.skipped.size)
        val r = p.rows.first()
        assertEquals(45, r.commonMinutes)
        assertEquals(20, r.otherMinutes)
        assertTrue(r.quoteCommon && r.quoteOther && r.finishedCommon && r.attended)
        assertEquals(66, p.rows[1].commonMinutes) // 1:05:40 rounds the 40 s up
    }

    @Test
    fun `plan merges same-day rows, creates unknown members, dedups achievements`() {
        val csv = """
            $header
            a1,سارة,02/07/2025,0:30:00,,,"أنهيت الكتاب المشترك"
            a2,سارة,02/07/2025,0:15:00,,"أرسلت اقتباساً من الكتاب المشترك","أنهيت الكتاب المشترك"
            a3,خالد,05/08/2025,0:40:00,,,"حضرت جلسة النقاش"
        """.trimIndent()
        val plan = CsvImport.plan(CsvImport.parse(csv), base) { "new_1" }
        assertEquals(listOf("خالد"), plan.newMembers.map { it.name })
        val sara = plan.entries.first { it.memberId == "m1" }
        assertEquals(45, sara.commonMinutes)
        assertTrue(sara.quoteCommon)
        assertEquals(1, plan.achievements.count { it.type == AchievementType.FINISHED_COMMON_BOOK })
        // 5 Aug is past the end + 3-day grace → the attendance is counted as outside.
        assertEquals(1, plan.outsideChallenges)
    }

    @Test
    fun `csv parser handles quotes, commas and newlines`() {
        val rows = CsvImport.parseCsv("a,\"b,c\",\"d \"\"e\"\"\"\n\"multi\nline\",x\n")
        assertEquals(listOf("a", "b,c", "d \"e\""), rows[0])
        assertEquals(listOf("multi\nline", "x"), rows[1])
    }
}
