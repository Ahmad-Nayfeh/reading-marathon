package com.readingmarathon.app.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.rounded.MenuBook
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
import com.readingmarathon.core.Shelf
import com.readingmarathon.core.ShelfBook
import com.readingmarathon.app.ui.components.BookCover
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
    val shelf by vm.shelf.collectAsStateWithLifecycle()
    @Suppress("UNUSED_VARIABLE") val reads by vm.reads.collectAsStateWithLifecycle()
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

        TodaySummary(stats.today, goal, stats.currentStreak)

        val reading = remember(shelf) { Shelf.reading(shelf) }
        settings.timerStartedAt?.let { started -> RunningTimer(started, settings.timerBookId?.let(vm::titleOf).orEmpty()) }
        SectionLabel("أقرأ الآن", trailing = {
            Text("أضف كتاباً", style = MaterialTheme.typography.labelLarge, color = Brand.colors.gold, modifier = Modifier.clickable { nav.addBook() })
        })
        if (reading.isEmpty()) {
            PaperCard {
                EmptyState(
                    Icons.AutoMirrored.Rounded.MenuBook, "ماذا تقرأ الآن؟",
                    "اختر كتابك، ثم سجّل وقتك فيه بلمسة. كل دقيقة تُحسب له، وفي مجموعاتك.",
                ) { PrimaryButton("اختر كتاباً", onClick = nav::addBook) }
            }
        }
        reading.take(3).forEach { b ->
            BookLogCard(b, vm.minutesOn(today, b.bookId), timerRunning = settings.timerStartedAt != null)
            Spacer(Modifier.height(10.dp))
        }
        if (reading.size > 3) {
            Text("و${Fmt.books(reading.size - 3)} أخرى في رفّك", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        SectionLabel("هذا الأسبوع", trailing = {
            Text(Fmt.duration(stats.minutesThisWeek), style = MaterialTheme.typography.labelLarge)
        })
        PaperCard(padding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 14.dp)) {
            WeekBars(Stats.lastDays(days, today, 7), goal, today, onDay = nav::day)
            Spacer(Modifier.height(4.dp))
            Text(
                "المس أي يوم لتعديله أو لإضافة قراءة نسيتها. الخط الذهبي هدفك اليومي.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 6.dp),
            )
        }

        SectionLabel("مجموعاتي")
        if (groups.isEmpty()) {
            PaperCard {
                EmptyState(
                    Icons.Rounded.Groups, "اقرأ مع غيرك",
                    "أنشئ مجموعة لأصدقائك أو عائلتك، أو انضم إلى مجموعة برمز دعوة. قراءتك تُحسب فيها تلقائياً.",
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

/** Today in one line: minutes against the goal, and the streak. */
@Composable
private fun TodaySummary(minutes: Int, goal: Int, streak: Int) {
    val nav = LocalNav.current
    val vm = LocalVm.current
    PaperCard(onClick = { nav.day(vm.today()) }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ProgressRing(minutes.toFloat() / goal.coerceAtLeast(1), size = 64.dp, stroke = 5.dp) {
                Text("$minutes", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text("اليوم: ${Fmt.minutes(minutes)}", style = MaterialTheme.typography.titleMedium)
                Text(
                    when {
                        minutes >= goal -> "حققت هدفك (${Fmt.minutes(goal)})."
                        minutes == 0 -> "هدفك ${Fmt.minutes(goal)}. ابدأ بكتابك أدناه."
                        else -> "بقي ${Fmt.minutes(goal - minutes)} على هدفك."
                    },
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (streak > 1) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Rounded.LocalFireDepartment, null, tint = Brand.colors.gold, modifier = Modifier.size(20.dp))
                    Text("$streak", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("أيام", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

/** A book being read: its totals, one-tap minutes, the timer, and the fine editor. */
@Composable
private fun BookLogCard(b: ShelfBook, todayMinutes: Int, timerRunning: Boolean) {
    val vm = LocalVm.current
    val nav = LocalNav.current
    val context = LocalContext.current
    // The timer shows as a notification; ask for permission the first time it's used.
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { vm.startTimer(b.bookId) }
    PaperCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BookCover(b.title, b.bookId, width = 46.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("«${b.title}»", style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(b.author, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                Text(
                    "اليوم ${Fmt.minutes(todayMinutes)} · المجموع ${Fmt.duration(b.minutes.coerceAtLeast(0))}",
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(15, 30, 45, 60).forEach { m -> Choice("\u2066+$m\u2069", false, Modifier.weight(1f)) { vm.quickAdd(b.bookId, m) } }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PrimaryButton(
                "مؤقت",
                enabled = !timerRunning,
                onClick = {
                    if (Build.VERSION.SDK_INT >= 33 && !Reminder.canNotify(context)) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    else vm.startTimer(b.bookId)
                },
                icon = Icons.Rounded.Timer,
                modifier = Modifier.weight(1f),
            )
            GhostButton("وقت آخر", onClick = { nav.day(vm.today(), b.bookId) }, icon = Icons.Rounded.Edit)
        }
    }
}

@Composable
private fun RunningTimer(startedAt: Long, title: String) {
    val vm = LocalVm.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(startedAt) {
        while (true) { now = System.currentTimeMillis(); delay(1_000) }
    }
    val secs = ((now - startedAt) / 1000).coerceAtLeast(0)
    val clock = if (secs >= 3600) "%d:%02d:%02d".format(secs / 3600, secs / 60 % 60, secs % 60) else "%02d:%02d".format(secs / 60, secs % 60)
    Spacer(Modifier.height(12.dp))
    PaperCard {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("تقرأ «$title»", style = MaterialTheme.typography.labelLarge, color = Brand.colors.gold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            // Digits read left to right even in Arabic.
            Text("\u2066$clock\u2069", fontSize = 40.sp, fontWeight = FontWeight.Light, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PrimaryButton("إيقاف وتسجيل", onClick = vm::stopTimer, modifier = Modifier.weight(1f))
                GhostButton("إلغاء", onClick = vm::cancelTimer)
            }
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
