package com.readingmarathon.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.readingmarathon.app.ui.theme.Amiri
import com.readingmarathon.app.ui.theme.Brand
import kotlin.math.abs

/** The app's standard card: soft paper surface, hairline border, generous padding. */
@Composable
fun PaperCard(
    modifier: Modifier = Modifier,
    padding: PaddingValues = PaddingValues(18.dp),
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    val border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    val shape = RoundedCornerShape(22.dp)
    if (onClick != null) {
        Card(onClick = onClick, modifier = modifier.fillMaxWidth(), shape = shape, colors = colors, border = border) {
            Column(Modifier.padding(padding), content = content)
        }
    } else {
        Card(modifier = modifier.fillMaxWidth(), shape = shape, colors = colors, border = border) {
            Column(Modifier.padding(padding), content = content)
        }
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, trailing: @Composable (() -> Unit)? = null) {
    Row(modifier.fillMaxWidth().padding(top = 8.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(width = 4.dp, height = 18.dp).clip(RoundedCornerShape(2.dp)).background(Brand.colors.gold))
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        trailing?.invoke()
    }
}

/** A labelled number. The label always says what the number means. */
@Composable
fun StatTile(value: String, label: String, modifier: Modifier = Modifier, icon: ImageVector? = null, accent: Color = Brand.colors.gold) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
                Spacer(Modifier.height(6.dp))
            }
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
        }
    }
}

@Composable
fun StatRow(content: @Composable (Modifier) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) { content(Modifier.weight(1f)) }
}

private val avatarPalette = listOf(
    Color(0xFF3D6FB6), Color(0xFFB7791F), Color(0xFF2E7D4F), Color(0xFF9C4A6B),
    Color(0xFF6A5ACD), Color(0xFF00897B), Color(0xFFC2185B), Color(0xFF5D4037),
)

/** Initial-letter avatar with a colour stable for the member's id. */
@Composable
fun Avatar(name: String, id: String, size: Dp = 40.dp, ring: Color? = null) {
    val color = avatarPalette[abs(id.hashCode()) % avatarPalette.size]
    Box(
        Modifier
            .size(size)
            .then(if (ring != null) Modifier.border(2.dp, ring, CircleShape) else Modifier)
            .padding(if (ring != null) 3.dp else 0.dp)
            .clip(CircleShape)
            .background(color),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            name.trim().take(1).ifEmpty { "؟" },
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = (size.value * 0.42f).sp,
        )
    }
}

@Composable
fun Pill(text: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.secondaryContainer, textColor: Color = MaterialTheme.colorScheme.onSecondaryContainer) {
    Surface(modifier, shape = RoundedCornerShape(50), color = color) {
        Text(text, Modifier.padding(horizontal = 10.dp, vertical = 4.dp), style = MaterialTheme.typography.labelMedium, color = textColor, maxLines = 1)
    }
}

@Composable
fun EmptyState(emoji: String, title: String, body: String, modifier: Modifier = Modifier, action: @Composable (() -> Unit)? = null) {
    Column(modifier.fillMaxWidth().padding(vertical = 28.dp, horizontal = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(emoji, fontSize = 40.sp)
        Spacer(Modifier.height(10.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        if (action != null) {
            Spacer(Modifier.height(14.dp))
            action()
        }
    }
}

/** A book title set in Amiri, the way it would appear on a cover. */
@Composable
fun BookTitle(title: String, author: String?, modifier: Modifier = Modifier, large: Boolean = false) {
    Column(modifier) {
        Text(
            title,
            fontFamily = Amiri,
            fontSize = if (large) 28.sp else 21.sp,
            lineHeight = if (large) 40.sp else 30.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (!author.isNullOrBlank()) {
            Text(author, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** A drawn book cover: no images needed, unique per title. */
@Composable
fun BookCover(title: String, seed: String, modifier: Modifier = Modifier, width: Dp = 64.dp) {
    val palettes = listOf(
        Color(0xFF14213D) to Color(0xFFF2B84B), Color(0xFF7A2E2E) to Color(0xFFF3D99E),
        Color(0xFF1F4D3A) to Color(0xFFE9DFC8), Color(0xFF3B2F63) to Color(0xFFF2B84B),
        Color(0xFF5D4037) to Color(0xFFF7E7C4), Color(0xFF0E4C61) to Color(0xFFF3D99E),
    )
    val (bg, fg) = palettes[abs(seed.hashCode()) % palettes.size]
    Box(
        modifier
            .size(width = width, height = width * 1.45f)
            .clip(RoundedCornerShape(topStart = 4.dp, bottomStart = 4.dp, topEnd = 10.dp, bottomEnd = 10.dp))
            .background(bg),
    ) {
        Box(Modifier.align(Alignment.CenterStart).padding(start = 5.dp).size(width = 2.dp, height = width * 1.45f).background(fg.copy(alpha = 0.35f)))
        Column(Modifier.align(Alignment.Center).padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(width = width * 0.45f, height = 1.dp).background(fg.copy(alpha = 0.7f)))
            Spacer(Modifier.height(4.dp))
            Text(
                title,
                fontFamily = Amiri,
                color = fg,
                fontSize = (width.value / 6.2f).sp,
                lineHeight = (width.value / 4.6f).sp,
                textAlign = TextAlign.Center,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Box(Modifier.size(width = width * 0.45f, height = 1.dp).background(fg.copy(alpha = 0.7f)))
        }
    }
}

/** "3 تحديات", "11 تحدياً"… Arabic count agreement for challenges. */
fun challengesCount(n: Int): String = when {
    n == 1 -> "تحدٍّ واحد"
    n == 2 -> "تحدّيان"
    n in 3..10 -> "$n تحديات"
    else -> "$n تحدياً"
}
