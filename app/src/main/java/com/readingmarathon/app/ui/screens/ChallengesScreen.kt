package com.readingmarathon.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.readingmarathon.app.report.Reports
import com.readingmarathon.app.ui.components.Avatar
import com.readingmarathon.app.ui.components.BarChart
import com.readingmarathon.app.ui.components.challengesCount
import com.readingmarathon.app.ui.components.BookCover
import com.readingmarathon.app.ui.components.BookTitle
import com.readingmarathon.app.ui.components.DonutChart
import com.readingmarathon.app.ui.components.EmptyState
import com.readingmarathon.app.ui.components.AreaChart
import com.readingmarathon.app.ui.components.Heatmap
import com.readingmarathon.app.ui.components.PaperCard
import com.readingmarathon.app.ui.components.Pill
import com.readingmarathon.app.ui.components.RankBars
import com.readingmarathon.app.ui.components.SectionTitle
import com.readingmarathon.app.ui.components.StatTile
import com.readingmarathon.app.ui.theme.Brand
import com.readingmarathon.core.AchievementType
import com.readingmarathon.core.Analytics
import com.readingmarathon.core.ChallengeStatus
import com.readingmarathon.core.Dates
import com.readingmarathon.core.Fmt
import com.readingmarathon.core.News
import com.readingmarathon.core.Scope
import com.readingmarathon.core.Scoring

@Composable
fun ChallengesScreen(ctx: Ctx) {
    val list = ctx.data.timeline.reversed()
    Page(
        "التحديات",
        actions = {
            if (ctx.isOrganizer) IconButton(onClick = { ctx.editChallenge(null) }) { Icon(Icons.Rounded.Add, "تحدٍّ جديد") }
        },
    ) {
        if (list.isEmpty()) {
            PaperCard {
                EmptyState("📚", "لا توجد تحديات", "كل تحدٍّ كتاب مشترك تقرؤه المجموعة في مدة محددة.") {
                    if (ctx.isOrganizer) Button(onClick = { ctx.editChallenge(null) }) { Text("أنشئ أول تحدٍّ") }
                }
            }
            return@Page
        }
        Text("${challengesCount(list.size)} منذ بداية الماراثون", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))
        list.forEach { c ->
            val status = c.status(ctx.today)
            val finishers = ctx.data.achievements.count { it.challengeId == c.id && it.type == AchievementType.FINISHED_COMMON_BOOK }
            PaperCard(onClick = { ctx.openChallenge(c.id) }, modifier = Modifier.padding(bottom = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BookCover(c.book.title, c.id, width = 58.dp)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        BookTitle(c.book.title, c.book.author)
                        Spacer(Modifier.height(4.dp))
                        Text("${Dates.short(c.start)} – ${Dates.long(c.end)} · ${c.lengthDays} يوماً", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            when (status) {
                                ChallengeStatus.ACTIVE -> Pill("جارٍ الآن", color = Brand.colors.goldSoft, textColor = MaterialTheme.colorScheme.onSurface)
                                ChallengeStatus.UPCOMING -> Pill("قادم", color = MaterialTheme.colorScheme.primaryContainer, textColor = MaterialTheme.colorScheme.onPrimaryContainer)
                                ChallengeStatus.FINISHED -> Pill("منتهٍ", color = MaterialTheme.colorScheme.surfaceContainerHigh, textColor = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (finishers > 0) Pill("$finishers أنهوا الكتاب", color = MaterialTheme.colorScheme.surfaceContainerHigh, textColor = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ChallengeDetailScreen(ctx: Ctx, id: String) {
    val d = ctx.data
    val c = d.challenge(id)
    val context = LocalContext.current
    if (c == null) {
        Page("التحدي", onBack = { ctx.nav.popBackStack() }) { EmptyState("🔎", "التحدي غير موجود", "ربما حُذف.") }
        return
    }
    val scope = Scope.OfChallenge(c)
    val ids = d.activeMembers.map { it.id }.toSet()
    val entries = remember(d, c) { Scoring.entriesIn(d, scope).filter { it.memberId in ids } }
    val standings = remember(d, c) { Scoring.standings(d, scope) }
    val lastDay = minOf(c.end, ctx.today).let { if (it.isBefore(c.start)) c.start else it }
    val series = remember(d, c) { Analytics.daily(entries, c.start, lastDay) }
    var showAll by remember { mutableStateOf(false) }

    Page(
        c.book.title,
        onBack = { ctx.nav.popBackStack() },
        actions = {
            IconButton(onClick = { Reports.shareChallengePdf(context, d, c, ctx.today) }) { Icon(Icons.Rounded.PictureAsPdf, "تقرير PDF") }
            if (ctx.isOrganizer) IconButton(onClick = { ctx.editChallenge(c.id) }) { Icon(Icons.Rounded.Edit, "تعديل") }
        },
    ) {
        ChallengeHero(c, ctx)
        Spacer(Modifier.height(14.dp))
        val kpis = remember(d, c) { Analytics.challengeKpis(d, c, ctx.today) }
        kpis.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { StatTile(it.value, it.label, Modifier.weight(1f)) }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }

        if (c.status(ctx.today) == ChallengeStatus.UPCOMING) {
            PaperCard { EmptyState("⏳", "لم يبدأ بعد", "يبدأ التحدي ${Dates.relative(c.start, ctx.today)}، في ${Dates.long(c.start)}.") }
            return@Page
        }

        SectionTitle("الترتيب")
        PaperCard {
            val rows = standings.filter { it.score.totalPoints > 0 }
            if (rows.isEmpty()) Text("لا نقاط بعد.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            (if (showAll) rows else rows.take(5)).forEach { s ->
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${s.rank}", Modifier.width(26.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Avatar(s.score.member.name, s.score.member.id, size = 32.dp)
                    Spacer(Modifier.width(10.dp))
                    Text(s.score.member.name, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
                    Text("${s.score.totalPoints} نقطة", style = MaterialTheme.typography.labelLarge)
                }
            }
            if (rows.size > 5) TextButton(onClick = { showAll = !showAll }) { Text(if (showAll) "عرض أقل" else "عرض الجميع (${rows.size})") }
        }

        if (entries.isNotEmpty()) {
            SectionTitle("الساعات المتراكمة")
            PaperCard {
                AreaChart(Analytics.cumulativeHours(series).map { it.second })
                Spacer(Modifier.height(6.dp))
                Text("مجموع ساعات المجموعة يوماً بعد يوم.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            SectionTitle("ساعات كل قارئ")
            PaperCard {
                val rows = standings.map { it.score.member.name to it.score.totalMinutes.toDouble() }.filter { it.second > 0 }.sortedByDescending { it.second }
                RankBars(rows, format = { "${Fmt.hours(it)} س" })
            }

            SectionTitle("أيام الأسبوع")
            PaperCard {
                BarChart(Analytics.weekdayShare(entries), valueSuffix = "%")
                Text("نسبة دقائق القراءة في كل يوم من أيام الأسبوع.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            SectionTitle("الكتاب المشترك وغيره")
            PaperCard {
                val (common, other) = Analytics.focus(entries)
                DonutChart(
                    listOf("الكتاب المشترك" to common.toDouble(), "كتب أخرى" to other.toDouble()),
                    centerTop = Fmt.hours(common + other),
                    centerBottom = "ساعة",
                )
            }

            SectionTitle("خريطة القراءة")
            PaperCard {
                Heatmap(Analytics.heatmap(entries, c.start, c.end))
                Spacer(Modifier.height(6.dp))
                Text("كل مربع يوم؛ كلما اشتد اللون زادت دقائق المجموعة.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        val finishers = d.achievements.filter { it.challengeId == c.id && it.type == AchievementType.FINISHED_COMMON_BOOK && it.memberId in ids }.sortedBy { it.date }
        if (finishers.isNotEmpty()) {
            SectionTitle("أنهوا الكتاب")
            PaperCard {
                finishers.forEachIndexed { i, a ->
                    val m = d.member(a.memberId) ?: return@forEachIndexed
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(if (i == 0) "🥇" else "📗")
                        Spacer(Modifier.width(10.dp))
                        Text(m.name, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                        Text(Dates.short(a.date), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        val news = remember(d, c) { News.forChallenge(d, c, ctx.today) }
        if (news.isNotEmpty()) {
            SectionTitle("أبرز ما حدث")
            PaperCard {
                news.forEach { n -> Text("${n.emoji}  ${n.text}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 4.dp)) }
            }
        }
    }
}
