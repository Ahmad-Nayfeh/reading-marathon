package com.readingmarathon.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.TravelExplore
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.readingmarathon.app.data.FoundBook
import com.readingmarathon.app.data.GoogleBooks
import com.readingmarathon.app.ui.LocalVm
import com.readingmarathon.app.ui.components.BookCover
import com.readingmarathon.app.ui.components.GhostButton
import com.readingmarathon.app.ui.components.ListDivider
import com.readingmarathon.app.ui.components.PaperCard
import com.readingmarathon.app.ui.components.PrimaryButton
import com.readingmarathon.app.ui.components.Tag
import com.readingmarathon.app.ui.theme.Brand
import com.readingmarathon.core.BookStatus
import com.readingmarathon.core.CatalogBook
import com.readingmarathon.core.Limits
import kotlinx.coroutines.delay

/**
 * Choose a book from the shared catalog. The author comes with the title, so every group writes
 * the same book the same way. If the book isn't there, the reader adds it (usable at once, reviewed later).
 */
@Composable
fun BookPicker(selected: CatalogBook?, onSelect: (CatalogBook?) -> Unit, excludeId: String? = null) {
    if (selected != null) {
        PaperCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BookCover(selected.title, selected.id.ifBlank { selected.title }, width = 44.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("«${selected.title}»", style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(selected.author, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (selected.status == BookStatus.PENDING) {
                        Spacer(Modifier.height(4.dp))
                        Tag("جديد، بانتظار المراجعة")
                    }
                }
                TextButton(onClick = { onSelect(null) }) { Text("تغيير") }
            }
        }
        return
    }
    var adding by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    if (adding) {
        NewBookForm(query, onCancel = { adding = false }) { onSelect(it); adding = false }
        return
    }
    BookSearch(query, { query = it }, excludeId = excludeId, onPick = onSelect)
    Spacer(Modifier.height(10.dp))
    TextButton(onClick = { adding = true }) { Text("لم أجد الكتاب؟ أضفه إلى الفهرس") }
}

/** A search box over the catalog with the matches under it. */
@Composable
fun BookSearch(query: String, onQuery: (String) -> Unit, excludeId: String? = null, onPick: (CatalogBook) -> Unit) {
    val vm = LocalVm.current
    var results by remember { mutableStateOf<List<CatalogBook>?>(null) }
    LaunchedEffect(query) {
        if (query.trim().length < 2) { results = null; return@LaunchedEffect }
        delay(300)
        results = runCatching { vm.backend.searchBooks(query) }.getOrDefault(emptyList()).filter { it.id != excludeId }
    }
    OutlinedTextField(
        query, { onQuery(it.take(Limits.MAX_TITLE)) },
        leadingIcon = { Icon(Icons.Rounded.Search, null) },
        label = { Text("ابحث بالعنوان أو المؤلف") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
    )
    val list = results ?: return
    Spacer(Modifier.height(8.dp))
    if (list.isEmpty()) {
        Text("لا نتائج في الفهرس.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(4.dp))
        return
    }
    PaperCard(padding = PaddingValues(vertical = 4.dp)) {
        list.forEachIndexed { i, b ->
            if (i > 0) ListDivider()
            Row(Modifier.fillMaxWidth().clickable { onPick(b) }.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("«${b.title}»", style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(b.author, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (b.aliases.isNotEmpty()) {
                        Text("ويُعرف بـ: " + b.aliases.take(2).joinToString("، "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                if (b.status == BookStatus.PENDING) Tag("بانتظار المراجعة")
            }
        }
    }
}

/** Add a book the catalog doesn't have. Google Books can fill the fields, but the reader confirms them. */
@Composable
private fun NewBookForm(initial: String, onCancel: () -> Unit, onAdded: (CatalogBook) -> Unit) {
    val vm = LocalVm.current
    var title by rememberSaveable { mutableStateOf(initial) }
    var author by rememberSaveable { mutableStateOf("") }
    var pages by rememberSaveable { mutableStateOf("") }
    var found by remember { mutableStateOf<List<FoundBook>?>(null) }
    var looking by remember { mutableStateOf(false) }
    var lookup by remember { mutableStateOf(0) }
    LaunchedEffect(lookup) {
        if (lookup == 0) return@LaunchedEffect
        looking = true
        found = runCatching { GoogleBooks.search(listOf(title, author).filter { it.isNotBlank() }.joinToString(" ")) }.getOrDefault(emptyList())
        looking = false
    }
    PaperCard {
        Text("كتاب جديد في الفهرس", style = MaterialTheme.typography.titleMedium)
        Text(
            "يمكنك استخدامه الآن، ويراجعه فريق التطبيق ليبقى الفهرس موحّداً.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(title, { title = it.take(Limits.MAX_TITLE) }, label = { Text("العنوان") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(author, { author = it.take(Limits.MAX_TITLE) }, label = { Text("المؤلف") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            pages, { pages = it.filter(Char::isDigit).take(5) }, label = { Text("عدد الصفحات (اختياري)") }, singleLine = true,
            modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
        Spacer(Modifier.height(10.dp))
        GhostButton("املأ من Google Books", onClick = { lookup++ }, enabled = title.isNotBlank() && !looking, icon = Icons.Rounded.TravelExplore, modifier = Modifier.fillMaxWidth())
        if (looking) CircularProgressIndicator(Modifier.padding(10.dp).size(22.dp).align(Alignment.CenterHorizontally), strokeWidth = 2.dp, color = Brand.colors.gold)
        found?.let { list ->
            Spacer(Modifier.height(6.dp))
            if (list.isEmpty()) Text("لم يجد Google Books شيئاً. اكتب البيانات بنفسك.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            list.take(5).forEach { f ->
                Column(Modifier.fillMaxWidth().clickable { title = f.title.take(Limits.MAX_TITLE); author = f.author.take(Limits.MAX_TITLE); pages = f.pages?.toString().orEmpty(); found = null }.padding(vertical = 8.dp)) {
                    Text(f.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(f.author.ifBlank { "مؤلف غير معروف" } + (f.pages?.let { " · $it صفحة" } ?: ""), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PrimaryButton("أضف الكتاب", enabled = title.isNotBlank() && author.isNotBlank(), modifier = Modifier.weight(1f), onClick = {
                var added: CatalogBook? = null
                vm.run(then = { added?.let(onAdded) }) { added = vm.backend.proposeBook(title, author, pages.toIntOrNull()) }
            })
            GhostButton("إلغاء", onClick = onCancel)
        }
    }
}
