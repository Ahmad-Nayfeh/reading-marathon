package com.readingmarathon.app.ui.screens

import com.readingmarathon.app.ui.components.icon
import com.readingmarathon.app.ui.components.IconBadge
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.readingmarathon.app.ui.components.Avatar
import com.readingmarathon.app.ui.components.BookCover
import com.readingmarathon.app.ui.components.EmptyState
import com.readingmarathon.app.ui.components.PaperCard
import com.readingmarathon.app.ui.components.Pill
import com.readingmarathon.app.ui.components.ProgressRing
import com.readingmarathon.app.ui.components.RhythmChart
import com.readingmarathon.app.ui.components.SectionTitle
import com.readingmarathon.app.ui.components.StatTile
import com.readingmarathon.app.ui.theme.Amiri
import com.readingmarathon.app.ui.theme.Brand
import com.readingmarathon.core.Analytics
import com.readingmarathon.core.Badges
import com.readingmarathon.core.Challenge
import com.readingmarathon.core.ChallengeStatus
import com.readingmarathon.core.Dates
import com.readingmarathon.core.Fmt
import com.readingmarathon.core.News
import com.readingmarathon.core.Scope
import com.readingmarathon.core.Scoring
import java.time.LocalTime

@Composable
fun HomeScreen(ctx: Ctx) {
    val d = ctx.data
    val challenge = d.currentChallenge(ctx.today)
    Page(
        title = d.marathon.name,
        actions = {
            if (ctx.isOrganizer) IconButton(onClick = { ctx.nav.navigate("admin") }) { Icon(Icons.Rounded.AdminPanelSettings, "الإدارة") }
            IconButton(onClick = { ctx.nav.navigate("settings") }) { Icon(Icons.Rounded.Settings, "الإعدادات") }
        },
    ) {
        Greeting(ctx)
        Spacer(Modifier.height(14.dp))
        if (challenge == null) {
            PaperCard {
                EmptyState(
                    Icons.Rounded.AutoStories, "لا يوجد تحدٍّ بعد",
                    if (ctx.isOrganizer) "أنشئ أول تحدٍّ: اختر الكتاب المشترك وتاريخ البداية والنهاية." else "سيظهر هنا التحدي حين ينشئه المشرف.",
                ) {
                    if (ctx.isOrganizer) Button(onClick = { ctx.editChallenge(null) }) { Text("أنشئ تحدياً") }
                }
            }
        } else {
            ChallengeHero(challenge, ctx) { ctx.openChallenge(challenge.id) }
            Spacer(Modifier.height(14.dp))
            TodayCard(ctx, challenge)
            Spacer(Modifier.height(8.dp))
            WhoReadToday(ctx)
            ChallengeNumbers(ctx, challenge)
            val news = remember(d, challenge) { News.forChallenge(d, challenge, ctx.today) + News.weekly(d, ctx.today) }
            if (news.isNotEmpty()) {
                SectionTitle("أخبار الماراثون")
                PaperCard {
                    news.take(6).forEachIndexed { i, n ->
                        if (i > 0) Spacer(Modifier.height(12.dp))
                        Row(verticalAlignment = Alignment.Top) {
                            IconBadge(n.mark.icon, size = 30.dp)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(n.text, style = MaterialTheme.typography.bodyMedium)
                                n.date?.let { Text(Dates.relative(it, ctx.today), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            }
                        }
                    }
                }
            }
            GroupRhythm(ctx)
        }
    }
}

@Composable
private fun Greeting(ctx: Ctx) {
    val hour = LocalTime.now().hour
    val hello = if (hour in 4..11) "صباح الخير" else "مساء الخير"
    val name = ctx.me?.name
    Column {
        Text(if (name != null) "$hello، $name" else hello, style = MaterialTheme.typography.headlineSmall)
        Text("${Dates.weekday(ctx.today)} ${Dates.long(ctx.today)}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** The current challenge, set like a book jacket on navy. */
@Composable
fun ChallengeHero(c: Challenge, ctx: Ctx, onClick: (() -> Unit)? = null) {
    val p = Analytics.progress(c, ctx.today)
    val status = c.status(ctx.today)
    val cream = Color(0xFFFBF7EF)
    val gold = Color(0xFFF2B84B)
    PaperCard(onClick = onClick, padding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
        Box(
            Modifier
                .fillMaxWidth()
                .background(Brush.linearGradient(listOf(Color(0xFF14213D), Color(0xFF233A68))))
                .padding(18.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BookCover(c.book.title, c.id, width = 78.dp)
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        when (status) {
                            ChallengeStatus.ACTIVE -> "التحدي الحالي"
                            ChallengeStatus.UPCOMING -> "التحدي القادم"
                            ChallengeStatus.FINISHED -> "آخر تحدٍّ"
                        },
                        color = gold, style = MaterialTheme.typography.labelLarge,
                    )
                    Text(c.book.title, fontFamily = Amiri, fontSize = 24.sp, lineHeight = 36.sp, color = cream, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (c.book.author.isNotBlank()) Text(c.book.author, color = cream.copy(alpha = 0.75f), style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(6.dp))
                    Text("${Dates.short(c.start)} – ${Dates.long(c.end)}", color = cream.copy(alpha = 0.6f), style = MaterialTheme.typography.labelMedium)
                }
                Spacer(Modifier.width(10.dp))
                ProgressRing(p.fraction, size = 84.dp) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        when (status) {
                            ChallengeStatus.ACTIVE -> {
                                Text("${p.daysLeft}", color = cream, fontWeight = FontWeight.Bold, fontSize = 22.sp)
                                Text(if (p.daysLeft == 1) "يوم باقٍ" else "يوماً باقية", color = cream.copy(alpha = 0.7f), fontSize = 10.sp)
                            }
                            ChallengeStatus.UPCOMING -> {
                                val until = (c.start.toEpochDay() - ctx.today.toEpochDay()).toInt()
                                Text("$until", color = cream, fontWeight = FontWeight.Bold, fontSize = 22.sp)
                                Text("يوماً للبدء", color = cream.copy(alpha = 0.7f), fontSize = 10.sp)
                            }
                            ChallengeStatus.FINISHED -> Text("انتهى", color = cream, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TodayCard(ctx: Ctx, challenge: Challenge) {
    val me = ctx.me
    if (me == null) {
        if (ctx.isOrganizer) {
            Button(
                onClick = { ctx.log(null, null) },
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(16.dp),
            ) {
                Icon(Icons.Rounded.Add, null)
                Spacer(Modifier.width(8.dp))
                Text("سجّل قراءة لأحد القرّاء", fontWeight = FontWeight.Bold)
            }
        }
        return
    }
    val d = ctx.data
    val todayEntry = d.entries.firstOrNull { it.memberId == me.id && it.date == ctx.today }
    val myDates = d.entries.filter { it.memberId == me.id && it.isReadingDay }.map { it.date }
    val streak = Badges.currentStreak(myDates, ctx.today)
    val standings = remember(d, challenge) { Scoring.standings(d, Scope.OfChallenge(challenge)) }
    val mine = standings.firstOrNull { it.score.member.id == me.id }
    PaperCard {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile(todayEntry?.let { Fmt.duration(it.totalMinutes) } ?: "—", "قراءتك اليوم", Modifier.weight(1f))
            StatTile("$streak", "أيام متتالية", Modifier.weight(1f), icon = Icons.Rounded.LocalFireDepartment)
            StatTile(mine?.let { "#${it.rank}" } ?: "—", "${mine?.score?.totalPoints ?: 0} نقطة", Modifier.weight(1f))
        }
        Spacer(Modifier.height(14.dp))
        if (challenge.status(ctx.today) == ChallengeStatus.UPCOMING && todayEntry == null) {
            Text("لم يبدأ التحدي بعد، لكن قراءتك اليوم تُحفظ وتُحسب في سجلّك العام.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
        }
        if (todayEntry == null) {
            Button(
                onClick = { ctx.log(me.id, ctx.today) },
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Brand.colors.gold, contentColor = Color(0xFF14213D)),
            ) {
                Icon(Icons.Rounded.Add, null)
                Spacer(Modifier.width(8.dp))
                Text("سجّل قراءة اليوم", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        } else {
            OutlinedButton(onClick = { ctx.log(me.id, ctx.today) }, modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(16.dp)) {
                Icon(Icons.Rounded.Edit, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("عدّل قراءة اليوم")
            }
        }
        if (ctx.isOrganizer) {
            TextButton(onClick = { ctx.log(null, null) }, modifier = Modifier.fillMaxWidth()) { Text("سجّل لقارئ آخر") }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WhoReadToday(ctx: Ctx) {
    val d = ctx.data
    val members = d.activeMembers
    if (members.isEmpty()) return
    val readers = d.entries.filter { it.date == ctx.today && it.isReadingDay }.map { it.memberId }.toSet()
    SectionTitle("من قرأ اليوم", trailing = {
        Text("${readers.count { id -> members.any { it.id == id } }} من ${members.size}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    })
    PaperCard {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            members.sortedByDescending { it.id in readers }.forEach { m ->
                val read = m.id in readers
                Column(
                    Modifier.width(56.dp).clip(RoundedCornerShape(12.dp)).clickable { ctx.openMember(m.id) }.alpha(if (read) 1f else 0.45f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Avatar(m.name, m.id, size = 44.dp, ring = if (read) Brand.colors.gold else null)
                    Text(
                        m.name, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ChallengeNumbers(ctx: Ctx, c: Challenge) {
    val kpis = remember(ctx.data, c) { Analytics.challengeKpis(ctx.data, c, ctx.today) }
    if (kpis.isEmpty()) return
    SectionTitle("التحدي بالأرقام")
    kpis.chunked(2).forEach { row ->
        Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            row.forEach { StatTile(it.value, it.label, Modifier.weight(1f)) }
            if (row.size == 1) Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
private fun GroupRhythm(ctx: Ctx) {
    val ids = ctx.data.activeMembers.map { it.id }.toSet()
    val series = remember(ctx.data) {
        Analytics.daily(ctx.data.entries.filter { it.memberId in ids }, ctx.today.minusDays(13), ctx.today)
    }
    if (series.all { it.minutes == 0 }) return
    SectionTitle("إيقاع المجموعة", trailing = { Text("آخر 14 يوماً", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) })
    PaperCard {
        RhythmChart(series.map { it.minutes })
        Spacer(Modifier.height(6.dp))
        Text(
            "مجموع ${Fmt.hours(series.sumOf { it.minutes })} ساعة. أعلى يوم: ${Fmt.duration(series.maxOf { it.minutes })}.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
