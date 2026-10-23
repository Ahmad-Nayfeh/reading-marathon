package com.readingmarathon.app.ui.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.readingmarathon.app.ui.components.PaperCard
import com.readingmarathon.app.ui.components.SectionTitle
import com.readingmarathon.app.ui.screens.Ctx
import com.readingmarathon.app.ui.screens.Page
import com.readingmarathon.core.Book
import com.readingmarathon.core.Challenge
import com.readingmarathon.core.Dates
import com.readingmarathon.core.Ids
import com.readingmarathon.core.Limits
import com.readingmarathon.core.RuleViolation
import com.readingmarathon.core.Validation
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@Composable
fun ChallengeEditor(ctx: Ctx, id: String?) {
    val d = ctx.data
    val existing = id?.let(d::challenge)
    // A new challenge starts the day after the last one ends (or today) and runs for 4 weeks.
    val defaultStart = remember {
        d.timeline.lastOrNull()?.end?.plusDays(1)?.takeIf { it.isAfter(ctx.today) } ?: ctx.today
    }
    var title by rememberSaveable { mutableStateOf(existing?.book?.title.orEmpty()) }
    var author by rememberSaveable { mutableStateOf(existing?.book?.author.orEmpty()) }
    var start by remember { mutableStateOf(existing?.start ?: defaultStart) }
    var end by remember { mutableStateOf(existing?.end ?: defaultStart.plusDays(27)) }
    var grace by rememberSaveable { mutableStateOf((existing?.graceDays ?: 3).toString()) }
    var rules by remember { mutableStateOf(existing?.rules ?: d.marathon.defaultRules) }
    var picking by remember { mutableStateOf<Int?>(null) } // 0 = start, 1 = end
    var confirmDelete by remember { mutableStateOf(false) }

    Page(
        if (existing == null) "تحدٍّ جديد" else "تعديل التحدي",
        onBack = { ctx.nav.popBackStack() },
        actions = {
            if (existing != null) IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Rounded.Delete, "حذف التحدي") }
        },
    ) {
        SectionTitle("الكتاب المشترك")
        PaperCard {
            OutlinedTextField(title, { title = it.take(Limits.MAX_TITLE) }, label = { Text("عنوان الكتاب") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(author, { author = it.take(Limits.MAX_TITLE) }, label = { Text("المؤلف (اختياري)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        }
        SectionTitle("المدة")
        PaperCard {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                DateButton("البداية", start, Modifier.weight(1f)) { picking = 0 }
                DateButton("النهاية", end, Modifier.weight(1f)) { picking = 1 }
            }
            Spacer(Modifier.height(8.dp))
            val days = (end.toEpochDay() - start.toEpochDay() + 1).toInt()
            Text(if (days > 0) "$days يوماً" else "النهاية قبل البداية", color = if (days > 0) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                grace, { grace = it.filter(Char::isDigit).take(2) },
                label = { Text("أيام السماح بعد النهاية") },
                supportingText = { Text("مهلة لتسجيل إنهاء الكتاب وحضور النقاش بعد آخر يوم.") },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
        }
        SectionTitle("نظام النقاط لهذا التحدي")
        PaperCard { RulesEditor(rules, saveLabel = null) { rules = it } }
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = {
                val c = Challenge(
                    id = existing?.id ?: Ids.newId("c_"),
                    book = Book(title.trim(), author.trim()),
                    start = start, end = end, rules = rules,
                    graceDays = (grace.toIntOrNull() ?: 3).coerceIn(0, 30),
                )
                ctx.vm.act(if (existing == null) "أُنشئ التحدي." else "حُفظ التحدي.", then = { ctx.nav.popBackStack() }) {
                    if (c.book.title.isBlank()) throw RuleViolation("اكتب عنوان الكتاب.")
                    if (!c.rules.isValid()) throw RuleViolation("قيم نظام النقاط يجب أن تكون بين 0 و10000.")
                    Validation.challenge(c, d.challenges)
                    it.upsertChallenge(c)
                }
            },
            modifier = Modifier.fillMaxWidth().height(54.dp),
            shape = RoundedCornerShape(16.dp),
        ) { Text(if (existing == null) "إنشاء التحدي" else "حفظ التعديلات", fontWeight = FontWeight.Bold) }
    }

    picking?.let { which -> DateDialog(if (which == 0) start else end, { picking = null }) { if (which == 0) { start = it; if (end.isBefore(it)) end = it.plusDays(27) } else end = it } }

    if (confirmDelete && existing != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("حذف «${existing.book.title}»؟") },
            text = { Text("ستُحذف إنجازات هذا التحدي ونقاطه. تبقى دقائق القراءة المسجّلة في سجلّ كل قارئ.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    ctx.vm.act("حُذف التحدي.", then = {
                        // Leave the editor, and the deleted challenge's page if we came from it.
                        ctx.nav.popBackStack()
                        if (ctx.nav.currentBackStackEntry?.destination?.route == "challenge/{id}") ctx.nav.popBackStack()
                    }) { it.deleteChallenge(existing.id) }
                }) { Text("حذف", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("إلغاء") } },
        )
    }
}

@Composable
private fun DateButton(label: String, date: LocalDate, modifier: Modifier, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = modifier, shape = RoundedCornerShape(14.dp)) {
        Icon(Icons.Rounded.CalendarMonth, null)
        Spacer(Modifier.width(6.dp))
        Text("$label: ${Dates.short(date)} ${date.year}", maxLines = 1)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateDialog(initial: LocalDate, onDismiss: () -> Unit, onPick: (LocalDate) -> Unit) {
    val state = rememberDatePickerState(initialSelectedDateMillis = initial.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli())
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let { onPick(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                onDismiss()
            }) { Text("اختيار") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } },
    ) { DatePicker(state, showModeToggle = false) }
}
