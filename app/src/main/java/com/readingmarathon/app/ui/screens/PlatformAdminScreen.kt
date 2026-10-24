package com.readingmarathon.app.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.readingmarathon.app.data.AdminGroup
import com.readingmarathon.app.data.AdminUser
import com.readingmarathon.app.ui.LocalVm
import com.readingmarathon.app.ui.components.EmptyState
import com.readingmarathon.app.ui.components.ListDivider
import com.readingmarathon.app.ui.components.Page
import com.readingmarathon.app.ui.components.PaperCard
import com.readingmarathon.app.ui.components.RhythmChart
import com.readingmarathon.app.ui.components.SectionLabel
import com.readingmarathon.app.ui.components.StatRow
import com.readingmarathon.app.ui.components.Tag
import com.readingmarathon.app.ui.theme.Brand
import com.readingmarathon.core.Dates
import com.readingmarathon.core.Fmt
import com.readingmarathon.core.Visibility
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Everything on the platform, for the owner's account only (firestore.rules enforce the same). */
@Composable
fun PlatformAdminScreen() {
    val vm = LocalVm.current
    val nav = LocalNav.current
    val overview by vm.admin.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var allUsers by rememberSaveable { mutableStateOf(false) }
    val today = vm.today()
    LaunchedEffect(Unit) { vm.loadAdmin() }

    Page("لوحة إدارة المنصة", onBack = nav::back, actions = { IconButton(onClick = vm::loadAdmin) { Icon(Icons.Rounded.Refresh, "تحديث") } }) {
        val o = overview
        if (o == null) {
            CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally).padding(40.dp), color = Brand.colors.gold)
            return@Page
        }
        val guests = o.users.count { it.isGuest }
        val activeWeek = o.groups.count { it.minutesLast7 > 0 }
        val readersWeek = o.users.count { it.lastSeen > System.currentTimeMillis() - 7 * 86_400_000L }
        PaperCard {
            StatRow(
                "${o.users.size}" to "حساب · $guests دون Google",
                "$readersWeek" to "دخلوا هذا الأسبوع",
                "${o.groups.size}" to "مجموعة · $activeWeek نشطة",
            )
            Spacer(Modifier.height(14.dp))
            ListDivider()
            Spacer(Modifier.height(14.dp))
            StatRow(
                Fmt.hours(o.totalMinutes.toDouble()) to "ساعة قراءة",
                Fmt.hours(o.minutesLast7.toDouble()) to "ساعة آخر 7 أيام",
                "${o.finishedBooks}" to "إتمام كتاب",
            )
        }
        if (o.daily.any { it > 0 }) {
            SectionLabel("القراءة على المنصة", trailing = { Text("آخر 30 يوماً", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) })
            PaperCard { RhythmChart(o.daily.map { it.toInt() }) }
        }

        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            query, { query = it },
            leadingIcon = { Icon(Icons.Rounded.Search, null) },
            placeholder = { Text("ابحث باسم مجموعة أو بريد") },
            singleLine = true, modifier = Modifier.fillMaxWidth(),
        )
        val q = query.trim()
        val groups = o.groups.filter { q.isEmpty() || it.name.contains(q, true) || it.owners.any { e -> e.contains(q, true) } || it.inviteCode.equals(q, true) }
        val users = o.users.filter { q.isEmpty() || (it.email ?: "").contains(q, true) || (it.name ?: "").contains(q, true) }

        SectionLabel("المجموعات (${groups.size})")
        if (groups.isEmpty()) PaperCard { EmptyState(Icons.Rounded.Groups, "لا مجموعات", if (q.isEmpty()) "لم تُنشأ أي مجموعة بعد." else "لا نتائج لهذا البحث.") }
        groups.forEach { g ->
            GroupRow(g, today) { nav.group(g.id) }
            Spacer(Modifier.height(10.dp))
        }

        SectionLabel("الحسابات (${users.size})")
        PaperCard(padding = PaddingValues(vertical = 4.dp)) {
            val shown = if (allUsers || q.isNotEmpty()) users else users.take(15)
            if (shown.isEmpty()) Text("لا حسابات.", Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            shown.forEachIndexed { i, u ->
                if (i > 0) ListDivider()
                UserRow(u, today)
            }
            if (!allUsers && q.isEmpty() && users.size > 15) TextButton(onClick = { allUsers = true }, modifier = Modifier.padding(horizontal = 8.dp)) { Text("عرض الجميع (${users.size})") }
        }
    }
}

private fun day(millis: Long): LocalDate = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()

@Composable
private fun GroupRow(g: AdminGroup, today: LocalDate, onOpen: () -> Unit) {
    PaperCard(onClick = onOpen) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(g.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(g.owners.joinToString("، ").ifEmpty { "بلا مالك" }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Tag(if (g.visibility == Visibility.PUBLIC) "عامة" else "خاصة", strong = g.minutesLast7 > 0)
        }
        Spacer(Modifier.height(8.dp))
        Text("${g.accounts} حساب · ${g.readers} قارئ · ${g.challenges} كتاب", style = MaterialTheme.typography.bodyMedium)
        Text("${Fmt.hours(g.minutes.toDouble())} ساعة · هذا الأسبوع ${Fmt.hours(g.minutesLast7.toDouble())}", style = MaterialTheme.typography.bodyMedium)
        Text(
            buildString {
                append("آخر قراءة: ")
                append(g.lastActivity?.let { runCatching { Dates.relative(LocalDate.parse(it), today) }.getOrDefault(it) } ?: "لا قراءات")
                if (g.createdAt > 0) append(" · أُنشئت ${Dates.long(day(g.createdAt))}")
                if (g.inviteCode.isNotBlank()) append(" · الرمز ${g.inviteCode}")
            },
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun UserRow(u: AdminUser, today: LocalDate) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(u.name?.takeIf { it.isNotBlank() } ?: u.email ?: "دون اسم", style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                buildString {
                    if (u.email != null && !u.name.isNullOrBlank()) append(u.email).append(" · ")
                    append("${u.groups} مجموعة · ${Fmt.hours(u.minutes.toDouble())} ساعة")
                    if (u.lastSeen > 0) append(" · آخر دخول ${Dates.relative(day(u.lastSeen), today)}")
                },
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (u.isGuest) Tag("دون Google")
    }
}
