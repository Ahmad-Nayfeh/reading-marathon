package com.readingmarathon.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.readingmarathon.app.ui.components.Avatar
import com.readingmarathon.app.ui.theme.Brand
import com.readingmarathon.core.Achievement
import com.readingmarathon.core.AchievementType
import com.readingmarathon.core.Dates
import com.readingmarathon.core.Entry
import com.readingmarathon.core.Fmt
import com.readingmarathon.core.Ids
import com.readingmarathon.core.Limits
import com.readingmarathon.core.Scoring
import com.readingmarathon.core.Validation
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Logging a day of reading. One entry per member per day: opening a day that already has an entry
 * edits it. Achievements (finished a book, attended the discussion) are logged from the same sheet.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun LogSheet(ctx: Ctx, initialMember: String?, initialDate: LocalDate, onDismiss: () -> Unit) {
    val d = ctx.data
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var memberId by remember { mutableStateOf(initialMember ?: d.activeMembers.singleOrNull()?.id) }
    var date by remember { mutableStateOf(initialDate) }
    var pickDate by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet, containerColor = MaterialTheme.colorScheme.surface) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp),
        ) {
            Text("تسجيل القراءة", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(12.dp))

            // Who
            if (ctx.isOrganizer && initialMember == null) {
                Text("القارئ", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(6.dp))
                if (d.activeMembers.isEmpty()) {
                    Text("أضف القرّاء أولاً من صفحة الإدارة.", color = MaterialTheme.colorScheme.error)
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    d.activeMembers.forEach { m ->
                        FilterChip(
                            selected = memberId == m.id,
                            onClick = { memberId = m.id },
                            label = { Text(m.name) },
                            leadingIcon = { Avatar(m.name, m.id, size = 22.dp) },
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
            } else {
                memberId?.let(d::member)?.let { m ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Avatar(m.name, m.id, size = 32.dp)
                        Spacer(Modifier.width(10.dp))
                        Text(m.name, style = MaterialTheme.typography.titleMedium)
                    }
                    Spacer(Modifier.height(12.dp))
                }
            }

            // When
            Text("اليوم", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                FilterChip(date == ctx.today, { date = ctx.today }, { Text("اليوم") })
                FilterChip(date == ctx.today.minusDays(1), { date = ctx.today.minusDays(1) }, { Text("أمس") })
                val other = date != ctx.today && date != ctx.today.minusDays(1)
                FilterChip(
                    other, { pickDate = true },
                    { Text(if (other) "${Dates.weekday(date)} ${Dates.short(date)}" else "يوم آخر") },
                    leadingIcon = { Icon(Icons.Rounded.CalendarMonth, null, Modifier.size(18.dp)) },
                )
            }
            Spacer(Modifier.height(16.dp))

            val mid = memberId
            if (mid == null) {
                Spacer(Modifier.height(24.dp))
                return@Column
            }
            // Re-read the form whenever the member or the day changes.
            androidx.compose.runtime.key(mid, date) { EntryForm(ctx, mid, date, onDismiss) }
        }
    }

    if (pickDate) {
        val earliest = if (ctx.isOrganizer) LocalDate.of(2000, 1, 1) else ctx.today.minusDays(Limits.MEMBER_BACKDATE_DAYS)
        val state = rememberDatePickerState(
            initialSelectedDateMillis = date.toEpochMillis(),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                    val day = Instant.ofEpochMilli(utcTimeMillis).atZone(ZoneOffset.UTC).toLocalDate()
                    return !day.isAfter(ctx.today) && !day.isBefore(earliest)
                }
            },
        )
        DatePickerDialog(
            onDismissRequest = { pickDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { date = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
                    pickDate = false
                }) { Text("اختيار") }
            },
            dismissButton = { TextButton(onClick = { pickDate = false }) { Text("إلغاء") } },
        ) { DatePicker(state, showModeToggle = false) }
    }
}

private fun LocalDate.toEpochMillis(): Long = atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()

@Composable
private fun EntryForm(ctx: Ctx, memberId: String, date: LocalDate, onDone: () -> Unit) {
    val d = ctx.data
    val existing = d.entries.firstOrNull { it.memberId == memberId && it.date == date }
    val challenge = d.challengeOn(date)
    val achChallenge = d.timeline.lastOrNull { it.acceptsAchievementOn(date) }
    val rules = challenge?.rules

    var common by remember { mutableIntStateOf(existing?.commonMinutes ?: 0) }
    var other by remember { mutableIntStateOf(existing?.otherMinutes ?: 0) }
    var quoteCommon by remember { mutableStateOf(existing?.quoteCommon ?: false) }
    var quoteOther by remember { mutableStateOf(existing?.quoteOther ?: false) }
    var finishedCommon by remember { mutableStateOf(false) }
    var attended by remember { mutableStateOf(false) }
    var finishedOther by remember { mutableStateOf(false) }
    var otherTitle by remember { mutableStateOf("") }

    val mine = d.achievements.filter { it.memberId == memberId && it.challengeId == achChallenge?.id }
    val hasFinished = mine.any { it.type == AchievementType.FINISHED_COMMON_BOOK }
    val hasAttended = mine.any { it.type == AchievementType.ATTENDED_DISCUSSION }

    if (existing != null) {
        Surface(color = Brand.colors.goldSoft, shape = RoundedCornerShape(12.dp)) {
            Text(
                "لديك تسجيل لهذا اليوم (${Fmt.duration(existing.totalMinutes)}). التعديل يستبدله.",
                Modifier.padding(horizontal = 12.dp, vertical = 8.dp), style = MaterialTheme.typography.bodySmall,
            )
        }
        Spacer(Modifier.height(12.dp))
    }

    MinutesField(
        title = challenge?.let { "الكتاب المشترك: ${it.book.title}" } ?: "الكتاب المشترك",
        hint = if (challenge == null) "لا يوجد تحدٍّ في هذا اليوم؛ تُحفظ الدقائق في سجلك دون نقاط." else null,
        value = common, onChange = { common = it },
    )
    Spacer(Modifier.height(14.dp))
    MinutesField("كتب أخرى", null, other) { other = it }
    Spacer(Modifier.height(10.dp))

    ToggleRow("شاركت اقتباساً من الكتاب المشترك", quoteCommon) { quoteCommon = it }
    ToggleRow("شاركت اقتباساً من كتاب آخر", quoteOther) { quoteOther = it }

    if (achChallenge != null) {
        Spacer(Modifier.height(10.dp))
        Text("إنجازات «${achChallenge.book.title}»", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        CheckRow(if (hasFinished) "أنهيت الكتاب المشترك (مسجّل)" else "أنهيت الكتاب المشترك", finishedCommon || hasFinished, enabled = !hasFinished) { finishedCommon = it }
        CheckRow(if (hasAttended) "حضرت جلسة النقاش (مسجّل)" else "حضرت جلسة النقاش", attended || hasAttended, enabled = !hasAttended) { attended = it }
        CheckRow("أنهيت كتاباً آخر", finishedOther, enabled = true) { finishedOther = it }
        if (finishedOther) {
            OutlinedTextField(otherTitle, { otherTitle = it }, label = { Text("عنوان الكتاب") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        }
    }

    // Live points preview
    val entry = Entry(memberId, date, common, other, quoteCommon, quoteOther, System.currentTimeMillis())
    val achRules = achChallenge?.rules
    val preview = (rules?.let { Scoring.entryPoints(entry, it).total } ?: 0) + (
        achRules?.let { r ->
            (if (finishedCommon && !hasFinished) r.finishCommonPoints else 0) +
                (if (attended && !hasAttended) r.attendDiscussionPoints else 0) +
                (if (finishedOther) r.finishOtherPoints else 0)
        } ?: 0
        )
    Spacer(Modifier.height(16.dp))
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("المجموع ${Fmt.duration(common + other)}", style = MaterialTheme.typography.titleMedium)
            Text("ستحصل على $preview نقطة", color = Brand.colors.gold, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        }
        if (existing != null) {
            TextButton(onClick = { ctx.vm.act("حُذف تسجيل اليوم.", then = onDone) { it.deleteEntry(existing.id) } }) {
                Text("حذف اليوم", color = MaterialTheme.colorScheme.error)
            }
        }
    }
    Spacer(Modifier.height(12.dp))
    val nothing = common + other == 0 && !quoteCommon && !quoteOther && !finishedCommon && !attended && !finishedOther && existing == null
    Button(
        onClick = {
            val newAchievements = buildList {
                val c = achChallenge ?: return@buildList
                if (finishedCommon && !hasFinished) add(Achievement(Ids.newId("a_"), memberId, c.id, AchievementType.FINISHED_COMMON_BOOK, date))
                if (attended && !hasAttended) add(Achievement(Ids.newId("a_"), memberId, c.id, AchievementType.ATTENDED_DISCUSSION, date))
                if (finishedOther) add(Achievement(Ids.newId("a_"), memberId, c.id, AchievementType.FINISHED_OTHER_BOOK, date, otherTitle.trim()))
            }
            val message = when {
                newAchievements.any { it.type == AchievementType.FINISHED_COMMON_BOOK } -> "مبارك! أنهيت الكتاب المشترك."
                common + other == 0 && existing != null && !quoteCommon && !quoteOther -> "حُذف تسجيل اليوم."
                else -> "حُفظت قراءتك. +$preview نقطة"
            }
            ctx.vm.act(message, then = onDone) { store ->
                // Check everything before writing anything, so a bad title doesn't leave a half-saved day.
                Validation.entry(entry, ctx.today, ctx.isOrganizer)
                newAchievements.fold(ctx.data) { acc, a -> Validation.achievement(a, acc); acc.copy(achievements = acc.achievements + a) }
                if (common + other > 0 || quoteCommon || quoteOther || existing != null) store.upsertEntry(entry, ctx.isOrganizer)
                newAchievements.forEach { store.addAchievement(it) }
            }
        },
        enabled = !nothing,
        modifier = Modifier.fillMaxWidth().height(54.dp),
        shape = RoundedCornerShape(16.dp),
    ) { Text("حفظ", fontWeight = FontWeight.Bold, fontSize = 16.sp) }
    Spacer(Modifier.height(20.dp))
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MinutesField(title: String, hint: String?, value: Int, onChange: (Int) -> Unit) {
    var text by remember(value) { mutableStateOf(if (value == 0) "" else value.toString()) }
    Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 2)
    if (hint != null) Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(6.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        FilledTonalIconButton(onClick = { onChange((value - 5).coerceAtLeast(0)) }) { Icon(Icons.Rounded.Remove, "أنقص 5 دقائق") }
        OutlinedTextField(
            text,
            { raw ->
                val digits = raw.filter(Char::isDigit).take(4)
                text = digits
                onChange((digits.toIntOrNull() ?: 0).coerceAtMost(Limits.MAX_MINUTES_PER_DAY))
            },
            modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
            singleLine = true,
            placeholder = { Text("0", Modifier.fillMaxWidth(), textAlign = TextAlign.Center) },
            suffix = { Text("دقيقة") },
            textStyle = MaterialTheme.typography.titleLarge.copy(textAlign = TextAlign.Center),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
        FilledTonalIconButton(onClick = { onChange((value + 5).coerceAtMost(Limits.MAX_MINUTES_PER_DAY)) }) { Icon(Icons.Rounded.Add, "زد 5 دقائق") }
    }
    Spacer(Modifier.height(6.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(15, 30, 45, 60, 90).forEach { m ->
            FilterChip(value == m, { onChange(m) }, { Text(if (m < 60) "$m د" else Fmt.duration(m)) })
        }
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Switch(checked, onChange)
    }
}

@Composable
private fun CheckRow(label: String, checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked, onChange, enabled = enabled)
        Text(label, style = MaterialTheme.typography.bodyLarge, color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
