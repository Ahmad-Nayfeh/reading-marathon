package com.readingmarathon.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.readingmarathon.app.AppViewModel
import com.readingmarathon.app.ui.components.Avatar
import com.readingmarathon.app.ui.components.PaperCard
import com.readingmarathon.app.ui.theme.Amiri
import com.readingmarathon.app.ui.theme.Brand

private enum class Step { WELCOME, LOCAL, SHARED_MENU, CREATE_SHARED, JOIN }

@Composable
fun Splash() {
    Box(Modifier.fillMaxSize().background(Color(0xFF14213D)), contentAlignment = Alignment.Center) {
        Emblem(96)
    }
}

/** The app's mark: an open book under a star, drawn in gold. */
@Composable
fun Emblem(sizeDp: Int, gold: Color = Color(0xFFF2B84B)) {
    Canvas(Modifier.size(sizeDp.dp)) {
        val w = size.width
        val h = size.height
        val stroke = w * 0.045f
        // Star
        val cx = w / 2
        val cy = h * 0.22f
        val r = w * 0.12f
        val star = androidx.compose.ui.graphics.Path().apply {
            for (i in 0 until 10) {
                val rad = if (i % 2 == 0) r else r * 0.45f
                val a = Math.toRadians((-90 + i * 36).toDouble())
                val x = cx + (rad * Math.cos(a)).toFloat()
                val y = cy + (rad * Math.sin(a)).toFloat()
                if (i == 0) moveTo(x, y) else lineTo(x, y)
            }
            close()
        }
        drawPath(star, gold)
        // Book: two pages meeting at the spine
        val top = h * 0.48f
        val bottom = h * 0.86f
        val left = androidx.compose.ui.graphics.Path().apply {
            moveTo(cx, top + h * 0.06f)
            quadraticTo(cx - w * 0.2f, top - h * 0.04f, w * 0.1f, top)
            lineTo(w * 0.1f, bottom - h * 0.04f)
            quadraticTo(cx - w * 0.2f, bottom - h * 0.1f, cx, bottom)
            close()
        }
        val right = androidx.compose.ui.graphics.Path().apply {
            moveTo(cx, top + h * 0.06f)
            quadraticTo(cx + w * 0.2f, top - h * 0.04f, w * 0.9f, top)
            lineTo(w * 0.9f, bottom - h * 0.04f)
            quadraticTo(cx + w * 0.2f, bottom - h * 0.1f, cx, bottom)
            close()
        }
        drawPath(left, gold.copy(alpha = 0.92f))
        drawPath(right, gold)
        drawLine(Color(0xFF14213D), Offset(cx, top + h * 0.06f), Offset(cx, bottom), stroke)
    }
}

@Composable
fun Onboarding(vm: AppViewModel) {
    val pending by vm.pendingCode.collectAsStateWithLifecycle()
    var step by rememberSaveable { mutableStateOf(Step.WELCOME) }
    val context = LocalContext.current
    val saved = remember { vm.savedLocal() }
    val restore = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val text = runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() } }.getOrNull()
            if (text == null) vm.say("تعذّرت قراءة الملف.") else vm.restoreBackup(text)
        }
    }
    LaunchedEffect(pending) { if (pending != null && vm.sharedAvailable) step = Step.JOIN }
    BackHandler(step != Step.WELCOME) {
        step = if (step == Step.CREATE_SHARED || step == Step.JOIN) Step.SHARED_MENU else Step.WELCOME
    }
    AnimatedContent(step, transitionSpec = { (fadeIn() + slideInHorizontally { -it / 6 }) togetherWith fadeOut() }, label = "onb") { s ->
        when (s) {
            Step.WELCOME -> Welcome(
                saved = saved,
                onOpenSaved = vm::openLocal,
                onRestore = { restore.launch(arrayOf("application/json", "application/octet-stream", "text/plain")) },
                sharedAvailable = vm.sharedAvailable,
                onLocal = { step = Step.LOCAL },
                onShared = { step = Step.SHARED_MENU },
                onDemo = vm::startDemo,
            )
            Step.LOCAL -> FormPage("ماراثون على هذا الجهاز", "تديره أنت من هاتفك وتسجّل قراءات المجموعة بنفسك. لا يحتاج حساباً ولا إنترنت.", onBack = { step = Step.WELCOME }) {
                LocalForm(vm)
            }
            Step.SHARED_MENU -> FormPage("ماراثون مشترك", "كل قارئ يسجّل قراءته من هاتفه، والنتائج تظهر للجميع لحظة بلحظة.", onBack = { step = Step.WELCOME }) {
                ChoiceCard(Icons.Rounded.Groups, "أنشئ ماراثوناً جديداً", "ستكون المشرف، وتحصل على رمز دعوة تشاركه مع المجموعة.") { step = Step.CREATE_SHARED }
                Spacer(Modifier.height(12.dp))
                ChoiceCard(Icons.Rounded.Link, "انضم برمز دعوة", "أدخل الرمز الذي أرسله لك المشرف.") { step = Step.JOIN }
            }
            Step.CREATE_SHARED -> FormPage("ماراثون مشترك جديد", "اختر اسماً تعرفه المجموعة. يمكنك تغييره لاحقاً.", onBack = { step = Step.SHARED_MENU }) {
                CreateSharedForm(vm)
            }
            Step.JOIN -> FormPage("الانضمام إلى ماراثون", "رمز الدعوة 6 أحرف وأرقام، يجده المشرف في صفحة الإدارة.", onBack = { step = Step.SHARED_MENU }) {
                JoinForm(vm, pending)
            }
        }
    }
}

@Composable
private fun Welcome(
    saved: List<Pair<String, String>>,
    onOpenSaved: (String) -> Unit,
    onRestore: () -> Unit,
    sharedAvailable: Boolean, onLocal: () -> Unit, onShared: () -> Unit, onDemo: () -> Unit) {
    val navy = Color(0xFF14213D)
    val gold = Color(0xFFF2B84B)
    Column(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF0B1222), navy, Color(0xFF1C2D52))))
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(56.dp))
        Emblem(104, gold)
        Spacer(Modifier.height(22.dp))
        Text("ماراثون القراءة", fontFamily = Amiri, fontSize = 44.sp, lineHeight = 64.sp, color = Color(0xFFFBF7EF))
        Spacer(Modifier.height(6.dp))
        Text(
            "اقرؤوا معاً كتاباً واحداً كل تحدٍّ، سجّلوا دقائقكم كل يوم، واحتفلوا بكل كتاب يُختم.",
            color = Color(0xFFC9D2E3), textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(Modifier.height(30.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Feature("🏆", "نقاط وترتيب")
            Feature("🔥", "أيام متتالية")
            Feature("🎖️", "أوسمة")
        }
        Spacer(Modifier.height(36.dp))
        if (sharedAvailable) {
            Button(
                onClick = onShared,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(containerColor = gold, contentColor = navy),
            ) { Text("ماراثون مشترك مع مجموعتي", fontWeight = FontWeight.Bold, fontSize = 16.sp) }
            Spacer(Modifier.height(10.dp))
        }
        Button(
            onClick = onLocal,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(18.dp),
            colors = if (sharedAvailable) {
                ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.08f), contentColor = Color.White)
            } else {
                ButtonDefaults.buttonColors(containerColor = gold, contentColor = navy)
            },
        ) { Text("ماراثون على هذا الجهاز", fontWeight = FontWeight.Bold, fontSize = 16.sp) }
        Spacer(Modifier.height(4.dp))
        if (saved.isNotEmpty()) {
            Spacer(Modifier.height(18.dp))
            Text("ماراثونات محفوظة على هذا الجهاز", color = Color(0xFFC9D2E3), style = MaterialTheme.typography.labelLarge, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(6.dp))
            saved.forEach { (id, name) ->
                Surface(
                    onClick = { onOpenSaved(id) },
                    shape = RoundedCornerShape(14.dp),
                    color = Color.White.copy(alpha = 0.06f),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                ) {
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.PhoneAndroid, null, tint = gold, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(name, color = Color.White, style = MaterialTheme.typography.titleSmall)
                    }
                }
            }
        }
        TextButton(onClick = onRestore, modifier = Modifier.fillMaxWidth()) {
            Text("استعادة نسخة احتياطية", color = Color(0xFFC9D2E3))
        }
        TextButton(onClick = onDemo, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Rounded.PlayCircle, null, tint = gold, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("جرّب التطبيق ببيانات تجريبية", color = gold)
        }
        Spacer(Modifier.height(18.dp))
    }
}

@Composable
private fun Feature(emoji: String, label: String) {
    Surface(shape = RoundedCornerShape(16.dp), color = Color.White.copy(alpha = 0.06f)) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(emoji, fontSize = 22.sp)
            Spacer(Modifier.height(4.dp))
            Text(label, color = Color(0xFFE6EAF2), style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun FormPage(title: String, subtitle: String, onBack: () -> Unit, content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 20.dp),
    ) {
        Spacer(Modifier.height(8.dp))
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "رجوع") }
        Spacer(Modifier.height(12.dp))
        Text(title, style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(6.dp))
        Text(subtitle, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(24.dp))
        content()
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun ChoiceCard(icon: ImageVector, title: String, body: String, onClick: () -> Unit) {
    PaperCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).background(Brand.colors.goldSoft), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = Brand.colors.gold)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun PrimaryButton(text: String, busy: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled && !busy,
        modifier = Modifier.fillMaxWidth().height(54.dp),
        shape = RoundedCornerShape(16.dp),
    ) {
        if (busy) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
        else Text(text, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun LocalForm(vm: AppViewModel) {
    val busy by vm.busy.collectAsStateWithLifecycle()
    var name by rememberSaveable { mutableStateOf("ماراثون القراءة") }
    var reader by rememberSaveable { mutableStateOf("") }
    OutlinedTextField(name, { name = it }, label = { Text("اسم الماراثون") }, singleLine = true, modifier = Modifier.fillMaxWidth())
    Spacer(Modifier.height(12.dp))
    OutlinedTextField(
        reader, { reader = it },
        label = { Text("اسمك (اختياري)") },
        supportingText = { Text("اكتبه إن كنت تقرأ مع المجموعة لتظهر في الترتيب.") },
        singleLine = true, modifier = Modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
    )
    Spacer(Modifier.height(20.dp))
    PrimaryButton("ابدأ الماراثون", busy, enabled = name.isNotBlank()) { vm.startLocal(name, reader.ifBlank { null }) }
}

@Composable
private fun CreateSharedForm(vm: AppViewModel) {
    val busy by vm.busy.collectAsStateWithLifecycle()
    var name by rememberSaveable { mutableStateOf("") }
    var me by rememberSaveable { mutableStateOf("") }
    OutlinedTextField(name, { name = it }, label = { Text("اسم الماراثون") }, placeholder = { Text("ماراثون الأصدقاء") }, singleLine = true, modifier = Modifier.fillMaxWidth())
    Spacer(Modifier.height(12.dp))
    OutlinedTextField(me, { me = it }, label = { Text("اسمك كما سيظهر للمجموعة") }, singleLine = true, modifier = Modifier.fillMaxWidth())
    Spacer(Modifier.height(20.dp))
    PrimaryButton("أنشئ الماراثون", busy, enabled = name.isNotBlank() && me.isNotBlank()) { vm.createShared(name, me) }
}

@Composable
private fun JoinForm(vm: AppViewModel, pending: String?) {
    val busy by vm.busy.collectAsStateWithLifecycle()
    var code by rememberSaveable { mutableStateOf(pending.orEmpty()) }
    OutlinedTextField(
        code, { code = it.uppercase().take(8) },
        label = { Text("رمز الدعوة") },
        singleLine = true,
        textStyle = MaterialTheme.typography.headlineSmall.copy(letterSpacing = 6.sp, textAlign = TextAlign.Center),
        modifier = Modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
    )
    Spacer(Modifier.height(20.dp))
    PrimaryButton("انضم", busy, enabled = code.isNotBlank()) { vm.joinShared(code) }
}

/** After joining, the reader says which member they are (or adds themselves). */
@Composable
fun PickMemberScreen(vm: AppViewModel) {
    val data by vm.data.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val d = data ?: return
    var newName by rememberSaveable { mutableStateOf("") }
    val free = d.activeMembers.filter { it.linkedUid == null }
    FormPage("من أنت في «${d.marathon.name}»؟", "اختر اسمك ليُنسب إليك ما تسجّله. إن لم تجده فأضف نفسك.", onBack = vm::leave) {
        free.forEach { m ->
            PaperCard(onClick = { vm.pickMember(m.id) }, modifier = Modifier.padding(bottom = 10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Avatar(m.name, m.id)
                    Spacer(Modifier.width(12.dp))
                    Text(m.name, style = MaterialTheme.typography.titleMedium)
                }
            }
        }
        if (free.isNotEmpty()) Spacer(Modifier.height(14.dp))
        Text("قارئ جديد", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(newName, { newName = it }, label = { Text("اسمك") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(14.dp))
        PrimaryButton("أضفني إلى الماراثون", busy, enabled = newName.isNotBlank()) { vm.joinAsNewMember(newName) }
    }
}
