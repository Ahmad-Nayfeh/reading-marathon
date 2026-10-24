package com.readingmarathon.app.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.PersonRemove
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.readingmarathon.app.ui.LocalVm
import com.readingmarathon.app.ui.components.Avatar
import com.readingmarathon.app.ui.components.ListDivider
import com.readingmarathon.app.ui.components.RowItem
import com.readingmarathon.core.Fmt
import com.readingmarathon.core.GroupData
import com.readingmarathon.core.Member
import com.readingmarathon.core.Role
import com.readingmarathon.core.Scope
import com.readingmarathon.core.Scoring

/** What a group manager can do with one member. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemberSheet(d: GroupData, m: Member, uid: String, myRole: Role?, onDismiss: () -> Unit) {
    val vm = LocalVm.current
    val gid = d.group.id
    val theirRole = d.group.roles[m.id]
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
                        "${Fmt.duration(score.minutes)} · ${Fmt.days(score.readingDays)}",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
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
