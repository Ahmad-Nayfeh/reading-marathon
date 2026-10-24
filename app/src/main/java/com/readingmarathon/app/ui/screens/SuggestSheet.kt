package com.readingmarathon.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.readingmarathon.app.ui.LocalVm
import com.readingmarathon.app.ui.components.PrimaryButton
import com.readingmarathon.core.CatalogBook
import com.readingmarathon.core.Limits
import com.readingmarathon.core.Suggestion
import com.readingmarathon.core.SuggestionType

/** A reader reports a wrong title or author, or that the book repeats another catalog entry. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SuggestSheet(bookId: String, title: String, author: String, onDismiss: () -> Unit) {
    val vm = LocalVm.current
    var duplicate by remember { mutableStateOf(false) }
    var newTitle by remember { mutableStateOf(title) }
    var newAuthor by remember { mutableStateOf(author) }
    var note by remember { mutableStateOf("") }
    var query by remember { mutableStateOf("") }
    var other by remember { mutableStateOf<CatalogBook?>(null) }
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state, containerColor = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding().imePadding().padding(horizontal = 20.dp).padding(bottom = 16.dp)) {
            Text("اقتراح تصحيح", style = MaterialTheme.typography.headlineSmall)
            Text("«$title» · $author", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Choice("العنوان أو المؤلف خطأ", !duplicate, Modifier.weight(1f)) { duplicate = false }
                Choice("مكرر مع كتاب آخر", duplicate, Modifier.weight(1f)) { duplicate = true }
            }
            Spacer(Modifier.height(14.dp))
            if (!duplicate) {
                OutlinedTextField(newTitle, { newTitle = it.take(Limits.MAX_TITLE) }, label = { Text("العنوان الصحيح") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(newAuthor, { newAuthor = it.take(Limits.MAX_TITLE) }, label = { Text("المؤلف الصحيح") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            } else {
                Text("ابحث عن النسخة الأخرى من الكتاب في الفهرس:", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(8.dp))
                val o = other
                if (o == null) BookSearch(query, { query = it }, excludeId = bookId) { other = it }
                else BookPicker(o, onSelect = { other = it })
            }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(note, { note = it.take(500) }, label = { Text("ملاحظة (اختياري)") }, minLines = 2, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(16.dp))
            val changed = newTitle.trim() != title || newAuthor.trim() != author || note.isNotBlank()
            PrimaryButton(
                "أرسل الاقتراح",
                enabled = if (duplicate) other != null else changed,
                onClick = {
                    val s = if (duplicate) {
                        Suggestion("", SuggestionType.DUPLICATE, bookId, otherBookId = other?.id, title = title, author = author, note = note.trim(), by = "")
                    } else {
                        Suggestion("", SuggestionType.FIX, bookId, title = newTitle.trim(), author = newAuthor.trim(), note = note.trim(), by = "")
                    }
                    vm.run("وصل اقتراحك. شكراً لك، ستجد ردّنا في صفحة «أنا».", then = onDismiss) { vm.backend.suggest(s) }
                },
            )
        }
    }
}
