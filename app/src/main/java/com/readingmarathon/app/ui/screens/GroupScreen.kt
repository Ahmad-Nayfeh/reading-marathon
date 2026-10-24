package com.readingmarathon.app.ui.screens

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PersonAddAlt
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.readingmarathon.app.ui.LocalVm
import com.readingmarathon.app.ui.components.Avatar
import com.readingmarathon.app.ui.components.BookCover
import com.readingmarathon.app.ui.components.BookTitle
import com.readingmarathon.app.ui.components.EmptyState
import com.readingmarathon.app.ui.components.GhostButton
import com.readingmarathon.app.ui.components.ListDivider
import com.readingmarathon.app.ui.components.Page
import com.readingmarathon.app.ui.components.PaperCard
import com.readingmarathon.app.ui.components.PrimaryButton
import com.readingmarathon.app.ui.components.Segments
import com.readingmarathon.app.ui.components.Tag
import com.readingmarathon.app.ui.theme.Brand
import com.readingmarathon.core.Activity
import com.readingmarathon.core.Challenge
import com.readingmarathon.core.ChallengeStatus
import com.readingmarathon.core.Dates
import com.readingmarathon.core.Export
import com.readingmarathon.core.Feed
import com.readingmarathon.core.Fmt
import com.readingmarathon.core.GroupData
import com.readingmarathon.core.Member
import com.readingmarathon.core.Role
import com.readingmarathon.core.Scope
import com.readingmarathon.core.Scoring
import com.readingmarathon.core.Visibility
import java.time.LocalDate

const val JOIN_URL = "https://ahmad-nayfeh.github.io/marathon/join/?code="

fun shareText(context: Context, text: String, title: String) {
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
    context.startActivity(Intent.createChooser(send, title))
}

fun inviteText(name: String, code: String) =
    "انضم إلى مجموعة «$name» في تطبيق ماراثون القراءة:\n$JOIN_URL$code\n\nأو أدخل الرمز $code من «المجموعات ← انضم برمز»."

/** A group: its current book, the ranking, what members read lately, members and past books. */
@Composable
fun GroupScreen(id: String) {
    val vm = LocalVm.current
    val nav = LocalNav.current
    val groups by vm.groups.collectAsStateWithLifecycle()
    val account by vm.account.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val uid = account?.uid.orEmpty()
    val isAdmin = account?.isAdmin == true

    // The platform admin can open any group; keep it loaded while it's on screen.
    DisposableEffect(id, isAdmin) {
        val peek = isAdmin && id !in vm.profile.value?.groups.orEmpty()
        if (peek) vm.backend.watch(id)
        onDispose { if (peek) vm.backend.unwatch(id) }
    }

    val d = groups[id]
    if (d == null) {
        Page("", onBack = nav::back) {
            CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally).padding(32.dp), color = Brand.colors.gold)
        }
        return
    }
    val today = vm.today()
    // The platform admin manages every group as its owner would.
    val role = if (isAdmin) Role.OWNER else d.group.roleOf(uid)
    val manager = role?.canManage == true
    var tab by rememberSaveable { mutableStateOf(0) }
    var menu by remember { mutableStateOf(false) }
    var confirmLeave by remember { mutableStateOf(false) }
    var memberSheet by remember { mutableStateOf<Member?>(null) }
    val current = d.currentChallenge(today)

    Page(
        d.group.name,
        onBack = nav::back,
        subtitle = d.group.about.ifBlank { null },
        actions = {
            IconButton(onClick = { shareText(context, inviteText(d.group.name, d.group.inviteCode), "دعوة إلى المجموعة") }) {
                Icon(Icons.Rounded.PersonAddAlt, "ادعُ قارئاً")
            }
            if (manager) IconButton(onClick = { nav.groupSettings(id) }) { Icon(Icons.Rounded.Settings, "إعدادات المجموعة") }
            if (d.group.roleOf(uid) != null) {
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, "المزيد") }
                    DropdownMenu(menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text("مغادرة المجموعة") },
                            leadingIcon = { Icon(Icons.AutoMirrored.Rounded.Logout, null) },
                            onClick = { menu = false; confirmLeave = true },
                        )
                    }
                }
            }
        },
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Tag(Fmt.members(d.activeMembers.size), icon = Icons.Rounded.Groups)
            Tag(if (d.group.visibility == Visibility.PUBLIC) "عامة" else "خاصة", icon = if (d.group.visibility == Visibility.PUBLIC) Icons.Rounded.Public else Icons.Rounded.Key)
            if (role == Role.OWNER) Tag("المالك", strong = true) else if (role == Role.ADMIN) Tag("مشرف", strong = true)
        }
        Spacer(Modifier.height(16.dp))
        CurrentBook(d, current, uid, today, manager)
        Spacer(Modifier.height(18.dp))
        Segments(listOf("الترتيب", "النشاط", "الأعضاء", "الكتب"), tab, { tab = it })
        Spacer(Modifier.height(10.dp))
        when (tab) {
            0 -> Ranking(d, current, uid, today)
            1 -> ActivityList(d, today)
            2 -> Members(d, uid, manager) { memberSheet = it }
            3 -> Books(d, today, manager)
        }
    }

    memberSheet?.let { m -> MemberSheet(d, m, uid, role) { memberSheet = null } }

    if (confirmLeave) {
        AlertDialog(
            onDismissRequest = { confirmLeave = false },
            title = { Text("مغادرة «${d.group.name}»؟") },
            text = { Text("لن تظهر قراءتك القادمة في هذه المجموعة. ما سجّلته من قبل يبقى في سجلّك الشخصي.") },
            confirmButton = {
                TextButton(onClick = { confirmLeave = false; vm.leave(id) { nav.home() } }) { Text("مغادرة", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmLeave = false }) { Text("إلغاء") } },
        )
    }
}

@Composable
private fun CurrentBook(d: GroupData, c: Challenge?, uid: String, today: LocalDate, manager: Boolean) {
    val vm = LocalVm.current
    val nav = LocalNav.current
    var suggesting by remember { mutableStateOf(false) }
    if (c == null) {
        PaperCard {
            EmptyState(
                Icons.Rounded.Flag, "لا يوجد كتاب مشترك بعد",
                if (manager) "اختر كتاباً ومدة، فيتسابق الأعضاء على قراءته. القراءة تُحسب للمجموعة حتى دون كتاب."
                else "حين يختار مشرف المجموعة كتاباً سيظهر هنا. قراءتك تُحسب في الترتيب على كل حال.",
            ) { if (manager) PrimaryButton("أضف كتاباً", onClick = { nav.book(d.group.id, null) }, icon = Icons.Rounded.Add) }
        }
        return
    }
    val status = c.status(today)
    val finishers = d.finishes.filter { it.challengeId == c.id }.sortedBy { it.date }.mapNotNull { f -> d.member(f.memberId)?.takeIf { it.active } }
    val mine = d.finished(uid, c.id)
    val inGroup = d.member(uid)?.active == true
    PaperCard {
        Row {
            BookCover(c.book.title, c.id, width = 64.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    when (status) { ChallengeStatus.ACTIVE -> "نقرأ الآن"; ChallengeStatus.UPCOMING -> "الكتاب القادم"; ChallengeStatus.FINISHED -> "آخر كتاب" },
                    style = MaterialTheme.typography.labelMedium, color = Brand.colors.gold,
                )
                BookTitle(c.book.title, c.book.author)
                Text(Dates.span(c.start, c.end), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (c.bookId != null) {
                    Text(
                        "خطأ في العنوان أو المؤلف؟",
                        style = MaterialTheme.typography.labelMedium, color = Brand.colors.gold,
                        modifier = Modifier.clickable { suggesting = true }.padding(top = 6.dp),
                    )
                }
            }
        }
        if (suggesting && c.bookId != null) SuggestSheet(c.bookId!!, c.book.title, c.book.author) { suggesting = false }
        Spacer(Modifier.height(14.dp))
        when (status) {
            ChallengeStatus.ACTIVE -> {
                LinearProgressIndicator(
                    progress = { c.dayNumber(today).toFloat() / c.lengthDays },
                    modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                    color = Brand.colors.gold, trackColor = MaterialTheme.colorScheme.surfaceContainerHigh, drawStopIndicator = {}, gapSize = 0.dp,
                )
                Spacer(Modifier.height(6.dp))
                val left = c.lengthDays - c.dayNumber(today)
                Text(
                    "اليوم ${c.dayNumber(today)} من ${c.lengthDays}" + if (left > 0) " · بقي ${Fmt.days(left)}" else " · اليوم الأخير",
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            ChallengeStatus.UPCOMING -> Text(
                "يبدأ ${Dates.relative(c.start, today).let { if (it == "اليوم") "اليوم" else "في ${Dates.long(c.start)}" }}",
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ChallengeStatus.FINISHED -> Text("انتهى في ${Dates.long(c.end)}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (finishers.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(
                "أتمّه: " + finishers.joinToString("، ") { it.name },
                style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
        }
        if (inGroup && c.acceptsFinishOn(today)) {
            Spacer(Modifier.height(14.dp))
            if (mine == null) {
                GhostButton(
                    "أتممت الكتاب (+${Fmt.points(d.group.rules.finishBonus)})",
                    onClick = { vm.run("مبارك! سُجّل إتمامك الكتاب.") { vm.backend.setFinished(d.group.id, c.id, today) } },
                    icon = Icons.Rounded.Flag, modifier = Modifier.fillMaxWidth(),
                )
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Tag("أتممته في ${Dates.short(mine.date)}", strong = true, icon = Icons.Rounded.Flag)
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { vm.run { vm.backend.setFinished(d.group.id, c.id, null) } }) { Text("تراجع") }
                }
            }
        }
    }
}

@Composable
private fun Ranking(d: GroupData, c: Challenge?, uid: String, today: LocalDate) {
    val context = LocalContext.current
    var allTime by rememberSaveable { mutableStateOf(c == null) }
    val scope = if (allTime || c == null) Scope.AllTime else Scope.OfChallenge(c)
    val rows = remember(d, scope) { Scoring.standings(d, scope) }
    if (c != null) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Choice("هذا الكتاب", !allTime, Modifier.weight(1f)) { allTime = false }
            Choice("منذ البداية", allTime, Modifier.weight(1f)) { allTime = true }
        }
        Spacer(Modifier.height(12.dp))
    }
    PaperCard(padding = PaddingValues(vertical = 6.dp)) {
        rows.forEachIndexed { i, s ->
            if (i > 0) ListDivider()
            val me = s.score.member.id == uid
            Row(
                Modifier.fillMaxWidth().background(if (me) Brand.colors.goldSoft.copy(alpha = 0.45f) else androidx.compose.ui.graphics.Color.Transparent)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "${s.rank}",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (s.rank <= 3 && s.score.points > 0) Brand.colors.gold else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(28.dp),
                )
                Avatar(s.score.member.name, s.score.member.id, size = 34.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(s.score.member.name + if (me) " (أنت)" else "", style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        buildString {
                            append(Fmt.duration(s.score.minutes))
                            if (d.group.rules.countAll && s.score.sharedMinutes > 0 && scope is Scope.OfChallenge) append(" (منها ${Fmt.duration(s.score.sharedMinutes)} في الكتاب)")
                            append(" · ${Fmt.days(s.score.readingDays)}")
                            if (s.score.finishes > 0) append(if (scope is Scope.OfChallenge) " · أتمّ الكتاب" else " · ${Fmt.books(s.score.finishes)}")
                        },
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("${s.score.points}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("نقطة", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
    Spacer(Modifier.height(8.dp))
    Text(
        (if (d.group.rules.countAll) "كل ${Fmt.minutes(d.group.rules.minutesPerPoint)} قراءة في أي كتاب = نقطة" else "كل ${Fmt.minutes(d.group.rules.minutesPerPoint)} في الكتاب المشترك = نقطة") +
            "، وإتمام الكتاب = ${Fmt.points(d.group.rules.finishBonus)}.",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(12.dp))
    GhostButton("شارك الترتيب", onClick = { shareText(context, Export.rankingText(d, scope, today), "مشاركة الترتيب") }, icon = Icons.Rounded.Share, modifier = Modifier.fillMaxWidth())
}

@Composable
private fun ActivityList(d: GroupData, today: LocalDate) {
    val feed = remember(d) { Feed.of(d) }
    if (feed.isEmpty()) {
        PaperCard { EmptyState(Icons.Rounded.Groups, "لا نشاط بعد", "حين يسجّل الأعضاء قراءتهم ستظهر هنا.") }
        return
    }
    PaperCard(padding = PaddingValues(vertical = 6.dp)) {
        feed.forEachIndexed { i, a ->
            if (i > 0) ListDivider()
            val m = d.member(a.memberId) ?: return@forEachIndexed
            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Avatar(m.name, m.id, size = 32.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(m.name, style = MaterialTheme.typography.titleSmall)
                    Text(
                        when (a) {
                            is Activity.Read -> "قراءة ${Fmt.minutes(a.minutes)}"
                            is Activity.Finished -> "إتمام «${a.challenge.book.title}»"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (a is Activity.Finished) Brand.colors.gold else MaterialTheme.colorScheme.onSurface,
                    )
                }
                val date = when (a) { is Activity.Read -> a.date; is Activity.Finished -> a.date }
                Text(Dates.relative(date, today), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun Members(d: GroupData, uid: String, manager: Boolean, onMember: (Member) -> Unit) {
    val nav = LocalNav.current
    val members = d.activeMembers.sortedWith(compareBy<Member>({ roleOrder(d.group.roles[it.id]) }, { it.name }))
    PaperCard(padding = PaddingValues(vertical = 6.dp)) {
        members.forEachIndexed { i, m ->
            if (i > 0) ListDivider()
            Row(
                Modifier.fillMaxWidth().then(if (manager && m.id != uid) Modifier.clickable { onMember(m) } else Modifier)
                    .padding(horizontal = 14.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Avatar(m.name, m.id, size = 34.dp)
                Spacer(Modifier.width(12.dp))
                Text(m.name + if (m.id == uid) " (أنت)" else "", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                when {
                    d.group.roles[m.id] == Role.OWNER -> Tag("المالك", strong = true)
                    d.group.roles[m.id] == Role.ADMIN -> Tag("مشرف", strong = true)
                }
            }
        }
    }
    if (manager) {
        Spacer(Modifier.height(8.dp))
        Text(
            "المس عضواً لإدارته.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(10.dp))
        GhostButton("إعدادات المجموعة", onClick = { nav.groupSettings(d.group.id) }, icon = Icons.Rounded.Settings, modifier = Modifier.fillMaxWidth())
    }
}

private fun roleOrder(r: Role?) = when (r) { Role.OWNER -> 0; Role.ADMIN -> 1; Role.MEMBER -> 2; null -> 3 }

@Composable
private fun Books(d: GroupData, today: LocalDate, manager: Boolean) {
    val nav = LocalNav.current
    val books = d.timeline.reversed()
    if (books.isEmpty()) {
        PaperCard { EmptyState(Icons.Rounded.Flag, "لا كتب بعد", "كل كتاب تقرؤه المجموعة سيبقى هنا مع من أتمّه ومن تصدّر.") }
    }
    books.forEach { c ->
        val leaders = remember(d, c) { Scoring.standings(d, Scope.OfChallenge(c)).filter { it.rank == 1 && it.score.points > 0 } }
        val finished = d.finishes.count { it.challengeId == c.id && d.member(it.memberId)?.active == true }
        PaperCard(onClick = if (manager) ({ nav.book(d.group.id, c.id) }) else null) {
            Row {
                BookCover(c.book.title, c.id, width = 44.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("«${c.book.title}»", style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(
                        Dates.span(c.start, c.end) + when (c.status(today)) {
                            ChallengeStatus.ACTIVE -> " · الآن"
                            ChallengeStatus.UPCOMING -> " · قادم"
                            ChallengeStatus.FINISHED -> ""
                        },
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(4.dp))
                    if (c.status(today) != ChallengeStatus.UPCOMING) {
                        Text(
                            buildString {
                                append(if (finished == 0) "لم يتمّه أحد" else "أتمّه ${Fmt.readers(finished)}")
                                if (leaders.isNotEmpty()) append(" · الأول: ${leaders.joinToString("، ") { it.score.member.name }}")
                            },
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
    }
    if (manager) GhostButton("كتاب جديد", onClick = { nav.book(d.group.id, null) }, icon = Icons.Rounded.Add, modifier = Modifier.fillMaxWidth())
}
