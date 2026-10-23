package com.readingmarathon.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Celebration
import androidx.compose.material.icons.rounded.EventAvailable
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.FormatQuote
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.LibraryBooks
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.MilitaryTech
import androidx.compose.material.icons.rounded.PersonAddAlt1
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.WorkspacePremium
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.readingmarathon.app.ui.theme.Brand
import com.readingmarathon.core.Mark

/** The vector icon for each meaning the core produces. One icon set, no emoji. */
val Mark.icon: ImageVector
    get() = when (this) {
        Mark.POINTS -> Icons.Rounded.WorkspacePremium
        Mark.HOURS -> Icons.Rounded.Schedule
        Mark.BOOKS -> Icons.Rounded.LibraryBooks
        Mark.QUOTES -> Icons.Rounded.FormatQuote
        Mark.DAYS -> Icons.Rounded.EventAvailable
        Mark.FAST -> Icons.Rounded.Bolt
        Mark.WEEK -> Icons.Rounded.Star
        Mark.MONTH -> Icons.Rounded.CalendarMonth
        Mark.STREAK -> Icons.Rounded.LocalFireDepartment
        Mark.EARLY_FINISH -> Icons.Rounded.Speed
        Mark.DISCUSSION -> Icons.Rounded.Forum
        Mark.UPCOMING -> Icons.Rounded.HourglassTop
        Mark.RACE -> Icons.Rounded.Timer
        Mark.FIRST -> Icons.Rounded.Flag
        Mark.GROUP -> Icons.Rounded.Celebration
        Mark.JOINED -> Icons.Rounded.PersonAddAlt1
        Mark.PROGRESS -> Icons.Rounded.AutoStories
        Mark.LEADER -> Icons.Rounded.MilitaryTech
        Mark.INFO -> Icons.Rounded.Info
    }

/** An icon in a soft round badge, the app's replacement for decorative emoji. */
@Composable
fun IconBadge(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    size: Dp = 36.dp,
    tint: Color = Brand.colors.gold,
    background: Color = Brand.colors.goldSoft,
) {
    Box(modifier.size(size).background(background, CircleShape), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(size * 0.55f))
    }
}
