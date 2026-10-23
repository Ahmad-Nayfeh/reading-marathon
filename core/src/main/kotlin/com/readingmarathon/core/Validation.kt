package com.readingmarathon.core

import java.time.LocalDate

/** Why an action was refused, in Arabic, ready to show. */
class RuleViolation(message: String) : IllegalArgumentException(message)

object Limits {
    const val MAX_MINUTES_PER_DAY = 16 * 60
    const val MEMBER_BACKDATE_DAYS = 14L
    const val MAX_NAME = 40
    const val MAX_TITLE = 120
}

object Validation {

    fun entry(e: Entry, today: LocalDate, asOrganizer: Boolean) {
        if (e.date.isAfter(today)) throw RuleViolation("لا يمكن تسجيل قراءة بتاريخ في المستقبل.")
        if (!asOrganizer && e.date.isBefore(today.minusDays(Limits.MEMBER_BACKDATE_DAYS))) {
            throw RuleViolation("يمكن تسجيل القراءة بأثر رجعي حتى ${Limits.MEMBER_BACKDATE_DAYS} يوماً فقط. اطلب من المشرف تسجيل الأيام الأقدم.")
        }
        if (e.commonMinutes < 0 || e.otherMinutes < 0) throw RuleViolation("الدقائق لا تكون سالبة.")
        if (e.totalMinutes > Limits.MAX_MINUTES_PER_DAY) throw RuleViolation("المجموع أكبر من 16 ساعة في يوم واحد.")
    }

    fun memberName(name: String, existing: List<Member>, selfId: String? = null) {
        val n = name.trim()
        if (n.isEmpty()) throw RuleViolation("اكتب اسم العضو.")
        if (n.length > Limits.MAX_NAME) throw RuleViolation("الاسم أطول من ${Limits.MAX_NAME} حرفاً.")
        if (existing.any { it.id != selfId && Text.normalize(it.name) == Text.normalize(n) }) {
            throw RuleViolation("يوجد عضو بهذا الاسم.")
        }
    }

    /** Challenges may not overlap (a day must belong to at most one challenge). */
    fun challenge(c: Challenge, others: List<Challenge>) {
        if (c.book.title.isBlank()) throw RuleViolation("اكتب عنوان الكتاب المشترك.")
        if (c.book.title.length > Limits.MAX_TITLE) throw RuleViolation("عنوان الكتاب طويل جداً.")
        if (c.end.isBefore(c.start)) throw RuleViolation("تاريخ النهاية قبل تاريخ البداية.")
        if (!c.rules.isValid()) throw RuleViolation("قيم نظام النقاط يجب أن تكون بين 0 و10000.")
        if (c.graceDays !in 0..30) throw RuleViolation("مهلة السماح بين 0 و30 يوماً.")
        val clash = others.firstOrNull { it.id != c.id && !(c.end.isBefore(it.start) || c.start.isAfter(it.end)) }
        if (clash != null) {
            throw RuleViolation("التواريخ تتداخل مع تحدي «${clash.book.title}» (${Dates.short(clash.start)} – ${Dates.short(clash.end)}).")
        }
    }

    fun achievement(a: Achievement, data: MarathonData) {
        val c = data.challenge(a.challengeId) ?: throw RuleViolation("التحدي غير موجود.")
        if (!c.acceptsAchievementOn(a.date)) {
            throw RuleViolation("هذا الإنجاز خارج فترة التحدي ومهلة السماح (${c.graceDays} أيام بعد النهاية).")
        }
        val mine = data.achievements.filter { it.memberId == a.memberId && it.id != a.id }
        when (a.type) {
            AchievementType.FINISHED_COMMON_BOOK, AchievementType.ATTENDED_DISCUSSION ->
                if (mine.any { it.challengeId == a.challengeId && it.type == a.type }) {
                    throw RuleViolation(
                        if (a.type == AchievementType.FINISHED_COMMON_BOOK) "سُجّل إنهاء الكتاب المشترك لهذا التحدي من قبل."
                        else "سُجّل حضور جلسة النقاش لهذا التحدي من قبل.",
                    )
                }
            AchievementType.FINISHED_OTHER_BOOK -> {
                val title = a.bookTitle?.trim().orEmpty()
                if (title.isEmpty()) throw RuleViolation("اكتب عنوان الكتاب الذي أنهيته.")
                if (mine.any { it.type == AchievementType.FINISHED_OTHER_BOOK && Text.normalize(it.bookTitle.orEmpty()) == Text.normalize(title) }) {
                    throw RuleViolation("سُجّل إنهاء «$title» من قبل.")
                }
            }
        }
    }
}

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
