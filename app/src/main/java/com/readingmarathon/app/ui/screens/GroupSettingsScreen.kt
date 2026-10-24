package com.readingmarathon.app.ui.screens

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.readingmarathon.app.ui.LocalVm
import com.readingmarathon.app.ui.components.GhostButton
import com.readingmarathon.app.ui.components.ListDivider
import com.readingmarathon.app.ui.components.Page
import com.readingmarathon.app.ui.components.PaperCard
import com.readingmarathon.app.ui.components.PrimaryButton
import com.readingmarathon.app.ui.components.RowItem
import com.readingmarathon.app.ui.components.SectionLabel
import com.readingmarathon.core.Dates
import com.readingmarathon.core.Export
import com.readingmarathon.core.GroupData
import com.readingmarathon.core.Limits
import com.readingmarathon.core.Role
import com.readingmarathon.core.Rules
import com.readingmarathon.core.Visibility
import java.io.File

@Composable
fun GroupSettingsScreen(id: String) {
    val vm = LocalVm.current
    val nav = LocalNav.current
    val groups by vm.groups.collectAsStateWithLifecycle()
    val account by vm.account.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val d = groups[id] ?: run { Page("", onBack = nav::back) {}; return }
    val g = d.group
    val owner = account?.isAdmin == true || g.roleOf(account?.uid) == Role.OWNER

    var name by rememberSaveable(g.id) { mutableStateOf(g.name) }
    var about by rememberSaveable(g.id) { mutableStateOf(g.about) }
    var public by rememberSaveable(g.id) { mutableStateOf(g.visibility == Visibility.PUBLIC) }
    var perPoint by rememberSaveable(g.id) { mutableIntStateOf(g.rules.minutesPerPoint) }
    var bonus by rememberSaveable(g.id) { mutableIntStateOf(g.rules.finishBonus) }
    var countAll by rememberSaveable(g.id) { mutableStateOf(g.rules.countAll) }
    var reader by rememberSaveable { mutableStateOf("") }
    var confirmDelete by remember { mutableStateOf(false) }
    val changed = name != g.name || about != g.about || public != (g.visibility == Visibility.PUBLIC) ||
        perPoint != g.rules.minutesPerPoint || bonus != g.rules.finishBonus || countAll != g.rules.countAll

    Page("إعدادات المجموعة", onBack = nav::back, subtitle = g.name) {
        SectionLabel("الدعوة")
        PaperCard {
            Text("رمز الدعوة", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("⁦${g.inviteCode}⁩", fontSize = 30.sp, letterSpacing = 6.sp, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PrimaryButton("شارك الدعوة", onClick = { shareText(context, inviteText(g.name, g.inviteCode), "دعوة إلى المجموعة") }, icon = Icons.Rounded.Share, modifier = Modifier.weight(1f))
                GhostButton("رمز جديد", onClick = { vm.run("صار للمجموعة رمز جديد. الرمز القديم لم يعد يعمل.") { vm.backend.newInviteCode(id) } }, icon = Icons.Rounded.Refresh)
            }
        }

        SectionLabel("الكتب")
        PaperCard(padding = PaddingValues(vertical = 4.dp)) {
            d.timeline.reversed().forEachIndexed { i, c ->
                if (i > 0) ListDivider()
                RowItem("«${c.book.title}»", Dates.span(c.start, c.end), onClick = { nav.book(id, c.id) })
            }
            if (d.challenges.isNotEmpty()) ListDivider()
            RowItem("كتاب جديد", icon = Icons.Rounded.Add, onClick = { nav.book(id, null) })
        }

        SectionLabel("المجموعة")
        PaperCard {
            OutlinedTextField(name, { name = it.take(Limits.MAX_TITLE) }, label = { Text("الاسم") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(about, { about = it.take(Limits.MAX_ABOUT) }, label = { Text("الوصف") }, minLines = 2, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(14.dp))
            Text("من يستطيع الانضمام؟", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Choice("برمز الدعوة فقط", !public, Modifier.weight(1f)) { public = false }
                Choice("أي قارئ", public, Modifier.weight(1f)) { public = true }
            }
            Spacer(Modifier.height(16.dp))
            Text("ما الذي يُحسب في الترتيب؟", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Choice("كل قراءة الأعضاء", countAll, Modifier.weight(1f)) { countAll = true }
                Choice("الكتاب المشترك فقط", !countAll, Modifier.weight(1f)) { countAll = false }
            }
            Text(
                if (countAll) "أي كتاب يقرؤه العضو يُحسب، ويظهر وقت الكتاب المشترك وحده بجانبه." else "السباق على الكتاب المشترك وحده. الكتب الأخرى تبقى في رفّ كل عضو.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            Text("نقطة لكل كم دقيقة؟", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(5, 10, 15, 20, 30).forEach { v -> Choice("$v", perPoint == v, Modifier.weight(1f)) { perPoint = v } }
            }
            Spacer(Modifier.height(16.dp))
            Text("نقاط إتمام الكتاب", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(0, 25, 50, 100).forEach { v -> Choice("$v", bonus == v, Modifier.weight(1f)) { bonus = v } }
            }
            Spacer(Modifier.height(18.dp))
            PrimaryButton("حفظ", busy = busy, enabled = changed && name.isNotBlank(), onClick = {
                vm.run("حُفظت الإعدادات.") { vm.backend.updateGroup(id, name, about, if (public) Visibility.PUBLIC else Visibility.PRIVATE, Rules(perPoint, bonus, countAll)) }
            })
        }

        SectionLabel("قرّاء دون التطبيق")
        PaperCard {
            Text(
                "لمن لا يستخدم التطبيق: أضف اسمه، ثم سجّل قراءته من قائمة الأعضاء. وإن انضم لاحقاً فادمج تاريخه في حسابه.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                OutlinedTextField(reader, { reader = it.take(Limits.MAX_NAME) }, label = { Text("الاسم") }, singleLine = true, modifier = Modifier.weight(1f))
                Spacer(Modifier.padding(4.dp))
                GhostButton("أضف", enabled = reader.isNotBlank(), onClick = { vm.run("أُضيف ${reader.trim()}.", then = { reader = "" }) { vm.backend.addReader(id, reader) } })
            }
        }

        SectionLabel("البيانات")
        PaperCard(padding = PaddingValues(vertical = 4.dp)) {
            RowItem("استيراد من نموذج Google", "ردود النموذج القديم بصيغة CSV.", Icons.Rounded.FileUpload, onClick = { nav.importCsv(id) })
            ListDivider()
            RowItem("تصدير إلى Excel", "كل الأيام والأعضاء في ملف CSV.", Icons.Rounded.FileDownload, onClick = { exportCsv(context, d) })
        }

        if (owner) {
            SectionLabel("حذف المجموعة")
            GhostButton("حذف المجموعة نهائياً", onClick = { confirmDelete = true }, icon = Icons.Rounded.DeleteOutline, color = MaterialTheme.colorScheme.error, modifier = Modifier.fillMaxWidth())
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("حذف «${g.name}»؟") },
            text = { Text("يُحذف الترتيب والكتب وتاريخ المجموعة كله لجميع الأعضاء، ولا يمكن التراجع. قراءة كل عضو تبقى في سجلّه الشخصي.") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; vm.run("حُذفت المجموعة.", then = nav::home) { vm.backend.deleteGroup(id) } }) {
                    Text("حذف", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("إلغاء") } },
        )
    }
}

private fun exportCsv(context: Context, d: GroupData) {
    val dir = File(context.cacheDir, "exports").apply { mkdirs() }
    val file = File(dir, "marathon-${d.group.id}.csv")
    file.writeText(Export.csv(d))
    val uri = FileProvider.getUriForFile(context, context.packageName + ".files", file)
    val send = Intent(Intent.ACTION_SEND).setType("text/csv").putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(Intent.createChooser(send, "تصدير ${d.group.name}"))
}
