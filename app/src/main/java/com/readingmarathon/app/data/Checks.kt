package com.readingmarathon.app.data

import com.readingmarathon.core.Challenge
import com.readingmarathon.core.GroupData
import com.readingmarathon.core.Role
import com.readingmarathon.core.RuleViolation
import com.readingmarathon.core.Validation

/**
 * Permission and consistency checks shared by every backend, so the in-memory one used in tests
 * refuses exactly what the real one does. firestore.rules enforce the same on the server.
 */
object Checks {

    fun role(d: GroupData, uid: String, isAdmin: Boolean): Role? = if (isAdmin) Role.OWNER else d.group.roleOf(uid)

    fun manager(d: GroupData, uid: String, isAdmin: Boolean) {
        if (role(d, uid, isAdmin)?.canManage != true) throw RuleViolation("هذا الإجراء لمشرفي المجموعة فقط.")
    }

    fun owner(d: GroupData, uid: String, isAdmin: Boolean) {
        if (role(d, uid, isAdmin) != Role.OWNER) throw RuleViolation("هذا الإجراء لمالك المجموعة فقط.")
    }

    fun challenge(d: GroupData, c: Challenge) = Validation.challenge(c, d.challenges)

    fun canRemove(d: GroupData, memberId: String, uid: String, isAdmin: Boolean) {
        manager(d, uid, isAdmin)
        val target = d.group.roles[memberId]
        if (target == Role.OWNER) throw RuleViolation("لا يمكن إخراج مالك المجموعة.")
        if (target == Role.ADMIN && role(d, uid, isAdmin) != Role.OWNER) throw RuleViolation("المالك وحده يُخرج المشرفين.")
    }
}
