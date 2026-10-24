package com.readingmarathon.app.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.readingmarathon.app.notify.Reminder
import com.readingmarathon.app.ui.LocalVm
import com.readingmarathon.app.ui.components.EmptyState
import com.readingmarathon.app.ui.components.GhostButton
import com.readingmarathon.app.ui.components.GoldRule
import com.readingmarathon.app.ui.components.PaperCard
import com.readingmarathon.app.ui.components.PrimaryButton
import com.readingmarathon.app.ui.components.ProgressRing
import com.readingmarathon.app.ui.components.SectionLabel
import com.readingmarathon.app.ui.components.WeekBars
import com.readingmarathon.app.ui.theme.Brand
import com.readingmarathon.core.ChallengeStatus
import com.readingmarathon.core.Dates
import com.readingmarathon.core.Fmt
import com.readingmarathon.core.GroupData
import com.readingmarathon.core.Scope
import com.readingmarathon.core.Scoring
import com.readingmarathon.core.Stats
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.LocalTime

/** Today first: how much was read, and the fastest ways to log more. Then the week and the groups. */
@Composable
fun HomeScreen(onGroups: () -> Unit) {
    val vm = LocalVm.current
    val nav = LocalNav.current
    val profile by vm.profile.collectAsStateWithLifecycle()
    val days by vm.days.collectAsStateWithLifecycle()
    val groups by vm.myGroups.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val today = vm.today()
    val goal = profile?.goal ?: 30
    val stats = remember(days, today) { Stats.personal(days, today) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().padding(horizontal = 20.dp),
    ) {
        Spacer(Modifier.height(20.dp))
        Text(greeting() + "، " + profile?.name.orEmpty(), style = MaterialTheme.typography.headlineMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text("${Dates.weekday(today)} ${Dates.long(today)}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        GoldRule(Modifier.padding(top = 10.dp, bottom = 18.dp))

        val news by vm.announcements.collectAsStateWithLifecycle()
        news.forEach { a ->
            PaperCard(modifier = Modifier.padding(bottom = 12.dp)) {
                Row(verticalAlignment = Alignment.Top) {
                    Icon(Icons.Rounded.Campaign, null, tint = Brand.colors.gold, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        if (a.title.isNotBlank()) Text(a.title, style = MaterialTheme.typography.titleMedium)
                        if (a.body.isNotBlank()) Text(a.body, style = MaterialTheme.typography.bodyMedium)
                    }
                    IconButton(onClick = { vm.dismiss(a.id) }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Rounded.Close, "إخفاء", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        TodayCard(stats.today, goal, stats.currentStreak, settings.timerStartedAt)

        SectionLabel("هذا الأسبوع", trailing = {
            Text(Fmt.duration(stats.minutesThisWeek), style = MaterialTheme.typography.labelLarge)
        })
        PaperCard(padding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 14.dp)) {
            WeekBars(Stats.lastDays(days, today, 7), goal, today, onDay = nav::day)
            Spacer(Modifier.height(4.dp))
            Text(
                "المس أي يوم لتعديله. الخط الذهبي هدفك اليومي.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 6.dp),
            )
        }

        SectionLabel("مجموعاتي")
        if (groups.isEmpty()) {
            PaperCard {
                EmptyState(
                    Icons.Rounded.Groups, "اقرأ مع غيرك",
                    "أنشئ مجموعة لأصدقائك أو عائلتك، أو انضم بمجموعة برمز دعوة. قراءتك تُحسب فيها تلقائياً.",
                ) { GhostButton("المجموعات", onClick = onGroups) }
            }
        } else {
            groups.forEach { g ->
                GroupCard(g, vm.account.value?.uid.orEmpty(), today) { nav.group(g.group.id) }
                Spacer(Modifier.height(10.dp))
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

private fun greeting(): String = if (LocalTime.now().hour < 12) "صباح الخير" else "مساء الخير"

@Composable
private fun TodayCard(minutes: Int, goal: Int, streak: Int, timerStartedAt: Long?) {
    val vm = LocalVm.current
    val nav = LocalNav.current
    val context = LocalContext.current
    // The timer shows as a notification; ask for permission the first time it's used.
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { vm.startTimer() }
    PaperCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ProgressRing(minutes.toFloat() / goal.coerceAtLeast(1), size = 96.dp, stroke = 7.dp) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("$minutes", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    Text("دقيقة", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.width(18.dp))
            Column(Modifier.weight(1f)) {
                Text("اليوم", style = MaterialTheme.typography.titleMedium)
                Text(
                    when {
                        minutes >= goal -> "حققت هدفك اليوم."
                        minutes == 0 -> "هدفك ${Fmt.minutes(goal)}."
                        else -> "بقي ${Fmt.minutes(goal - minutes)} على هدفك."
                    },
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (streak > 1) {
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.LocalFireDepartment, null, tint = Brand.colors.gold, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("${Fmt.days(streak)} متتالية", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
        Spacer(Modifier.height(18.dp))
        if (timerStartedAt != null) {
            RunningTimer(timerStartedAt)
        } else {
            Text("سجّل بلمسة", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(15, 30, 45, 60).forEach { m -> Choice("\u2066+$m\u2069", false, Modifier.weight(1f)) { vm.quickAdd(m) } }
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PrimaryButton(
                    "ابدأ المؤقت",
                    onClick = {
                        if (Build.VERSION.SDK_INT >= 33 && !Reminder.canNotify(context)) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        else vm.startTimer()
                    },
                    icon = Icons.Rounded.Timer,
                    modifier = Modifier.weight(1f),
                )
                GhostButton("تعديل", onClick = { nav.day(vm.today()) }, icon = Icons.Rounded.Edit)
            }
        }
    }
}

@Composable
private fun RunningTimer(startedAt: Long) {
    val vm = LocalVm.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(startedAt) {
        while (true) { now = System.currentTimeMillis(); delay(1_000) }
    }
    val secs = ((now - startedAt) / 1000).coerceAtLeast(0)
    val clock = if (secs >= 3600) "%d:%02d:%02d".format(secs / 3600, secs / 60 % 60, secs % 60) else "%02d:%02d".format(secs / 60, secs % 60)
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("المؤقت يعمل", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        // Digits read left to right even in Arabic.
        Text("⁦$clock⁩", fontSize = 40.sp, fontWeight = FontWeight.Light, color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PrimaryButton("إيقاف وتسجيل", onClick = vm::stopTimer, modifier = Modifier.weight(1f))
            GhostButton("إلغاء", onClick = vm::cancelTimer)
        }
    }
}

/** A group at a glance: its book, where the reader stands, and who read today. */
@Composable
fun GroupCard(d: GroupData, uid: String, today: LocalDate, onClick: () -> Unit) {
    val c = d.currentChallenge(today)
    val active = c?.status(today) == ChallengeStatus.ACTIVE
    val standing = remember(d, uid, c) { if (c != null) Scoring.rankOf(d, uid, Scope.OfChallenge(c)) else Scoring.rankOf(d, uid, Scope.AllTime) }
    val readers = remember(d, today) { Scoring.readersOn(d, today) }
    PaperCard(onClick = onClick) {
        Text(d.group.name, style = MaterialTheme.typography.headlineSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (c != null) {
            Text(
                "«${c.book.title}»" + when (c.status(today)) {
                    ChallengeStatus.ACTIVE -> " · اليوم ${c.dayNumber(today)} من ${c.lengthDays}"
                    ChallengeStatus.UPCOMING -> " · يبدأ ${Dates.short(c.start)}"
                    ChallengeStatus.FINISHED -> " · انتهى"
                },
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        } else {
            Text("لا يوجد كتاب حالياً", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            val ranked = standing?.takeIf { it.score.points > 0 || it.score.minutes > 0 }
            Text(
                if (ranked != null && (active || c == null)) "ترتيبك ${ranked.rank} من ${d.activeMembers.size}" else Fmt.members(d.activeMembers.size),
                style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f),
            )
            Text(
                if (readers == 0) "لم يقرأ أحد اليوم بعد" else "${Fmt.readers(readers)} اليوم",
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
