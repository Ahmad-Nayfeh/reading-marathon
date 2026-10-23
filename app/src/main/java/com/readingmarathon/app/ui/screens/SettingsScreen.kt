package com.readingmarathon.app.ui.screens

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
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
import com.readingmarathon.app.data.Mode
import com.readingmarathon.app.data.ThemeMode
import com.readingmarathon.app.notify.Reminder
import com.readingmarathon.app.ui.components.PaperCard
import com.readingmarathon.app.ui.components.SectionTitle
import com.readingmarathon.app.ui.theme.Brand

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(ctx: Ctx) {
    val settings by ctx.vm.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var pickTime by remember { mutableStateOf(false) }
    var confirmLeave by remember { mutableStateOf(false) }
    val askPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) ctx.vm.setReminder(true, settings.reminderHour, settings.reminderMinute)
        else ctx.vm.say("بدون إذن الإشعارات لن يظهر التذكير.")
    }

    Page("الإعدادات", onBack = { ctx.nav.popBackStack() }) {
        SectionTitle("المظهر")
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            val options = listOf(ThemeMode.SYSTEM to "تلقائي", ThemeMode.LIGHT to "فاتح", ThemeMode.DARK to "داكن")
            options.forEachIndexed { i, (mode, label) ->
                SegmentedButton(settings.theme == mode, { ctx.vm.setTheme(mode) }, SegmentedButtonDefaults.itemShape(i, options.size)) { Text(label) }
            }
        }

        SectionTitle("التذكير اليومي")
        PaperCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("ذكّرني بتسجيل القراءة", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "كل يوم الساعة %02d:%02d".format(settings.reminderHour, settings.reminderMinute),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(settings.reminderEnabled, { on ->
                    if (on && Build.VERSION.SDK_INT >= 33 && !Reminder.canNotify(context)) askPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    else ctx.vm.setReminder(on, settings.reminderHour, settings.reminderMinute)
                })
            }
            TextButton(onClick = { pickTime = true }) { Text("تغيير الوقت") }
        }

        SectionTitle("الماراثون")
        PaperCard {
            Text(ctx.data.marathon.name, style = MaterialTheme.typography.titleMedium)
            Text(
                when (ctx.session.mode) {
                    Mode.LOCAL -> "على هذا الجهاز · أنت المشرف"
                    Mode.SHARED -> if (ctx.isOrganizer) "مشترك · أنت المشرف" else "مشترك · قارئ"
                    Mode.DEMO -> "بيانات تجريبية"
                },
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ctx.me?.let { Text("تقرأ باسم: ${it.name}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 6.dp)) }
            if (ctx.isOrganizer && ctx.me != null) {
                TextButton(onClick = { ctx.vm.setMyMember(null) }) { Text("لا أقرأ مع المجموعة") }
            }
            Spacer(Modifier.height(10.dp))
            OutlinedButton(onClick = { confirmLeave = true }, modifier = Modifier.fillMaxWidth()) {
                Text(if (ctx.session.mode == Mode.DEMO) "إنهاء التجربة" else "الخروج من الماراثون")
            }
        }

        SectionTitle("عن التطبيق")
        PaperCard {
            Text("ماراثون القراءة", style = MaterialTheme.typography.titleMedium)
            Text("الإصدار ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(6.dp))
            Text("تطبيق لمجموعات القراءة: كتاب مشترك لكل تحدٍّ، تسجيل يومي، نقاط وأوسمة وتقارير.", style = MaterialTheme.typography.bodyMedium)
            Text("صنعه أحمد نايفة.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://ahmad-nayfeh.github.io/marathon/")))
            }) { Text("صفحة التطبيق والتحديثات", color = Brand.colors.gold) }
        }
    }

    if (pickTime) {
        val state = rememberTimePickerState(settings.reminderHour, settings.reminderMinute, is24Hour = false)
        AlertDialog(
            onDismissRequest = { pickTime = false },
            title = { Text("وقت التذكير") },
            text = { TimePicker(state) },
            confirmButton = {
                TextButton(onClick = {
                    pickTime = false
                    ctx.vm.setReminder(settings.reminderEnabled, state.hour, state.minute)
                }) { Text("حفظ") }
            },
            dismissButton = { TextButton(onClick = { pickTime = false }) { Text("إلغاء") } },
        )
    }

    if (confirmLeave) {
        AlertDialog(
            onDismissRequest = { confirmLeave = false },
            title = { Text("الخروج من الماراثون؟") },
            text = {
                Text(
                    when (ctx.session.mode) {
                        Mode.LOCAL -> "لن يُحذف شيء: يبقى الماراثون محفوظاً على هذا الجهاز وتفتحه من شاشة البداية."
                        Mode.SHARED -> "تبقى بياناتك في الماراثون المشترك. للعودة تحتاج رمز الدعوة."
                        Mode.DEMO -> "ستعود إلى شاشة البداية."
                    },
                )
            },
            confirmButton = { TextButton(onClick = { confirmLeave = false; ctx.vm.leave() }) { Text("خروج") } },
            dismissButton = { TextButton(onClick = { confirmLeave = false }) { Text("إلغاء") } },
        )
    }
}
