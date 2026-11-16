package com.loypos.app.core

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import com.loypos.app.data.local.ReceiptWithLines
import com.loypos.app.data.local.SettingsModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.Calendar

object ReceiptPdfGenerator {
    // 80mm thermal roll width at 72dpi (~226pt)
    private const val PAGE_WIDTH = 226
    private const val MARGIN = 12f

    suspend fun createReceiptPdf(
        context: Context,
        receiptWithLines: ReceiptWithLines,
        settings: SettingsModel,
        branchName: String?,
        branchAddress: String?,
    ): File = withContext(Dispatchers.IO) {
        val r = receiptWithLines.receipt
        val lines = receiptWithLines.lines
        val sym = settings.currencySymbol

        val estHeight = (240 + lines.size * 28 +
            (if (r.discountCents > 0L) 16 else 0) +
            (if (r.taxCents > 0L) 16 else 0) +
            (if (r.note.isNotEmpty()) 22 else 0) +
            (if (!r.isCompleted) 22 else 0)).coerceAtLeast(280)

        val doc = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, estHeight, 1).create()
        val page = doc.startPage(pageInfo)
        val canvas = page.canvas

        val boldPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 11f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val regularPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 8.5f
            typeface = Typeface.DEFAULT
        }
        val semiBoldPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 8.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val mutedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.DKGRAY
            textSize = 7.5f
            typeface = Typeface.DEFAULT
        }
        val linePaint = Paint().apply {
            color = Color.LTGRAY
            strokeWidth = 0.8f
        }

        var y = 20f
        fun drawCentered(text: String, paint: Paint) {
            val w = paint.measureText(text)
            canvas.drawText(text, ((PAGE_WIDTH - w) / 2f).coerceAtLeast(MARGIN), y, paint)
            y += paint.textSize + 4f
        }
        fun drawRow(left: String, right: String, paint: Paint = regularPaint) {
            val rw = paint.measureText(right)
            canvas.drawText(left, MARGIN, y, paint)
            canvas.drawText(right, PAGE_WIDTH - MARGIN - rw, y, paint)
            y += paint.textSize + 5f
        }
        fun divider() {
            y += 2f
            canvas.drawLine(MARGIN, y, PAGE_WIDTH - MARGIN, y, linePaint)
            y += 10f
        }

        drawCentered(settings.storeName.uppercase(), boldPaint)
        if (!branchName.isNullOrEmpty()) drawCentered(branchName, mutedPaint)
        if (!branchAddress.isNullOrEmpty()) drawCentered(branchAddress, mutedPaint)
        divider()

        val cal = Calendar.getInstance().apply { timeInMillis = r.createdAt }
        val dateStr = "${cal.get(Calendar.DAY_OF_MONTH)}/${cal.get(Calendar.MONTH) + 1}/${cal.get(Calendar.YEAR)} " +
            "${cal.get(Calendar.HOUR_OF_DAY).toString().padStart(2, '0')}:${cal.get(Calendar.MINUTE).toString().padStart(2, '0')}"
        drawRow("Receipt #${r.number.toString().padStart(4, '0')}", dateStr, semiBoldPaint)
        if (!r.customerName.isNullOrEmpty()) {
            canvas.drawText("Customer: ${r.customerName}", MARGIN, y, mutedPaint)
            y += 12f
        }
        divider()

        for (l in lines) {
            canvas.drawText(l.name.take(30), MARGIN, y, semiBoldPaint)
            y += 11f
            drawRow("${formatQty(l.qty)} x ${formatCents(l.priceCents, sym)}", formatCents(l.lineTotalCents, sym))
        }
        divider()

        drawRow("Subtotal", formatCents(r.subtotalCents, sym))
        if (r.discountCents > 0L) {
            drawRow(r.discountLabel(), "-${formatCents(r.discountCents, sym)}")
        }
        if (r.taxCents > 0L) {
            drawRow("Tax", formatCents(r.taxCents, sym))
        }
        drawRow("TOTAL", formatCents(r.totalCents, sym), boldPaint)
        drawRow(if (r.paymentType == "card") "Paid by card" else "Paid in cash", formatCents(r.paidCents, sym))
        if (r.changeCents > 0L) {
            drawRow("Change", formatCents(r.changeCents, sym))
        }
        if (r.note.isNotEmpty()) {
            y += 2f
            canvas.drawText("Note: ${r.note.take(36)}", MARGIN, y, mutedPaint)
            y += 12f
        }
        if (!r.isCompleted) {
            val refundPaint = Paint(semiBoldPaint).apply { color = Color.RED }
            drawCentered("*** REFUNDED ***", refundPaint)
        }
        divider()
        drawCentered(settings.receiptFooter.take(40), mutedPaint)
        drawCentered("Printed by LoyPOS", mutedPaint)

        doc.finishPage(page)

        val dir = File(context.cacheDir, "receipts").apply { if (!exists()) mkdirs() }
        val file = File(dir, "receipt-${r.number.toString().padStart(4, '0')}.pdf")
        FileOutputStream(file).use { doc.writeTo(it) }
        doc.close()
        file
    }

    fun sharePdfFile(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, file.name)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share Receipt PDF"))
    }

    fun shareReceiptText(
        context: Context,
        receiptWithLines: ReceiptWithLines,
        settings: SettingsModel,
        branchName: String?,
    ) {
        val r = receiptWithLines.receipt
        val sym = settings.currencySymbol
        val sb = StringBuilder()
        sb.appendLine(settings.storeName)
        if (!branchName.isNullOrEmpty()) sb.appendLine(branchName)
        sb.appendLine("Receipt #${r.number.toString().padStart(4, '0')} · ${formatDateTime(r.createdAt)}")
        if (!r.customerName.isNullOrEmpty()) sb.appendLine("Customer: ${r.customerName}")
        sb.appendLine("--------------------------------")
        for (l in receiptWithLines.lines) {
            sb.appendLine("${l.name} (${formatQty(l.qty)} x ${formatCents(l.priceCents, sym)}) = ${formatCents(l.lineTotalCents, sym)}")
        }
        sb.appendLine("--------------------------------")
        sb.appendLine("Subtotal: ${formatCents(r.subtotalCents, sym)}")
        if (r.discountCents > 0L) sb.appendLine("${r.discountLabel()}: -${formatCents(r.discountCents, sym)}")
        if (r.taxCents > 0L) sb.appendLine("Tax: ${formatCents(r.taxCents, sym)}")
        sb.appendLine("TOTAL: ${formatCents(r.totalCents, sym)}")
        sb.appendLine("${if (r.paymentType == "card") "Card" else "Cash"}: ${formatCents(r.paidCents, sym)}")
        if (r.changeCents > 0L) sb.appendLine("Change: ${formatCents(r.changeCents, sym)}")
        if (!r.isCompleted) sb.appendLine("*** REFUNDED ***")
        sb.appendLine(settings.receiptFooter)

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Receipt #${r.number.toString().padStart(4, '0')}")
            putExtra(Intent.EXTRA_TEXT, sb.toString())
        }
        context.startActivity(Intent.createChooser(intent, "Share Receipt"))
    }
}
