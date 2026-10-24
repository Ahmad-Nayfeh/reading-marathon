package com.readingmarathon.app.ui.screens

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.NotificationsNone
import androidx.compose.material.icons.rounded.Policy
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.readingmarathon.app.BuildConfig
import com.readingmarathon.app.data.ThemeMode
import com.readingmarathon.app.notify.Reminder
import com.readingmarathon.app.ui.LocalVm
import com.readingmarathon.app.ui.components.Avatar
import com.readingmarathon.app.ui.components.Heatmap
import com.readingmarathon.app.ui.components.ListDivider
import com.readingmarathon.app.ui.components.Page
import com.readingmarathon.app.ui.components.PaperCard
import com.readingmarathon.app.ui.components.RowItem
import com.readingmarathon.app.ui.components.SectionLabel
import com.readingmarathon.app.ui.components.StatRow
import com.readingmarathon.core.Fmt
import com.readingmarathon.core.Limits
import com.readingmarathon.core.Stats
import com.readingmarathon.core.Suggestion
import com.readingmarathon.core.SuggestionStatus
import com.readingmarathon.core.SuggestionType
import com.readingmarathon.app.ui.components.Tag

/** The reader's own numbers and the phone's settings. */
@Composable
fun MeScreen() {
    val vm = LocalVm.current
    val nav = LocalNav.current
    val account by vm.account.collectAsStateWithLifecycle()
    val profile by vm.profile.collectAsStateWithLifecycle()
    val days by vm.days.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val today = vm.today()
    val stats = remember(days, today) { Stats.personal(days, today) }
    var editName by remember { mutableStateOf(false) }
    var pickTime by remember { mutableStateOf(false) }
    val askPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) vm.setReminder(true, settings.reminderHour, settings.reminderMinute) else vm.say("لن تصلك التذكيرات دون إذن الإشعارات.")
    }
    val p = profile ?: return
    val acc = account ?: return

    Page("أنا") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(p.name, acc.uid, size = 56.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(p.name, style = MaterialTheme.typography.headlineSmall)
                Text(if (acc.isGuest) "دون حساب" else acc.email.orEmpty(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = { editName = true }) { Icon(Icons.Rounded.Edit, "تغيير الاسم") }
        }

        SectionLabel("قراءتي")
        PaperCard {
            StatRow(
                Fmt.hours(stats.minutesThisMonth) to "ساعة هذا الشهر",
                "${stats.currentStreak}" to "سلسلتك الحالية",
                "${stats.longestStreak}" to "أطول سلسلة",
            )
            Spacer(Modifier.height(14.dp))
            ListDivider()
            Spacer(Modifier.height(14.dp))
            StatRow(
                "${stats.daysThisMonth}" to "يوم قراءة هذا الشهر",
                Fmt.hours(stats.minutesThisYear) to "ساعة هذا العام",
                Fmt.hours(stats.totalMinutes) to "ساعة منذ البداية",
            )
        }
        Spacer(Modifier.height(10.dp))
        PaperCard {
            val byDay = remember(days) { days.associate { it.date to it.minutes } }
            Heatmap(remember(byDay, today) { Stats.heatmap(byDay, today.minusWeeks(16), today) })
        }

        SectionLabel("هدفي اليومي")
        GoalChips(p.goal) { vm.saveProfile(p.name, it, "صار هدفك ${Fmt.minutes(it)} في اليوم.") }

        var mine by remember { mutableStateOf<List<Suggestion>>(emptyList()) }
        LaunchedEffect(acc.uid) { mine = runCatching { vm.backend.mySuggestions() }.getOrDefault(emptyList()) }
        if (mine.isNotEmpty()) {
            SectionLabel("اقتراحاتي للفهرس")
            PaperCard(padding = PaddingValues(vertical = 4.dp)) {
                mine.take(10).forEachIndexed { i, sg ->
                    if (i > 0) ListDivider()
                    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                when (sg.type) { SuggestionType.NEW_BOOK -> "كتاب جديد"; SuggestionType.FIX -> "تصحيح"; SuggestionType.DUPLICATE -> "كتاب مكرر" } + ": «${sg.title}»",
                                style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), maxLines = 1,
                            )
                            Tag(when (sg.status) { SuggestionStatus.OPEN -> "قيد المراجعة"; SuggestionStatus.ACCEPTED -> "قُبل"; SuggestionStatus.REJECTED -> "لم يُقبل" }, strong = sg.status == SuggestionStatus.ACCEPTED)
                        }
                        if (sg.reply.isNotBlank()) Text("الرد: ${sg.reply}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        SectionLabel("الإعدادات")
        PaperCard(padding = PaddingValues(vertical = 4.dp)) {
            RowItem(
                "تذكير يومي",
                if (settings.reminderEnabled) "الساعة ${time(settings.reminderHour, settings.reminderMinute)} · فيه أزرار تسجيل سريع" else "إشعار مسائي تسجّل منه دون فتح التطبيق",
                Icons.Rounded.NotificationsNone,
                onClick = if (settings.reminderEnabled) ({ pickTime = true }) else null,
            ) {
                Switch(
                    settings.reminderEnabled,
                    { on ->
                        if (on && Build.VERSION.SDK_INT >= 33 && !Reminder.canNotify(context)) askPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        else vm.setReminder(on, settings.reminderHour, settings.reminderMinute)
                    },
                    colors = SwitchDefaults.colors(checkedTrackColor = MaterialTheme.colorScheme.primary),
                )
            }
            ListDivider()
            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Text("المظهر", style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(ThemeMode.SYSTEM to "حسب الهاتف", ThemeMode.LIGHT to "فاتح", ThemeMode.DARK to "داكن").forEach { (m, label) ->
                        Choice(label, settings.theme == m, Modifier.weight(1f)) { vm.setTheme(m) }
                    }
                }
            }
        }

        SectionLabel("الحساب")
        PaperCard(padding = PaddingValues(vertical = 4.dp)) {
            if (acc.isGuest) {
                RowItem("اربط حساب Google", "احفظ قراءتك ومجموعاتك لتعود إليها من أي هاتف.", Icons.Rounded.Link, onClick = {
                    (context as? Activity)?.let(vm::signInWithGoogle)
                })
                ListDivider()
            }
            if (acc.isAdmin) {
                RowItem("لوحة إدارة المنصة", "كل المجموعات والحسابات والنشاط.", Icons.Rounded.AdminPanelSettings, onClick = nav::admin)
                ListDivider()
            }
            RowItem("سياسة الخصوصية", icon = Icons.Rounded.Policy, onClick = {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://ahmad-nayfeh.github.io/marathon/privacy/")))
            })
            ListDivider()
            RowItem(
                "تسجيل الخروج",
                if (acc.isGuest) "تنبيه: دون ربط حساب Google لن تستطيع العودة إلى بياناتك." else null,
                Icons.AutoMirrored.Rounded.Logout,
                onClick = vm::signOut,
            )
        }
        Spacer(Modifier.height(16.dp))
        Text("ماراثون القراءة · الإصدار ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.align(Alignment.CenterHorizontally))
    }

    if (editName) NameDialog(p.name, onDismiss = { editName = false }) { vm.saveProfile(it, p.goal, "حُفظ الاسم."); editName = false }
    if (pickTime) TimeDialog(settings.reminderHour, settings.reminderMinute, onDismiss = { pickTime = false }) { h, m -> vm.setReminder(true, h, m); pickTime = false }
}

private fun time(h: Int, m: Int): String = "%d:%02d %s".format(if (h % 12 == 0) 12 else h % 12, m, if (h < 12) "ص" else "م")

@Composable
private fun NameDialog(current: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var name by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("اسمك في المجموعات") },
        text = { OutlinedTextField(name, { name = it.take(Limits.MAX_NAME) }, singleLine = true, modifier = Modifier.fillMaxWidth()) },
        confirmButton = { TextButton(onClick = { onSave(name) }, enabled = name.isNotBlank()) { Text("حفظ") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeDialog(hour: Int, minute: Int, onDismiss: () -> Unit, onPick: (Int, Int) -> Unit) {
    val state = rememberTimePickerState(hour, minute, is24Hour = false)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("وقت التذكير") },
        text = { TimePicker(state) },
        confirmButton = { TextButton(onClick = { onPick(state.hour, state.minute) }) { Text("حفظ") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } },
    )
}

