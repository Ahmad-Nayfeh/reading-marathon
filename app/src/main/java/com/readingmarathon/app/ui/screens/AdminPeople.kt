package com.readingmarathon.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.readingmarathon.app.data.AdminOverview
import com.readingmarathon.app.data.AdminUser
import com.readingmarathon.app.data.AdminUserDetail
import com.readingmarathon.app.data.Announcement
import com.readingmarathon.app.ui.LocalVm
import com.readingmarathon.app.ui.components.EmptyState
import com.readingmarathon.app.ui.components.GhostButton
import com.readingmarathon.app.ui.components.ListDivider
import com.readingmarathon.app.ui.components.PaperCard
import com.readingmarathon.app.ui.components.PrimaryButton
import com.readingmarathon.app.ui.components.StatRow
import com.readingmarathon.app.ui.components.Tag
import com.readingmarathon.core.Dates
import com.readingmarathon.core.Fmt
import com.readingmarathon.core.Limits
import com.readingmarathon.core.Stats
import java.time.LocalDate

// ---- Users ----

@Composable
internal fun AdminUsers(o: AdminOverview, refresh: () -> Unit) {
    var q by rememberSaveable { mutableStateOf("") }
    var open by remember { mutableStateOf<AdminUser?>(null) }
    val today = LocalDate.now()
    OutlinedTextField(q, { q = it }, leadingIcon = { Icon(Icons.Rounded.Search, null) }, placeholder = { Text("اسم أو بريد") }, singleLine = true, modifier = Modifier.fillMaxWidth())
    Spacer(Modifier.height(10.dp))
    val list = o.users.filter { q.isBlank() || (it.email ?: "").contains(q.trim(), true) || (it.name ?: "").contains(q.trim(), true) }
    PaperCard(padding = PaddingValues(vertical = 4.dp)) {
        if (list.isEmpty()) Text("لا حسابات.", Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        list.take(200).forEachIndexed { i, u ->
            if (i > 0) ListDivider()
            Row(Modifier.fillMaxWidth().clickable { open = u }.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(u.name?.takeIf { it.isNotBlank() } ?: u.email ?: "دون اسم", style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        buildString {
                            if (u.email != null && !u.name.isNullOrBlank()) append(u.email).append(" · ")
                            append("${u.groups} مجموعة · ${Fmt.hours(u.minutes.toDouble())} ساعة")
                            if (u.lastSeen > 0) append(" · ${Dates.relative(day(u.lastSeen), today)}")
                        },
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                if (u.uid in o.banned) Tag("موقوف", strong = true) else if (u.isGuest) Tag("دون Google")
            }
        }
    }
    open?.let { u -> UserSheet(u.uid, onChanged = refresh, onDismiss = { open = null }) }
}

/** One account: its numbers and groups, and what the admin can do about it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UserSheet(uid: String, onChanged: () -> Unit, onDismiss: () -> Unit) {
    val vm = LocalVm.current
    var d by remember { mutableStateOf<AdminUserDetail?>(null) }
    var version by remember { mutableStateOf(0) }
    var renaming by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf<Pair<String, () -> Unit>?>(null) }
    LaunchedEffect(uid, version) { vm.run(quiet = true) { d = vm.backend.adminUser(uid) } }
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    fun done(msg: String, block: suspend () -> Unit) = vm.run(msg, then = { version++; onChanged() }) { block() }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state, containerColor = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 16.dp)) {
            val x = d ?: run { Loading(); return@Column }
            val u = x.user
            Text(u.name?.takeIf { it.isNotBlank() } ?: "دون اسم", style = MaterialTheme.typography.headlineSmall)
            Text(
                listOf(u.email ?: "دون حساب Google", if (u.createdAt > 0) "أُنشئ ${Dates.long(day(u.createdAt))}" else "").filter { it.isNotBlank() }.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (x.banned) { Spacer(Modifier.height(6.dp)); Tag("موقوف", strong = true) }
            Spacer(Modifier.height(14.dp))
            val today = LocalDate.now()
            val stats = Stats.personal(x.days, today)
            StatRow(
                Fmt.hours(u.minutes.toDouble()) to "ساعة منذ البداية",
                "${x.days.count { it.minutes > 0 }}" to "يوم قراءة",
                "${stats.currentStreak}" to "سلسلته الحالية",
            )
            Spacer(Modifier.height(10.dp))
            Text("الهدف اليومي: ${Fmt.minutes(x.goal)} · آخر قراءة: ${x.days.firstOrNull { it.minutes > 0 }?.let { Dates.relative(it.date, today) } ?: "لا قراءات"}", style = MaterialTheme.typography.bodySmall)
            Text("المجموعات: " + x.groups.joinToString("، ") { it.second }.ifBlank { "لا شيء" }, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(16.dp))
            GhostButton("تغيير الاسم", onClick = { renaming = true }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            GhostButton(
                if (x.banned) "إعادة تفعيل الحساب" else "إيقاف الحساب",
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    if (x.banned) done("أُعيد تفعيل الحساب.") { vm.backend.adminSetBanned(uid, false) }
                    else confirm = "إيقاف هذا الحساب؟ لن يستطيع قراءة أو كتابة أي شيء حتى تعيد تفعيله." to { done("أُوقف الحساب.") { vm.backend.adminSetBanned(uid, true) } }
                },
            )
            Spacer(Modifier.height(8.dp))
            GhostButton("حذف كل بياناته", color = MaterialTheme.colorScheme.error, modifier = Modifier.fillMaxWidth(), onClick = {
                confirm = "حذف سجلّه وملفه ومكانه في كل المجموعات؟ لا يمكن التراجع." to {
                    vm.run("حُذفت بياناته.", then = { onChanged(); onDismiss() }) { vm.backend.adminDeleteUserData(uid) }
                }
            })
            if (renaming) NameDialogAdmin(u.name.orEmpty(), onDismiss = { renaming = false }) { n -> renaming = false; done("حُفظ الاسم.") { vm.backend.adminRenameUser(uid, n) } }
        }
    }
    confirm?.let { (text, go) ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text(text) },
            confirmButton = { TextButton(onClick = { confirm = null; go() }) { Text("تأكيد", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("إلغاء") } },
        )
    }
}

@Composable
private fun NameDialogAdmin(current: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var name by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("اسم القارئ") },
        text = { OutlinedTextField(name, { name = it.take(Limits.MAX_NAME) }, singleLine = true, modifier = Modifier.fillMaxWidth()) },
        confirmButton = { TextButton(onClick = { onSave(name) }, enabled = name.isNotBlank()) { Text("حفظ") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } },
    )
}

// ---- Announcements ----

@Composable
internal fun AdminNews(version: Int, refresh: () -> Unit) {
    val vm = LocalVm.current
    var list by remember { mutableStateOf<List<Announcement>?>(null) }
    var editing by remember { mutableStateOf<Announcement?>(null) }
    LaunchedEffect(version) { vm.run(quiet = true) { list = vm.backend.adminAnnouncements() } }
    val all = list ?: return Loading()
    PrimaryButton("إعلان جديد", onClick = { editing = Announcement("", "", "") }, icon = Icons.Rounded.Add)
    Spacer(Modifier.height(8.dp))
    Text("يظهر الإعلان النشط أعلى الرئيسية لكل القرّاء حتى يغلقه كل منهم.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(10.dp))
    if (all.isEmpty()) { PaperCard { EmptyState(Icons.Rounded.Campaign, "لا إعلانات", "أعلن عن ميزة جديدة أو تحدٍّ عام أو صيانة.") }; return }
    PaperCard(padding = PaddingValues(vertical = 4.dp)) {
        all.forEachIndexed { i, a ->
            if (i > 0) ListDivider()
            Row(Modifier.fillMaxWidth().clickable { editing = a }.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(a.title.ifBlank { a.body.take(40) }, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(Dates.long(day(a.createdAt)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Tag(if (a.active) "منشور" else "مخفي", strong = a.active)
            }
        }
    }
    editing?.let { a -> NewsDialog(a, onDone = { editing = null; refresh() }, onDismiss = { editing = null }) }
}

@Composable
private fun NewsDialog(a: Announcement, onDone: () -> Unit, onDismiss: () -> Unit) {
    val vm = LocalVm.current
    var title by remember { mutableStateOf(a.title) }
    var body by remember { mutableStateOf(a.body) }
    var active by remember { mutableStateOf(a.active) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (a.id.isBlank()) "إعلان جديد" else "تعديل الإعلان") },
        text = {
            Column {
                OutlinedTextField(title, { title = it.take(80) }, label = { Text("العنوان") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(body, { body = it.take(400) }, label = { Text("النص") }, minLines = 3, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("منشور", modifier = Modifier.weight(1f))
                    Switch(active, { active = it })
                }
                if (a.id.isNotBlank()) {
                    TextButton(onClick = { vm.run("حُذف الإعلان.", then = onDone) { vm.backend.adminDeleteAnnouncement(a.id) } }) {
                        Text("حذف الإعلان", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = title.isNotBlank() || body.isNotBlank(), onClick = {
                vm.run(if (active) "نُشر الإعلان." else "حُفظ الإعلان.", then = onDone) { vm.backend.adminSaveAnnouncement(a.copy(title = title, body = body, active = active)) }
            }) { Text("حفظ") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } },
    )
}
