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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.readingmarathon.app.ui.components.Avatar
import com.readingmarathon.app.ui.components.EmptyState
import com.readingmarathon.app.ui.components.PaperCard
import com.readingmarathon.app.ui.components.SectionTitle
import com.readingmarathon.app.ui.theme.Brand
import com.readingmarathon.core.Fmt
import com.readingmarathon.core.HallOfFame
import com.readingmarathon.core.Names
import com.readingmarathon.core.Scope
import com.readingmarathon.core.Scoring
import com.readingmarathon.core.Standing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LeaderboardScreen(ctx: Ctx) {
    val d = ctx.data
    val challenge = d.currentChallenge(ctx.today)
    var allTime by rememberSaveable { mutableStateOf(challenge == null) }
    val scope: Scope = if (allTime || challenge == null) Scope.AllTime else Scope.OfChallenge(challenge)
    val standings = remember(d, scope) { Scoring.standings(d, scope) }
    val heroes = remember(d, scope) { HallOfFame.heroes(d, scope) }

    Page("المتصدرون") {
        if (challenge != null) {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SegmentedButton(!allTime, { allTime = false }, SegmentedButtonDefaults.itemShape(0, 2)) { Text("التحدي الحالي", maxLines = 1) }
                SegmentedButton(allTime, { allTime = true }, SegmentedButtonDefaults.itemShape(1, 2)) { Text("كل الماراثون", maxLines = 1) }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                if (allTime) "مجموع النقاط في كل التحديات." else "نقاط «${challenge.book.title}» فقط.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
        }
        if (standings.none { it.score.totalPoints > 0 }) {
            PaperCard { EmptyState("🏁", "لم تُسجَّل نقاط بعد", "حين يسجّل القرّاء دقائقهم سيظهر الترتيب هنا.") }
            return@Page
        }
        Podium(standings.take(3), ctx)
        Spacer(Modifier.height(16.dp))
        PaperCard(padding = androidx.compose.foundation.layout.PaddingValues(vertical = 6.dp)) {
            standings.forEachIndexed { i, s ->
                if (i > 0) HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
                StandingRow(s, isMe = s.score.member.id == ctx.session.myMemberId) { ctx.openMember(s.score.member.id) }
            }
        }
        if (heroes.any { it.names.isNotEmpty() }) {
            SectionTitle("لوحة الشرف")
            heroes.filter { it.names.isNotEmpty() }.chunked(2).forEach { row ->
                Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { h ->
                        PaperCard(Modifier.weight(1f), padding = androidx.compose.foundation.layout.PaddingValues(14.dp)) {
                            Text(h.emoji, fontSize = 24.sp)
                            Spacer(Modifier.height(4.dp))
                            Text(h.title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(Names.join(h.names), style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(h.value, style = MaterialTheme.typography.bodySmall, color = Brand.colors.gold, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun Podium(top: List<Standing>, ctx: Ctx) {
    val c = Brand.colors
    // Second, first, third — the winner stands in the middle.
    val order = listOfNotNull(top.getOrNull(1), top.getOrNull(0), top.getOrNull(2))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
        order.forEach { s ->
            val (color, height, avatar) = when (s.rank) {
                1 -> Triple(c.gold, 118.dp, 64.dp)
                2 -> Triple(c.silver, 88.dp, 54.dp)
                else -> Triple(c.bronze, 70.dp, 50.dp)
            }
            PodiumColumn(s, color, height, avatar, Modifier.weight(1f)) { ctx.openMember(s.score.member.id) }
        }
    }
}

@Composable
private fun PodiumColumn(s: Standing, color: Color, height: Dp, avatar: Dp, modifier: Modifier, onClick: () -> Unit) {
    Column(modifier.clickable(onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally) {
        if (s.rank == 1) Text("👑", fontSize = 22.sp)
        Avatar(s.score.member.name, s.score.member.id, size = avatar, ring = color)
        Spacer(Modifier.height(6.dp))
        Text(s.score.member.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
        Text("${s.score.totalPoints} نقطة", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(6.dp))
        Box(
            Modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp)).background(color.copy(alpha = 0.22f)),
            contentAlignment = Alignment.TopCenter,
        ) {
            Text("${s.rank}", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = color, modifier = Modifier.padding(top = 10.dp))
        }
    }
}

@Composable
private fun StandingRow(s: Standing, isMe: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(if (isMe) Brand.colors.goldSoft.copy(alpha = 0.5f) else Color.Transparent)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("${s.rank}", Modifier.width(28.dp), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Avatar(s.score.member.name, s.score.member.id, size = 38.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(s.score.member.name + if (isMe) " (أنت)" else "", style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "${Fmt.hours(s.score.totalMinutes)} ساعة · ${s.score.readingDays} يوم قراءة",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text("${s.score.totalPoints}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    }
}
