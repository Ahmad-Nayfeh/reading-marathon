package com.readingmarathon.app.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.readingmarathon.app.ui.LocalVm
import com.readingmarathon.app.ui.components.EmptyState
import com.readingmarathon.app.ui.components.GhostButton
import com.readingmarathon.app.ui.components.Page
import com.readingmarathon.app.ui.components.PaperCard
import com.readingmarathon.app.ui.components.PrimaryButton
import com.readingmarathon.app.ui.components.SectionLabel
import com.readingmarathon.app.ui.components.StatRow
import com.readingmarathon.core.Book
import com.readingmarathon.core.CatalogBook
import com.readingmarathon.core.Challenge
import com.readingmarathon.core.CsvImport
import com.readingmarathon.core.Dates
import com.readingmarathon.core.Fmt
import com.readingmarathon.core.Ids
import com.readingmarathon.core.Limits
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** Add or edit a shared book: title, author and dates. */
@Composable
fun ChallengeEditor(groupId: String, challengeId: String?) {
    val vm = LocalVm.current
    val nav = LocalNav.current
    val groups by vm.groups.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val d = groups[groupId] ?: run { Page("", onBack = nav::back) {}; return }
    val today = vm.today()
    val existing = challengeId?.let(d::challenge)
    // A new book starts the day after the last one ends (or today) and runs four weeks.
    val defaultStart = remember { d.timeline.lastOrNull()?.end?.plusDays(1)?.takeIf { it.isAfter(today) } ?: today }
    // The book comes from the shared catalog; a challenge from before the catalog keeps its own title.
    var picked by remember { mutableStateOf(existing?.let { CatalogBook(it.bookId.orEmpty(), it.book.title, it.book.author) }) }
    LaunchedEffect(existing?.bookId) {
        existing?.bookId?.let { id -> runCatching { vm.backend.book(id) }.getOrNull()?.let { picked = it } }
    }
    var start by remember { mutableStateOf(existing?.start ?: defaultStart) }
    var end by remember { mutableStateOf(existing?.end ?: defaultStart.plusDays(27)) }
    var picking by remember { mutableStateOf<Int?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }

    Page(
        if (existing == null) "كتاب جديد" else "تعديل الكتاب",
        onBack = nav::back,
        subtitle = d.group.name,
        actions = { if (existing != null) IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Rounded.DeleteOutline, "حذف الكتاب") } },
    ) {
        SectionLabel("الكتاب")
        BookPicker(picked, onSelect = { picked = it })
        SectionLabel("المدة")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GhostButton("من ${Dates.short(start)}", onClick = { picking = 0 }, icon = Icons.Rounded.CalendarMonth, modifier = Modifier.weight(1f))
            GhostButton("إلى ${Dates.short(end)}", onClick = { picking = 1 }, icon = Icons.Rounded.CalendarMonth, modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(14, 21, 28, 30).forEach { n -> Choice(Fmt.days(n), end == start.plusDays(n - 1L), Modifier.weight(1f)) { end = start.plusDays(n - 1L) } }
        }
        Spacer(Modifier.height(8.dp))
        val days = (end.toEpochDay() - start.toEpochDay() + 1).toInt()
        Text(
            if (days > 0) "${Fmt.days(days)} · ${Dates.span(start, end)}" else "النهاية قبل البداية",
            style = MaterialTheme.typography.bodySmall,
            color = if (days > 0) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
        )
        Text(
            "يمكن تسجيل إتمام الكتاب حتى ${Fmt.days(Limits.FINISH_GRACE_DAYS.toInt())} بعد آخر يوم.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(28.dp))
        PrimaryButton(if (existing == null) "أضف الكتاب" else "حفظ", busy = busy, enabled = picked != null, onClick = {
            val b = picked ?: return@PrimaryButton
            val c = Challenge(existing?.id ?: Ids.newId("c_"), Book(b.title, b.author), start, end, bookId = b.id.ifBlank { null })
            vm.run(if (existing == null) "أُضيف الكتاب." else "حُفظ الكتاب.", then = nav::back) { vm.backend.saveChallenge(groupId, c) }
        })
    }

    picking?.let { which ->
        DateDialog(if (which == 0) start else end, { picking = null }) {
            if (which == 0) { val len = end.toEpochDay() - start.toEpochDay(); start = it; end = it.plusDays(len.coerceAtLeast(0)) } else end = it
        }
    }

    if (confirmDelete && existing != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("حذف «${existing.book.title}»؟") },
            text = { Text("يُحذف الكتاب ومن أتمّه. الدقائق المسجّلة تبقى وتُحسب في الترتيب العام.") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; vm.run("حُذف الكتاب.", then = nav::back) { vm.backend.deleteChallenge(groupId, existing.id) } }) {
                    Text("حذف", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("إلغاء") } },
        )
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
