package com.readingmarathon.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.CallMerge
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.PersonRemove
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.readingmarathon.app.ui.LocalVm
import com.readingmarathon.app.ui.components.Avatar
import com.readingmarathon.app.ui.components.ListDivider
import com.readingmarathon.app.ui.components.PrimaryButton
import com.readingmarathon.app.ui.components.RowItem
import com.readingmarathon.core.Challenge
import com.readingmarathon.core.Dates
import com.readingmarathon.core.Fmt
import com.readingmarathon.core.GroupData
import com.readingmarathon.core.Limits
import com.readingmarathon.core.Member
import com.readingmarathon.core.Role
import com.readingmarathon.core.Scope
import com.readingmarathon.core.Scoring
import java.time.LocalDate

private enum class Mode { MENU, LOG, RENAME, MERGE }

/** What a group manager can do with one member. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemberSheet(d: GroupData, m: Member, uid: String, myRole: Role?, current: Challenge?, onDismiss: () -> Unit) {
    val vm = LocalVm.current
    val today = vm.today()
    val gid = d.group.id
    val theirRole = d.group.roles[m.id]
    var mode by remember { mutableStateOf(Mode.MENU) }
    var confirm by remember { mutableStateOf<Pair<String, () -> Unit>?>(null) }
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    fun act(success: String, block: suspend () -> Unit) = vm.run(success, then = onDismiss) { block() }
    val score = remember(d, m) { Scoring.memberScore(d, m, Scope.AllTime) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state, containerColor = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(bottom = 16.dp)) {
            Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                Avatar(m.name, m.id, size = 44.dp)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(m.name, style = MaterialTheme.typography.headlineSmall)
                    Text(
                        "${Fmt.duration(score.minutes)} · ${Fmt.days(score.readingDays)}" + if (!m.hasApp) " · دون تطبيق" else "",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            when (mode) {
                Mode.MENU -> {
                    if (!m.hasApp) {
                        RowItem("تسجيل قراءة", "يسجّل المشرف عمّن لا يستخدم التطبيق.", Icons.Rounded.Edit, onClick = { mode = Mode.LOG })
                        RowItem("تغيير الاسم", icon = Icons.Rounded.Edit, onClick = { mode = Mode.RENAME })
                        RowItem("دمجه مع حساب", "انضم بالتطبيق؟ انقل تاريخه إلى حسابه.", Icons.AutoMirrored.Rounded.CallMerge, onClick = { mode = Mode.MERGE })
                        if (current != null && current.acceptsFinishOn(today)) {
                            val done = d.finished(m.id, current.id)
                            RowItem(
                                if (done == null) "أتمّ «${current.book.title}»" else "إلغاء إتمام «${current.book.title}»",
                                icon = Icons.Rounded.Flag,
                                onClick = { act(if (done == null) "سُجّل الإتمام." else "أُلغي الإتمام.") { vm.backend.setFinished(gid, current.id, m.id, if (done == null) today else null) } },
                            )
                        }
                    }
                    if (myRole == Role.OWNER && theirRole != null && theirRole != Role.OWNER) {
                        if (theirRole == Role.ADMIN) {
                            RowItem("إلغاء الإشراف", icon = Icons.Rounded.AdminPanelSettings, onClick = { act("أصبح عضواً.") { vm.backend.setRole(gid, m.id, Role.MEMBER) } })
                        } else {
                            RowItem("جعله مشرفاً", "يدير الكتب والأعضاء والإعدادات.", Icons.Rounded.AdminPanelSettings, onClick = { act("أصبح مشرفاً.") { vm.backend.setRole(gid, m.id, Role.ADMIN) } })
                        }
                        RowItem("نقل الملكية إليه", "تبقى أنت مشرفاً.", Icons.Rounded.Verified, onClick = {
                            confirm = "نقل ملكية المجموعة إلى ${m.name}؟" to { act("انتقلت الملكية.") { vm.backend.setRole(gid, m.id, Role.OWNER) } }
                        })
                    }
                    val canRemove = theirRole != Role.OWNER && (theirRole != Role.ADMIN || myRole == Role.OWNER) && m.id != uid
                    if (canRemove) {
                        ListDivider()
                        RowItem("إخراج من المجموعة", "يبقى تاريخه في الكتب السابقة.", Icons.Rounded.PersonRemove, onClick = {
                            confirm = "إخراج ${m.name} من المجموعة؟" to { act("أُخرج من المجموعة.") { vm.backend.removeMember(gid, m.id) } }
                        })
                    }
                }
                Mode.LOG -> ReaderLog(d, m, onDismiss)
                Mode.RENAME -> {
                    var name by remember { mutableStateOf(m.name) }
                    Column(Modifier.padding(horizontal = 20.dp)) {
                        OutlinedTextField(name, { name = it.take(Limits.MAX_NAME) }, label = { Text("الاسم") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(14.dp))
                        PrimaryButton("حفظ", onClick = { act("حُفظ الاسم.") { vm.backend.renameReader(gid, m.id, name) } }, enabled = name.isNotBlank())
                    }
                }
                Mode.MERGE -> {
                    val targets = d.activeMembers.filter { it.hasApp }
                    Text(
                        "اختر حساب ${m.name} في التطبيق. تُضاف دقائقه وإتماماته إلى الحساب، ثم يُزال هذا الاسم.",
                        style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 20.dp),
                    )
                    Spacer(Modifier.height(8.dp))
                    targets.forEach { t ->
                        RowItem(t.name, onClick = {
                            confirm = "دمج «${m.name}» في حساب ${t.name}؟ لا يمكن التراجع." to { act("دُمج التاريخ في حساب ${t.name}.") { vm.backend.mergeReader(gid, m.id, t.id) } }
                        })
                    }
                }
            }
        }
    }

    confirm?.let { (title, go) ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text(title) },
            confirmButton = { TextButton(onClick = { confirm = null; go() }) { Text("تأكيد") } },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("إلغاء") } },
        )
    }
}

/** A manager logs a day for a reader without the app (no two-week limit, for catching up). */
@Composable
private fun ReaderLog(d: GroupData, m: Member, onDone: () -> Unit) {
    val vm = LocalVm.current
    val today = vm.today()
    fun minutesOn(date: LocalDate) = d.entries.firstOrNull { it.memberId == m.id && it.date == date }?.minutes ?: 0
    var date by remember { mutableStateOf(today) }
    var minutes by remember { mutableIntStateOf(minutesOn(today)) }
    Column(Modifier.padding(horizontal = 20.dp)) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items((0..30).map { today.minusDays(it.toLong()) }) { day ->
                Choice(if (day == today) "اليوم" else Dates.short(day), day == date) { date = day; minutes = minutesOn(day) }
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(0, 15, 30, 45, 60, 90).forEach { v -> Choice("$v", minutes == v, Modifier.weight(1f)) { minutes = v } }
        }
        Spacer(Modifier.height(16.dp))
        PrimaryButton("حفظ ${Fmt.minutes(minutes)} · ${Dates.relative(date, today)}", onClick = {
            vm.run("حُفظ.", then = onDone) { vm.backend.setReaderDay(d.group.id, m.id, date, minutes) }
        })
    }
}
