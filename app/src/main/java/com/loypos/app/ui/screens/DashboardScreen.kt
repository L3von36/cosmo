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
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material.icons.outlined.Assessment
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.PeopleOutline
import androidx.compose.material.icons.outlined.PointOfSale
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.loypos.app.core.formatCents
import com.loypos.app.core.formatCentsShort
import com.loypos.app.core.formatDateTime
import com.loypos.app.data.local.DailyRevenuePoint
import com.loypos.app.ui.LoyPosDataState
import com.loypos.app.ui.LoyPosViewModel
import com.loypos.app.ui.UiSessionState
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberBottom
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberStart
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLine
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.common.fill
import com.patrykandpatrick.vico.core.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.core.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.core.cartesian.data.lineSeries
import com.patrykandpatrick.vico.core.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.core.common.shader.ShaderProvider
import kotlin.math.roundToLong
import com.loypos.app.ui.components.BranchPickerSheet
import com.loypos.app.ui.components.EmptyState
import com.loypos.app.ui.components.GroupCard
import com.loypos.app.ui.components.PillBadge
import com.loypos.app.ui.components.SectionHeader
import com.loypos.app.ui.components.StatBlock
import com.loypos.app.ui.components.TopSyncIconButton
import com.loypos.app.ui.theme.LocalLoyMetrics
import com.loypos.app.ui.theme.LocalLoyWindowSize
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
import com.loypos.app.ui.theme.accessibleTouchTarget

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

    val lowThreshold = data.settings.lowStockThreshold
    val outOfStockCount = remember(data.items) {
        data.items.count { it.trackStock && it.stock <= 0.0 }
    }
    val lowStockCount = remember(data.items, lowThreshold) {
        data.items.count { it.trackStock && it.stock > 0.0 && it.stock < lowThreshold }
    }

    val branchReceipts = remember(data.receipts, branchId) {
        data.receipts.filter { r ->
            branchId == null || r.receipt.branchId == null || r.receipt.branchId == branchId
        }
    }

    val trendDays by viewModel.revenueTrendDays.collectAsStateWithLifecycle()
    val revenueTrend by viewModel.dailyRevenueTrend.collectAsStateWithLifecycle()

    val quickActions = remember(
        viewModel,
        onNavigateToCheckout,
        onNavigateToInventory,
        onNavigateToShift,
        onNavigateToCustomers,
        onNavigateToReports,
    ) {
        listOf(
            QuickActionItem("Checkout", Icons.Outlined.PointOfSale, "dash_action_checkout", onNavigateToCheckout),
            QuickActionItem("Back Office", Icons.Outlined.AdminPanelSettings, "dash_action_backoffice") {
                viewModel.openBackOffice()
            },
            QuickActionItem("Inventory", Icons.Outlined.Inventory2, "dash_action_inventory", onNavigateToInventory),
            QuickActionItem("Shift", Icons.Outlined.AccessTime, "dash_action_shift", onNavigateToShift),
            QuickActionItem("Customers", Icons.Outlined.PeopleOutline, "dash_action_customers", onNavigateToCustomers),
            QuickActionItem("Reports", Icons.Outlined.Assessment, "dash_action_reports", onNavigateToReports),
        )
    }

    val windowSize = LocalLoyWindowSize.current
    val metrics = LocalLoyMetrics.current

    Column(modifier = Modifier.fillMaxSize().background(LoyBackground)) {
        TopAppBar(
            expandedHeight = metrics.topBarHeight,
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
                                modifier = Modifier.size(12.dp),
                            )
                            Spacer(Modifier.width(3.dp))
                            Text(
                                text = activeBranch.name,
                                style = MaterialTheme.typography.labelSmall,
                                color = LoyGreen,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                }
            },
            actions = {
                IconButton(
                    onClick = { viewModel.openBackOffice() },
                    modifier = Modifier
                        .accessibleTouchTarget(metrics.minTouchTarget)
                        .testTag("dashboard_open_backoffice_button"),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.AdminPanelSettings,
                        contentDescription = "Open Back Office",
                        tint = LoyGreen,
                        modifier = Modifier.size(19.dp),
                    )
                }
                TopSyncIconButton(
                    syncing = session.syncing,
                    onClick = { viewModel.syncCloud() },
                )
            },
        )
        HorizontalDivider(color = LoyBorder)

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(metrics.screenPadding),
            verticalArrangement = Arrangement.spacedBy(metrics.sectionGap),
        ) {
            // Today's Overview
            item {
                SectionHeader("Today's Overview")
                GroupCard {
                    Column(
                        modifier = Modifier.padding(metrics.cardPadding),
                        verticalArrangement = Arrangement.spacedBy(metrics.itemGap),
                    ) {
                        if (windowSize.isCompact) {
                            Row(horizontalArrangement = Arrangement.spacedBy(metrics.itemGap)) {
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
                            Row(horizontalArrangement = Arrangement.spacedBy(metrics.itemGap)) {
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
                        } else {
                            Row(horizontalArrangement = Arrangement.spacedBy(metrics.itemGap)) {
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
                        }
                        Button(
                            onClick = onNavigateToCheckout,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = metrics.minTouchTarget)
                                .testTag("dashboard_start_checkout_button"),
                        ) {
                            Icon(Icons.Outlined.PointOfSale, contentDescription = null, modifier = Modifier.size(17.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Open Checkout Register", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            // Quick Actions
            item {
                SectionHeader("Quick Actions")
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(quickActions, key = { it.tag }) { action ->
                        Surface(
                            shape = RoundedCornerShape(11.dp),
                            color = LoySurface,
                            border = BorderStroke(1.dp, LoyBorder),
                            modifier = Modifier
                                .clip(RoundedCornerShape(11.dp))
                                .clickable(onClick = action.onClick)
                                .testTag(action.tag),
                        ) {
                            Column(
                                modifier = Modifier
                                    .width(76.dp)
                                    .heightIn(min = 66.dp)
                                    .padding(horizontal = 6.dp, vertical = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(30.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(LoyGreenLight),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        imageVector = action.icon,
                                        contentDescription = action.label,
                                        tint = LoyGreen,
                                        modifier = Modifier.size(17.dp),
                                    )
                                }
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = action.label,
                                    style = MaterialTheme.typography.labelSmall,
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

            // Inventory Alerts + Trend Card (side-by-side on Expanded screens)
            if (windowSize.isExpanded) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(metrics.sectionGap),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            SectionHeader("Daily Revenue Trend")
                            DailyRevenueTrendCard(
                                points = revenueTrend,
                                selectedDays = trendDays,
                                currencySymbol = sym,
                                onSelectDays = viewModel::setRevenueTrendDays,
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            SectionHeader("Inventory Alerts")
                            GroupCard {
                                Column(
                                    modifier = Modifier.padding(metrics.cardPadding),
                                    verticalArrangement = Arrangement.spacedBy(metrics.itemGap),
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(34.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(if (outOfStockCount + lowStockCount > 0) Color(0xFFFFF3E0) else LoyGreenLight),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Icon(
                                                imageVector = if (outOfStockCount + lowStockCount > 0) Icons.Outlined.WarningAmber else Icons.Outlined.Inventory2,
                                                contentDescription = "Inventory status",
                                                tint = if (outOfStockCount + lowStockCount > 0) LoyWarning else LoyGreen,
                                                modifier = Modifier.size(18.dp),
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
                                                .heightIn(min = metrics.minTouchTarget)
                                                .testTag("dashboard_manage_catalog_button"),
                                        ) {
                                            Text("Catalog", maxLines = 1)
                                        }
                                        OutlinedButton(
                                            onClick = onNavigateToStock,
                                            modifier = Modifier
                                                .weight(1f)
                                                .heightIn(min = metrics.minTouchTarget)
                                                .testTag("dashboard_adjust_stock_button"),
                                        ) {
                                            Text("Adjust Stock", maxLines = 1)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // Inventory Alerts
                item {
                    SectionHeader("Inventory Alerts")
                    GroupCard {
                        Column(
                            modifier = Modifier.padding(metrics.cardPadding),
                            verticalArrangement = Arrangement.spacedBy(metrics.itemGap),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (outOfStockCount + lowStockCount > 0) Color(0xFFFFF3E0) else LoyGreenLight),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        imageVector = if (outOfStockCount + lowStockCount > 0) Icons.Outlined.WarningAmber else Icons.Outlined.Inventory2,
                                        contentDescription = "Inventory status",
                                        tint = if (outOfStockCount + lowStockCount > 0) LoyWarning else LoyGreen,
                                        modifier = Modifier.size(18.dp),
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
                                        .heightIn(min = metrics.minTouchTarget)
                                        .testTag("dashboard_manage_catalog_button"),
                                ) {
                                    Text("Catalog", maxLines = 1)
                                }
                                OutlinedButton(
                                    onClick = onNavigateToStock,
                                    modifier = Modifier
                                        .weight(1f)
                                        .heightIn(min = metrics.minTouchTarget)
                                        .testTag("dashboard_adjust_stock_button"),
                                ) {
                                    Text("Adjust Stock", maxLines = 1)
                                }
                            }
                        }
                    }
                }

                // Daily Revenue Trend Chart (Vico powered by Room SaleEntity)
                item {
                    SectionHeader("Daily Revenue Trend")
                    DailyRevenueTrendCard(
                        points = revenueTrend,
                        selectedDays = trendDays,
                        currencySymbol = sym,
                        onSelectDays = viewModel::setRevenueTrendDays,
                    )
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
                            modifier = Modifier.heightIn(min = metrics.minTouchTarget),
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
                                        .heightIn(min = 46.dp)
                                        .clickable { onNavigateToReceiptDetail(r.id) }
                                        .padding(horizontal = 12.dp, vertical = 8.dp)
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
                                        modifier = Modifier.size(18.dp),
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

@Composable
private fun DailyRevenueTrendCard(
    points: List<DailyRevenuePoint>,
    selectedDays: Int,
    currencySymbol: String,
    onSelectDays: (Int) -> Unit,
) {
    val totalWindowCents = remember(points) { points.sumOf { it.totalCents } }
    val totalOrders = remember(points) { points.sumOf { it.salesCount } }
    val avgDailyCents = if (points.isEmpty()) 0L else totalWindowCents / points.size

    val modelProducer = remember { CartesianChartModelProducer() }
    LaunchedEffect(points) {
        if (points.isNotEmpty()) {
            modelProducer.runTransaction {
                lineSeries {
                    series(points.map { it.revenueUnits })
                }
            }
        }
    }

    val startAxisFormatter = remember(currencySymbol) {
        CartesianValueFormatter { _, value, _ ->
            formatCentsShort((value * 100.0).roundToLong(), currencySymbol)
        }
    }
    val bottomAxisFormatter = remember(points) {
        CartesianValueFormatter { _, value, _ ->
            points.getOrNull(value.toInt())?.label ?: ""
        }
    }

    GroupCard(modifier = Modifier.testTag("dashboard_revenue_trend_card")) {
        Column(
            modifier = Modifier.padding(11.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Last $selectedDays days",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = LoyTextPrimary,
                    )
                    Text(
                        text = "$totalOrders completed ${if (totalOrders == 1) "sale" else "sales"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = LoyTextSecondary,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    listOf(7, 14, 30).forEach { days ->
                        FilterChip(
                            selected = selectedDays == days,
                            onClick = { onSelectDays(days) },
                            label = {
                                Text(
                                    text = "${days}D",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = LoyGreenLight,
                                selectedLabelColor = LoyGreen,
                            ),
                            modifier = Modifier.height(28.dp).testTag("dashboard_trend_${days}d"),
                        )
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                StatBlock(
                    label = "${selectedDays}D Revenue",
                    value = formatCents(totalWindowCents, currencySymbol),
                    valueColor = LoyGreen,
                    modifier = Modifier.weight(1f),
                )
                StatBlock(
                    label = "Daily Average",
                    value = formatCents(avgDailyCents, currencySymbol),
                    valueColor = LoyInfo,
                    modifier = Modifier.weight(1f),
                )
            }

            if (points.isNotEmpty()) {
                CartesianChartHost(
                    chart = rememberCartesianChart(
                        rememberLineCartesianLayer(
                            lineProvider = LineCartesianLayer.LineProvider.series(
                                LineCartesianLayer.rememberLine(
                                    fill = LineCartesianLayer.LineFill.single(fill(LoyGreen)),
                                    areaFill = LineCartesianLayer.AreaFill.single(
                                        fill(
                                            ShaderProvider.verticalGradient(
                                                LoyGreen.copy(alpha = 0.28f).toArgb(),
                                                Color.Transparent.toArgb(),
                                            )
                                        )
                                    ),
                                    pointConnector = LineCartesianLayer.PointConnector.cubic(0.4f),
                                )
                            )
                        ),
                        startAxis = VerticalAxis.rememberStart(
                            valueFormatter = startAxisFormatter,
                        ),
                        bottomAxis = HorizontalAxis.rememberBottom(
                            valueFormatter = bottomAxisFormatter,
                        ),
                    ),
                    modelProducer = modelProducer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(148.dp)
                        .testTag("dashboard_revenue_chart"),
                )
            }

            if (totalWindowCents == 0L) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(LoyGreenLight.copy(alpha = 0.55f))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.TrendingUp,
                        contentDescription = null,
                        tint = LoyGreen,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = "No completed sales in this period yet. Complete an order in Checkout to populate the daily revenue trend.",
                        style = MaterialTheme.typography.bodySmall,
                        color = LoyTextSecondary,
                    )
                }
            }
        }
    }
}
