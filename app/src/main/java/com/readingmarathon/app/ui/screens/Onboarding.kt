package com.readingmarathon.app.ui.screens

import android.app.Activity
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.readingmarathon.app.ui.LocalVm
import com.readingmarathon.app.ui.components.GoldRule
import com.readingmarathon.app.ui.components.PrimaryButton
import com.readingmarathon.app.ui.theme.Amiri
import com.readingmarathon.app.ui.theme.Brand
import com.readingmarathon.core.Limits

@Composable
fun Splash() {
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center) {
        Emblem(88)
    }
}

/** The app's mark: an open book under a star. */
@Composable
fun Emblem(sizeDp: Int) {
    val gold = Brand.colors.gold
    val ink = MaterialTheme.colorScheme.primary
    Canvas(Modifier.size(sizeDp.dp)) {
        val w = size.width
        val h = size.height
        val cx = w / 2
        val cy = h * 0.22f
        val r = w * 0.11f
        val star = Path().apply {
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
        val top = h * 0.48f
        val bottom = h * 0.86f
        fun page(sign: Float) = Path().apply {
            moveTo(cx, top + h * 0.06f)
            quadraticTo(cx + sign * w * 0.2f, top - h * 0.04f, cx + sign * w * 0.4f, top)
            lineTo(cx + sign * w * 0.4f, bottom - h * 0.04f)
            quadraticTo(cx + sign * w * 0.2f, bottom - h * 0.1f, cx, bottom)
            close()
        }
        drawPath(page(-1f), ink, style = Stroke(w * 0.035f))
        drawPath(page(1f), ink, style = Stroke(w * 0.035f))
        drawLine(gold, Offset(cx, top + h * 0.06f), Offset(cx, bottom), w * 0.03f)
    }
}

/** First screen: sign in with Google, or continue as a guest. */
@Composable
fun SignInScreen() {
    val vm = LocalVm.current
    val busy by vm.busy.collectAsStateWithLifecycle()
    val activity = LocalContext.current as? Activity
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(72.dp))
        Emblem(92)
        Spacer(Modifier.height(20.dp))
        Text("ماراثون القراءة", style = MaterialTheme.typography.displaySmall, textAlign = TextAlign.Center)
        GoldRule(Modifier.padding(vertical = 12.dp), width = 44.dp)
        Text(
            "اقرأ كل يوم، وسجّل دقائقك بلمسة واحدة، ونافس أصدقاءك على كتاب واحد.",
            style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(28.dp))
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Point("سجّل قراءتك بلمسة، أو بمؤقت، أو من الإشعار.")
            Point("مجموعات للأصدقاء والعائلة ونوادي القراءة.")
            Point("كتاب مشترك، وترتيب، واحتفال بكل كتاب يُختم.")
        }
        Spacer(Modifier.height(40.dp))
        if (!vm.configured) {
            Text(
                "هذه النسخة غير مهيأة للاتصال بالخادم. حمّل التطبيق من صفحته الرسمية.",
                color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium,
            )
        } else {
            Button(
                onClick = { activity?.let(vm::signInWithGoogle) },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surface, contentColor = MaterialTheme.colorScheme.onSurface),
            ) {
                if (busy) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    GoogleMark()
                    Spacer(Modifier.width(12.dp))
                    Text("الدخول بحساب Google", style = MaterialTheme.typography.labelLarge)
                }
            }
            Spacer(Modifier.height(6.dp))
            TextButton(onClick = vm::signInAsGuest, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                Text("المتابعة دون حساب", color = MaterialTheme.colorScheme.onSurface)
            }
            Text(
                "يمكنك ربط حساب Google لاحقاً دون أن تخسر شيئاً.",
                color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodySmall,
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun Point(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(Brand.colors.gold))
        Spacer(Modifier.width(12.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

/** The four-colour Google "G", drawn so no image asset is needed. */
@Composable
private fun GoogleMark() {
    Canvas(Modifier.size(20.dp)) {
        val s = size.minDimension
        val stroke = s * 0.2f
        val inset = stroke / 2
        val arc = Size(s - stroke, s - stroke)
        val tl = Offset(inset, inset)
        fun seg(color: Color, start: Float, sweep: Float) = drawArc(color, start, sweep, false, tl, arc, style = Stroke(stroke))
        seg(Color(0xFFEA4335), 200f, 115f)
        seg(Color(0xFFFBBC05), 135f, 65f)
        seg(Color(0xFF34A853), 45f, 90f)
        seg(Color(0xFF4285F4), -15f, 60f)
        drawRect(Color(0xFF4285F4), Offset(s / 2, s / 2 - stroke / 2), Size(s / 2 - inset / 2, stroke))
    }
}

/** Once, after the first sign-in: the name groups will see. */
@Composable
fun NameScreen() {
    val vm = LocalVm.current
    val account by vm.account.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    var name by rememberSaveable { mutableStateOf(account?.name.orEmpty()) }
    var goal by rememberSaveable { mutableStateOf(30) }
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 24.dp),
    ) {
        Spacer(Modifier.height(64.dp))
        Text("أهلاً بك", style = MaterialTheme.typography.headlineLarge)
        GoldRule(Modifier.padding(top = 8.dp, bottom = 16.dp))
        Text("بأي اسم يعرفك أصدقاؤك في المجموعات؟", style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            name, { name = it.take(Limits.MAX_NAME) },
            label = { Text("اسمك") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
        )
        Spacer(Modifier.height(28.dp))
        Text("كم دقيقة تريد أن تقرأ كل يوم؟", style = MaterialTheme.typography.bodyLarge)
        Text("هدف شخصي لا يراه غيرك. يمكنك تغييره متى شئت.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))
        GoalChips(goal) { goal = it }
        Spacer(Modifier.height(36.dp))
        PrimaryButton("ابدأ", onClick = { vm.saveProfile(name, goal) }, enabled = name.isNotBlank(), busy = busy)
        Spacer(Modifier.height(24.dp))
    }
}

val GOALS = listOf(10, 15, 20, 30, 45, 60)

@Composable
fun GoalChips(selected: Int, onPick: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        GOALS.forEach { g ->
            Choice("$g", selected == g, Modifier.weight(1f)) { onPick(g) }
        }
    }
}

/** A round-cornered choice cell: ink when selected, hairline otherwise. */
@Composable
fun Choice(text: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Button(
        onClick = onClick,
        modifier = modifier.height(46.dp),
        shape = shape,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp),
        border = if (selected) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
            contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
        ),
    ) { Text(text, style = MaterialTheme.typography.labelLarge, maxLines = 1) }
}

/** Signed in, waiting for the profile. If it takes too long, say why and offer a way out. */
@Composable
fun LoadingScreen() {
    val vm = LocalVm.current
    var slow by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) { kotlinx.coroutines.delay(10_000); slow = true }
    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Emblem(64)
        Spacer(Modifier.height(20.dp))
        CircularProgressIndicator(Modifier.size(26.dp), strokeWidth = 2.dp, color = Brand.colors.gold)
        if (slow) {
            Spacer(Modifier.height(16.dp))
            Text(
                "يستغرق وقتاً أطول من المعتاد. تحقق من الاتصال بالإنترنت.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
            )
            TextButton(onClick = vm::signOut) { Text("تسجيل الخروج") }
        }
    }
}

/** The platform admin suspended this account. */
@Composable
fun BannedScreen() {
    val vm = LocalVm.current
    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Emblem(64)
        Spacer(Modifier.height(20.dp))
        Text("هذا الحساب موقوف", style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(
            "إن ظننت أن هذا خطأ فراسلنا على readingmarathon.admin@gmail.com.",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        TextButton(onClick = vm::signOut) { Text("تسجيل الخروج") }
    }
}
