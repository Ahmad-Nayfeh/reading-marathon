package com.readingmarathon.core

import java.time.LocalDate

/** Why an action was refused, in Arabic, ready to show. */
class RuleViolation(message: String) : IllegalArgumentException(message)

object Validation {

    fun day(date: LocalDate, minutes: Int, today: LocalDate) {
        if (date.isAfter(today)) throw RuleViolation("لا يمكن تسجيل قراءة ليوم لم يأتِ بعد.")
        if (date.isBefore(today.minusDays(Limits.BACKDATE_DAYS))) {
            throw RuleViolation("يمكن تسجيل الأيام السابقة حتى ${Limits.BACKDATE_DAYS} يوماً فقط.")
        }
        if (minutes < 0) throw RuleViolation("الدقائق لا تكون سالبة.")
        if (minutes > Limits.MAX_MINUTES_PER_DAY) throw RuleViolation("أكثر من 16 ساعة في يوم واحد؟ راجع الرقم.")
    }

    fun personName(name: String) {
        val n = name.trim()
        if (n.isEmpty()) throw RuleViolation("اكتب الاسم.")
        if (n.length > Limits.MAX_NAME) throw RuleViolation("الاسم أطول من ${Limits.MAX_NAME} حرفاً.")
    }

    /** A name for a reader without the app must not clash with another member of the group. */
    fun memberName(name: String, existing: List<Member>, selfId: String? = null) {
        personName(name)
        if (existing.any { it.id != selfId && it.active && Text.normalize(it.name) == Text.normalize(name) }) {
            throw RuleViolation("في المجموعة عضو بهذا الاسم.")
        }
    }

    fun group(name: String, about: String, rules: Rules) {
        if (name.isBlank()) throw RuleViolation("اكتب اسم المجموعة.")
        if (name.trim().length > Limits.MAX_TITLE) throw RuleViolation("اسم المجموعة طويل جداً.")
        if (about.length > Limits.MAX_ABOUT) throw RuleViolation("الوصف أطول من ${Limits.MAX_ABOUT} حرفاً.")
        if (!rules.isValid()) throw RuleViolation("دقائق النقطة بين 1 و600، ومكافأة إنهاء الكتاب بين 0 و10000.")
    }

    /** Challenges may not overlap, so every day belongs to at most one book. */
    fun challenge(c: Challenge, others: List<Challenge>) {
        if (c.book.title.isBlank()) throw RuleViolation("اكتب عنوان الكتاب.")
        if (c.book.title.length > Limits.MAX_TITLE || c.book.author.length > Limits.MAX_TITLE) throw RuleViolation("العنوان طويل جداً.")
        if (c.end.isBefore(c.start)) throw RuleViolation("تاريخ النهاية قبل تاريخ البداية.")
        if (c.lengthDays > 366) throw RuleViolation("مدة التحدي أطول من سنة.")
        val clash = others.firstOrNull { it.id != c.id && !(c.end.isBefore(it.start) || c.start.isAfter(it.end)) }
        if (clash != null) {
            throw RuleViolation("التواريخ تتداخل مع «${clash.book.title}» (${Dates.short(clash.start)} – ${Dates.short(clash.end)}).")
        }
    }

    fun finish(c: Challenge, date: LocalDate) {
        if (date.isBefore(c.start)) throw RuleViolation("لم يبدأ هذا التحدي بعد.")
        if (!c.acceptsFinishOn(date)) throw RuleViolation("انتهت مهلة تسجيل إنهاء هذا الكتاب.")
    }
}
