package com.readingmarathon.app.ui.admin

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Unarchive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.readingmarathon.app.data.Mode
import com.readingmarathon.app.report.Reports
import com.readingmarathon.app.ui.components.Avatar
import com.readingmarathon.app.ui.components.challengesCount
import com.readingmarathon.app.ui.components.PaperCard
import com.readingmarathon.app.ui.components.SectionTitle
import com.readingmarathon.app.ui.screens.Ctx
import com.readingmarathon.app.ui.screens.Page
import com.readingmarathon.app.ui.theme.Brand
import com.readingmarathon.core.CsvImport
import com.readingmarathon.core.Member
import com.readingmarathon.core.Rules

@Composable
fun AdminScreen(ctx: Ctx) {
    val d = ctx.data
    val context = LocalContext.current
    var plan by remember { mutableStateOf<CsvImport.Plan?>(null) }
    val importCsv = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val text = runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() } }.getOrNull()
        if (text == null) { ctx.vm.say("تعذّرت قراءة الملف."); return@rememberLauncherForActivityResult }
        val parsed = CsvImport.parse(text)
        if (parsed.rows.isEmpty()) ctx.vm.say("لم أجد صفوفاً صالحة. تأكد أنه ملف ردود نموذج Google للماراثون.")
        else plan = CsvImport.plan(parsed, d)
    }

    Page("الإدارة", onBack = { ctx.nav.popBackStack() }) {
        // Marathon
        SectionTitle("الماراثون")
        PaperCard {
            var name by rememberSaveable(d.marathon.name) { mutableStateOf(d.marathon.name) }
            OutlinedTextField(name, { name = it }, label = { Text("اسم الماراثون") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            if (name.trim() != d.marathon.name && name.isNotBlank()) {
                TextButton(onClick = { ctx.vm.act("حُفظ الاسم.") { it.renameMarathon(name.trim()) } }) { Text("حفظ الاسم") }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                when (ctx.session.mode) {
                    Mode.LOCAL -> "محفوظ على هذا الجهاز. صدّر نسخة احتياطية من حين لآخر."
                    Mode.SHARED -> "ماراثون مشترك: كل قارئ يسجّل من هاتفه."
                    Mode.DEMO -> "بيانات تجريبية: التعديلات لا تُحفظ."
                },
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (ctx.session.mode == Mode.SHARED && d.marathon.inviteCode.isNotBlank()) {
                Spacer(Modifier.height(12.dp))
                Text("رمز الدعوة", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(d.marathon.inviteCode, fontSize = 30.sp, fontWeight = FontWeight.Bold, letterSpacing = 6.sp, color = Brand.colors.gold)
                OutlinedButton(onClick = {
                    val link = "https://ahmad-nayfeh.github.io/marathon/join/?code=${d.marathon.inviteCode}"
                    val msg = "انضم إلى «${d.marathon.name}» في تطبيق ماراثون القراءة.\nرمز الدعوة: ${d.marathon.inviteCode}\n$link"
                    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, msg), "مشاركة الدعوة"))
                }) { Text("مشاركة الدعوة") }
            }
        }

        // Members
        SectionTitle("القرّاء", trailing = { Text("${d.activeMembers.size} نشط", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant) })
        PaperCard {
            var newName by rememberSaveable { mutableStateOf("") }
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(newName, { newName = it }, label = { Text("اسم قارئ جديد") }, singleLine = true, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                Button(onClick = { val n = newName; ctx.vm.act("أُضيف $n.", then = { newName = "" }) { it.addMember(n) } }, enabled = newName.isNotBlank()) { Text("إضافة") }
            }
            Spacer(Modifier.height(8.dp))
            val sorted = d.members.sortedWith(compareBy({ !it.active }, { it.name }))
            sorted.forEachIndexed { i, m ->
                if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                MemberRow(ctx, m)
            }
            Spacer(Modifier.height(4.dp))
            Text("الأرشفة تُخفي القارئ من الترتيب وتحفظ سجلّه. يمكن إرجاعه في أي وقت.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        // Challenges
        SectionTitle("التحديات")
        PaperCard {
            Text("${if (d.challenges.isEmpty()) "لا تحديات بعد" else challengesCount(d.challenges.size)}. التحديات لا تتداخل في التواريخ؛ كل يوم ينتمي إلى تحدٍّ واحد على الأكثر.", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(10.dp))
            Button(onClick = { ctx.editChallenge(null) }, modifier = Modifier.fillMaxWidth()) { Text("تحدٍّ جديد") }
            TextButton(onClick = { ctx.nav.navigate("challenges") }, modifier = Modifier.fillMaxWidth()) { Text("عرض كل التحديات") }
        }

        // Rules
        SectionTitle("نظام النقاط الافتراضي")
        PaperCard {
            Text("يُنسخ إلى كل تحدٍّ جديد. تعديله لا يغيّر التحديات الموجودة.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(10.dp))
            RulesEditor(d.marathon.defaultRules, saveLabel = "حفظ النظام الافتراضي") { r -> ctx.vm.act("حُفظ نظام النقاط.") { it.setDefaultRules(r) } }
        }

        // Data
        SectionTitle("البيانات والتقارير")
        PaperCard {
            DataButton("تقرير الماراثون (PDF)") { Reports.shareMarathonPdf(context, d, ctx.today) }
            DataButton("تصدير السجلّ (CSV لبرنامج Excel)") { Reports.shareCsv(context, d) }
            DataButton("نسخة احتياطية كاملة (JSON)") { Reports.shareBackup(context, d) }
            DataButton("استيراد ردود نموذج Google (CSV)") { importCsv.launch(arrayOf("text/*", "application/csv", "application/vnd.ms-excel", "application/octet-stream")) }
            Text(
                "الاستيراد يقرأ ملف ردود النموذج القديم: يضيف القرّاء غير الموجودين، ويدمج تسجيلات اليوم الواحد، ويتجاهل الأيام المسجّلة مسبقاً.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    plan?.let { p ->
        AlertDialog(
            onDismissRequest = { plan = null },
            title = { Text("استيراد السجلّ") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("${p.entries.size} يوم قراءة جديد")
                    Text("${p.achievements.size} إنجاز")
                    Text("${p.newMembers.size} قارئ جديد" + if (p.newMembers.isNotEmpty()) ": ${p.newMembers.take(6).joinToString("، ") { it.name }}" else "")
                    if (p.outsideChallenges > 0) Text("${p.outsideChallenges} إنجاز خارج أي تحدٍّ لن يُستورد", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (p.skipped.isNotEmpty()) Text("${p.skipped.size} صف غير مقروء تم تخطيه", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (ctx.data.challenges.isEmpty()) Text("تنبيه: لا توجد تحديات بعد، فلن تُحسب نقاط ولا إنجازات. أنشئ التحديات أولاً.", color = MaterialTheme.colorScheme.error)
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { plan = null; ctx.vm.act("تمّ الاستيراد.") { it.applyImport(p) } },
                    enabled = p.entries.isNotEmpty() || p.achievements.isNotEmpty() || p.newMembers.isNotEmpty(),
                ) { Text("استيراد") }
            },
            dismissButton = { TextButton(onClick = { plan = null }) { Text("إلغاء") } },
        )
    }
}

@Composable
private fun DataButton(label: String, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) { Text(label) }
}

@Composable
private fun MemberRow(ctx: Ctx, m: Member) {
    var renaming by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Avatar(m.name, m.id, size = 34.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(m.name, style = MaterialTheme.typography.titleSmall, color = if (m.active) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
            val tags = buildList {
                if (!m.active) add("مؤرشف")
                if (m.id == ctx.session.myMemberId) add("أنت")
                if (m.linkedUid != null && m.id != ctx.session.myMemberId) add("مرتبط بهاتف")
            }
            if (tags.isNotEmpty()) Text(tags.joinToString(" · "), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(onClick = { renaming = true }) { Icon(Icons.Rounded.Edit, "تعديل الاسم") }
        IconButton(onClick = { ctx.vm.act(if (m.active) "أُرشف ${m.name}." else "أُعيد ${m.name}.") { it.setMemberActive(m.id, !m.active) } }) {
            Icon(if (m.active) Icons.Rounded.Archive else Icons.Rounded.Unarchive, if (m.active) "أرشفة" else "إرجاع")
        }
    }
    if (renaming) {
        var name by remember { mutableStateOf(m.name) }
        AlertDialog(
            onDismissRequest = { renaming = false },
            title = { Text("تعديل الاسم") },
            text = { OutlinedTextField(name, { name = it }, singleLine = true) },
            confirmButton = { TextButton(onClick = { renaming = false; ctx.vm.act("حُفظ الاسم.") { it.renameMember(m.id, name) } }) { Text("حفظ") } },
            dismissButton = { TextButton(onClick = { renaming = false }) { Text("إلغاء") } },
        )
    }
}

/** Edits the seven point values. Shared by the default rules and each challenge. */
@Composable
fun RulesEditor(initial: Rules, saveLabel: String?, onChange: (Rules) -> Unit) {
    var r by remember(initial) { mutableStateOf(initial) }
    @Composable
    fun field(label: String, value: Int, set: (Int) -> Rules) {
        var text by remember(value) { mutableStateOf(value.toString()) }
        Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            OutlinedTextField(
                text,
                {
                    text = it.filter(Char::isDigit).take(5)
                    r = set(text.toIntOrNull() ?: 0)
                    if (saveLabel == null) onChange(r)
                },
                modifier = Modifier.width(92.dp),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
        }
    }
    field("دقائق لكل نقطة (الكتاب المشترك)", r.minutesPerPointCommon) { r.copy(minutesPerPointCommon = it) }
    field("دقائق لكل نقطة (كتب أخرى)", r.minutesPerPointOther) { r.copy(minutesPerPointOther = it) }
    field("اقتباس من الكتاب المشترك", r.quoteCommonPoints) { r.copy(quoteCommonPoints = it) }
    field("اقتباس من كتاب آخر", r.quoteOtherPoints) { r.copy(quoteOtherPoints = it) }
    field("إنهاء الكتاب المشترك", r.finishCommonPoints) { r.copy(finishCommonPoints = it) }
    field("إنهاء كتاب آخر", r.finishOtherPoints) { r.copy(finishOtherPoints = it) }
    field("حضور جلسة النقاش", r.attendDiscussionPoints) { r.copy(attendDiscussionPoints = it) }
    if (saveLabel != null && r != initial) {
        Spacer(Modifier.height(8.dp))
        Button(onClick = { onChange(r) }, modifier = Modifier.fillMaxWidth()) { Text(saveLabel) }
    }
}
