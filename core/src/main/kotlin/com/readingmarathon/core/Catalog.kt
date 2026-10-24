package com.readingmarathon.core

/**
 * The shared book catalog. Every group picks its books from it, so a book has one official title
 * and author everywhere. Other names a book is known by are kept as aliases and found by search.
 */
data class CatalogBook(
    val id: String,
    val title: String,
    val author: String,
    val aliases: List<String> = emptyList(),
    val pages: Int? = null,
    val status: BookStatus = BookStatus.VERIFIED,
    /** Set when this entry was a duplicate: the book it now points to. */
    val mergedInto: String? = null,
    val createdBy: String? = null,
    val createdAt: Long = 0,
) {
    val book: Book get() = Book(title, author)
}

enum class BookStatus { VERIFIED, PENDING, MERGED }

enum class SuggestionType { NEW_BOOK, FIX, DUPLICATE }
enum class SuggestionStatus { OPEN, ACCEPTED, REJECTED }

/** A reader asks the admin to add a book, fix one, or merge two. */
data class Suggestion(
    val id: String,
    val type: SuggestionType,
    val bookId: String?,
    /** For DUPLICATE: the book this one repeats. */
    val otherBookId: String? = null,
    val title: String = "",
    val author: String = "",
    val note: String = "",
    val by: String,
    val byName: String = "",
    val status: SuggestionStatus = SuggestionStatus.OPEN,
    val createdAt: Long = 0,
    val resolvedAt: Long = 0,
    val reply: String = "",
)

object Catalog {

    private const val MAX_PREFIX = 12

    /** Words of a text, normalized the Arabic-aware way, without the definite article on its own. */
    fun words(text: String): List<String> =
        Text.normalize(text).split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotBlank() }

    /** A word and the same word without "ال", so «الأيام» is found by «أيام» too. */
    private fun forms(word: String): List<String> =
        if (word.startsWith("ال") && word.length > 3) listOf(word, word.substring(2)) else listOf(word)

    /**
     * Search keys stored on each book: every prefix (2 to 12 letters) of every word of the title,
     * the aliases and the author. Firestore matches one key with array-contains; the rest of the
     * query is checked on the phone with [matches].
     */
    fun keys(title: String, author: String, aliases: List<String>): List<String> {
        val out = LinkedHashSet<String>()
        (listOf(title, author) + aliases).flatMap(::words).flatMap(::forms).forEach { w ->
            for (n in 2..minOf(w.length, MAX_PREFIX)) out += w.substring(0, n)
        }
        return out.toList()
    }

    /** The key to query Firestore with: the longest word of the query, cut to the prefix limit. */
    fun queryKey(query: String): String? =
        words(query).flatMap(::forms).maxByOrNull { it.length }?.take(MAX_PREFIX)?.takeIf { it.length >= 2 }

    /** Every word of the query starts some word of the title, an alias or the author. */
    fun matches(query: String, b: CatalogBook): Boolean {
        val q = words(query).flatMap { listOf(forms(it).last()) }
        if (q.isEmpty()) return false
        val hay = (listOf(b.title, b.author) + b.aliases).flatMap(::words).flatMap(::forms)
        return q.all { w -> hay.any { it.startsWith(w) } }
    }

    /** Best matches first: exact title, then title starts with the query, then the rest; verified before pending. */
    fun rank(query: String, books: List<CatalogBook>): List<CatalogBook> {
        val q = Text.normalize(query)
        return books.filter { it.status != BookStatus.MERGED && matches(query, it) }
            .sortedWith(
                compareBy<CatalogBook>(
                    { if (Text.normalize(it.title) == q || it.aliases.any { a -> Text.normalize(a) == q }) 0 else 1 },
                    { if (Text.normalize(it.title).startsWith(q)) 0 else 1 },
                    { if (it.status == BookStatus.VERIFIED) 0 else 1 },
                    { it.title.length },
                ),
            )
    }

    /** Is this probably a book the catalog already has? Same title (or alias) and same author, normalized. */
    fun findSame(title: String, author: String, books: List<CatalogBook>): CatalogBook? {
        val t = Text.normalize(title)
        val a = Text.normalize(author)
        return books.firstOrNull { b ->
            b.status != BookStatus.MERGED &&
                (Text.normalize(b.title) == t || b.aliases.any { Text.normalize(it) == t }) &&
                (a.isEmpty() || Text.normalize(b.author) == a)
        }
    }

    fun validate(title: String, author: String) {
        if (title.isBlank()) throw RuleViolation("اكتب عنوان الكتاب.")
        if (author.isBlank()) throw RuleViolation("اكتب اسم المؤلف.")
        if (title.trim().length > Limits.MAX_TITLE || author.trim().length > Limits.MAX_TITLE) throw RuleViolation("العنوان أو الاسم طويل جداً.")
    }

    /** Aliases after merging [from] into [into]: the duplicate's names become other names of the kept book. */
    fun mergedAliases(into: CatalogBook, from: CatalogBook): List<String> {
        val seen = HashSet<String>().apply { add(Text.normalize(into.title)) }
        return (into.aliases + from.title + from.aliases).filter { seen.add(Text.normalize(it)) }
    }

    /** "title,author[,alias;alias][,pages]" lines, for filling the catalog in one go. Header line optional. */
    fun parseList(csv: String): List<CatalogBook> =
        CsvImport.parseCsv(csv).mapNotNull { r ->
            val title = r.getOrNull(0)?.trim().orEmpty()
            val author = r.getOrNull(1)?.trim().orEmpty()
            if (title.isEmpty() || author.isEmpty() || title.equals("title", true) || title == "العنوان") return@mapNotNull null
            CatalogBook(
                id = "",
                title = title,
                author = author,
                aliases = r.getOrNull(2)?.split(';', '؛')?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty(),
                pages = r.getOrNull(3)?.trim()?.toIntOrNull(),
            )
        }
}
