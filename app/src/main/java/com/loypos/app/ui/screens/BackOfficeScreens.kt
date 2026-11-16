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
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.PeopleOutline
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.loypos.app.core.formatCents
import com.loypos.app.core.formatDateTime
import com.loypos.app.core.formatQty
import com.loypos.app.core.newId
import com.loypos.app.core.nowMs
import com.loypos.app.core.parseQty
import com.loypos.app.core.startOfTodayMs
import com.loypos.app.data.local.CategoryReportRow
import com.loypos.app.data.local.CustomerEntity
import com.loypos.app.data.local.DailySalesPoint
import com.loypos.app.data.local.ItemEntity
import com.loypos.app.data.local.TopItemReportRow
import com.loypos.app.ui.LoyPosDataState
import com.loypos.app.ui.LoyPosViewModel
import com.loypos.app.ui.components.CurrencyNumPad
import com.loypos.app.ui.components.EmptyState
import com.loypos.app.ui.components.GroupCard
import com.loypos.app.ui.components.ItemThumbnail
import com.loypos.app.ui.components.PillBadge
import com.loypos.app.ui.components.SectionHeader
import com.loypos.app.ui.components.StatBlock
import com.loypos.app.ui.components.StockDotBadge
import com.loypos.app.ui.theme.LoyBackground
import com.loypos.app.ui.theme.LoyBorder
import com.loypos.app.ui.theme.LoyError
import com.loypos.app.ui.theme.LoyGreen
import com.loypos.app.ui.theme.LoyGreenLight
import com.loypos.app.ui.theme.LoyInfo
import com.loypos.app.ui.theme.LoySurface
import com.loypos.app.ui.theme.LoySurfaceVariant
import com.loypos.app.ui.theme.LoyTextPrimary
import com.loypos.app.ui.theme.LoyTextSecondary
import com.loypos.app.ui.theme.LoyWarning
import com.loypos.app.ui.theme.TabularMoneyStyle
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StockScreen(
    viewModel: LoyPosViewModel,
    data: LoyPosDataState,
    onBack: () -> Unit,
) {
    var search by remember { mutableStateOf("") }
    var selectedItem by remember { mutableStateOf<ItemEntity?>(null) }

    val trackedItems = remember(data.items, search) {
        val q = search.trim().lowercase()
        data.items
            .filter { it.trackStock && (q.isEmpty() || it.name.lowercase().contains(q) || it.sku.lowercase().contains(q)) }
            .sortedWith(compareBy<ItemEntity> {
                when {
                    it.stock <= 0.0 -> 0
                    it.stock <= 3.0 -> 1
                    else -> 2
                }
            }.thenBy { it.name })
    }
    val itemNameById = remember(data.items) { data.items.associate { it.id to it.name } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Inventory & Stock", fontWeight = FontWeight.Bold) },
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
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                placeholder = { Text("Search tracked items") },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(LoySurface)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            )
            HorizontalDivider(color = LoyBorder)

            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(trackedItems, key = { it.id }) { item ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = LoySurface,
                        border = BorderStroke(1.dp, LoyBorder),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { selectedItem = item },
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            ItemThumbnail(item = item, size = 40.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(item.name, fontWeight = FontWeight.SemiBold)
                                StockDotBadge(item = item)
                            }
                            PillBadge(
                                text = formatQty(item.stock),
                                backgroundColor = when {
                                    item.stock <= 0.0 -> Color(0xFFFFEBEE)
                                    item.stock <= 3.0 -> Color(0xFFFFF3E0)
                                    else -> LoyGreenLight
                                },
                                textColor = when {
                                    item.stock <= 0.0 -> LoyError
                                    item.stock <= 3.0 -> LoyWarning
                                    else -> LoyGreen
                                },
                            )
                        }
                    }
                }

                if (data.movements.isNotEmpty()) {
                    item { SectionHeader("Recent Stock Movements") }
                    items(data.movements.take(25), key = { it.id }) { mv ->
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = LoySurface,
                            border = BorderStroke(1.dp, LoyBorder),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(itemNameById[mv.itemId] ?: "Item", fontWeight = FontWeight.Medium)
                                    Text(
                                        "${mv.type.replaceFirstChar { it.uppercase() }} · ${formatDateTime(mv.createdAt)}${if (mv.note.isNotEmpty()) " · ${mv.note}" else ""}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = LoyTextSecondary,
                                    )
                                }
                                Text(
                                    text = "${if (mv.qty > 0) "+" else ""}${formatQty(mv.qty)}",
                                    style = TabularMoneyStyle,
                                    fontWeight = FontWeight.Bold,
                                    color = if (mv.qty >= 0) LoyGreen else LoyError,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    selectedItem?.let { item ->
        StockAdjustBottomSheet(
            item = item,
            onReceive = { delta, note ->
                viewModel.receiveStock(item.id, delta, note)
                selectedItem = null
            },
            onSetExact = { count, note ->
                viewModel.setStockCount(item.id, count, note)
                selectedItem = null
            },
            onDismiss = { selectedItem = null },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StockAdjustBottomSheet(
    item: ItemEntity,
    onReceive: (Double, String) -> Unit,
    onSetExact: (Double, String) -> Unit,
    onDismiss: () -> Unit,
) {
    var mode by remember { mutableStateOf("receive") }
    var qtyText by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }

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
            Text(item.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("Current stock: ${formatQty(item.stock)}", color = LoyTextSecondary)

            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = mode == "receive",
                    onClick = { mode = "receive"; qtyText = "" },
                    shape = SegmentedButtonDefaults.itemShape(0, 2),
                ) { Text("Receive (+)") }
                SegmentedButton(
                    selected = mode == "set",
                    onClick = { mode = "set"; qtyText = formatQty(item.stock) },
                    shape = SegmentedButtonDefaults.itemShape(1, 2),
                ) { Text("Set count") }
            }

            Surface(color = LoySurfaceVariant, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = if (mode == "receive") "+${qtyText.ifEmpty { "0" }}" else qtyText.ifEmpty { "0" },
                    style = TabularMoneyStyle.merge(MaterialTheme.typography.headlineSmall),
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(14.dp),
                )
            }

            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text("Note (optional)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            CurrencyNumPad(
                onKey = { d ->
                    if (d == "." && qtyText.contains(".")) return@CurrencyNumPad
                    qtyText += d
                },
                onBackspace = { if (qtyText.isNotEmpty()) qtyText = qtyText.dropLast(1) },
                onClear = { qtyText = "" },
                allowDecimal = true,
            )

            Button(
                onClick = {
                    val v = parseQty(qtyText) ?: 0.0
                    if (mode == "receive") {
                        if (v > 0.0) onReceive(v, note)
                    } else {
                        onSetExact(v, note)
                    }
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                Text(if (mode == "receive") "Add to stock" else "Save stock count")
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomersScreen(
    viewModel: LoyPosViewModel,
    data: LoyPosDataState,
    onBack: () -> Unit,
) {
    var search by remember { mutableStateOf("") }
    var editingCustomer by remember { mutableStateOf<CustomerEntity?>(null) }
    var isNew by remember { mutableStateOf(false) }

    val filtered = remember(data.customers, search) {
        val q = search.trim().lowercase()
        if (q.isEmpty()) data.customers else data.customers.filter {
            it.name.lowercase().contains(q) || it.phone.lowercase().contains(q) || it.email.lowercase().contains(q)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Customers (${data.customers.size})", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = LoySurface),
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    isNew = true
                    editingCustomer = CustomerEntity(id = newId(), name = "", createdAt = nowMs())
                },
                containerColor = LoyGreen,
                contentColor = Color.White,
            ) {
                Icon(Icons.Outlined.Add, contentDescription = "Add customer")
            }
        },
        containerColor = LoyBackground,
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                placeholder = { Text("Search name, phone or email") },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(LoySurface)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            )
            HorizontalDivider(color = LoyBorder)

            if (filtered.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    EmptyState(
                        icon = Icons.Outlined.PeopleOutline,
                        title = "No customers yet",
                        subtitle = "Tap + to add a customer and track loyalty points.",
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(14.dp, 12.dp, 14.dp, 88.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(filtered, key = { it.id }) { cust ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = LoySurface,
                            border = BorderStroke(1.dp, LoyBorder),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    isNew = false
                                    editingCustomer = cust
                                },
                        ) {
                            Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Outlined.PersonOutline, contentDescription = null, tint = LoyGreen)
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(cust.name, fontWeight = FontWeight.SemiBold)
                                    val sub = listOf(cust.phone, cust.email).filter { it.isNotEmpty() }.joinToString(" · ")
                                    if (sub.isNotEmpty()) {
                                        Text(sub, style = MaterialTheme.typography.bodySmall, color = LoyTextSecondary)
                                    }
                                }
                                PillBadge("${cust.points} pts")
                            }
                        }
                    }
                }
            }
        }
    }

    editingCustomer?.let { cust ->
        CustomerEditorSheet(
            customer = cust,
            isNew = isNew,
            onSave = {
                viewModel.saveCustomer(it)
                editingCustomer = null
            },
            onDelete = {
                viewModel.deleteCustomer(cust.id)
                editingCustomer = null
            },
            onDismiss = { editingCustomer = null },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CustomerEditorSheet(
    customer: CustomerEntity,
    isNew: Boolean,
    onSave: (CustomerEntity) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(customer.name) }
    var phone by remember { mutableStateOf(customer.phone) }
    var email by remember { mutableStateOf(customer.email) }
    var note by remember { mutableStateOf(customer.note) }

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
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(if (isNew) "New Customer" else "Edit Customer", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                if (!isNew) {
                    IconButton(onClick = onDelete, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Outlined.DeleteOutline, contentDescription = "Delete customer", tint = LoyError)
                    }
                }
            }
            OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name *") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text("Phone") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("Email") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text("Note") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Button(
                onClick = {
                    if (name.trim().isNotEmpty()) {
                        onSave(customer.copy(name = name.trim(), phone = phone.trim(), email = email.trim(), note = note.trim()))
                    }
                },
                enabled = name.trim().isNotEmpty(),
                modifier = Modifier.fillMaxWidth().height(50.dp),
            ) {
                Text("Save customer")
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(
    viewModel: LoyPosViewModel,
    data: LoyPosDataState,
    onBack: () -> Unit,
) {
    var periodIdx by remember { mutableIntStateOf(1) } // 0=Today, 1=7d, 2=30d, 3=All
    val sym = data.settings.currencySymbol

    val sinceMs = remember(periodIdx) {
        val today = startOfTodayMs()
        when (periodIdx) {
            0 -> today
            1 -> today - 6L * 86_400_000L
            2 -> today - 29L * 86_400_000L
            else -> 0L
        }
    }

    val filteredReceipts = remember(data.receipts, sinceMs) {
        data.receipts.filter { it.receipt.createdAt >= sinceMs }
    }
    val completed = remember(filteredReceipts) { filteredReceipts.filter { it.receipt.isCompleted } }
    val refunded = remember(filteredReceipts) { filteredReceipts.filter { !it.receipt.isCompleted } }

    val grossCents = completed.sumOf { it.receipt.subtotalCents }
    val discountCents = completed.sumOf { it.receipt.discountCents }
    val netCents = completed.sumOf { it.receipt.totalCents }
    val taxCents = completed.sumOf { it.receipt.taxCents }
    val avgTicketCents = if (completed.isEmpty()) 0L else netCents / completed.size
    val cashCents = completed.filter { it.receipt.paymentType != "card" }.sumOf { it.receipt.totalCents }
    val cardCents = completed.filter { it.receipt.paymentType == "card" }.sumOf { it.receipt.totalCents }

    val itemById = remember(data.items) { data.items.associateBy { it.id } }
    val catById = remember(data.categories) { data.categories.associateBy { it.id } }

    val categoryRows = remember(completed, itemById, catById) {
        val totals = mutableMapOf<String, Pair<Long, Double>>()
        for (rwl in completed) {
            for (line in rwl.lines) {
                val catId = itemById[line.itemId]?.categoryId
                val catName = catById[catId]?.name ?: "Other"
                val cur = totals[catName] ?: (0L to 0.0)
                totals[catName] = (cur.first + line.lineTotalCents) to (cur.second + line.qty)
            }
        }
        totals.entries.map { CategoryReportRow(it.key, it.value.first, it.value.second) }
            .sortedByDescending { it.totalCents }
    }

    val topItems = remember(completed) {
        val map = mutableMapOf<String, Pair<Long, Double>>()
        for (rwl in completed) {
            for (line in rwl.lines) {
                val cur = map[line.name] ?: (0L to 0.0)
                map[line.name] = (cur.first + line.lineTotalCents) to (cur.second + line.qty)
            }
        }
        map.entries.map { TopItemReportRow(it.key, it.value.first, it.value.second) }
            .sortedByDescending { it.totalCents }
            .take(8)
    }

    // 7-day daily sales series for Bar Chart
    val last7Days = remember(data.receipts) {
        val todayStart = startOfTodayMs()
        val cal = Calendar.getInstance()
        val dayLabels = arrayOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
        (6 downTo 0).map { offset ->
            val start = todayStart - offset * 86_400_000L
            val end = start + 86_400_000L
            cal.timeInMillis = start
            val label = dayLabels[cal.get(Calendar.DAY_OF_WEEK) - 1]
            val sum = data.receipts
                .filter { it.receipt.isCompleted && it.receipt.createdAt in start until end }
                .sumOf { it.receipt.totalCents }
            DailySalesPoint(label, sum)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sales Reports", fontWeight = FontWeight.Bold) },
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
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Today", "7 days", "30 days", "All").forEachIndexed { idx, label ->
                    FilterChip(
                        selected = periodIdx == idx,
                        onClick = { periodIdx = idx },
                        label = { Text(label) },
                    )
                }
            }

            GroupCard {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatBlock("Net sales", formatCents(netCents, sym), LoyGreen, Modifier.weight(1f))
                        StatBlock("Gross sales", formatCents(grossCents, sym), LoyTextPrimary, Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatBlock("Discounts", formatCents(discountCents, sym), LoyTextPrimary, Modifier.weight(1f))
                        StatBlock("Tax", formatCents(taxCents, sym), LoyTextPrimary, Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatBlock("Receipts / Refunds", "${completed.size} / ${refunded.size}", LoyTextPrimary, Modifier.weight(1f))
                        StatBlock("Avg ticket", formatCents(avgTicketCents, sym), LoyTextPrimary, Modifier.weight(1f))
                    }
                }
            }

            // Last 7 days bar chart
            SectionHeader("Last 7 Days")
            GroupCard {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    val maxBar = (last7Days.maxOfOrNull { it.totalCents } ?: 1L).coerceAtLeast(1L)
                    Canvas(modifier = Modifier.fillMaxWidth().height(120.dp)) {
                        val barCount = last7Days.size
                        val slot = size.width / barCount
                        val barWidth = slot * 0.54f
                        last7Days.forEachIndexed { i, point ->
                            val frac = point.totalCents.toFloat() / maxBar.toFloat()
                            val barHeight = (size.height - 8f) * frac.coerceAtLeast(0.04f)
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
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            )
                        }
                    }
                }
            }

            // Cash vs Card
            SectionHeader("Payment Methods")
            GroupCard {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    val totalPay = (cashCents + cardCents).coerceAtLeast(1L)
                    val cashFrac = cashCents.toFloat() / totalPay.toFloat()
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Cash: ${formatCents(cashCents, sym)}", fontWeight = FontWeight.Medium)
                        Text("Card: ${formatCents(cardCents, sym)}", fontWeight = FontWeight.Medium, color = LoyInfo)
                    }
                    LinearProgressIndicator(
                        progress = { if (cashCents + cardCents == 0L) 0f else cashFrac },
                        modifier = Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(999.dp)),
                        color = LoyGreen,
                        trackColor = if (cardCents > 0L) LoyInfo else LoySurfaceVariant,
                    )
                }
            }

            // Category share
            if (categoryRows.isNotEmpty()) {
                SectionHeader("Sales by Category")
                GroupCard {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        val maxCat = categoryRows.maxOf { it.totalCents }.coerceAtLeast(1L)
                        categoryRows.forEach { row ->
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(row.categoryName, fontWeight = FontWeight.Medium)
                                    Text(formatCents(row.totalCents, sym), style = TabularMoneyStyle, fontWeight = FontWeight.SemiBold)
                                }
                                LinearProgressIndicator(
                                    progress = { row.totalCents.toFloat() / maxCat.toFloat() },
                                    modifier = Modifier.fillMaxWidth().height(7.dp).clip(RoundedCornerShape(999.dp)),
                                    color = LoyGreen,
                                    trackColor = LoySurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }

            // Top items
            if (topItems.isNotEmpty()) {
                SectionHeader("Top Selling Items")
                GroupCard {
                    Column {
                        topItems.forEachIndexed { idx, row ->
                            if (idx > 0) HorizontalDivider(color = LoyBorder)
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text("${idx + 1}.", color = LoyTextSecondary, modifier = Modifier.width(26.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(row.itemName, fontWeight = FontWeight.Medium)
                                    Text("${formatQty(row.qty)} sold", style = MaterialTheme.typography.bodySmall, color = LoyTextSecondary)
                                }
                                Text(formatCents(row.totalCents, sym), style = TabularMoneyStyle, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}
