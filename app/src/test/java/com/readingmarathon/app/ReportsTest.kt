package com.readingmarathon.app

import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import com.readingmarathon.app.report.Reports
import com.readingmarathon.app.report.Reports.challengeReport
import com.readingmarathon.app.report.Reports.marathonReport
import com.readingmarathon.core.DemoData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate

/**
 * Exports run without errors. Robolectric has no PDF backend, so the report pages are drawn onto
 * bitmaps (same drawing code) and saved to build/reports-out for review.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class ReportsTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val today = LocalDate.of(2026, 10, 23)
    private val data = DemoData.build(today)
    private val out = File("build/reports-out").apply { mkdirs() }

    private fun save(name: String, pages: List<Bitmap>) = pages.forEachIndexed { i, b ->
        File(out, "$name-${i + 1}.png").outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun reportPagesRender() {
        val challenge = Reports.preview(context) { challengeReport(data, data.currentChallenge(today)!!, today) }
        val marathon = Reports.preview(context) { marathonReport(data, today) }
        assertTrue(challenge.isNotEmpty() && marathon.isNotEmpty())
        save("challenge", challenge)
        save("marathon", marathon)
    }

    @Test
    fun csvAndBackup() {
        val dir = File(context.cacheDir, "exports").apply { deleteRecursively() }
        Reports.shareCsv(context, data)
        Reports.shareBackup(context, data)
        val files = dir.listFiles().orEmpty()
        assertEquals(2, files.size)
        val csv = files.first { it.extension == "csv" }.readText()
        assertTrue(csv.startsWith("﻿"))
        assertEquals(data.entries.size + data.achievements.size + 3, csv.trimEnd().lines().size)
    }
}
