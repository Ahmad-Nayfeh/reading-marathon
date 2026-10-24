package com.readingmarathon.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.readingmarathon.app.ui.LocalVm
import com.readingmarathon.app.ui.components.PrimaryButton
import com.readingmarathon.app.ui.theme.Brand
import com.readingmarathon.core.Dates
import com.readingmarathon.core.Fmt
import com.readingmarathon.core.Limits
import com.readingmarathon.core.Shelf
import com.readingmarathon.core.ShelfStatus
import java.time.LocalDate

/** Edit any of the last two weeks: pick the day and the book, set the minutes, save. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DaySheet(initial: LocalDate, initialBook: String?, onDismiss: () -> Unit) {
    val vm = LocalVm.current
    val nav = LocalNav.current
    val today = vm.today()
    val shelf by vm.shelf.collectAsStateWithLifecycle()
    val reads by vm.reads.collectAsStateWithLifecycle()
    var date by remember { mutableStateOf(initial) }
    // Books to choose from: what is being read, plus anything already logged that day.
    val books = remember(shelf, reads, date) {
        val onDay = reads.filter { it.date == date }.map { it.bookId }.toSet()
        (Shelf.reading(shelf) + shelf.filter { it.bookId in onDay && it.status != ShelfStatus.READING }).distinctBy { it.bookId }
    }
    var book by remember { mutableStateOf(initialBook ?: books.firstOrNull()?.bookId) }
    var minutes by remember { mutableIntStateOf(book?.let { vm.minutesOn(initial, it) } ?: 0) }
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    fun pick(d: LocalDate, b: String?) { date = d; book = b; minutes = b?.let { vm.minutesOn(d, it) } ?: 0 }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state, containerColor = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 16.dp)) {
            Text("تسجيل قراءة", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(12.dp))
            Text("اليوم", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(6.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items((0..Limits.BACKDATE_DAYS.toInt()).map { today.minusDays(it.toLong()) }) { d ->
                    DayChip(d, today, d == date, vm.minutesOn(d)) { pick(d, book) }
                }
            }
            Spacer(Modifier.height(16.dp))
            Text("الكتاب", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(6.dp))
            if (books.isEmpty()) {
                Text("لا كتب في «أقرأ الآن». أضف كتابك أولاً.", style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = { onDismiss(); nav.addBook() }) { Text("أضف كتاباً") }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    books.forEach { b ->
                        BookChoice(b.title, vm.minutesOn(date, b.bookId), b.bookId == book) { pick(date, b.bookId) }
                    }
                }
            }
            if (book != null) {
                Spacer(Modifier.height(20.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                    FilledTonalIconButton(onClick = { minutes = (minutes + 5).coerceAtMost(Limits.MAX_MINUTES_PER_DAY) }, modifier = Modifier.size(52.dp)) {
                        Icon(Icons.Rounded.KeyboardArrowUp, "خمس دقائق أكثر")
                    }
                    Column(Modifier.width(150.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("$minutes", fontSize = 48.sp, fontWeight = FontWeight.Light)
                        Text(if (minutes >= 60) Fmt.duration(minutes) else "دقيقة", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    FilledTonalIconButton(onClick = { minutes = (minutes - 5).coerceAtLeast(0) }, modifier = Modifier.size(52.dp)) {
                        Icon(Icons.Rounded.KeyboardArrowDown, "خمس دقائق أقل")
                    }
                }
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(15, 30, 45, 60, 90).forEach { m -> Choice("$m", minutes == m, Modifier.weight(1f)) { minutes = m } }
                }
                Spacer(Modifier.height(20.dp))
                val b = book!!
                PrimaryButton("حفظ ${Dates.relative(date, today)}", onClick = { vm.setRead(date, b, minutes, then = onDismiss) })
                if (vm.minutesOn(date, b) > 0) {
                    TextButton(onClick = { vm.setRead(date, b, 0, then = onDismiss) }, modifier = Modifier.fillMaxWidth()) {
                        Text("مسح قراءة هذا الكتاب في هذا اليوم", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

@Composable
private fun BookChoice(title: String, minutes: Int, selected: Boolean, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
        border = BorderStroke(if (selected) 1.5.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
    ) {
        Text("«$title»", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (minutes > 0) Text(Fmt.minutes(minutes), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun DayChip(d: LocalDate, today: LocalDate, selected: Boolean, minutes: Int, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        border = BorderStroke(if (selected) 1.5.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                when (today.toEpochDay() - d.toEpochDay()) { 0L -> "اليوم"; 1L -> "أمس"; else -> Dates.weekday(d) },
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface,
            )
            Text("${d.dayOfMonth}", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(2.dp))
            Box(Modifier.size(5.dp).clip(CircleShape).background(if (minutes > 0) Brand.colors.gold else Color.Transparent))
        }
    }
}
