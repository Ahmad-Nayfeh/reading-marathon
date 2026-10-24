package com.readingmarathon.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.readingmarathon.app.ui.theme.Amiri
import com.readingmarathon.app.ui.theme.Brand
import kotlin.math.abs

/** A full screen with a title (Amiri), an optional back arrow and actions, scrolling content. */
@Composable
fun Page(
    title: String,
    onBack: (() -> Unit)? = null,
    subtitle: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 20.dp),
    ) {
        Row(Modifier.fillMaxWidth().height(56.dp), verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) {
                IconButton(onClick = onBack, modifier = Modifier.padding(end = 4.dp)) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "رجوع")
                }
            }
            Spacer(Modifier.weight(1f))
            actions()
        }
        Text(title, style = MaterialTheme.typography.headlineMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (subtitle != null) {
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        GoldRule(Modifier.padding(top = 10.dp, bottom = 18.dp))
        content()
        Spacer(Modifier.height(32.dp))
    }
}

/** The short gold hairline under titles. */
@Composable
fun GoldRule(modifier: Modifier = Modifier, width: Dp = 36.dp) {
    Box(modifier.size(width = width, height = 1.5.dp).background(Brand.colors.gold))
}

/** A card: paper surface, hairline border, no shadow. */
@Composable
fun PaperCard(
    modifier: Modifier = Modifier,
    padding: PaddingValues = PaddingValues(18.dp),
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth().then(if (onClick != null) Modifier.clip(RoundedCornerShape(16.dp)).clickable(role = Role.Button, onClick = onClick) else Modifier),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(padding), content = content)
    }
}

/** A small section label with a gold dot, Shadhra style. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier, trailing: @Composable (() -> Unit)? = null) {
    Row(modifier.fillMaxWidth().padding(top = 22.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(5.dp).clip(CircleShape).background(Brand.colors.gold))
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        trailing?.invoke()
    }
}

/** The one ink button per screen. */
@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, busy: Boolean = false, icon: ImageVector? = null) {
    Button(
        onClick = onClick,
        enabled = enabled && !busy,
        modifier = modifier.fillMaxWidth().height(52.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary),
    ) {
        if (busy) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
        } else {
            if (icon != null) { Icon(icon, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)) }
            Text(text, style = MaterialTheme.typography.labelLarge)
        }
    }
}

/** The hairline secondary button. */
@Composable
fun GhostButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, icon: ImageVector? = null, color: Color = MaterialTheme.colorScheme.onSurface) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(48.dp),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        contentPadding = PaddingValues(horizontal = 16.dp),
    ) {
        if (icon != null) { Icon(icon, null, Modifier.size(18.dp), tint = color); Spacer(Modifier.width(8.dp)) }
        Text(text, style = MaterialTheme.typography.labelLarge, color = color, maxLines = 1)
    }
}

/** A number with what it means under it. */
@Composable
fun Stat(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 1)
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
    }
}

/** A row of stats split by hairlines. */
@Composable
fun StatRow(vararg stats: Pair<String, String>) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        stats.forEachIndexed { i, (v, l) ->
            if (i > 0) Box(Modifier.padding(horizontal = 12.dp).size(width = 1.dp, height = 34.dp).background(MaterialTheme.colorScheme.outlineVariant))
            Stat(v, l, Modifier.weight(1f))
        }
    }
}

@Composable
fun ListDivider() = HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

private val avatarPalette = listOf(
    Color(0xFF34507A), Color(0xFF8A6A2A), Color(0xFF3F6B4F), Color(0xFF7A3F55),
    Color(0xFF574B8A), Color(0xFF2F6E6A), Color(0xFF8A4B3A), Color(0xFF5B5146),
)

/** Initial-letter avatar with a colour stable for the member's id. */
@Composable
fun Avatar(name: String, id: String, size: Dp = 38.dp, ring: Color? = null) {
    val color = avatarPalette[abs(id.hashCode()) % avatarPalette.size]
    Box(
        Modifier
            .size(size)
            .then(if (ring != null) Modifier.border(1.5.dp, ring, CircleShape).padding(3.dp) else Modifier)
            .clip(CircleShape)
            .background(color),
        contentAlignment = Alignment.Center,
    ) {
        Text(name.trim().take(1).ifEmpty { "؟" }, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = (size.value * 0.4f).sp)
    }
}

/** A small tag: hairline outline, or filled soft gold when [strong]. */
@Composable
fun Tag(text: String, modifier: Modifier = Modifier, strong: Boolean = false, icon: ImageVector? = null) {
    Surface(
        modifier,
        shape = RoundedCornerShape(50),
        color = if (strong) Brand.colors.goldSoft else Color.Transparent,
        border = if (strong) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) { Icon(icon, null, Modifier.size(13.dp), tint = Brand.colors.gold); Spacer(Modifier.width(4.dp)) }
            Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
        }
    }
}

/** An icon in a soft round badge. */
@Composable
fun IconBadge(icon: ImageVector, modifier: Modifier = Modifier, size: Dp = 40.dp, tint: Color = Brand.colors.gold, background: Color = Brand.colors.goldSoft) {
    Box(modifier.size(size).background(background, CircleShape), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(size * 0.52f))
    }
}

@Composable
fun EmptyState(icon: ImageVector, title: String, body: String, modifier: Modifier = Modifier, action: @Composable (() -> Unit)? = null) {
    Column(modifier.fillMaxWidth().padding(vertical = 22.dp, horizontal = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        IconBadge(icon, size = 52.dp)
        Spacer(Modifier.height(12.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(4.dp))
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        if (action != null) {
            Spacer(Modifier.height(16.dp))
            action()
        }
    }
}

/** A book title in Amiri, the way it would appear on a cover. */
@Composable
fun BookTitle(title: String, author: String?, modifier: Modifier = Modifier, large: Boolean = false) {
    Column(modifier) {
        Text(
            "«$title»",
            fontFamily = Amiri,
            fontSize = if (large) 25.sp else 20.sp,
            lineHeight = if (large) 38.sp else 30.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (!author.isNullOrBlank()) Text(author, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** A drawn book cover: no images needed, unique per title. */
@Composable
fun BookCover(title: String, seed: String, modifier: Modifier = Modifier, width: Dp = 60.dp) {
    val palettes = listOf(
        Color(0xFF14213D) to Color(0xFFE2B95A), Color(0xFF5E2A2A) to Color(0xFFF0DDB0),
        Color(0xFF23412F) to Color(0xFFE9DFC8), Color(0xFF3A2F57) to Color(0xFFE2B95A),
        Color(0xFF4F3A2E) to Color(0xFFF3E6C8), Color(0xFF15485A) to Color(0xFFF0DDB0),
    )
    val (bg, fg) = palettes[abs(seed.hashCode()) % palettes.size]
    Box(
        modifier
            .size(width = width, height = width * 1.45f)
            .clip(RoundedCornerShape(topStart = 3.dp, bottomStart = 3.dp, topEnd = 8.dp, bottomEnd = 8.dp))
            .background(bg),
    ) {
        Box(Modifier.align(Alignment.CenterStart).padding(start = 5.dp).size(width = 1.5.dp, height = width * 1.45f).background(fg.copy(alpha = 0.35f)))
        Column(Modifier.align(Alignment.Center).padding(7.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Box(Modifier.size(width = width * 0.4f, height = 1.dp).background(fg.copy(alpha = 0.7f)))
            Spacer(Modifier.height(4.dp))
            Text(
                title, fontFamily = Amiri, color = fg,
                fontSize = (width.value / 6.4f).sp, lineHeight = (width.value / 4.7f).sp,
                textAlign = TextAlign.Center, maxLines = 3, overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Box(Modifier.size(width = width * 0.4f, height = 1.dp).background(fg.copy(alpha = 0.7f)))
        }
    }
}

/** Segmented tabs drawn as text with a gold underline: quieter than Material tabs. */
@Composable
fun Segments(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth()) {
        options.forEachIndexed { i, label ->
            Column(
                Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).clickable(role = Role.Tab) { onSelect(i) }.padding(vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (i == selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
                Spacer(Modifier.height(6.dp))
                Box(Modifier.size(width = 22.dp, height = 2.dp).clip(RoundedCornerShape(1.dp)).background(if (i == selected) Brand.colors.gold else Color.Transparent))
            }
        }
    }
}

/** A tappable settings row: title, optional detail, optional trailing content. */
@Composable
fun RowItem(title: String, detail: String? = null, icon: ImageVector? = null, onClick: (() -> Unit)? = null, trailing: @Composable (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) { Icon(icon, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant); Spacer(Modifier.width(14.dp)) }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (detail != null) Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        trailing?.invoke()
    }
}
