package com.readingmarathon.app

import androidx.test.core.app.ApplicationProvider
import com.readingmarathon.app.data.BundledCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The catalog shipped in the app: big enough, clean, and found by Arabic or English names. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BundledCatalogTest {
    private val catalog = BundledCatalog(ApplicationProvider.getApplicationContext<android.content.Context>())

    @Test
    fun bigAndClean() {
        val books = catalog.books
        assertTrue("${books.size} books", books.size >= 1000)
        assertEquals("ids are unique", books.size, books.map { it.id }.toSet().size)
        assertTrue(books.all { it.title.isNotBlank() && it.author.isNotBlank() && it.title.length <= 120 && it.author.length <= 120 })
    }

    @Test
    fun search() {
        catalog.books // load once, then time the searches themselves
        val start = System.nanoTime()
        assertEquals("باولو كويلو", catalog.search("الخيميائي").first().author)
        assertEquals("مزرعة الحيوان", catalog.search("Animal Farm").first().title)
        assertTrue(catalog.search("نجيب محفوظ").size >= 5)
        assertTrue(catalog.search("ايام طه").any { it.title == "الأيام" })
        val ms = (System.nanoTime() - start) / 1_000_000
        assertTrue("searches took $ms ms", ms < 1500)
    }
}
