package com.readingmarathon.app.ui.screens

import android.app.Activity
import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.MilitaryTech
import androidx.compose.material.icons.rounded.PersonOutline
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
import com.readingmarathon.app.data.MarathonSummary
import com.readingmarathon.app.data.Role
import com.readingmarathon.app.ui.components.Avatar
import com.readingmarathon.app.ui.components.EmptyState
import com.readingmarathon.app.ui.components.IconBadge
import com.readingmarathon.app.ui.components.PaperCard
import com.readingmarathon.app.ui.components.Pill
import com.readingmarathon.app.ui.theme.Amiri
import com.readingmarathon.app.ui.theme.Brand

private val Navy = Color(0xFF14213D)
private val Gold = Color(0xFFF2B84B)
private val Cream = Color(0xFFFBF7EF)

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


/** First screen: sign in with Google, or continue as a guest. */
@Composable
fun SignInScreen(vm: AppViewModel) {
    val busy by vm.busy.collectAsStateWithLifecycle()
    val activity = LocalContext.current as? Activity
    Column(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF0B1222), Navy, Color(0xFF1C2D52))))
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(56.dp))
        Emblem(104, Gold)
        Spacer(Modifier.height(22.dp))
        Text("ماراثون القراءة", fontFamily = Amiri, fontSize = 44.sp, lineHeight = 64.sp, color = Cream)
        Spacer(Modifier.height(6.dp))
        Text(
            "اقرؤوا معاً كتاباً واحداً كل تحدٍّ، سجّلوا دقائقكم كل يوم، واحتفلوا بكل كتاب يُختم.",
            color = Color(0xFFC9D2E3), textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(Modifier.height(30.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Feature(Icons.Rounded.EmojiEvents, "نقاط وترتيب")
            Feature(Icons.Rounded.LocalFireDepartment, "أيام متتالية")
            Feature(Icons.Rounded.MilitaryTech, "أوسمة")
        }
        Spacer(Modifier.height(40.dp))
        if (!vm.configured) {
            Text(
                "هذه النسخة غير مهيأة للاتصال بالخادم. حمّل التطبيق من صفحته الرسمية.",
                color = Gold, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium,
            )
        } else {
            Button(
                onClick = { activity?.let(vm::signInWithGoogle) },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color(0xFF1F1F1F)),
            ) {
                if (busy) {
                    CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = Navy)
                } else {
                    GoogleMark()
                    Spacer(Modifier.width(12.dp))
                    Text("الدخول بحساب Google", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }
            Spacer(Modifier.height(10.dp))
            TextButton(onClick = vm::signInAsGuest, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Rounded.PersonOutline, null, tint = Gold, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("المتابعة كضيف", color = Gold)
            }
            Text(
                "الضيف لا يحتاج حساباً، ويمكنه ربط حساب Google لاحقاً من الإعدادات دون أن يخسر شيئاً.",
                color = Color(0xFF9AA6BD), textAlign = TextAlign.Center, style = MaterialTheme.typography.bodySmall,
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}

/** The four-colour Google "G", drawn so no image asset is needed. */
@Composable
private fun GoogleMark() {
    Canvas(Modifier.size(20.dp)) {
        val s = size.minDimension
        val stroke = s * 0.2f
        val inset = stroke / 2
        val arc = androidx.compose.ui.geometry.Size(s - stroke, s - stroke)
        val tl = Offset(inset, inset)
        fun seg(color: Color, start: Float, sweep: Float) =
            drawArc(color, start, sweep, false, tl, arc, style = androidx.compose.ui.graphics.drawscope.Stroke(stroke))
        seg(Color(0xFFEA4335), 200f, 115f)
        seg(Color(0xFFFBBC05), 135f, 65f)
        seg(Color(0xFF34A853), 45f, 90f)
        seg(Color(0xFF4285F4), -15f, 60f)
        drawRect(Color(0xFF4285F4), Offset(s / 2, s / 2 - stroke / 2), androidx.compose.ui.geometry.Size(s / 2 - inset / 2, stroke))
    }
}

@Composable
private fun Feature(icon: ImageVector, label: String) {
    Surface(shape = RoundedCornerShape(16.dp), color = Color.White.copy(alpha = 0.06f)) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, tint = Gold, modifier = Modifier.size(24.dp))
            Spacer(Modifier.height(6.dp))
            Text(label, color = Color(0xFFE6EAF2), style = MaterialTheme.typography.labelMedium)
        }
    }
}

private enum class Step { LIST, CREATE, JOIN }

/** Signed in, nothing open: the account's marathons, plus create and join. */
@Composable
fun MarathonsScreen(vm: AppViewModel) {
    val account by vm.account.collectAsStateWithLifecycle()
    val mine by vm.mine.collectAsStateWithLifecycle()
    val pending by vm.pendingCode.collectAsStateWithLifecycle()
    var step by rememberSaveable { mutableStateOf(Step.LIST) }
    LaunchedEffect(account?.uid) { vm.loadMine() }
    LaunchedEffect(pending) { if (pending != null) step = Step.JOIN }
    BackHandler(step != Step.LIST) { step = Step.LIST }

    AnimatedContent(step, transitionSpec = { (fadeIn() + slideInHorizontally { -it / 6 }) togetherWith fadeOut() }, label = "start") { s ->
        when (s) {
            Step.CREATE -> FormPage("ماراثون جديد", "ستكون المشرف، وتحصل على رمز دعوة تشاركه مع المجموعة.", onBack = { step = Step.LIST }) {
                CreateForm(vm, account?.name.orEmpty())
            }
            Step.JOIN -> FormPage("الانضمام إلى ماراثون", "رمز الدعوة 6 أحرف وأرقام، يجده المشرف في صفحة الإدارة.", onBack = { step = Step.LIST }) {
                JoinForm(vm, pending)
            }
            Step.LIST -> Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp),
            ) {
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("ماراثوناتي", style = MaterialTheme.typography.headlineMedium)
                        Text(
                            account?.let { if (it.isGuest) "ضيف" else it.email ?: it.label }.orEmpty(),
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = vm::signOut) { Icon(Icons.AutoMirrored.Rounded.Logout, "تسجيل الخروج") }
                }
                Spacer(Modifier.height(18.dp))
                if (account?.isAdmin == true) {
                    ChoiceCard(Icons.Rounded.AdminPanelSettings, "لوحة إدارة المنصة", "كل الماراثونات والحسابات والنشاط.") { vm.openAdminPanel() }
                    Spacer(Modifier.height(18.dp))
                }
                when (val list = mine) {
                    null -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    else -> if (list.isEmpty()) {
                        PaperCard {
                            EmptyState(Icons.Rounded.AutoStories, "لم تنضم إلى أي ماراثون بعد", "أنشئ ماراثوناً لمجموعتك، أو انضم برمز الدعوة الذي أرسله لك المشرف.")
                        }
                    } else {
                        list.forEach { m ->
                            MarathonRow(m) { vm.openMarathon(m) }
                            Spacer(Modifier.height(10.dp))
                        }
                    }
                }
                Spacer(Modifier.height(18.dp))
                ChoiceCard(Icons.Rounded.Groups, "أنشئ ماراثوناً جديداً", "ستكون المشرف، وتدعو المجموعة برمز.") { step = Step.CREATE }
                Spacer(Modifier.height(12.dp))
                ChoiceCard(Icons.Rounded.Link, "انضم برمز دعوة", "أدخل الرمز الذي أرسله لك المشرف.") { step = Step.JOIN }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun MarathonRow(m: MarathonSummary, onClick: () -> Unit) {
    PaperCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(Icons.Rounded.AutoStories, size = 44.dp)
            Spacer(Modifier.width(14.dp))
            Text(m.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Pill(if (m.role == Role.ORGANIZER) "مشرف" else "قارئ")
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
private fun CreateForm(vm: AppViewModel, suggestedName: String) {
    val busy by vm.busy.collectAsStateWithLifecycle()
    var name by rememberSaveable { mutableStateOf("") }
    var me by rememberSaveable { mutableStateOf(suggestedName) }
    OutlinedTextField(name, { name = it }, label = { Text("اسم الماراثون") }, placeholder = { Text("ماراثون الأصدقاء") }, singleLine = true, modifier = Modifier.fillMaxWidth())
    Spacer(Modifier.height(12.dp))
    OutlinedTextField(
        me, { me = it }, label = { Text("اسمك كما سيظهر للمجموعة") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
    )
    Spacer(Modifier.height(20.dp))
    PrimaryButton("أنشئ الماراثون", busy, enabled = name.isNotBlank() && me.isNotBlank()) { vm.createMarathon(name, me) }
}

@Composable
private fun JoinForm(vm: AppViewModel, pending: String?) {
    val busy by vm.busy.collectAsStateWithLifecycle()
    var code by rememberSaveable(pending) { mutableStateOf(pending.orEmpty()) }
    OutlinedTextField(
        code, { code = it.uppercase().take(8) },
        label = { Text("رمز الدعوة") },
        singleLine = true,
        textStyle = MaterialTheme.typography.headlineSmall.copy(letterSpacing = 6.sp, textAlign = TextAlign.Center),
        modifier = Modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
    )
    Spacer(Modifier.height(20.dp))
    PrimaryButton("انضم", busy, enabled = code.isNotBlank()) { vm.joinMarathon(code) }
}

/** After joining, the reader says which member they are (or adds themselves). */
@Composable
fun PickMemberScreen(vm: AppViewModel) {
    val data by vm.data.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val account by vm.account.collectAsStateWithLifecycle()
    val d = data ?: return
    var newName by rememberSaveable { mutableStateOf(account?.name.orEmpty()) }
    val free = d.activeMembers.filter { it.linkedUid == null }
    FormPage("من أنت في «${d.marathon.name}»؟", "اختر اسمك ليُنسب إليك ما تسجّله. إن لم تجده فأضف نفسك.", onBack = vm::switchMarathon) {
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

/** A marathon is opening. If it takes too long (no connection, no access), offer a way back. */
@Composable
fun LoadingScreen(vm: AppViewModel) {
    var slow by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) { kotlinx.coroutines.delay(8_000); slow = true }
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator(color = Brand.colors.gold)
        Spacer(Modifier.height(16.dp))
        Text("جارٍ فتح الماراثون…", style = MaterialTheme.typography.bodyLarge)
        if (slow) {
            Spacer(Modifier.height(10.dp))
            Text(
                "يستغرق وقتاً أطول من المعتاد. تحقق من الاتصال بالإنترنت.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
            )
            TextButton(onClick = vm::switchMarathon) { Text("العودة إلى ماراثوناتي") }
        }
    }
}
