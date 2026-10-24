package com.readingmarathon.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CatalogTest {
    private val ayyam = CatalogBook("b1", "الأيام", "طه حسين")
    private val granada = CatalogBook("b2", "ثلاثية غرناطة", "رضوى عاشور", aliases = listOf("غرناطة"))
    private val mawsim = CatalogBook("b3", "موسم الهجرة إلى الشمال", "الطيب صالح")
    private val old = CatalogBook("b4", "الايام", "طه حسين", status = BookStatus.MERGED, mergedInto = "b1")
    private val all = listOf(ayyam, granada, mawsim, old)

    @Test
    fun `keys hold prefixes of title, aliases and author, with and without the article`() {
        val k = Catalog.keys(granada.title, granada.author, granada.aliases)
        assertTrue("غر" in k && "غرناطه" in k && "رضو" in k && "ثلاثيه" in k)
        val a = Catalog.keys(ayyam.title, ayyam.author, emptyList())
        assertTrue("الايام" in a && "ايام" in a && "اي" in a)
    }

    @Test
    fun `search is Arabic-aware and skips merged entries`() {
        assertEquals(listOf("b1"), Catalog.rank("ايام", all).map { it.id })
        assertEquals(listOf("b1"), Catalog.rank("الأيّام", all).map { it.id })
        assertEquals(listOf("b2"), Catalog.rank("غرناطه", all).map { it.id })
        assertEquals(listOf("b3"), Catalog.rank("الطيب الهجرة", all).map { it.id })
        assertEquals(listOf("b1"), Catalog.rank("طه", all).map { it.id })
        assertTrue(Catalog.rank("كنفاني", all).isEmpty())
    }

    @Test
    fun `query key is a stored key`() {
        val key = Catalog.queryKey("موسم الهجرة")!!
        assertTrue(key in Catalog.keys(mawsim.title, mawsim.author, mawsim.aliases))
        assertNull(Catalog.queryKey("أ"))
    }

    @Test
    fun `same book is found by title or alias`() {
        assertEquals("b2", Catalog.findSame("غرناطة", "رضوى عاشور", all)?.id)
        assertEquals("b1", Catalog.findSame("الايام", "", all)?.id)
        assertNull(Catalog.findSame("الأيام", "نجيب محفوظ", all))
    }

    @Test
    fun `merge keeps every name once`() {
        val dup = CatalogBook("x", "أيام طه حسين", "طه حسين", aliases = listOf("الأيام", "Al-Ayyam"))
        assertEquals(listOf("أيام طه حسين", "Al-Ayyam"), Catalog.mergedAliases(ayyam, dup))
    }

    @Test
    fun `list import reads title, author, aliases and pages`() {
        val list = Catalog.parseList("العنوان,المؤلف\nالأيام,طه حسين,,\n\"رجال في الشمس\",غسان كنفاني,رجال الشمس؛Men in the Sun,96\n,بلا عنوان\n")
        assertEquals(2, list.size)
        assertEquals(listOf("رجال الشمس", "Men in the Sun"), list[1].aliases)
        assertEquals(96, list[1].pages)
    }
}

class CatalogParityTest {
    /** The web admin panel (marathon/admin/index.html) computes the same keys; this fixes them. */
    @Test
    fun `keys are stable for the web panel`() {
        // Output of bookKeys() in the web panel for the same input.
        assertEquals(
            "ال,الا,الاي,الايا,الايام,اي,ايا,ايام,طه,al,ay,ayy,ayya,ayyam",
            Catalog.keys("الأيّام", "طه", listOf("Al-Ayyam!")).joinToString(","),
        )
    }
}
