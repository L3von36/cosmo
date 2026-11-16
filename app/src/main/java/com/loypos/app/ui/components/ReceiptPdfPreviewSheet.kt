package com.loypos.app.ui.components

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Print
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.loypos.app.core.ReceiptPaperFormat
import com.loypos.app.core.ReceiptPdfGenerator
import com.loypos.app.core.ReceiptPdfPreviewResult
import com.loypos.app.data.local.ReceiptWithLines
import com.loypos.app.ui.LoyPosViewModel
import com.loypos.app.ui.theme.LoyBorder
import com.loypos.app.ui.theme.LoyError
import com.loypos.app.ui.theme.LoyGreen
import com.loypos.app.ui.theme.LoyGreenDarkText
import com.loypos.app.ui.theme.LoyGreenLight
import com.loypos.app.ui.theme.LoySurfaceVariant
import com.loypos.app.ui.theme.LoyTextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReceiptPdfPreviewSheet(
    viewModel: LoyPosViewModel,
    receiptWithLines: ReceiptWithLines,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var selectedFormat by remember { mutableStateOf(ReceiptPaperFormat.THERMAL_80MM) }
    var previewResult by remember { mutableStateOf<ReceiptPdfPreviewResult?>(null) }
    var isRendering by remember { mutableStateOf(true) }
    var renderError by remember { mutableStateOf<String?>(null) }

    val savePdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf"),
    ) { uri ->
        val file = previewResult?.file
        if (uri != null && file != null) {
            viewModel.saveReceiptPdfToUri(uri, file)
        }
    }

    LaunchedEffect(receiptWithLines.receipt.id, selectedFormat) {
        isRendering = true
        renderError = null
        viewModel.generateReceiptPdfPreview(receiptWithLines, selectedFormat) { res ->
            isRendering = false
            res.onSuccess { preview ->
                previewResult = preview
            }.onFailure { err ->
                renderError = err.message ?: "Failed to generate PDF preview"
            }
        }
    }

    val receiptNum = receiptWithLines.receipt.number.toString().padStart(4, '0')

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(LoyGreenLight),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.PictureAsPdf,
                        contentDescription = "Receipt PDF preview",
                        tint = LoyGreen,
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Receipt #$receiptNum Preview",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = previewResult?.let {
                            "${it.file.name} · ${it.pageCount} ${if (it.pageCount == 1) "page" else "pages"}"
                        } ?: "Rendering printable PDF...",
                        style = MaterialTheme.typography.bodySmall,
                        color = LoyTextSecondary,
                    )
                }
                previewResult?.let {
                    PillBadge(
                        text = it.format.label,
                        backgroundColor = LoyGreenLight,
                        textColor = LoyGreenDarkText,
                    )
                }
            }

            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = selectedFormat == ReceiptPaperFormat.THERMAL_80MM,
                    onClick = { selectedFormat = ReceiptPaperFormat.THERMAL_80MM },
                    shape = SegmentedButtonDefaults.itemShape(0, 2),
                    modifier = Modifier.testTag("pdf_format_thermal"),
                ) {
                    Text("80mm Thermal")
                }
                SegmentedButton(
                    selected = selectedFormat == ReceiptPaperFormat.A4_STANDARD,
                    onClick = { selectedFormat = ReceiptPaperFormat.A4_STANDARD },
                    shape = SegmentedButtonDefaults.itemShape(1, 2),
                    modifier = Modifier.testTag("pdf_format_a4"),
                ) {
                    Text("A4 Document")
                }
            }

            // Rendered PDF Pages Preview Frame
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = LoySurfaceVariant,
                border = BorderStroke(1.dp, LoyBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 240.dp, max = 380.dp)
                    .testTag("pdf_preview_container"),
            ) {
                when {
                    isRendering -> {
                        Box(
                            modifier = Modifier.fillMaxWidth().height(260.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                CircularProgressIndicator(color = LoyGreen, modifier = Modifier.size(32.dp))
                                Text("Generating PDF preview...", style = MaterialTheme.typography.bodySmall, color = LoyTextSecondary)
                            }
                        }
                    }
                    renderError != null -> {
                        Box(
                            modifier = Modifier.fillMaxWidth().height(220.dp).padding(16.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(renderError!!, color = LoyError, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    previewResult != null -> {
                        val preview = previewResult!!
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState())
                                .padding(14.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            preview.previewBitmaps.forEachIndexed { pageIndex, bitmap ->
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color.White,
                                    shadowElevation = 3.dp,
                                    border = BorderStroke(1.dp, LoyBorder),
                                    modifier = if (selectedFormat == ReceiptPaperFormat.THERMAL_80MM) {
                                        Modifier.widthIn(max = 260.dp).fillMaxWidth()
                                    } else {
                                        Modifier.fillMaxWidth()
                                    },
                                ) {
                                    Image(
                                        bitmap = bitmap.asImageBitmap(),
                                        contentDescription = "Receipt #$receiptNum PDF page ${pageIndex + 1}",
                                        contentScale = ContentScale.FillWidth,
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Print / Save / Share Actions
            val readyFile = previewResult?.file
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = {
                        if (readyFile != null) {
                            runCatching {
                                ReceiptPdfGenerator.printPdfFile(
                                    context = context,
                                    file = readyFile,
                                    jobName = "LoyPOS Receipt #$receiptNum",
                                )
                            }.onFailure { err ->
                                viewModel.showMessage("Print failed: ${err.message}")
                            }
                        }
                    },
                    enabled = readyFile != null && !isRendering,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("pdf_preview_print_button"),
                ) {
                    Icon(Icons.Outlined.Print, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Print")
                }

                OutlinedButton(
                    onClick = {
                        if (readyFile != null) {
                            savePdfLauncher.launch(readyFile.name)
                        }
                    },
                    enabled = readyFile != null && !isRendering,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("pdf_preview_save_button"),
                ) {
                    Icon(Icons.Outlined.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Save")
                }

                Button(
                    onClick = {
                        if (readyFile != null) {
                            viewModel.shareGeneratedPdfFile(readyFile)
                        }
                    },
                    enabled = readyFile != null && !isRendering,
                    colors = ButtonDefaults.buttonColors(containerColor = LoyGreen, contentColor = Color.White),
                    modifier = Modifier.weight(1.1f).heightIn(min = 48.dp).testTag("pdf_preview_share_button"),
                ) {
                    Icon(Icons.Outlined.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Share")
                }
            }
            Spacer(Modifier.height(6.dp))
        }
    }
}
