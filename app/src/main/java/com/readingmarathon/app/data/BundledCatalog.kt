package com.readingmarathon.app.data

import android.content.Context
import com.readingmarathon.core.Catalog
import com.readingmarathon.core.CatalogBook
import org.json.JSONArray

/**
 * The starting catalog shipped inside the app (assets/catalog.json, about 4,900 books from
 * Wikidata: works with an Arabic Wikipedia article and a named author). Search runs on it on the
 * phone, so it works offline and before the shared catalog is filled. The admin's app copies it
 * into Firestore once, where the admin can then edit it.
 */
class BundledCatalog(private val load: () -> String) {

    constructor(context: Context) : this({ context.assets.open("catalog.json").bufferedReader().use { it.readText() } })

    private class Entry(val book: CatalogBook, val words: List<String>)

    private val entries: List<Entry> by lazy {
        val arr = runCatching { JSONArray(load()) }.getOrElse { JSONArray() }
        (0 until arr.length()).map { i ->
            val r = arr.getJSONArray(i)
            val aliases = r.getJSONArray(3).let { a -> (0 until a.length()).map(a::getString) }
            val b = CatalogBook(id = r.getString(0), title = r.getString(1), author = r.getString(2), aliases = aliases)
            Entry(b, (listOf(b.title, b.author) + aliases).flatMap(Catalog::words).flatMap { w -> if (w.startsWith("ال") && w.length > 3) listOf(w, w.substring(2)) else listOf(w) })
        }
    }

    val books: List<CatalogBook> by lazy { entries.map { it.book } }

    /** Books matching every word of the query (as a word prefix), best first. */
    fun search(query: String, limit: Int = 20): List<CatalogBook> {
        val q = Catalog.words(query).map { w -> if (w.startsWith("ال") && w.length > 3) w.substring(2) else w }
        if (q.isEmpty() || q.all { it.length < 2 }) return emptyList()
        val found = entries.filter { e -> q.all { w -> e.words.any { it.startsWith(w) } } }.map { it.book }
        return Catalog.rank(query, found).take(limit)
    }
}
