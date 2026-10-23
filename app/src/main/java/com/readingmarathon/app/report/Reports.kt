package com.readingmarathon.app.report

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import com.readingmarathon.app.R
import com.readingmarathon.core.AchievementType
import com.readingmarathon.core.Analytics
import com.readingmarathon.core.Challenge
import com.readingmarathon.core.Dates
import com.readingmarathon.core.Fmt
import com.readingmarathon.core.HallOfFame
import com.readingmarathon.core.MarathonData
import com.readingmarathon.core.MarathonJson
import com.readingmarathon.core.Names
import com.readingmarathon.core.Scope
import com.readingmarathon.core.Scoring
import java.io.File
import java.time.LocalDate

/** PDF reports, CSV export and JSON backups, shared through the system share sheet. */
object Reports {

    private const val W = 595 // A4 at 72 dpi
    private const val H = 842
    private const val M = 40f

    private fun exportDir(context: Context) = File(context.cacheDir, "exports").apply { mkdirs() }

    private fun share(context: Context, file: File, mime: String, title: String) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, title)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun safeName(s: String) = s.replace(Regex("[\\\\/:*?\"<>|\\s]+"), "-").take(40)

    private fun guarded(context: Context, block: () -> Unit) {
        runCatching(block).onFailure {
            android.util.Log.e("Reports", "export failed", it)
            Toast.makeText(context, "تعذّر إنشاء الملف.", Toast.LENGTH_SHORT).show()
        }
    }

    // ---- CSV / JSON ----

    fun shareCsv(context: Context, data: MarathonData) = guarded(context) {
        val sb = StringBuilder("﻿") // BOM so Excel reads Arabic correctly
        sb.append("التاريخ,القارئ,دقائق الكتاب المشترك,دقائق كتب أخرى,اقتباس مشترك,اقتباس آخر,التحدي\n")
        data.entries.sortedWith(compareBy({ it.date }, { data.member(it.memberId)?.name.orEmpty() })).forEach { e ->
            val name = data.member(e.memberId)?.name.orEmpty()
            val ch = data.challengeOn(e.date)?.book?.title.orEmpty()
            sb.append(listOf(e.date.toString(), name, e.commonMinutes, e.otherMinutes, if (e.quoteCommon) 1 else 0, if (e.quoteOther) 1 else 0, ch).joinToString(",") { csv(it.toString()) }).append('\n')
        }
        sb.append('\n').append("التاريخ,القارئ,الإنجاز,التحدي,عنوان الكتاب\n")
        data.achievements.sortedBy { it.date }.forEach { a ->
            val type = when (a.type) {
                AchievementType.FINISHED_COMMON_BOOK -> "إنهاء الكتاب المشترك"
                AchievementType.ATTENDED_DISCUSSION -> "حضور النقاش"
                AchievementType.FINISHED_OTHER_BOOK -> "إنهاء كتاب آخر"
            }
            sb.append(listOf(a.date.toString(), data.member(a.memberId)?.name.orEmpty(), type, data.challenge(a.challengeId)?.book?.title.orEmpty(), a.bookTitle.orEmpty()).joinToString(",") { csv(it) }).append('\n')
        }
        val f = File(exportDir(context), "${safeName(data.marathon.name)}-${LocalDate.now()}.csv")
        f.writeText(sb.toString())
        share(context, f, "text/csv", "سجلّ ${data.marathon.name}")
    }

    private fun csv(s: String) = if (s.any { it == ',' || it == '"' || it == '\n' }) "\"" + s.replace("\"", "\"\"") + "\"" else s

    fun shareBackup(context: Context, data: MarathonData) = guarded(context) {
        val f = File(exportDir(context), "${safeName(data.marathon.name)}-backup-${LocalDate.now()}.json")
        f.writeText(MarathonJson.encodeToString(MarathonData.serializer(), data))
        share(context, f, "application/json", "نسخة احتياطية: ${data.marathon.name}")
    }

    // ---- PDF ----

    /** Where pages go: a PdfDocument in the app, bitmaps in tests (Robolectric has no PDF backend). */
    internal interface Pages {
        fun start(number: Int): Canvas
        fun finish()
    }

    private class PdfPages(private val doc: PdfDocument) : Pages {
        private var page: PdfDocument.Page? = null
        override fun start(number: Int): Canvas {
            val p = doc.startPage(PdfDocument.PageInfo.Builder(W, H, number).create())
            page = p
            return p.canvas
        }
        override fun finish() { page?.let { doc.finishPage(it) }; page = null }
    }

    internal class BitmapPages : Pages {
        val pages = mutableListOf<android.graphics.Bitmap>()
        override fun start(number: Int): Canvas {
            val b = android.graphics.Bitmap.createBitmap(W * 2, H * 2, android.graphics.Bitmap.Config.ARGB_8888)
            pages += b
            return Canvas(b).apply { scale(2f, 2f) }
        }
        override fun finish() {}
    }

    /** A tiny right-to-left page writer. */
    internal class Writer(context: Context, private val pages: Pages) {
        private val regular: Typeface = ResourcesCompat.getFont(context, R.font.plex_regular) ?: Typeface.DEFAULT
        private val bold: Typeface = ResourcesCompat.getFont(context, R.font.plex_bold) ?: Typeface.DEFAULT_BOLD
        private val serif: Typeface = ResourcesCompat.getFont(context, R.font.amiri_regular) ?: Typeface.SERIF
        private var pageNo = 0
        private var open = false
        lateinit var canvas: Canvas
        var y = M

        val navy = Color.rgb(20, 33, 61)
        val gold = Color.rgb(183, 121, 31)
        val muted = Color.rgb(91, 100, 120)
        val line = Color.rgb(230, 223, 208)

        fun newPage() {
            if (open) pages.finish()
            pageNo++
            canvas = pages.start(pageNo)
            open = true
            canvas.drawColor(Color.rgb(255, 253, 248))
            y = M
            footer()
        }

        private fun footer() {
            canvas.drawLine(M, H - 34f, W - M, H - 34f, Paint().apply { color = line; strokeWidth = 1f })
            text("ماراثون القراءة · صفحة $pageNo", 9f, regular, muted, top = H - 28f, advance = false)
        }

        fun finish() { if (open) pages.finish(); open = false }

        fun paint(size: Float, face: Typeface, color: Int) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size; typeface = face; this.color = color
        }

        /** Draws right-aligned RTL text wrapped to [width]; returns its height. */
        fun text(
            s: String, size: Float, face: Typeface = regular, color: Int = navy,
            left: Float = M, width: Float = W - 2 * M, top: Float = y, advance: Boolean = true,
            align: Layout.Alignment = Layout.Alignment.ALIGN_NORMAL,
        ): Float {
            val layout = StaticLayout.Builder.obtain(s, 0, s.length, paint(size, face, color), width.toInt())
                .setAlignment(align)
                .setTextDirection(TextDirectionHeuristics.RTL)
                .setLineSpacing(0f, 1.15f)
                .build()
            canvas.save()
            canvas.translate(left, top)
            layout.draw(canvas)
            canvas.restore()
            if (advance) y = top + layout.height
            return layout.height.toFloat()
        }

        fun ensure(space: Float) { if (y + space > H - 50f) newPage() }

        fun heading(s: String) {
            ensure(60f)
            y += 14f
            text(s, 15f, bold, navy)
            canvas.drawRect(W - M - 36f, y + 3f, W - M, y + 5f, Paint().apply { color = gold })
            y += 12f
        }

        fun title(main: String, sub: String) {
            val bar = Paint().apply { color = navy }
            text("ماراثون القراءة", 11f, bold, gold, top = 24f, advance = false)
            // Amiri's line box is tall: measure the title, then place the subtitle under it.
            canvas.save(); canvas.clipRect(0f, 0f, 0f, 0f)
            val titleH = text(main, 24f, serif, Color.WHITE, top = 0f, advance = false)
            canvas.restore()
            val barH = 40f + titleH + 34f
            canvas.drawRect(0f, 0f, W.toFloat(), barH, bar)
            text("ماراثون القراءة", 11f, bold, gold, top = 24f, advance = false)
            text(main, 24f, serif, Color.WHITE, top = 40f, advance = false)
            text(sub, 10f, regular, Color.rgb(201, 210, 227), top = 40f + titleH + 4f, advance = false)
            y = barH + 22f
        }

        fun kpis(items: List<Pair<String, String>>) {
            val cols = 3
            val gap = 10f
            val cw = (W - 2 * M - gap * (cols - 1)) / cols
            items.chunked(cols).forEach { row ->
                ensure(64f)
                row.forEachIndexed { i, (label, value) ->
                    // Right to left: first item on the right.
                    val x = W - M - (i + 1) * cw - i * gap
                    canvas.drawRoundRect(x, y, x + cw, y + 56f, 10f, 10f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(246, 241, 230) })
                    text(value, 15f, bold, navy, left = x + 10f, width = cw - 20f, top = y + 8f, advance = false)
                    text(label, 9f, regular, muted, left = x + 10f, width = cw - 20f, top = y + 32f, advance = false)
                }
                y += 64f
            }
        }

        /** A table; column 0 is the right-most. */
        fun table(headers: List<String>, widths: List<Float>, rows: List<List<String>>) {
            val total = W - 2 * M
            val xs = widths.runningFold(W - M) { acc, w -> acc - w * total }
            fun row(cells: List<String>, face: Typeface, color: Int, shade: Boolean) {
                ensure(24f)
                if (shade) canvas.drawRect(M, y, W - M, y + 22f, Paint().apply { this.color = Color.rgb(246, 241, 230) })
                cells.forEachIndexed { i, c ->
                    val right = xs[i]
                    val w = widths[i] * total
                    text(c, 10f, face, color, left = right - w + 6f, width = w - 12f, top = y + 4f, advance = false)
                }
                y += 22f
            }
            row(headers, bold, muted, false)
            canvas.drawLine(M, y, W - M, y, Paint().apply { color = line })
            rows.forEachIndexed { i, r -> row(r, regular, navy, i % 2 == 1) }
            y += 6f
        }

        fun spacer(h: Float) { y += h }
    }

    private fun render(context: Context, name: String, title: String, draw: Writer.() -> Unit) = guarded(context) {
        val doc = PdfDocument()
        try {
            val w = Writer(context, PdfPages(doc))
            w.newPage()
            w.draw()
            w.finish()
            val f = File(exportDir(context), "${safeName(name)}.pdf")
            f.outputStream().use { doc.writeTo(it) }
            share(context, f, "application/pdf", title)
        } finally {
            doc.close()
        }
    }

    fun shareChallengePdf(context: Context, data: MarathonData, c: Challenge, today: LocalDate) =
        render(context, "تقرير-${c.book.title}", "تقرير تحدي «${c.book.title}»") { challengeReport(data, c, today) }

    fun shareMarathonPdf(context: Context, data: MarathonData, today: LocalDate) =
        render(context, "تقرير-${data.marathon.name}", "تقرير ${data.marathon.name}") { marathonReport(data, today) }

    /** Draws the reports onto bitmaps, for tests and previews. */
    internal fun preview(context: Context, draw: Writer.() -> Unit): List<android.graphics.Bitmap> {
        val pages = BitmapPages()
        Writer(context, pages).apply { newPage(); draw(); finish() }
        return pages.pages
    }

    internal fun Writer.challengeReport(data: MarathonData, c: Challenge, today: LocalDate) {
        val scope = Scope.OfChallenge(c)
        val standings = Scoring.standings(data, scope)
        title(c.book.title, "${data.marathon.name} · ${Dates.long(c.start)} – ${Dates.long(c.end)}" + (if (c.book.author.isNotBlank()) " · ${c.book.author}" else ""))
        kpis(Analytics.challengeKpis(data, c, today).map { it.label to it.value })
        heading("الترتيب")
        table(
            listOf("#", "القارئ", "النقاط", "الساعات", "أيام القراءة", "اقتباسات"),
            listOf(0.07f, 0.33f, 0.14f, 0.14f, 0.18f, 0.14f),
            standings.map { s ->
                listOf("${s.rank}", s.score.member.name, "${s.score.totalPoints}", Fmt.hours(s.score.totalMinutes), "${s.score.readingDays}", "${s.score.quotes}")
            },
        )
        val heroes = HallOfFame.heroes(data, scope).filter { it.names.isNotEmpty() }
        if (heroes.isNotEmpty()) {
            heading("لوحة الشرف")
            table(listOf("اللقب", "القارئ", "القيمة"), listOf(0.34f, 0.40f, 0.26f), heroes.map { listOf(it.title, Names.join(it.names), it.value) })
        }
        val finishers = data.achievements.filter { it.challengeId == c.id && it.type == AchievementType.FINISHED_COMMON_BOOK }.sortedBy { it.date }
        if (finishers.isNotEmpty()) {
            heading("أنهوا الكتاب")
            table(listOf("#", "القارئ", "التاريخ"), listOf(0.1f, 0.55f, 0.35f), finishers.mapIndexed { i, a -> listOf("${i + 1}", data.member(a.memberId)?.name.orEmpty(), Dates.long(a.date)) })
        }
        spacer(10f)
        text("أُنشئ في ${Dates.long(today)}. النقاط تُحسب بنظام نقاط التحدي نفسه.", 9f, color = muted)
    }

    internal fun Writer.marathonReport(data: MarathonData, today: LocalDate) {
        title(data.marathon.name, "تقرير شامل حتى ${Dates.long(today)}")
        kpis(Analytics.marathonKpis(data, today).map { it.label to it.value })
        heading("الترتيب العام")
        table(
            listOf("#", "القارئ", "النقاط", "الساعات", "الكتب", "النقاشات"),
            listOf(0.07f, 0.33f, 0.14f, 0.16f, 0.14f, 0.16f),
            Scoring.standings(data, Scope.AllTime).map { s ->
                listOf("${s.rank}", s.score.member.name, "${s.score.totalPoints}", Fmt.hours(s.score.totalMinutes), "${s.score.booksFinished}", "${s.score.discussions}")
            },
        )
        if (data.challenges.isNotEmpty()) {
            heading("التحديات")
            table(
                listOf("الكتاب", "الفترة", "المتصدر", "أنهوه"),
                listOf(0.34f, 0.30f, 0.22f, 0.14f),
                data.timeline.map { c ->
                    val top = Scoring.standings(data, Scope.OfChallenge(c)).filter { it.rank == 1 && it.score.totalPoints > 0 }
                    val n = data.achievements.count { it.challengeId == c.id && it.type == AchievementType.FINISHED_COMMON_BOOK }
                    listOf(c.book.title, "${Dates.short(c.start)} – ${Dates.short(c.end)} ${c.end.year}", Names.join(top.map { it.score.member.name }).ifEmpty { "—" }, "$n")
                },
            )
        }
    }
}
