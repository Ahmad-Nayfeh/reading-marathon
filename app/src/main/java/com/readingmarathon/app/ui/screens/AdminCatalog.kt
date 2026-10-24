package com.readingmarathon.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.readingmarathon.app.ui.LocalVm
import com.readingmarathon.app.ui.components.EmptyState
import com.readingmarathon.app.ui.components.GhostButton
import com.readingmarathon.app.ui.components.ListDivider
import com.readingmarathon.app.ui.components.PaperCard
import com.readingmarathon.app.ui.components.PrimaryButton
import com.readingmarathon.app.ui.components.Tag
import com.readingmarathon.core.BookStatus
import com.readingmarathon.core.Catalog
import com.readingmarathon.core.CatalogBook
import com.readingmarathon.core.Dates
import com.readingmarathon.core.Limits
import com.readingmarathon.core.Suggestion
import com.readingmarathon.core.SuggestionStatus
import com.readingmarathon.core.SuggestionType
import java.time.LocalDate

// ---- Suggestions ----

@Composable
internal fun AdminSuggestions(version: Int, refresh: () -> Unit) {
    val vm = LocalVm.current
    var list by remember { mutableStateOf<List<Suggestion>?>(null) }
    var showClosed by rememberSaveable { mutableStateOf(false) }
    var open by remember { mutableStateOf<Suggestion?>(null) }
    LaunchedEffect(version) { vm.run(quiet = true) { list = vm.backend.adminSuggestions() } }
    val all = list ?: return Loading()
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Choice("مفتوحة (${all.count { it.status == SuggestionStatus.OPEN }})", !showClosed, Modifier.weight(1f)) { showClosed = false }
        Choice("منتهية", showClosed, Modifier.weight(1f)) { showClosed = true }
    }
    Spacer(Modifier.height(10.dp))
    val shown = all.filter { (it.status == SuggestionStatus.OPEN) != showClosed }
    if (shown.isEmpty()) { PaperCard { EmptyState(Icons.Rounded.Inbox, "لا اقتراحات", if (showClosed) "لم تُحسم اقتراحات بعد." else "كل الاقتراحات محسومة.") }; return }
    PaperCard(padding = PaddingValues(vertical = 4.dp)) {
        shown.forEachIndexed { i, s ->
            if (i > 0) ListDivider()
            Column(Modifier.fillMaxWidth().clickable { open = s }.padding(horizontal = 16.dp, vertical = 10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(typeLabel(s.type) + ": «${s.title}»", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (s.status != SuggestionStatus.OPEN) Tag(if (s.status == SuggestionStatus.ACCEPTED) "قُبل" else "رُفض")
                }
                Text(
                    listOf(s.author, s.byName.ifBlank { "قارئ" }, Dates.relative(day(s.createdAt), LocalDate.now())).filter { it.isNotBlank() }.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (s.note.isNotBlank()) Text(s.note, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
    open?.let { s -> SuggestionSheet(s, onDone = { open = null; refresh() }, onDismiss = { open = null }) }
}

private fun typeLabel(t: SuggestionType) = when (t) {
    SuggestionType.NEW_BOOK -> "كتاب جديد"
    SuggestionType.FIX -> "تصحيح"
    SuggestionType.DUPLICATE -> "مكرر"
}

/** Review one suggestion: see what it asks, adjust the book if needed, then accept or reject with a reply. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SuggestionSheet(s: Suggestion, onDone: () -> Unit, onDismiss: () -> Unit) {
    val vm = LocalVm.current
    var book by remember { mutableStateOf<CatalogBook?>(null) }
    var other by remember { mutableStateOf<CatalogBook?>(null) }
    var title by remember { mutableStateOf("") }
    var author by remember { mutableStateOf("") }
    var reply by remember { mutableStateOf("") }
    LaunchedEffect(s.id) {
        book = s.bookId?.let { runCatching { vm.backend.book(it) }.getOrNull() }
        other = s.otherBookId?.let { runCatching { vm.backend.book(it) }.getOrNull() }
        // A fix proposes new values; a new book starts from what the reader typed.
        title = s.title.ifBlank { book?.title.orEmpty() }
        author = s.author.ifBlank { book?.author.orEmpty() }
    }
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state, containerColor = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding().imePadding().padding(horizontal = 20.dp).padding(bottom = 16.dp)) {
            Text(typeLabel(s.type), style = MaterialTheme.typography.headlineSmall)
            Text("من ${s.byName.ifBlank { "قارئ" }} · ${Dates.long(day(s.createdAt))}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (s.note.isNotBlank()) { Spacer(Modifier.height(8.dp)); Text("«${s.note}»", style = MaterialTheme.typography.bodyMedium) }
            Spacer(Modifier.height(12.dp))
            val b = book
            if (b != null) {
                Text("في الفهرس الآن: «${b.title}» · ${b.author}" + if (b.status == BookStatus.PENDING) " (بانتظار المراجعة)" else "", style = MaterialTheme.typography.bodyMedium)
            } else if (s.bookId != null) {
                Text("الكتاب لم يعد في الفهرس.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
            }
            if (s.type == SuggestionType.DUPLICATE) {
                Spacer(Modifier.height(6.dp))
                Text("ويقول إنه مكرر مع: " + (other?.let { "«${it.title}» · ${it.author}" } ?: "كتاب غير موجود"), style = MaterialTheme.typography.bodyMedium)
                Text("القبول يدمج الأول في الثاني: تصبح أسماؤه أسماء بديلة، وتنتقل كل المجموعات إليه.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else if (s.status == SuggestionStatus.OPEN && b != null) {
                Spacer(Modifier.height(10.dp))
                Text(if (s.type == SuggestionType.FIX) "التصحيح المقترح (عدّله إن لزم):" else "راجع البيانات قبل الاعتماد:", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(title, { title = it.take(Limits.MAX_TITLE) }, label = { Text("العنوان") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(author, { author = it.take(Limits.MAX_TITLE) }, label = { Text("المؤلف") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
            if (s.status == SuggestionStatus.OPEN) {
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(reply, { reply = it.take(300) }, label = { Text("رد للقارئ (اختياري)") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PrimaryButton(
                        when (s.type) { SuggestionType.NEW_BOOK -> "اعتماد"; SuggestionType.FIX -> "تطبيق التصحيح"; SuggestionType.DUPLICATE -> "دمج" },
                        modifier = Modifier.weight(1f),
                        enabled = s.type == SuggestionType.DUPLICATE && other != null && b != null || s.type != SuggestionType.DUPLICATE && b != null && title.isNotBlank() && author.isNotBlank(),
                        onClick = {
                            vm.run("حُسم الاقتراح.", then = onDone) {
                                // Apply the (possibly edited) title and author before marking it accepted.
                                if (s.type != SuggestionType.DUPLICATE && b != null && (b.title != title.trim() || b.author != author.trim() || b.status == BookStatus.PENDING)) {
                                    vm.backend.adminSaveBook(b.copy(title = title, author = author))
                                }
                                vm.backend.adminResolve(s.id, true, reply)
                            }
                        },
                    )
                    GhostButton("رفض", onClick = { vm.run("رُفض الاقتراح.", then = onDone) { vm.backend.adminResolve(s.id, false, reply) } })
                }
            } else if (s.reply.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text("الرد: ${s.reply}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

// ---- Books ----

@Composable
internal fun AdminBooks(version: Int, refresh: () -> Unit) {
    val vm = LocalVm.current
    var books by remember { mutableStateOf<List<CatalogBook>?>(null) }
    var q by rememberSaveable { mutableStateOf("") }
    var editing by remember { mutableStateOf<CatalogBook?>(null) }
    var importing by remember { mutableStateOf(false) }
    var onlyPending by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(version) { vm.run(quiet = true) { books = vm.backend.adminBooks() } }
    val all = books ?: return Loading()
    val live = all.filter { it.status != BookStatus.MERGED }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PrimaryButton("كتاب جديد", onClick = { editing = CatalogBook("", "", "") }, icon = Icons.AutoMirrored.Rounded.MenuBook, modifier = Modifier.weight(1f))
        GhostButton("استيراد قائمة", onClick = { importing = true }, icon = Icons.Rounded.FileUpload)
    }
    Spacer(Modifier.height(10.dp))
    OutlinedTextField(q, { q = it }, leadingIcon = { Icon(Icons.Rounded.Search, null) }, placeholder = { Text("عنوان أو اسم آخر أو مؤلف") }, singleLine = true, modifier = Modifier.fillMaxWidth())
    Spacer(Modifier.height(8.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Choice("الكل (${live.size})", !onlyPending, Modifier.weight(1f)) { onlyPending = false }
        Choice("بانتظار المراجعة (${live.count { it.status == BookStatus.PENDING }})", onlyPending, Modifier.weight(1f)) { onlyPending = true }
    }
    Spacer(Modifier.height(10.dp))
    val shown = (if (q.isBlank()) live else Catalog.rank(q, live)).filter { !onlyPending || it.status == BookStatus.PENDING }
    if (shown.isEmpty()) PaperCard { Text(if (all.isEmpty()) "الفهرس فارغ. أضف كتباً أو استورد قائمة." else "لا نتائج.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
    else PaperCard(padding = PaddingValues(vertical = 4.dp)) {
        shown.take(150).forEachIndexed { i, b ->
            if (i > 0) ListDivider()
            Row(Modifier.fillMaxWidth().clickable { editing = b }.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("«${b.title}»", style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(b.author + if (b.aliases.isNotEmpty()) " · ويُعرف بـ ${b.aliases.joinToString("، ")}" else "", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (b.status == BookStatus.PENDING) Tag("بانتظار المراجعة")
            }
        }
    }
    if (shown.size > 150) Text("يظهر أول 150. ابحث لتضييق النتائج.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    editing?.let { b -> BookEditor(b, live, onDone = { editing = null; refresh() }, onDismiss = { editing = null }) }
    if (importing) ImportBooksDialog(onDone = { importing = false; refresh() }, onDismiss = { importing = false })
}

/** Edit a catalog book: title, author, other names, pages; verify it, merge it into another, or delete it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BookEditor(b: CatalogBook, all: List<CatalogBook>, onDone: () -> Unit, onDismiss: () -> Unit) {
    val vm = LocalVm.current
    var title by remember { mutableStateOf(b.title) }
    var author by remember { mutableStateOf(b.author) }
    var aliases by remember { mutableStateOf(b.aliases.joinToString("، ")) }
    var pages by remember { mutableStateOf(b.pages?.toString().orEmpty()) }
    var merging by remember { mutableStateOf(false) }
    var mergeQuery by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf<Pair<String, () -> Unit>?>(null) }
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state, containerColor = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding().imePadding().padding(horizontal = 20.dp).padding(bottom = 16.dp)) {
            Text(if (b.id.isBlank()) "كتاب جديد" else "تعديل الكتاب", style = MaterialTheme.typography.headlineSmall)
            if (b.status == BookStatus.PENDING) Text("أضافه قارئ، ولم يُراجَع بعد. الحفظ يعتمده.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(title, { title = it.take(Limits.MAX_TITLE) }, label = { Text("العنوان الرسمي") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(author, { author = it.take(Limits.MAX_TITLE) }, label = { Text("المؤلف") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(aliases, { aliases = it }, label = { Text("أسماء أخرى للكتاب (افصل بفاصلة)") }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(pages, { pages = it.filter(Char::isDigit).take(5) }, label = { Text("عدد الصفحات") }, singleLine = true, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            Spacer(Modifier.height(16.dp))
            PrimaryButton(if (b.status == BookStatus.PENDING) "حفظ واعتماد" else "حفظ", enabled = title.isNotBlank() && author.isNotBlank(), onClick = {
                val edited = b.copy(title = title, author = author, aliases = aliases.split(',', '،', ';', '؛').map { it.trim() }.filter { it.isNotEmpty() }, pages = pages.toIntOrNull())
                if (b.id.isBlank()) Catalog.findSame(title, author, all)?.let { dup ->
                    confirm = "«${dup.title}» لـ ${dup.author} موجود في الفهرس. أضيفه رغم ذلك؟" to { vm.run("حُفظ الكتاب.", then = onDone) { vm.backend.adminSaveBook(edited) } }
                    return@PrimaryButton
                }
                vm.run(if (b.id.isBlank()) "أُضيف الكتاب." else "حُفظ الكتاب، وتحدّث في كل المجموعات.", then = onDone) { vm.backend.adminSaveBook(edited) }
            })
            if (b.id.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                if (!merging) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GhostButton("دمجه في كتاب آخر", onClick = { merging = true }, modifier = Modifier.weight(1f))
                        GhostButton("حذف", color = MaterialTheme.colorScheme.error, onClick = {
                            confirm = "حذف «${b.title}» من الفهرس؟ المجموعات التي تقرؤه تحتفظ بعنوانه." to { vm.run("حُذف الكتاب.", then = onDone) { vm.backend.adminDeleteBook(b.id) } }
                        })
                    }
                } else {
                    Text("اختر النسخة التي تبقى. يصبح هذا الكتاب اسماً آخر لها، وتنتقل إليها كل المجموعات.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(mergeQuery, { mergeQuery = it }, leadingIcon = { Icon(Icons.Rounded.Search, null) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Catalog.rank(mergeQuery, all).filter { it.id != b.id }.take(8).forEach { t ->
                        Text(
                            "«${t.title}» · ${t.author}",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.fillMaxWidth().clickable {
                                confirm = "دمج «${b.title}» في «${t.title}»؟" to { vm.run("دُمج الكتابان.", then = onDone) { vm.backend.adminMergeBooks(b.id, t.id) } }
                            }.padding(vertical = 10.dp),
                        )
                    }
                }
            }
        }
    }
    confirm?.let { (text, go) ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text(text) },
            confirmButton = { TextButton(onClick = { confirm = null; go() }) { Text("تأكيد") } },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("إلغاء") } },
        )
    }
}

/** Paste a list (one book per line: title, author, other names separated by «؛», pages). */
@Composable
private fun ImportBooksDialog(onDone: () -> Unit, onDismiss: () -> Unit) {
    val vm = LocalVm.current
    var text by remember { mutableStateOf("") }
    val parsed = remember(text) { Catalog.parseList(text) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("استيراد قائمة كتب") },
        text = {
            Column {
                Text("سطر لكل كتاب: العنوان، المؤلف، أسماء أخرى مفصولة بـ «؛»، عدد الصفحات. الموجود في الفهرس يُتخطّى.", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(text, { text = it }, minLines = 6, maxLines = 10, modifier = Modifier.fillMaxWidth(), placeholder = { Text("الأيام,طه حسين\nرجال في الشمس,غسان كنفاني,,96") })
                Text("${parsed.size} كتاباً جاهزاً", style = MaterialTheme.typography.labelMedium)
            }
        },
        confirmButton = {
            TextButton(enabled = parsed.isNotEmpty(), onClick = {
                var added = 0
                vm.run(then = { vm.say("أُضيف $added كتاباً جديداً."); onDone() }) { added = vm.backend.adminImportBooks(parsed) }
            }) { Text("استيراد") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } },
    )
}
