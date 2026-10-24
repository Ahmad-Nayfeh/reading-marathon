package com.readingmarathon.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.readingmarathon.app.ui.LocalVm
import com.readingmarathon.app.ui.components.BookCover
import com.readingmarathon.app.ui.components.EmptyState
import com.readingmarathon.app.ui.components.GhostButton
import com.readingmarathon.app.ui.components.ListDivider
import com.readingmarathon.app.ui.components.Page
import com.readingmarathon.app.ui.components.PaperCard
import com.readingmarathon.app.ui.components.PrimaryButton
import com.readingmarathon.app.ui.components.StatRow
import com.readingmarathon.core.CatalogBook
import com.readingmarathon.core.Dates
import com.readingmarathon.core.Fmt
import com.readingmarathon.core.ShelfBook
import com.readingmarathon.core.ShelfStatus

private val tabs = listOf(ShelfStatus.READING to "أقرأ الآن", ShelfStatus.FINISHED to "أتممتها", ShelfStatus.WANT to "أريد قراءتها")

/** The reader's own books, with or without any group. */
@Composable
fun ShelfScreen() {
    val vm = LocalVm.current
    val nav = LocalNav.current
    val shelf by vm.shelf.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(ShelfStatus.READING) }
    var open by remember { mutableStateOf<ShelfBook?>(null) }

    Page("رفّي", subtitle = "كتبك أنت، في مجموعة أو بدونها.") {
        PrimaryButton("أضف كتاباً", onClick = nav::addBook, icon = Icons.AutoMirrored.Rounded.MenuBook)
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            tabs.forEach { (st, label) -> Choice("$label (${shelf.count { it.status == st }})", tab == st, Modifier.weight(1f)) { tab = st } }
        }
        Spacer(Modifier.height(12.dp))
        val list = shelf.filter { it.status == tab }.sortedByDescending { maxOf(it.lastReadAt, it.startedAt, it.finishedAt) }
        if (list.isEmpty()) {
            PaperCard {
                EmptyState(
                    Icons.AutoMirrored.Rounded.MenuBook,
                    when (tab) { ShelfStatus.READING -> "لا كتاب تقرؤه الآن"; ShelfStatus.FINISHED -> "لم تُتمّ كتاباً بعد"; ShelfStatus.WANT -> "قائمتك فارغة" },
                    when (tab) {
                        ShelfStatus.READING -> "أضف الكتاب الذي بين يديك لتسجّل وقتك فيه."
                        ShelfStatus.FINISHED -> "حين تُتمّ كتاباً اضغط «أتممته» فيظهر هنا."
                        ShelfStatus.WANT -> "احفظ هنا الكتب التي تنوي قراءتها لاحقاً."
                    },
                )
            }
        } else {
            PaperCard(padding = PaddingValues(vertical = 4.dp)) {
                list.forEachIndexed { i, b ->
                    if (i > 0) ListDivider()
                    Row(Modifier.fillMaxWidth().clickable { open = b }.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        BookCover(b.title, b.bookId, width = 38.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("«${b.title}»", style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(b.author, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                        }
                        Text(if (b.minutes > 0) Fmt.duration(b.minutes) else "", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
    open?.let { b -> ShelfBookSheet(shelf.firstOrNull { it.bookId == b.bookId } ?: b) { open = null } }
}

/** One book on the shelf: its numbers and what can be done with it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShelfBookSheet(b: ShelfBook, onDismiss: () -> Unit) {
    val vm = LocalVm.current
    val nav = LocalNav.current
    val reads by vm.reads.collectAsStateWithLifecycle()
    var suggesting by remember { mutableStateOf(false) }
    val mine = reads.filter { it.bookId == b.bookId && it.minutes > 0 }
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state, containerColor = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BookCover(b.title, b.bookId, width = 56.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("«${b.title}»", style = MaterialTheme.typography.headlineSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(b.author, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(16.dp))
            StatRow(
                Fmt.duration(b.minutes.coerceAtLeast(0)) to "وقتك فيه",
                "${mine.map { it.date }.distinct().size}" to "يوم قراءة",
                (mine.minOfOrNull { it.date }?.let { Dates.short(it) } ?: "—") to "بدأته",
            )
            if (b.status == ShelfStatus.FINISHED && b.finishedAt > 0) {
                Spacer(Modifier.height(8.dp))
                Text("أتممته في ${Dates.long(day(b.finishedAt))}", style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.height(18.dp))
            when (b.status) {
                ShelfStatus.READING -> {
                    PrimaryButton("أتممته", icon = Icons.Rounded.Flag, onClick = { vm.setShelfStatus(b, ShelfStatus.FINISHED); onDismiss() })
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GhostButton("سجّل وقتاً", onClick = { onDismiss(); nav.day(vm.today(), b.bookId) }, modifier = Modifier.weight(1f))
                        GhostButton("لاحقاً", onClick = { vm.setShelfStatus(b, ShelfStatus.WANT); onDismiss() })
                    }
                }
                ShelfStatus.FINISHED -> GhostButton("أعيد قراءته", onClick = { vm.setShelfStatus(b, ShelfStatus.READING); onDismiss() }, modifier = Modifier.fillMaxWidth())
                ShelfStatus.WANT -> PrimaryButton("أقرؤه الآن", onClick = { vm.setShelfStatus(b, ShelfStatus.READING); onDismiss() })
            }
            Spacer(Modifier.height(6.dp))
            TextButton(onClick = { suggesting = true }) { Text("خطأ في العنوان أو المؤلف؟") }
            if (b.minutes == 0 || b.status != ShelfStatus.READING) {
                TextButton(onClick = { vm.removeFromShelf(b); onDismiss() }) { Text("إزالة من رفّي", color = MaterialTheme.colorScheme.error) }
            }
        }
    }
    if (suggesting) SuggestSheet(b.bookId, b.title, b.author) { suggesting = false }
}

/** Find a book (in the catalog, or from Google Books) and put it on the shelf. */
@Composable
fun AddBookScreen() {
    val vm = LocalVm.current
    val nav = LocalNav.current
    val shelf by vm.shelf.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var manual by rememberSaveable { mutableStateOf(false) }
    var status by rememberSaveable { mutableStateOf(ShelfStatus.READING) }
    fun add(b: CatalogBook) {
        if (shelf.any { it.bookId == b.id && it.status == status }) { vm.say("«${b.title}» في رفّك من قبل."); nav.back(); return }
        vm.shelve(b, status, then = nav::back)
    }
    Page("أضف كتاباً", onBack = nav::back, subtitle = "ابحث باسم الكتاب أو المؤلف.") {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Choice("أقرؤه الآن", status == ShelfStatus.READING, Modifier.weight(1f)) { status = ShelfStatus.READING }
            Choice("أتممته", status == ShelfStatus.FINISHED, Modifier.weight(1f)) { status = ShelfStatus.FINISHED }
            Choice("لاحقاً", status == ShelfStatus.WANT, Modifier.weight(1f)) { status = ShelfStatus.WANT }
        }
        Spacer(Modifier.height(14.dp))
        if (manual) NewBookForm(query, onCancel = { manual = false }) { add(it) }
        else BookSearch(query, { query = it }, onManual = { manual = true }, onPick = ::add)
    }
}
