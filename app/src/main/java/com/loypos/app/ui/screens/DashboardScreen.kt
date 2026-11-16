package com.loypos.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.Assessment
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.PeopleOutline
import androidx.compose.material.icons.outlined.PointOfSale
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.loypos.app.core.formatCents
import com.loypos.app.core.formatDateTime
import com.loypos.app.core.startOfTodayMs
import com.loypos.app.data.local.DailySalesPoint
import com.loypos.app.ui.LoyPosDataState
import com.loypos.app.ui.LoyPosViewModel
import com.loypos.app.ui.UiSessionState
import com.loypos.app.ui.components.BranchPickerSheet
import com.loypos.app.ui.components.EmptyState
import com.loypos.app.ui.components.GroupCard
import com.loypos.app.ui.components.PillBadge
import com.loypos.app.ui.components.SectionHeader
import com.loypos.app.ui.components.StatBlock
import com.loypos.app.ui.components.TopSyncIconButton
import com.loypos.app.ui.theme.LoyBackground
import com.loypos.app.ui.theme.LoyBorder
import com.loypos.app.ui.theme.LoyError
import com.loypos.app.ui.theme.LoyErrorLight
import com.loypos.app.ui.theme.LoyGreen
import com.loypos.app.ui.theme.LoyGreenLight
import com.loypos.app.ui.theme.LoyInfo
import com.loypos.app.ui.theme.LoySurface
import com.loypos.app.ui.theme.LoyTextPrimary
import com.loypos.app.ui.theme.LoyTextSecondary
import com.loypos.app.ui.theme.LoyWarning
import com.loypos.app.ui.theme.TabularMoneyStyle
import java.util.Calendar

private data class QuickActionItem(
    val label: String,
    val icon: ImageVector,
    val tag: String,
    val onClick: () -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: LoyPosViewModel,
    session: UiSessionState,
    data: LoyPosDataState,
    onNavigateToCheckout: () -> Unit,
    onNavigateToInventory: () -> Unit,
    onNavigateToStock: () -> Unit,
    onNavigateToShift: () -> Unit,
    onNavigateToCustomers: () -> Unit,
    onNavigateToReports: () -> Unit,
    onNavigateToReceipts: () -> Unit,
    onNavigateToReceiptDetail: (String) -> Unit,
) {
    var showBranchSheet by remember { mutableStateOf(false) }

    val sym = data.settings.currencySymbol
    val branchId = session.activeBranchId
    val activeBranch = data.branches.firstOrNull { it.id == branchId }
    val isCashier = session.currentUser?.isCashier == true

    val todaySummary = remember(data.receipts, branchId) {
        viewModel.computeTodaySummary(data, branchId)
    }
    val avgTicketCents = if (todaySummary.salesCount == 0) 0L else todaySummary.netCents / todaySummary.salesCount

    val outOfStockCount = remember(data.items) {
        data.items.count { it.trackStock && it.stock <= 0.0 }
    }
    val lowStockCount = remember(data.items) {
        data.items.count { it.trackStock && it.stock > 0.0 && it.stock <= 3.0 }
    }

    val branchReceipts = remember(data.receipts, branchId) {
        data.receipts.filter { r ->
            branchId == null || r.receipt.branchId == null || r.receipt.branchId == branchId
        }
    }

    val last7Days = remember(branchReceipts) {
        val todayStart = startOfTodayMs()
        val cal = Calendar.getInstance()
        val dayLabels = arrayOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
        (6 downTo 0).map { offset ->
            val start = todayStart - offset * 86_400_000L
            val end = start + 86_400_000L
            cal.timeInMillis = start
            val label = dayLabels[cal.get(Calendar.DAY_OF_WEEK) - 1]
            val sum = branchReceipts
                .filter { it.receipt.isCompleted && it.receipt.createdAt in start until end }
                .sumOf { it.receipt.totalCents }
            DailySalesPoint(label, sum)
        }
    }

    val quickActions = remember(
        onNavigateToCheckout,
        onNavigateToInventory,
        onNavigateToShift,
        onNavigateToCustomers,
        onNavigateToReports,
    ) {
        listOf(
            QuickActionItem("Checkout", Icons.Outlined.PointOfSale, "dash_action_checkout", onNavigateToCheckout),
            QuickActionItem("Inventory", Icons.Outlined.Inventory2, "dash_action_inventory", onNavigateToInventory),
            QuickActionItem("Shift", Icons.Outlined.AccessTime, "dash_action_shift", onNavigateToShift),
            QuickActionItem("Customers", Icons.Outlined.PeopleOutline, "dash_action_customers", onNavigateToCustomers),
            QuickActionItem("Reports", Icons.Outlined.Assessment, "dash_action_reports", onNavigateToReports),
        )
    }

    Column(modifier = Modifier.fillMaxSize().background(LoyBackground)) {
        TopAppBar(
            colors = TopAppBarDefaults.topAppBarColors(containerColor = LoySurface),
            title = {
                Column {
                    Text(
                        text = data.settings.storeName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (activeBranch != null) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clickable(enabled = !isCashier && data.branches.size > 1) {
                                    showBranchSheet = true
                                }
                                .testTag("dashboard_branch_selector"),
                        ) {
                            Icon(
                                Icons.Outlined.Storefront,
                                contentDescription = "Active branch",
                                tint = LoyGreen,
                                modifier = Modifier.size(13.dp),
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = activeBranch.name,
                                style = MaterialTheme.typography.bodySmall,
                                color = LoyGreen,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                }
            },
            actions = {
                TopSyncIconButton(
                    syncing = session.syncing,
                    onClick = { viewModel.syncCloud() },
                )
            },
        )
        HorizontalDivider(color = LoyBorder)

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Today's Overview
            item {
                SectionHeader("Today's Overview")
                GroupCard {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            StatBlock(
                                label = "Net sales",
                                value = formatCents(todaySummary.netCents, sym),
                                valueColor = LoyGreen,
                                modifier = Modifier.weight(1f),
                            )
                            StatBlock(
                                label = "Sales / Refunds",
                                value = "${todaySummary.salesCount} / ${todaySummary.refundsCount}",
                                modifier = Modifier.weight(1f),
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            StatBlock(
                                label = "Avg ticket",
                                value = formatCents(avgTicketCents, sym),
                                modifier = Modifier.weight(1f),
                            )
                            StatBlock(
                                label = "Cash / Card",
                                value = "${formatCents(todaySummary.cashCents, sym)} / ${formatCents(todaySummary.cardCents, sym)}",
                                valueColor = LoyInfo,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        Button(
                            onClick = onNavigateToCheckout,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("dashboard_start_checkout_button"),
                        ) {
                            Icon(Icons.Outlined.PointOfSale, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Open Checkout Register", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            // Quick Actions
            item {
                SectionHeader("Quick Actions")
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(quickActions, key = { it.tag }) { action ->
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = LoySurface,
                            border = BorderStroke(1.dp, LoyBorder),
                            modifier = Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .clickable(onClick = action.onClick)
                                .testTag(action.tag),
                        ) {
                            Column(
                                modifier = Modifier
                                    .width(96.dp)
                                    .heightIn(min = 84.dp)
                                    .padding(horizontal = 10.dp, vertical = 12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(LoyGreenLight),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        imageVector = action.icon,
                                        contentDescription = action.label,
                                        tint = LoyGreen,
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    text = action.label,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = LoyTextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }

            // Inventory Alerts
            item {
                SectionHeader("Inventory Alerts")
                GroupCard {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (outOfStockCount + lowStockCount > 0) Color(0xFFFFF3E0) else LoyGreenLight),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = if (outOfStockCount + lowStockCount > 0) Icons.Outlined.WarningAmber else Icons.Outlined.Inventory2,
                                    contentDescription = "Inventory status",
                                    tint = if (outOfStockCount + lowStockCount > 0) LoyWarning else LoyGreen,
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${data.items.size} products · ${data.categories.size} categories",
                                    fontWeight = FontWeight.SemiBold,
                                    color = LoyTextPrimary,
                                )
                                Text(
                                    text = "$outOfStockCount out of stock · $lowStockCount low stock",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (outOfStockCount > 0) LoyError else LoyTextSecondary,
                                )
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = onNavigateToInventory,
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                                    .testTag("dashboard_manage_catalog_button"),
                            ) {
                                Text("Catalog", maxLines = 1)
                            }
                            OutlinedButton(
                                onClick = onNavigateToStock,
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                                    .testTag("dashboard_adjust_stock_button"),
                            ) {
                                Text("Adjust Stock", maxLines = 1)
                            }
                        }
                    }
                }
            }

            // Last 7 Days Sales Chart
            item {
                SectionHeader("Last 7 Days")
                GroupCard {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        val maxBar = (last7Days.maxOfOrNull { it.totalCents } ?: 1L).coerceAtLeast(1L)
                        Canvas(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(110.dp),
                        ) {
                            val barCount = last7Days.size
                            val slot = size.width / barCount
                            val barWidth = slot * 0.54f
                            last7Days.forEachIndexed { i, point ->
                                val frac = point.totalCents.toFloat() / maxBar.toFloat()
                                val barHeight = (size.height - 8f) * frac.coerceAtLeast(0.05f)
                                val left = i * slot + (slot - barWidth) / 2f
                                val top = size.height - barHeight
                                drawRoundRect(
                                    color = if (point.totalCents > 0L) LoyGreen else Color(0xFFE0E0E0),
                                    topLeft = Offset(left, top),
                                    size = Size(barWidth, barHeight),
                                    cornerRadius = CornerRadius(8f, 8f),
                                )
                            }
                        }
                        Row(Modifier.fillMaxWidth()) {
                            last7Days.forEach { pt ->
                                Text(
                                    text = pt.label,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = LoyTextSecondary,
                                    modifier = Modifier.weight(1f),
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }
                    }
                }
            }

            // Recent Sales
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SectionHeader("Recent Sales")
                    if (branchReceipts.isNotEmpty()) {
                        TextButton(
                            onClick = onNavigateToReceipts,
                            modifier = Modifier.heightIn(min = 48.dp),
                        ) {
                            Text("View all")
                        }
                    }
                }
                GroupCard {
                    val recent = branchReceipts.take(5)
                    if (recent.isEmpty()) {
                        EmptyState(
                            icon = Icons.Outlined.ReceiptLong,
                            title = "No sales recorded yet",
                            subtitle = "Ring up your first order in Checkout.",
                            actionLabel = "Go to Checkout",
                            onAction = onNavigateToCheckout,
                        )
                    } else {
                        Column {
                            recent.forEachIndexed { idx, item ->
                                if (idx > 0) HorizontalDivider(color = LoyBorder)
                                val r = item.receipt
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 56.dp)
                                        .clickable { onNavigateToReceiptDetail(r.id) }
                                        .padding(horizontal = 14.dp, vertical = 10.dp)
                                        .testTag("dashboard_recent_sale_${r.id}"),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        ) {
                                            Text(
                                                "#${r.number.toString().padStart(4, '0')}",
                                                fontWeight = FontWeight.SemiBold,
                                                color = LoyTextPrimary,
                                            )
                                            if (!r.isCompleted) {
                                                PillBadge(
                                                    text = "Refunded",
                                                    backgroundColor = LoyErrorLight,
                                                    textColor = LoyError,
                                                )
                                            }
                                        }
                                        Text(
                                            text = "${formatDateTime(r.createdAt)}${if (!r.customerName.isNullOrEmpty()) " · ${r.customerName}" else ""}",
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
                                    Spacer(Modifier.width(4.dp))
                                    Icon(
                                        Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                                        contentDescription = "Open receipt",
                                        tint = LoyTextSecondary,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showBranchSheet) {
        BranchPickerSheet(
            branches = data.branches,
            activeBranchId = session.activeBranchId,
            onSelect = { viewModel.selectBranch(it.id) },
            onDismiss = { showBranchSheet = false },
        )
    }
}
