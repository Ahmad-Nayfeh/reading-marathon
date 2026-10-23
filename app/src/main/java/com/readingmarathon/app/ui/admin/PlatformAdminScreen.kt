package com.readingmarathon.app.ui.admin

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.LibraryBooks
import androidx.compose.material.icons.rounded.ManageAccounts
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.TrendingUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.readingmarathon.app.AppViewModel
import com.readingmarathon.app.data.AdminMarathon
import com.readingmarathon.app.data.AdminUser
import com.readingmarathon.app.ui.components.EmptyState
import com.readingmarathon.app.ui.components.IconBadge
import com.readingmarathon.app.ui.components.PaperCard
import com.readingmarathon.app.ui.components.Pill
import com.readingmarathon.app.ui.components.RhythmChart
import com.readingmarathon.app.ui.components.SectionTitle
import com.readingmarathon.app.ui.components.StatTile
import com.readingmarathon.app.ui.screens.Page
import com.readingmarathon.app.ui.theme.Brand
import com.readingmarathon.core.Dates
import com.readingmarathon.core.Fmt
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Everything on the platform, for the owner's account only (firestore.rules enforce the same). */
@Composable
fun PlatformAdminScreen(vm: AppViewModel) {
    val overview by vm.admin.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var allUsers by rememberSaveable { mutableStateOf(false) }
    val today = vm.today()
    BackHandler(onBack = vm::closeAdminPanel)

    Page(
        "لوحة إدارة المنصة",
        onBack = vm::closeAdminPanel,
        actions = { IconButton(onClick = vm::loadAdmin) { Icon(Icons.Rounded.Refresh, "تحديث") } },
    ) {
        val o = overview
        if (o == null) {
            Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Brand.colors.gold) }
            return@Page
        }
        val guests = o.users.count { it.isGuest }
        val activeWeek = o.marathons.count { it.minutesLast7 > 0 }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile("${o.marathons.size}", "ماراثون · $activeWeek نشط هذا الأسبوع", Modifier.weight(1f), icon = Icons.Rounded.AutoStories)
            StatTile("${o.users.size}", "حساب · $guests ضيف", Modifier.weight(1f), icon = Icons.Rounded.ManageAccounts)
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile("${o.totalMembers}", "قارئ في كل الماراثونات", Modifier.weight(1f), icon = Icons.Rounded.Group)
            StatTile("${o.finishedBooks}", "كتاباً أُنهي", Modifier.weight(1f), icon = Icons.Rounded.LibraryBooks)
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile(Fmt.hours(o.totalMinutes.toDouble()), "ساعة قراءة · ${o.totalEntries} يوم مسجّل", Modifier.weight(1f), icon = Icons.Rounded.Schedule)
            StatTile(Fmt.hours(o.minutesLast7.toDouble()), "ساعة في آخر 7 أيام", Modifier.weight(1f), icon = Icons.Rounded.TrendingUp)
        }

        if (o.daily.any { it > 0 }) {
            SectionTitle("القراءة على المنصة", trailing = { Text("آخر 30 يوماً", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) })
            PaperCard { RhythmChart(o.daily.map { it.toInt() }) }
        }

        Spacer(Modifier.height(14.dp))
        OutlinedTextField(
            query, { query = it },
            leadingIcon = { Icon(Icons.Rounded.Search, null) },
            placeholder = { Text("ابحث باسم ماراثون أو بريد") },
            singleLine = true, modifier = Modifier.fillMaxWidth(),
        )
        val q = query.trim()
        val marathons = o.marathons.filter { q.isEmpty() || it.name.contains(q, true) || it.organizers.any { e -> e.contains(q, true) } || it.inviteCode.equals(q, true) }
        val users = o.users.filter { q.isEmpty() || (it.email ?: "").contains(q, true) || (it.name ?: "").contains(q, true) }

        SectionTitle("الماراثونات (${marathons.size})")
        if (marathons.isEmpty()) {
            PaperCard { EmptyState(Icons.Rounded.AutoStories, "لا ماراثونات", if (q.isEmpty()) "لم يُنشأ أي ماراثون بعد." else "لا نتائج لهذا البحث.") }
        }
        marathons.forEach { m ->
            MarathonCard(m, today) { vm.adminOpen(m.id) }
            Spacer(Modifier.height(10.dp))
        }

        SectionTitle("الحسابات (${users.size})")
        PaperCard(padding = androidx.compose.foundation.layout.PaddingValues(vertical = 4.dp)) {
            val shown = if (allUsers || q.isNotEmpty()) users else users.take(15)
            if (shown.isEmpty()) Text("لا حسابات.", Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            shown.forEachIndexed { i, u ->
                if (i > 0) HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
                UserRow(u, today)
            }
            if (!allUsers && q.isEmpty() && users.size > 15) {
                TextButton(onClick = { allUsers = true }, modifier = Modifier.padding(horizontal = 8.dp)) { Text("عرض الجميع (${users.size})") }
            }
        }
    }
}

private fun day(millis: Long): LocalDate = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()

@Composable
private fun MarathonCard(m: AdminMarathon, today: LocalDate, onOpen: () -> Unit) {
    PaperCard(onClick = onOpen) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(Icons.Rounded.AutoStories, size = 40.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(m.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    m.organizers.joinToString("، ").ifEmpty { "بلا مشرف" },
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            if (m.minutesLast7 > 0) Pill("نشط", color = Brand.colors.goldSoft, textColor = MaterialTheme.colorScheme.onSurface)
        }
        Spacer(Modifier.height(10.dp))
        Text(
            "${m.activeMembers} قارئ نشط من ${m.members} · ${m.accounts} حساب · ${m.challenges} تحدٍّ",
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            "${Fmt.hours(m.minutes.toDouble())} ساعة · ${m.readingDays} يوم مسجّل · هذا الأسبوع ${Fmt.hours(m.minutesLast7.toDouble())} ساعة",
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            buildString {
                append("آخر قراءة: ")
                append(m.lastActivity?.let { runCatching { Dates.relative(LocalDate.parse(it), today) }.getOrDefault(it) } ?: "لا قراءات")
                if (m.createdAt > 0) append(" · أُنشئ ${Dates.long(day(m.createdAt))}")
                if (m.inviteCode.isNotBlank()) append(" · الرمز ${m.inviteCode}")
            },
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun UserRow(u: AdminUser, today: LocalDate) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                u.email ?: u.name ?: "ضيف",
                style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Text(
                buildString {
                    if (u.email != null && !u.name.isNullOrBlank()) append(u.name).append(" · ")
                    append("${u.marathons} ماراثون")
                    if (u.lastSeen > 0) append(" · آخر دخول ${Dates.relative(day(u.lastSeen), today)}")
                },
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (u.isGuest) Pill("ضيف")
    }
}
