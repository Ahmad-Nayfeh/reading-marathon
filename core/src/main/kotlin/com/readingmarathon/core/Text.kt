package com.readingmarathon.core

import java.security.SecureRandom
import java.time.DayOfWeek
import java.time.LocalDate

object Text {
    /** Arabic-aware comparison key: trims, folds alef/yaa/taa-marbuta variants, drops tashkeel and tatweel. */
    fun normalize(s: String): String = s.trim()
        .replace(Regex("[\\u064B-\\u0652\\u0670\\u0640]"), "")
        .replace(Regex("[إأآٱ]"), "ا")
        .replace('ى', 'ي')
        .replace('ة', 'ه')
        .replace(Regex("\\s+"), " ")
        .lowercase()
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

object Fmt {
    /** Minutes → hours with one decimal, without a trailing ".0". */
    fun hours(minutes: Double): String {
        val r = Math.round(minutes / 60.0 * 10) / 10.0
        return if (r == r.toLong().toDouble()) r.toLong().toString() else r.toString()
    }

    fun hours(minutes: Int): String = hours(minutes.toDouble())

    /** "1 س 25 د" style compact duration. */
    fun duration(minutes: Int): String {
        val h = minutes / 60
        val m = minutes % 60
        return when {
            h == 0 -> "$m د"
            m == 0 -> "$h س"
            else -> "$h س $m د"
        }
    }

    /** "دقيقة واحدة", "دقيقتان", "5 دقائق", "45 دقيقة". */
    fun minutes(n: Int): String = count(n, "دقيقة واحدة", "دقيقتان", "دقائق", "دقيقة")
    fun days(n: Int): String = count(n, "يوم واحد", "يومان", "أيام", "يوماً")
    fun members(n: Int): String = count(n, "عضو واحد", "عضوان", "أعضاء", "عضواً")
    fun books(n: Int): String = count(n, "كتاب واحد", "كتابان", "كتب", "كتاباً")
    fun points(n: Int): String = count(n, "نقطة واحدة", "نقطتان", "نقاط", "نقطة")
    fun readers(n: Int): String = count(n, "قارئ واحد", "قارئان", "قرّاء", "قارئاً")

    /** Arabic number agreement: 1, 2, 3–10 (plural), 11+ (singular accusative). */
    private fun count(n: Int, one: String, two: String, few: String, many: String): String = when {
        n == 1 -> one
        n == 2 -> two
        n % 100 in 3..10 -> "$n $few"
        else -> "$n $many"
    }
}

object Names {
    fun join(names: List<String>): String = when (names.size) {
        0 -> ""
        1 -> names[0]
        else -> names.dropLast(1).joinToString("، ") + " و" + names.last()
    }
}

object Dates {
    private val MONTHS = listOf(
        "يناير", "فبراير", "مارس", "أبريل", "مايو", "يونيو",
        "يوليو", "أغسطس", "سبتمبر", "أكتوبر", "نوفمبر", "ديسمبر",
    )
    private val WEEKDAYS: Map<DayOfWeek, String> = mapOf(
        DayOfWeek.SATURDAY to "السبت", DayOfWeek.SUNDAY to "الأحد", DayOfWeek.MONDAY to "الاثنين",
        DayOfWeek.TUESDAY to "الثلاثاء", DayOfWeek.WEDNESDAY to "الأربعاء", DayOfWeek.THURSDAY to "الخميس",
        DayOfWeek.FRIDAY to "الجمعة",
    )

    fun long(d: LocalDate): String = "${d.dayOfMonth} ${MONTHS[d.monthValue - 1]} ${d.year}"
    fun short(d: LocalDate): String = "${d.dayOfMonth} ${MONTHS[d.monthValue - 1]}"
    fun month(d: LocalDate): String = MONTHS[d.monthValue - 1]
    fun weekday(d: LocalDate): String = WEEKDAYS.getValue(d.dayOfWeek)
    /** One-letter weekday for chart labels. */
    fun weekdayLetter(d: LocalDate): String = when (d.dayOfWeek) {
        DayOfWeek.SATURDAY -> "س"; DayOfWeek.SUNDAY -> "ح"; DayOfWeek.MONDAY -> "ن"; DayOfWeek.TUESDAY -> "ث"
        DayOfWeek.WEDNESDAY -> "ر"; DayOfWeek.THURSDAY -> "خ"; DayOfWeek.FRIDAY -> "ج"
        null -> ""
    }

    /** "اليوم", "أمس" or the weekday + date. */
    fun relative(d: LocalDate, today: LocalDate): String = when (today.toEpochDay() - d.toEpochDay()) {
        0L -> "اليوم"
        1L -> "أمس"
        else -> "${weekday(d)} ${short(d)}"
    }

    /** Span of a challenge, e.g. "1 – 28 نوفمبر" or "20 نوفمبر – 18 ديسمبر". */
    fun span(start: LocalDate, end: LocalDate): String = when {
        start.year != end.year -> "${long(start)} – ${long(end)}"
        start.month == end.month -> "${start.dayOfMonth} – ${end.dayOfMonth} ${month(end)}"
        else -> "${short(start)} – ${short(end)}"
    }
}
