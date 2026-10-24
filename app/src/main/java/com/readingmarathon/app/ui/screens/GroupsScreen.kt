package com.readingmarathon.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.TravelExplore
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.readingmarathon.app.data.GroupPreview
import com.readingmarathon.app.ui.LocalVm
import com.readingmarathon.app.ui.components.EmptyState
import com.readingmarathon.app.ui.components.GhostButton
import com.readingmarathon.app.ui.components.Page
import com.readingmarathon.app.ui.components.PaperCard
import com.readingmarathon.app.ui.components.PrimaryButton
import com.readingmarathon.app.ui.components.SectionLabel
import com.readingmarathon.app.ui.components.Tag
import com.readingmarathon.core.Dates
import com.readingmarathon.core.Fmt
import com.readingmarathon.core.Limits
import com.readingmarathon.core.Visibility

@Composable
fun GroupsScreen() {
    val vm = LocalVm.current
    val nav = LocalNav.current
    val mine by vm.myGroups.collectAsStateWithLifecycle()
    val account by vm.account.collectAsStateWithLifecycle()
    var publicGroups by remember { mutableStateOf<List<GroupPreview>?>(null) }
    LaunchedEffect(Unit) { vm.loadPublic { publicGroups = it } }
    val today = vm.today()

    Page("المجموعات", subtitle = "قراءتك تُحسب في كل مجموعة تنضم إليها.") {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PrimaryButton("مجموعة جديدة", onClick = nav::create, icon = Icons.Rounded.Add, modifier = Modifier.weight(1f))
            GhostButton("انضم برمز", onClick = { nav.join() }, icon = Icons.Rounded.Key)
        }
        if (mine.isNotEmpty()) {
            SectionLabel("مجموعاتي")
            mine.forEach { g ->
                GroupCard(g, account?.uid.orEmpty(), today) { nav.group(g.group.id) }
                Spacer(Modifier.height(10.dp))
            }
        }
        SectionLabel("مجموعات عامة")
        val others = publicGroups?.filter { p -> mine.none { it.group.id == p.id } }
        when {
            others == null -> CircularProgressIndicator(Modifier.padding(16.dp).align(Alignment.CenterHorizontally))
            others.isEmpty() -> PaperCard {
                EmptyState(Icons.Rounded.TravelExplore, "لا مجموعات عامة بعد", "حين تُنشئ مجموعة يمكنك جعلها عامة ليجدها القرّاء هنا وينضموا إليها.")
            }
            else -> others.forEach { p ->
                PreviewCard(p) {
                    GhostButton("انضم", onClick = { vm.join(p, null) { nav.group(p.id) } })
                }
                Spacer(Modifier.height(10.dp))
            }
        }
    }
}

/** A group seen from outside: name, about, size and current book. */
@Composable
fun PreviewCard(p: GroupPreview, action: @Composable () -> Unit) {
    PaperCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(p.name, style = MaterialTheme.typography.headlineSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(Fmt.members(p.memberCount), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.width(10.dp))
            action()
        }
        if (p.about.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(p.about, style = MaterialTheme.typography.bodyMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
        p.current?.let { b ->
            Spacer(Modifier.height(10.dp))
            Text("يقرؤون «${b.title}» · ${Dates.span(b.start, b.end)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun CreateGroupScreen() {
    val vm = LocalVm.current
    val nav = LocalNav.current
    val busy by vm.busy.collectAsStateWithLifecycle()
    var name by rememberSaveable { mutableStateOf("") }
    var about by rememberSaveable { mutableStateOf("") }
    var public by rememberSaveable { mutableStateOf(false) }
    Page("مجموعة جديدة", onBack = nav::back, subtitle = "ستكون مالكها، وتدعو القرّاء برمز من 6 أحرف.") {
        OutlinedTextField(name, { name = it.take(Limits.MAX_TITLE) }, label = { Text("اسم المجموعة") }, placeholder = { Text("نادي كتاب الحي") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(about, { about = it.take(Limits.MAX_ABOUT) }, label = { Text("وصف قصير (اختياري)") }, minLines = 2, modifier = Modifier.fillMaxWidth())
        SectionLabel("من يستطيع الانضمام؟")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Choice("برمز الدعوة فقط", !public, Modifier.weight(1f)) { public = false }
            Choice("أي قارئ", public, Modifier.weight(1f)) { public = true }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            if (public) "تظهر في «مجموعات عامة» ويستطيع أي قارئ الانضمام إليها." else "لا يراها إلا من يملك رمز الدعوة.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(28.dp))
        PrimaryButton("أنشئ المجموعة", busy = busy, enabled = name.isNotBlank(), onClick = {
            vm.createGroup(name, about, if (public) Visibility.PUBLIC else Visibility.PRIVATE) { id ->
                nav.back()
                nav.group(id)
            }
        })
    }
}

@Composable
fun JoinScreen(initialCode: String?) {
    val vm = LocalVm.current
    val nav = LocalNav.current
    val busy by vm.busy.collectAsStateWithLifecycle()
    val mine by vm.myGroups.collectAsStateWithLifecycle()
    var code by rememberSaveable { mutableStateOf(initialCode.orEmpty()) }
    var found by remember { mutableStateOf<GroupPreview?>(null) }
    LaunchedEffect(initialCode) { if (!initialCode.isNullOrBlank()) vm.findByCode(initialCode) { found = it } }

    Page("الانضمام إلى مجموعة", onBack = nav::back, subtitle = "اطلب الرمز من صاحب المجموعة، أو افتح رابط الدعوة الذي أرسله.") {
        OutlinedTextField(
            code, { code = it.uppercase().take(8); found = null },
            label = { Text("رمز الدعوة") },
            singleLine = true,
            textStyle = MaterialTheme.typography.headlineSmall.copy(letterSpacing = 6.sp, textAlign = TextAlign.Center),
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
        )
        Spacer(Modifier.height(16.dp))
        val p = found
        if (p == null) {
            PrimaryButton("ابحث", busy = busy, enabled = code.isNotBlank(), onClick = { vm.findByCode(code) { found = it } })
        } else {
            val already = mine.any { it.group.id == p.id }
            PreviewCard(p) { Tag(if (p.visibility == Visibility.PUBLIC) "عامة" else "خاصة", icon = if (p.visibility == Visibility.PUBLIC) Icons.Rounded.Public else Icons.Rounded.Key) }
            Spacer(Modifier.height(16.dp))
            if (already) {
                PrimaryButton("افتح المجموعة", onClick = { nav.back(); nav.group(p.id) })
            } else {
                PrimaryButton("انضم إلى «${p.name}»", busy = busy, onClick = { vm.join(p, code) { nav.back(); nav.group(p.id) } })
            }
        }
    }
}
