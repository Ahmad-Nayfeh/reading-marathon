package com.readingmarathon.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** A book as Google Books describes it: only used to fill the "new book" form, never as the catalog. */
data class FoundBook(val title: String, val author: String, val pages: Int?)

object GoogleBooks {
    suspend fun search(query: String): List<FoundBook> = withContext(Dispatchers.IO) {
        val q = URLEncoder.encode(query.trim(), "UTF-8")
        val conn = URL("https://www.googleapis.com/books/v1/volumes?q=$q&maxResults=10&printType=books").openConnection() as HttpURLConnection
        conn.connectTimeout = 8_000
        conn.readTimeout = 8_000
        try {
            if (conn.responseCode != 200) return@withContext emptyList()
            val items = JSONObject(conn.inputStream.bufferedReader().readText()).optJSONArray("items") ?: return@withContext emptyList()
            (0 until items.length()).mapNotNull { i ->
                val v = items.getJSONObject(i).optJSONObject("volumeInfo") ?: return@mapNotNull null
                val title = listOf(v.optString("title"), v.optString("subtitle")).filter { it.isNotBlank() }.firstOrNull() ?: return@mapNotNull null
                val authors = v.optJSONArray("authors")
                val author = if (authors == null) "" else (0 until authors.length()).joinToString("، ") { authors.getString(it) }
                FoundBook(title, author, v.optInt("pageCount").takeIf { it > 0 })
            }.distinctBy { it.title + "|" + it.author }
        } finally {
            conn.disconnect()
        }
    }
}
