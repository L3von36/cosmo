package com.loypos.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.loypos.app.core.formatCents
import com.loypos.app.core.formatDateTime
import com.loypos.app.core.formatQty
import com.loypos.app.data.local.ReceiptWithLines
import com.loypos.app.ui.LoyPosDataState
import com.loypos.app.ui.LoyPosViewModel
import com.loypos.app.ui.UiSessionState
import com.loypos.app.ui.components.ConfirmDialog
import com.loypos.app.ui.components.EmptyState
import com.loypos.app.ui.components.GroupCard
import com.loypos.app.ui.components.PillBadge
import com.loypos.app.ui.components.TopSyncIconButton
import com.loypos.app.ui.theme.LoyBackground
import com.loypos.app.ui.theme.LoyBorder
import com.loypos.app.ui.theme.LoyError
import com.loypos.app.ui.theme.LoyErrorLight
import com.loypos.app.ui.theme.LoyGreen
import com.loypos.app.ui.theme.LoyGreenLight
import com.loypos.app.ui.theme.LoySurface
import com.loypos.app.ui.theme.LoyTextPrimary
import com.loypos.app.ui.theme.LoyTextSecondary
import com.loypos.app.ui.theme.TabularMoneyStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReceiptsScreen(
    viewModel: LoyPosViewModel,
    session: UiSessionState,
    data: LoyPosDataState,
    onOpenReceipt: (String) -> Unit,
) {
    var search by remember { mutableStateOf("") }
    val sym = data.settings.currencySymbol
    val filtered = remember(data.receipts, search) {
        val q = search.trim().lowercase()
        if (q.isEmpty()) data.receipts else data.receipts.filter {
            "#${it.receipt.number}".contains(q) ||
                it.receipt.number.toString().padStart(4, '0').contains(q) ||
                (it.receipt.customerName?.lowercase()?.contains(q) == true)
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(LoyBackground)) {
        TopAppBar(
            title = { Text("Receipts", fontWeight = FontWeight.Bold) },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = LoySurface),
            actions = {
                TopSyncIconButton(syncing = session.syncing, onClick = { viewModel.syncCloud() })
            },
        )
        HorizontalDivider(color = LoyBorder)

        OutlinedTextField(
            value = search,
            onValueChange = { search = it },
            placeholder = { Text("Search by #number or customer") },
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .background(LoySurface)
                .padding(horizontal = 14.dp, vertical = 10.dp)
                .testTag("receipts_search_input"),
        )
        HorizontalDivider(color = LoyBorder)

        if (filtered.isEmpty()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                EmptyState(
                    icon = Icons.Outlined.ReceiptLong,
                    title = "No receipts yet",
                    subtitle = "Completed sales will appear here.",
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(filtered, key = { it.receipt.id }) { item ->
                    val r = item.receipt
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = LoySurface,
                        border = BorderStroke(1.dp, LoyBorder),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onOpenReceipt(r.id) }
                            .testTag("receipt_row_${r.id}"),
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (r.isCompleted) LoyGreenLight else LoyErrorLight),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = if (r.paymentType == "card") Icons.Outlined.CreditCard else Icons.Outlined.Payments,
                                    contentDescription = r.paymentType,
                                    tint = if (r.isCompleted) LoyGreen else LoyError,
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(
                                        "#${r.number.toString().padStart(4, '0')}",
                                        fontWeight = FontWeight.Bold,
                                        color = LoyTextPrimary,
                                    )
                                    if (!r.isCompleted) {
                                        PillBadge(text = "Refunded", backgroundColor = LoyErrorLight, textColor = LoyError)
                                    }
                                }
                                Text(
                                    buildString {
                                        append(formatDateTime(r.createdAt))
                                        if (!r.customerName.isNullOrEmpty()) append(" · ${r.customerName}")
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = LoyTextSecondary,
                                )
                            }
                            Text(
                                text = formatCents(r.totalCents, sym),
                                style = TabularMoneyStyle.merge(MaterialTheme.typography.titleMedium),
                                fontWeight = FontWeight.Bold,
                                color = if (r.isCompleted) LoyTextPrimary else LoyError,
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReceiptDetailScreen(
    viewModel: LoyPosViewModel,
    receiptId: String,
    data: LoyPosDataState,
    onBack: () -> Unit,
) {
    val receiptWithLines = remember(data.receipts, receiptId) {
        data.receipts.firstOrNull { it.receipt.id == receiptId }
    }
    if (receiptWithLines == null) {
        LaunchedEffect(receiptId) { onBack() }
        return
    }
    var showRefundDialog by remember { mutableStateOf(false) }
    val r = receiptWithLines.receipt
    val sym = data.settings.currencySymbol
    val branch = data.branches.firstOrNull { it.id == r.branchId }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Receipt #${r.number.toString().padStart(4, '0')}", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = LoySurface),
            )
        },
        containerColor = LoyBackground,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            GroupCard {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(formatDateTime(r.createdAt), color = LoyTextSecondary, style = MaterialTheme.typography.bodySmall)
                        PillBadge(
                            text = if (r.isCompleted) "Completed" else "Refunded",
                            backgroundColor = if (r.isCompleted) LoyGreenLight else LoyErrorLight,
                            textColor = if (r.isCompleted) LoyGreen else LoyError,
                        )
                    }
                    if (branch != null) {
                        Text("Branch: ${branch.name}", style = MaterialTheme.typography.bodySmall, color = LoyTextSecondary)
                    }
                    if (!r.cashierName.isNullOrEmpty()) {
                        Text("Cashier: ${r.cashierName}", style = MaterialTheme.typography.bodySmall, color = LoyTextSecondary)
                    }
                    if (!r.customerName.isNullOrEmpty()) {
                        Text("Customer: ${r.customerName}", style = MaterialTheme.typography.bodySmall, color = LoyTextSecondary)
                    }
                    if (r.note.isNotEmpty()) {
                        Text("Note: ${r.note}", style = MaterialTheme.typography.bodySmall, color = LoyTextSecondary)
                    }
                }
            }

            GroupCard {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    receiptWithLines.lines.forEach { line ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(line.name, fontWeight = FontWeight.Medium)
                                Text(
                                    "${formatQty(line.qty)} × ${formatCents(line.priceCents, sym)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = LoyTextSecondary,
                                )
                            }
                            Text(
                                formatCents(line.lineTotalCents, sym),
                                style = TabularMoneyStyle.merge(MaterialTheme.typography.titleMedium),
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                    HorizontalDivider(color = LoyBorder)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Subtotal", color = LoyTextSecondary)
                        Text(formatCents(r.subtotalCents, sym), style = TabularMoneyStyle)
                    }
                    if (r.discountCents > 0L) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(r.discountLabel(), color = LoyGreen)
                            Text("-${formatCents(r.discountCents, sym)}", color = LoyGreen, style = TabularMoneyStyle)
                        }
                    }
                    if (r.taxCents > 0L) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Tax", color = LoyTextSecondary)
                            Text(formatCents(r.taxCents, sym), style = TabularMoneyStyle)
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Total", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text(
                            formatCents(r.totalCents, sym),
                            style = TabularMoneyStyle.merge(MaterialTheme.typography.titleLarge),
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Paid (${r.paymentType})", color = LoyTextSecondary)
                        Text(formatCents(r.paidCents, sym), style = TabularMoneyStyle)
                    }
                    if (r.changeCents > 0L) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Change", color = LoyTextSecondary)
                            Text(formatCents(r.changeCents, sym), style = TabularMoneyStyle)
                        }
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = { viewModel.shareReceiptText(receiptWithLines) },
                    modifier = Modifier.weight(1f).height(50.dp),
                ) {
                    Icon(Icons.Outlined.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Share")
                }
                OutlinedButton(
                    onClick = { viewModel.shareReceiptPdf(receiptWithLines) },
                    modifier = Modifier.weight(1f).height(50.dp),
                ) {
                    Icon(Icons.Outlined.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Receipt PDF")
                }
            }

            if (r.isCompleted) {
                Button(
                    onClick = { showRefundDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = LoyError, contentColor = Color.White),
                    modifier = Modifier.fillMaxWidth().height(50.dp).testTag("refund_receipt_button"),
                ) {
                    Text("Refund Receipt")
                }
            }
        }
    }

    if (showRefundDialog) {
        ConfirmDialog(
            title = "Refund receipt #${r.number.toString().padStart(4, '0')}?",
            message = "This marks the receipt as refunded and returns tracked items to inventory.",
            confirmLabel = "Refund",
            destructive = true,
            onConfirm = {
                viewModel.refundReceipt(r.id)
                showRefundDialog = false
            },
            onDismiss = { showRefundDialog = false },
        )
    }
}
