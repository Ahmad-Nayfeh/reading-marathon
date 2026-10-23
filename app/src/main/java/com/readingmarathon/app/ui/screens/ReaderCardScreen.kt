package com.readingmarathon.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.readingmarathon.app.ui.components.Avatar
import com.readingmarathon.app.ui.components.DonutChart
import com.readingmarathon.app.ui.components.EmptyState
import com.readingmarathon.app.ui.components.Heatmap
import com.readingmarathon.app.ui.components.PaperCard
import com.readingmarathon.app.ui.components.Pill
import com.readingmarathon.app.ui.components.SectionTitle
import com.readingmarathon.app.ui.components.StatTile
import com.readingmarathon.app.ui.theme.Brand
import com.readingmarathon.core.Achievement
import com.readingmarathon.core.AchievementType
import com.readingmarathon.core.Analytics
import com.readingmarathon.core.BadgeKind
import com.readingmarathon.core.Badges
import com.readingmarathon.core.Dates
import com.readingmarathon.core.Fmt
import com.readingmarathon.core.Limits
import com.readingmarathon.core.Member
import com.readingmarathon.core.Scope
import com.readingmarathon.core.Scoring

/** The "بطاقتي" tab: my own card, or a prompt for an organiser who doesn't read. */
@Composable
fun MyCardScreen(ctx: Ctx) {
    val me = ctx.me
    if (me != null) {
        MemberCard(ctx, me, isTab = true)
        return
    }
    Page("بطاقتي", actions = { IconButton(onClick = { ctx.nav.navigate("settings") }) { Icon(Icons.Rounded.Settings, "الإعدادات") } }) {
        PaperCard {
            EmptyState(
                "🪪", "أنت المشرف",
                "إن كنت تقرأ مع المجموعة فاختر اسمك من القائمة لتظهر بطاقتك هنا، أو أضف نفسك من صفحة الإدارة.",
            )
        }
        SectionTitle("القرّاء")
        PaperCard {
            ctx.data.activeMembers.forEach { m ->
                Row(Modifier.fillMaxWidth().clickable { ctx.openMember(m.id) }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Avatar(m.name, m.id, size = 36.dp)
                    Spacer(Modifier.width(12.dp))
                    Text(m.name, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                    TextButton(onClick = { ctx.vm.setMyMember(m.id) }) { Text("هذا أنا") }
                }
            }
        }
    }
}

@Composable
fun MemberScreen(ctx: Ctx, id: String) {
    val m = ctx.data.member(id)
    if (m == null) {
        Page("قارئ", onBack = { ctx.nav.popBackStack() }) { EmptyState("🔎", "القارئ غير موجود", "") }
        return
    }
    MemberCard(ctx, m, isTab = false)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MemberCard(ctx: Ctx, m: Member, isTab: Boolean) {
    val d = ctx.data
    val challenge = d.currentChallenge(ctx.today)
    var allTime by rememberSaveable { mutableStateOf(challenge == null) }
    val scope: Scope = if (allTime || challenge == null) Scope.AllTime else Scope.OfChallenge(challenge)
    val score = remember(d, m, scope) { Scoring.memberScore(d, m, scope) }
    val rank = remember(d, scope) { Scoring.standings(d, scope) }.firstOrNull { it.score.member.id == m.id }?.rank
    val badges = remember(d, m, scope) { Badges.forMember(d, m, scope) }
    val mine = d.entries.filter { it.memberId == m.id }
    val readingDates = mine.filter { it.isReadingDay }.map { it.date }
    val isMe = m.id == ctx.session.myMemberId
    val canEdit = ctx.isOrganizer || isMe

    Page(
        if (isTab) "بطاقتي" else m.name,
        onBack = if (isTab) null else ({ ctx.nav.popBackStack() }),
        actions = {
            if (isTab) IconButton(onClick = { ctx.nav.navigate("settings") }) { Icon(Icons.Rounded.Settings, "الإعدادات") }
        },
    ) {
        // Identity card
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(Brush.linearGradient(listOf(Color(0xFF14213D), Color(0xFF2A4478))))
                .padding(20.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar(m.name, m.id, size = 72.dp, ring = Color(0xFFF2B84B))
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(m.name, color = Color(0xFFFBF7EF), style = MaterialTheme.typography.headlineSmall)
                    if (!m.active) Text("مؤرشف", color = Color(0xFFF2B84B), style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "${score.totalPoints} نقطة" + (rank?.let { " · المركز $it" } ?: ""),
                        color = Color(0xFFF2B84B), style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        "أطول سلسلة ${Badges.longestStreak(readingDates)} يوماً · الحالية ${Badges.currentStreak(readingDates, ctx.today)}",
                        color = Color(0xFFC9D2E3), style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        if (challenge != null) {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SegmentedButton(!allTime, { allTime = false }, SegmentedButtonDefaults.itemShape(0, 2)) { Text("التحدي الحالي", maxLines = 1) }
                SegmentedButton(allTime, { allTime = true }, SegmentedButtonDefaults.itemShape(1, 2)) { Text("كل الماراثون", maxLines = 1) }
            }
            Spacer(Modifier.height(12.dp))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile(Fmt.hours(score.totalMinutes), "ساعة قراءة", Modifier.weight(1f))
            StatTile("${score.readingDays}", "يوم قراءة", Modifier.weight(1f))
            StatTile("${score.booksFinished}", "كتاباً أُنهي", Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile("${score.quotes}", "اقتباساً", Modifier.weight(1f))
            StatTile("${score.discussions}", "جلسة نقاش", Modifier.weight(1f))
            StatTile(
                if (score.readingDays == 0) "—" else Fmt.duration(score.totalMinutes / score.readingDays),
                "معدّل يوم القراءة", Modifier.weight(1f),
            )
        }

        if (isMe || ctx.isOrganizer) {
            Spacer(Modifier.height(14.dp))
            Button(onClick = { ctx.log(m.id, ctx.today) }, modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(16.dp)) {
                Icon(Icons.Rounded.Add, null)
                Spacer(Modifier.width(6.dp))
                Text(if (isMe) "سجّل قراءة" else "سجّل قراءة لـ${m.name}")
            }
        }

        SectionTitle("الأوسمة")
        BadgeGrid(badges.associateBy { it.kind }.mapValues { it.value.detail })

        val labelled = score.points.labelled()
        if (labelled.isNotEmpty()) {
            SectionTitle("من أين جاءت النقاط")
            PaperCard {
                DonutChart(labelled.map { it.first to it.second.toDouble() }, centerTop = "${score.totalPoints}", centerBottom = "نقطة")
            }
        }

        if (readingDates.isNotEmpty()) {
            SectionTitle("آخر 12 أسبوعاً")
            PaperCard { Heatmap(Analytics.heatmap(mine, ctx.today.minusWeeks(11), ctx.today)) }
        }

        val achievements = d.achievements.filter { it.memberId == m.id }.sortedByDescending { it.date }
        if (achievements.isNotEmpty()) {
            SectionTitle("الإنجازات")
            PaperCard {
                achievements.forEachIndexed { i, a ->
                    if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    AchievementRow(ctx, a, canDelete = canEdit)
                }
            }
        }

        SectionTitle("سجلّ القراءة")
        val history = mine.sortedByDescending { it.date }.take(40)
        if (history.isEmpty()) {
            PaperCard { EmptyState("🕮", "لا قراءات بعد", "كل يوم قراءة يُسجَّل يظهر هنا.") }
        } else {
            PaperCard(padding = androidx.compose.foundation.layout.PaddingValues(vertical = 4.dp)) {
                history.forEachIndexed { i, e ->
                    if (i > 0) HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    val editable = ctx.isOrganizer || (isMe && !e.date.isBefore(ctx.today.minusDays(Limits.MEMBER_BACKDATE_DAYS)))
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .then(if (editable) Modifier.clickable { ctx.log(m.id, e.date) } else Modifier)
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("${Dates.weekday(e.date)} ${Dates.short(e.date)}", style = MaterialTheme.typography.titleSmall)
                            val parts = buildList {
                                if (e.commonMinutes > 0) add("المشترك ${Fmt.duration(e.commonMinutes)}")
                                if (e.otherMinutes > 0) add("أخرى ${Fmt.duration(e.otherMinutes)}")
                                if (e.quotes > 0) add("${e.quotes} اقتباس")
                            }
                            Text(parts.joinToString(" · ").ifEmpty { "—" }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(Fmt.duration(e.totalMinutes), style = MaterialTheme.typography.labelLarge, color = Brand.colors.gold)
                    }
                }
            }
        }
    }
}

@Composable
private fun BadgeGrid(earned: Map<BadgeKind, String>) {
    BadgeKind.entries.chunked(3).forEach { row ->
        Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            row.forEach { k ->
                val got = earned[k]
                PaperCard(Modifier.weight(1f).alpha(if (got != null) 1f else 0.5f), padding = androidx.compose.foundation.layout.PaddingValues(10.dp)) {
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            Modifier.size(46.dp).clip(RoundedCornerShape(50)).background(if (got != null) Brand.colors.goldSoft else MaterialTheme.colorScheme.surfaceContainerHigh),
                            contentAlignment = Alignment.Center,
                        ) { Text(if (got != null) k.emoji else "🔒", fontSize = 22.sp) }
                        Spacer(Modifier.height(6.dp))
                        Text(k.title.removePrefix("وسام "), style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
                        Text(got ?: k.rule, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, maxLines = 3)
                    }
                }
            }
            repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}

@Composable
private fun AchievementRow(ctx: Ctx, a: Achievement, canDelete: Boolean) {
    var confirm by remember { mutableStateOf(false) }
    val c = ctx.data.challenge(a.challengeId)
    val (emoji, text) = when (a.type) {
        AchievementType.FINISHED_COMMON_BOOK -> "📗" to "أنهى «${c?.book?.title.orEmpty()}»"
        AchievementType.ATTENDED_DISCUSSION -> "🗣️" to "حضر نقاش «${c?.book?.title.orEmpty()}»"
        AchievementType.FINISHED_OTHER_BOOK -> "📘" to "أنهى «${a.bookTitle.orEmpty()}»"
    }
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(emoji, fontSize = 20.sp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(text, style = MaterialTheme.typography.titleSmall)
            Text(Dates.long(a.date), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (a.type == AchievementType.FINISHED_OTHER_BOOK) Pill("كتاب آخر")
        if (canDelete) IconButton(onClick = { confirm = true }) { Icon(Icons.Rounded.Close, "حذف", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("حذف الإنجاز؟") },
            text = { Text("ستُخصم نقاطه من المجموع.") },
            confirmButton = { TextButton(onClick = { confirm = false; ctx.vm.act("حُذف الإنجاز.") { it.deleteAchievement(a.id) } }) { Text("حذف", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("إلغاء") } },
        )
    }
}
