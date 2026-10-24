package com.readingmarathon.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.readingmarathon.app.data.AdminGroup
import com.readingmarathon.app.data.AdminOverview
import com.readingmarathon.app.data.LogEntry
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

internal fun day(millis: Long): LocalDate = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()

private enum class AdminTab(val label: String) {
    OVERVIEW("نظرة عامة"), SUGGESTIONS("الاقتراحات"), BOOKS("الكتب"), GROUPS("المجموعات"),
    USERS("المستخدمون"), NEWS("الإعلانات"), LOG("السجل"),
}

/** Everything on the platform, for the owner's account only (firestore.rules enforce the same). */
@Composable
fun PlatformAdminScreen() {
    val vm = LocalVm.current
    val nav = LocalNav.current
    val overview by vm.admin.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(AdminTab.OVERVIEW) }
    var version by remember { mutableIntStateOf(0) }
    LaunchedEffect(version) { vm.loadAdmin() }
    val refresh: () -> Unit = { version++ }

    Page("لوحة الإدارة", onBack = nav::back, actions = { IconButton(onClick = refresh) { Icon(Icons.Rounded.Refresh, "تحديث") } }) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AdminTab.entries.forEach { t ->
                val badge = if (t == AdminTab.SUGGESTIONS) overview?.openSuggestions?.takeIf { it > 0 }?.let { " ($it)" }.orEmpty() else ""
                Choice(t.label + badge, tab == t) { tab = t }
            }
        }
        Spacer(Modifier.height(14.dp))
        val o = overview
        when (tab) {
            AdminTab.OVERVIEW -> if (o == null) Loading() else Overview(o)
            AdminTab.SUGGESTIONS -> AdminSuggestions(version, refresh)
            AdminTab.BOOKS -> AdminBooks(version, refresh)
            AdminTab.GROUPS -> if (o == null) Loading() else AdminGroups(o)
            AdminTab.USERS -> if (o == null) Loading() else AdminUsers(o, refresh)
            AdminTab.NEWS -> AdminNews(version, refresh)
            AdminTab.LOG -> AdminLog(version)
        }
    }
}

@Composable
internal fun Loading() {
    Row(Modifier.fillMaxWidth().padding(40.dp), horizontalArrangement = Arrangement.Center) { CircularProgressIndicator(color = Brand.colors.gold) }
}

@Composable
private fun Overview(o: AdminOverview) {
    val guests = o.users.count { it.isGuest }
    val week = System.currentTimeMillis() - 7 * 86_400_000L
    PaperCard {
        StatRow(
            "${o.users.size}" to "حساب · $guests دون Google",
            "${o.users.count { it.lastSeen > week }}" to "دخلوا هذا الأسبوع",
            "${o.groups.size}" to "مجموعة · ${o.groups.count { it.minutesLast7 > 0 }} نشطة",
        )
        Spacer(Modifier.height(14.dp)); ListDivider(); Spacer(Modifier.height(14.dp))
        StatRow(
            Fmt.hours(o.totalMinutes.toDouble()) to "ساعة قراءة",
            Fmt.hours(o.minutesLast7.toDouble()) to "ساعة آخر 7 أيام",
            "${o.finishedBooks}" to "إتمام كتاب",
        )
        Spacer(Modifier.height(14.dp)); ListDivider(); Spacer(Modifier.height(14.dp))
        StatRow(
            "${o.catalogBooks}" to "كتاب في الفهرس",
            "${o.pendingBooks}" to "بانتظار المراجعة",
            "${o.openSuggestions}" to "اقتراح مفتوح",
        )
    }
    if (o.daily.any { it > 0 }) {
        SectionLabel("دقائق القراءة يومياً", trailing = { Text("آخر 30 يوماً", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) })
        PaperCard { RhythmChart(o.daily.map { it.toInt() }) }
        SectionLabel("قرّاء نشطون يومياً", trailing = { Text("اليوم ${o.activeReaders.lastOrNull() ?: 0}", style = MaterialTheme.typography.labelMedium) })
        PaperCard { RhythmChart(o.activeReaders) }
    }
    if (o.topBooks.isNotEmpty()) {
        SectionLabel("أكثر الكتب اختياراً")
        PaperCard(padding = PaddingValues(vertical = 4.dp)) {
            o.topBooks.forEachIndexed { i, (title, n) ->
                if (i > 0) ListDivider()
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
                    Text("«$title»", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${n} مجموعة", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

/** Groups: open any of them with full owner rights (settings, members, books, deletion). */
@Composable
private fun AdminGroups(o: AdminOverview) {
    val nav = LocalNav.current
    var q by rememberSaveable { mutableStateOf("") }
    val today = LocalDate.now()
    OutlinedTextField(q, { q = it }, leadingIcon = { Icon(Icons.Rounded.Search, null) }, placeholder = { Text("اسم مجموعة أو بريد مالكها أو رمزها") }, singleLine = true, modifier = Modifier.fillMaxWidth())
    Spacer(Modifier.height(8.dp))
    Text("افتح أي مجموعة لتديرها كمالكها: الإعدادات، الأعضاء والأدوار، الكتب، الحذف.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(10.dp))
    val list = o.groups.filter { q.isBlank() || it.name.contains(q.trim(), true) || it.owners.any { e -> e.contains(q.trim(), true) } || it.inviteCode.equals(q.trim(), true) }
    if (list.isEmpty()) PaperCard { EmptyState(Icons.Rounded.Groups, "لا مجموعات", if (q.isBlank()) "لم تُنشأ أي مجموعة بعد." else "لا نتائج لهذا البحث.") }
    list.forEach { g ->
        GroupRow(g, today) { nav.group(g.id) }
        Spacer(Modifier.height(10.dp))
    }
}

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
        Text("${g.accounts} حساب · ${g.readers} قارئ · ${g.challenges} كتاب · ${Fmt.hours(g.minutes.toDouble())} ساعة", style = MaterialTheme.typography.bodyMedium)
        Text(
            buildString {
                append("آخر قراءة: ")
                append(g.lastActivity?.let { runCatching { Dates.relative(LocalDate.parse(it), today) }.getOrDefault(it) } ?: "لا قراءات")
                if (g.inviteCode.isNotBlank()) append(" · الرمز ${g.inviteCode}")
            },
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun AdminLog(version: Int) {
    val vm = LocalVm.current
    var log by remember { mutableStateOf<List<LogEntry>?>(null) }
    LaunchedEffect(version) { vm.run(quiet = true) { log = vm.backend.adminLog() } }
    val l = log ?: return Loading()
    if (l.isEmpty()) { PaperCard { Text("لا عمليات بعد.", color = MaterialTheme.colorScheme.onSurfaceVariant) }; return }
    PaperCard(padding = PaddingValues(vertical = 4.dp)) {
        l.forEachIndexed { i, e ->
            if (i > 0) ListDivider()
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 9.dp)) {
                Text(e.action, style = MaterialTheme.typography.titleSmall)
                Text(
                    listOf(e.target, Dates.relative(day(e.at), LocalDate.now()) + " " + "%02d:%02d".format(Instant.ofEpochMilli(e.at).atZone(ZoneId.systemDefault()).hour, Instant.ofEpochMilli(e.at).atZone(ZoneId.systemDefault()).minute))
                        .filter { it.isNotBlank() }.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

