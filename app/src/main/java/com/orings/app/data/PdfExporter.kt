package com.orings.app.data

import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.orings.core.DesignReport
import java.io.OutputStream

/**
 * 把 [DesignReport.Report] 排成 A4 PDF（純文字，使用系統字型，中文可正常顯示）。
 */
object PdfExporter {
    private const val PAGE_W = 595 // A4，單位 pt
    private const val PAGE_H = 842
    private const val MARGIN = 48f
    private const val CONTENT_W = PAGE_W - 2 * MARGIN

    fun write(report: DesignReport.Report, out: OutputStream) {
        val doc = PdfDocument()
        val title = paint(18f, bold = true)
        val subtitle = paint(10f, color = Color.rgb(90, 100, 110))
        val sectionTitle = paint(12.5f, bold = true, color = Color.rgb(11, 106, 133))
        val label = paint(10f, color = Color.rgb(70, 80, 90))
        val value = paint(10f)
        val note = paint(9f, color = Color.rgb(70, 80, 90))
        val verdictPaint = paint(
            12f, bold = true,
            color = when (report.verdict) {
                DesignReport.Verdict.OK -> Color.rgb(27, 116, 68)
                DesignReport.Verdict.WARNING -> Color.rgb(143, 90, 0)
                DesignReport.Verdict.ERROR -> Color.rgb(179, 38, 30)
            },
        )
        val rule = Paint().apply { color = Color.rgb(211, 220, 225); strokeWidth = 0.8f }

        var pageNo = 0
        var page: PdfDocument.Page? = null
        var y = 0f

        fun newPage() {
            page?.let { doc.finishPage(it) }
            pageNo++
            page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNo).create())
            y = MARGIN
            // 頁尾
            page!!.canvas.drawText("O 型環溝槽設計報告　第 $pageNo 頁", MARGIN, PAGE_H - 24f, note)
        }

        fun ensure(height: Float) {
            if (page == null || y + height > PAGE_H - MARGIN) newPage()
        }

        /** 自動換行；回傳寫了幾行 */
        fun drawWrapped(text: String, x: Float, width: Float, p: Paint, lineH: Float) {
            var rest = text
            while (rest.isNotEmpty()) {
                val n = p.breakText(rest, true, width, null).coerceAtLeast(1)
                ensure(lineH)
                page!!.canvas.drawText(rest, 0, n, x, y + p.textSize, p)
                y += lineH
                rest = rest.substring(n)
            }
        }

        newPage()
        drawWrapped(report.title, MARGIN, CONTENT_W, title, 26f)
        drawWrapped(report.subtitle, MARGIN, CONTENT_W, subtitle, 16f)
        y += 6f
        drawWrapped(report.verdict.label, MARGIN, CONTENT_W, verdictPaint, 20f)

        val labelW = CONTENT_W * 0.36f
        report.sections.forEach { s ->
            ensure(40f)
            y += 12f
            drawWrapped(s.title, MARGIN, CONTENT_W, sectionTitle, 18f)
            page!!.canvas.drawLine(MARGIN, y, MARGIN + CONTENT_W, y, rule)
            y += 6f
            s.rows.forEach { (k, v) ->
                val startY = y
                drawWrapped(k, MARGIN, labelW - 8f, label, 15f)
                val afterLabel = y
                y = startY
                drawWrapped(v, MARGIN + labelW, CONTENT_W - labelW, value, 15f)
                y = maxOf(y, afterLabel)
            }
            s.notes.forEach { drawWrapped("・$it", MARGIN, CONTENT_W, note, 13.5f) }
        }

        page?.let { doc.finishPage(it) }
        doc.writeTo(out)
        doc.close()
    }

    private fun paint(size: Float, bold: Boolean = false, color: Int = Color.rgb(22, 33, 42)) = Paint().apply {
        isAntiAlias = true
        textSize = size
        this.color = color
        typeface = Typeface.create(Typeface.DEFAULT, if (bold) Typeface.BOLD else Typeface.NORMAL)
    }
}
