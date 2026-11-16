package com.loypos.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.NoteAdd
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.LocalOffer
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.loypos.app.core.formatCents
import com.loypos.app.core.formatQty
import com.loypos.app.core.newId
import com.loypos.app.core.nowMs
import com.loypos.app.data.local.CartLine
import com.loypos.app.data.local.CustomerEntity
import com.loypos.app.data.local.ItemEntity
import com.loypos.app.ui.CartSessionState
import com.loypos.app.ui.LoyPosDataState
import com.loypos.app.ui.LoyPosViewModel
import com.loypos.app.ui.TransactionViewModel
import com.loypos.app.ui.UiSessionState
import com.loypos.app.ui.components.BranchPickerSheet
import com.loypos.app.ui.components.CustomerPickerSheet
import com.loypos.app.ui.components.DiscountBottomSheet
import com.loypos.app.ui.components.EmptyState
import com.loypos.app.ui.components.ItemThumbnail
import com.loypos.app.ui.components.LineEditorBottomSheet
import com.loypos.app.ui.components.PillBadge
import com.loypos.app.ui.components.StockDotBadge
import com.loypos.app.ui.components.TicketsBottomSheet
import com.loypos.app.ui.components.TopSyncIconButton
import com.loypos.app.ui.theme.LoyBackground
import com.loypos.app.ui.theme.LoyBorder
import com.loypos.app.ui.theme.LoyError
import com.loypos.app.ui.theme.LoyGreen
import com.loypos.app.ui.theme.LoyGreenLight
import com.loypos.app.ui.theme.LoySurface
import com.loypos.app.ui.theme.LoySurfaceVariant
import com.loypos.app.ui.theme.LoyTextPrimary
import com.loypos.app.ui.theme.LoyTextSecondary
import com.loypos.app.ui.theme.TabularMoneyStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SellScreen(
    viewModel: LoyPosViewModel,
    transactionViewModel: TransactionViewModel,
    session: UiSessionState,
    data: LoyPosDataState,
    onNavigateToCart: () -> Unit,
    onNavigateToPayment: () -> Unit,
) {
    var searchOpen by remember { mutableStateOf(false) }
    var showBranchSheet by remember { mutableStateOf(false) }
    var showTicketsSheet by remember { mutableStateOf(false) }
    var editingLine by remember { mutableStateOf<CartLine?>(null) }
    var showDiscountSheet by remember { mutableStateOf(false) }
    var showCustomerSheet by remember { mutableStateOf(false) }
    var showSaveTicketDialog by remember { mutableStateOf(false) }
    var showNoteDialog by remember { mutableStateOf(false) }

    val cartSession by transactionViewModel.cartSession.collectAsStateWithLifecycle()
    val totals by transactionViewModel.cartTotals.collectAsStateWithLifecycle()

    val activeBranch = data.branches.firstOrNull { it.id == session.activeBranchId }
    val branchTickets = remember(data.tickets, session.activeBranchId) {
        data.tickets.filter { it.ticket.branchId == null || it.ticket.branchId == session.activeBranchId }
    }
    val filteredItems = remember(data.items, session.activeBranchId, session.sellCategoryId, session.sellSearchQuery) {
        val q = session.sellSearchQuery.trim().lowercase()
        data.items.filter { item ->
            item.availableInBranch(session.activeBranchId) &&
                (session.sellCategoryId == null || item.categoryId == session.sellCategoryId) &&
                (q.isEmpty() ||
                    item.name.lowercase().contains(q) ||
                    item.sku.lowercase().contains(q) ||
                    item.barcode.lowercase().contains(q))
        }
    }
    val sym = data.settings.currencySymbol
    val isCashier = session.currentUser?.isCashier == true

    Column(modifier = Modifier.fillMaxSize().background(LoyBackground)) {
        TopAppBar(
            colors = TopAppBarDefaults.topAppBarColors(containerColor = LoySurface),
            title = {
                if (searchOpen) {
                    OutlinedTextField(
                        value = session.sellSearchQuery,
                        onValueChange = { viewModel.setSellSearch(it) },
                        placeholder = { Text("Search name, SKU or barcode") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("sell_search_input"),
                    )
                } else {
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
                                    .testTag("sell_branch_selector"),
                            ) {
                                Icon(
                                    Icons.Outlined.Storefront,
                                    contentDescription = null,
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
                }
            },
            actions = {
                IconButton(
                    onClick = {
                        if (searchOpen) {
                            searchOpen = false
                            viewModel.setSellSearch("")
                        } else {
                            searchOpen = true
                        }
                    },
                    modifier = Modifier.size(48.dp).testTag("sell_search_toggle"),
                ) {
                    Icon(
                        if (searchOpen) Icons.Outlined.Close else Icons.Outlined.Search,
                        contentDescription = if (searchOpen) "Close search" else "Search catalog",
                    )
                }
                TopSyncIconButton(
                    syncing = session.syncing,
                    onClick = { viewModel.syncCloud() },
                )
            },
        )
        HorizontalDivider(color = LoyBorder)

        // Ticket Tabs Bar
        Surface(color = LoySurface) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                item {
                    val activeSelected = cartSession.openTicketId == null
                    FilterChip(
                        selected = activeSelected,
                        onClick = { if (!activeSelected) transactionViewModel.clearCart() },
                        label = { Text("Sale${if (cartSession.items.isNotEmpty() && activeSelected) " (${cartSession.items.size})" else ""}") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = LoyGreenLight,
                            selectedLabelColor = LoyGreen,
                        ),
                    )
                }
                items(branchTickets, key = { it.ticket.id }) { t ->
                    val isSel = cartSession.openTicketId == t.ticket.id
                    FilterChip(
                        selected = isSel,
                        onClick = { transactionViewModel.loadTicket(t) },
                        label = { Text("${t.ticket.name.ifEmpty { "Ticket" }} (${t.lines.size})") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = LoyGreenLight,
                            selectedLabelColor = LoyGreen,
                        ),
                    )
                }
                item {
                    OutlinedButton(
                        onClick = { showTicketsSheet = true },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.height(34.dp).testTag("open_tickets_button"),
                    ) {
                        Icon(Icons.Outlined.ReceiptLong, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Tickets (${branchTickets.size})", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
        HorizontalDivider(color = LoyBorder)

        BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth()) {
            val isWide = maxWidth >= 860.dp

            Row(modifier = Modifier.fillMaxSize()) {
                Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    // Category Filter Row
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        item {
                            FilterChip(
                                selected = session.sellCategoryId == null,
                                onClick = { viewModel.setSellCategory(null) },
                                label = { Text("All (${data.items.count { it.availableInBranch(session.activeBranchId) }})") },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = LoyGreen,
                                    selectedLabelColor = Color.White,
                                ),
                            )
                        }
                        items(data.categories, key = { it.id }) { cat ->
                            val selected = session.sellCategoryId == cat.id
                            FilterChip(
                                selected = selected,
                                onClick = { viewModel.setSellCategory(if (selected) null else cat.id) },
                                label = { Text(cat.name) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = LoyGreen,
                                    selectedLabelColor = Color.White,
                                ),
                            )
                        }
                    }

                    if (filteredItems.isEmpty()) {
                        Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                            EmptyState(
                                icon = Icons.Outlined.ShoppingBag,
                                title = "No matching items",
                                subtitle = if (session.sellSearchQuery.isNotEmpty()) "Try clearing your search filter." else "Add items from the Items tab.",
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(filteredItems, key = { it.id }) { item ->
                                val qtyInCart = cartSession.items.firstOrNull { it.itemId == item.id }?.qty ?: 0.0
                                ProductSaleRow(
                                    item = item,
                                    currencySymbol = sym,
                                    qtyInCart = qtyInCart,
                                    onAdd = { transactionViewModel.addProductToCart(item) },
                                    onDecrement = {
                                        transactionViewModel.updateCartItemQty(item.id, (qtyInCart - 1.0).coerceAtLeast(0.0))
                                    },
                                )
                            }
                        }
                    }

                    if (!isWide) {
                        CheckoutCartSummaryCard(
                            cartItems = cartSession.items,
                            totals = totals,
                            currencySymbol = sym,
                            onIncrement = { line ->
                                val product = data.items.firstOrNull { it.id == line.itemId }
                                if (product != null) {
                                    transactionViewModel.addProductToCart(product)
                                } else {
                                    transactionViewModel.updateCartItemQty(line.itemId, line.qty + 1.0)
                                }
                            },
                            onDecrement = { line ->
                                transactionViewModel.updateCartItemQty(line.itemId, (line.qty - 1.0).coerceAtLeast(0.0))
                            },
                            onEditLine = { editingLine = it },
                            onOpenCart = onNavigateToCart,
                            onCharge = onNavigateToPayment,
                        )
                    }
                }

                if (isWide) {
                    Surface(
                        modifier = Modifier.width(360.dp).fillMaxHeight(),
                        color = LoySurface,
                        border = BorderStroke(1.dp, LoyBorder),
                    ) {
                        ReceiptPanelContent(
                            cartSession = cartSession,
                            data = data,
                            totals = totals,
                            onEditLine = { editingLine = it },
                            onOpenDiscount = { showDiscountSheet = true },
                            onOpenCustomer = { showCustomerSheet = true },
                            onOpenNote = { showNoteDialog = true },
                            onSaveTicket = { showSaveTicketDialog = true },
                            onClear = { transactionViewModel.clearCart() },
                            onCharge = onNavigateToPayment,
                        )
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

    if (showTicketsSheet) {
        TicketsBottomSheet(
            tickets = branchTickets,
            currencySymbol = sym,
            onLoad = { transactionViewModel.loadTicket(it) },
            onDelete = { transactionViewModel.deleteTicket(it) },
            onDismiss = { showTicketsSheet = false },
        )
    }

    editingLine?.let { line ->
        LineEditorBottomSheet(
            line = line,
            currencySymbol = sym,
            onUpdateQty = { transactionViewModel.updateCartItemQty(line.itemId, it) },
            onDelete = { transactionViewModel.removeFromCart(line.itemId) },
            onDismiss = { editingLine = null },
        )
    }

    if (showDiscountSheet) {
        DiscountBottomSheet(
            current = cartSession.discount,
            currencySymbol = sym,
            onApply = { transactionViewModel.setDiscount(it) },
            onDismiss = { showDiscountSheet = false },
        )
    }

    if (showCustomerSheet) {
        CustomerPickerSheet(
            customers = data.customers,
            selectedId = cartSession.customerId,
            onSelect = { transactionViewModel.setCustomer(it?.id) },
            onCreateCustomer = { name, phone ->
                val c = CustomerEntity(id = newId(), name = name, phone = phone, createdAt = nowMs())
                viewModel.saveCustomer(c)
                transactionViewModel.setCustomer(c.id)
            },
            onDismiss = { showCustomerSheet = false },
        )
    }

    if (showSaveTicketDialog) {
        val currentCustomer = data.customers.firstOrNull { it.id == cartSession.customerId }
        SaveTicketDialog(
            initialName = branchTickets.firstOrNull { it.ticket.id == cartSession.openTicketId }?.ticket?.name
                ?: "Table ${branchTickets.size + 1}",
            onSave = { name ->
                transactionViewModel.saveCurrentTicket(name, currentCustomer, session.activeBranchId)
                showSaveTicketDialog = false
            },
            onDismiss = { showSaveTicketDialog = false },
        )
    }

    if (showNoteDialog) {
        NoteInputDialog(
            initialNote = cartSession.note,
            onSave = { note ->
                transactionViewModel.setCartNote(note)
                showNoteDialog = false
            },
            onDismiss = { showNoteDialog = false },
        )
    }
}

@Composable
private fun ProductSaleRow(
    item: ItemEntity,
    currencySymbol: String,
    qtyInCart: Double,
    onAdd: () -> Unit,
    onDecrement: () -> Unit,
) {
    val outOfStock = item.trackStock && item.stock <= 0.0
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = LoySurface,
        border = BorderStroke(1.dp, if (qtyInCart > 0) LoyGreen else LoyBorder),
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (outOfStock) 0.52f else 1f)
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = !outOfStock, onClick = onAdd)
            .testTag("product_row_${item.id}"),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ItemThumbnail(item = item, size = 44.dp)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = item.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = LoyTextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (qtyInCart > 0) {
                        PillBadge(text = "×${formatQty(qtyInCart)}")
                    }
                }
                Spacer(Modifier.height(2.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (item.sku.isNotEmpty()) {
                        Text(
                            text = "SKU ${item.sku}",
                            style = MaterialTheme.typography.bodySmall,
                            color = LoyTextSecondary,
                            maxLines = 1,
                        )
                    }
                    StockDotBadge(item = item)
                }
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = if (item.priceCents > 0L) formatCents(item.priceCents, currencySymbol) else "Set price",
                style = TabularMoneyStyle.merge(MaterialTheme.typography.titleMedium),
                fontWeight = FontWeight.Bold,
                color = if (item.priceCents > 0L) LoyTextPrimary else LoyTextSecondary,
            )
            Spacer(Modifier.width(6.dp))
            if (qtyInCart > 0) {
                IconButton(
                    onClick = onDecrement,
                    modifier = Modifier.size(48.dp).testTag("product_dec_${item.id}"),
                ) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = LoySurfaceVariant,
                        modifier = Modifier.size(34.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Outlined.Remove,
                                contentDescription = "Remove one ${item.name}",
                                tint = LoyTextPrimary,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
            }
            IconButton(
                onClick = onAdd,
                enabled = !outOfStock,
                modifier = Modifier.size(48.dp).testTag("product_inc_${item.id}"),
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (outOfStock) LoySurfaceVariant else LoyGreenLight,
                    modifier = Modifier.size(34.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Outlined.Add,
                            contentDescription = "Add ${item.name}",
                            tint = if (outOfStock) LoyTextSecondary else LoyGreen,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CheckoutCartSummaryCard(
    cartItems: List<CartLine>,
    totals: com.loypos.app.core.SaleTotals,
    currencySymbol: String,
    onIncrement: (CartLine) -> Unit,
    onDecrement: (CartLine) -> Unit,
    onEditLine: (CartLine) -> Unit,
    onOpenCart: () -> Unit,
    onCharge: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val totalUnits = cartItems.sumOf { it.qty }

    Surface(
        color = LoySurface,
        shadowElevation = 6.dp,
        border = BorderStroke(1.dp, LoyBorder),
        modifier = Modifier.fillMaxWidth().testTag("checkout_cart_summary"),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Shopping Cart Summary header row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = cartItems.isNotEmpty()) { expanded = !expanded }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Outlined.ShoppingCart,
                    contentDescription = null,
                    tint = if (cartItems.isNotEmpty()) LoyGreen else LoyTextSecondary,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Shopping Cart Summary",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = LoyTextPrimary,
                    )
                    Text(
                        text = if (cartItems.isEmpty()) {
                            "Tap products above to add to cart"
                        } else {
                            "${cartItems.size} ${if (cartItems.size == 1) "item" else "items"} · ${formatQty(totalUnits)} qty"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = LoyTextSecondary,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "Total Cost",
                        style = MaterialTheme.typography.labelSmall,
                        color = LoyTextSecondary,
                    )
                    Text(
                        text = formatCents(totals.totalCents, currencySymbol),
                        style = TabularMoneyStyle.merge(MaterialTheme.typography.titleMedium),
                        fontWeight = FontWeight.Bold,
                        color = if (cartItems.isNotEmpty()) LoyGreen else LoyTextPrimary,
                        modifier = Modifier.testTag("checkout_total_cost"),
                    )
                }
                if (cartItems.isNotEmpty()) {
                    IconButton(
                        onClick = { expanded = !expanded },
                        modifier = Modifier.size(48.dp).testTag("cart_summary_expand"),
                    ) {
                        Icon(
                            imageVector = if (expanded) Icons.Outlined.ExpandMore else Icons.Outlined.ExpandLess,
                            contentDescription = if (expanded) "Collapse cart summary" else "Expand cart summary",
                        )
                    }
                }
            }

            AnimatedVisibility(visible = expanded && cartItems.isNotEmpty()) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    HorizontalDivider(color = LoyBorder)
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 200.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        items(cartItems, key = { it.itemId }) { line ->
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = LoySurfaceVariant,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { onEditLine(line) },
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = line.name,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Text(
                                            text = "${formatQty(line.qty)} × ${formatCents(line.priceCents, currencySymbol)}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = LoyTextSecondary,
                                        )
                                    }
                                    IconButton(
                                        onClick = { onDecrement(line) },
                                        modifier = Modifier.size(48.dp),
                                    ) {
                                        Icon(Icons.Outlined.Remove, contentDescription = "Decrease ${line.name}", modifier = Modifier.size(18.dp))
                                    }
                                    IconButton(
                                        onClick = { onIncrement(line) },
                                        modifier = Modifier.size(48.dp),
                                    ) {
                                        Icon(Icons.Outlined.Add, contentDescription = "Increase ${line.name}", tint = LoyGreen, modifier = Modifier.size(18.dp))
                                    }
                                    Text(
                                        text = formatCents(line.lineTotalCents, currencySymbol),
                                        style = TabularMoneyStyle.merge(MaterialTheme.typography.bodyMedium),
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                            }
                        }
                    }
                    HorizontalDivider(color = LoyBorder)
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Subtotal", style = MaterialTheme.typography.bodySmall, color = LoyTextSecondary)
                            Text(formatCents(totals.subtotalCents, currencySymbol), style = TabularMoneyStyle.merge(MaterialTheme.typography.bodySmall))
                        }
                        if (totals.discountCents > 0L) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Discount", style = MaterialTheme.typography.bodySmall, color = LoyGreen)
                                Text("-${formatCents(totals.discountCents, currencySymbol)}", style = TabularMoneyStyle.merge(MaterialTheme.typography.bodySmall), color = LoyGreen)
                            }
                        }
                        if (totals.taxCents > 0L) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Tax", style = MaterialTheme.typography.bodySmall, color = LoyTextSecondary)
                                Text(formatCents(totals.taxCents, currencySymbol), style = TabularMoneyStyle.merge(MaterialTheme.typography.bodySmall))
                            }
                        }
                    }
                }
            }

            HorizontalDivider(color = LoyBorder)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedButton(
                    onClick = onOpenCart,
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                        .testTag("view_ticket_button"),
                ) {
                    Text(
                        text = if (cartItems.isEmpty()) "Ticket (0)" else "View Cart (${cartItems.size})",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Button(
                    onClick = onCharge,
                    enabled = cartItems.isNotEmpty(),
                    modifier = Modifier
                        .weight(1.35f)
                        .height(50.dp)
                        .testTag("charge_bar_button"),
                ) {
                    Text("Charge ${formatCents(totals.totalCents, currencySymbol)}", maxLines = 1)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CartScreen(
    viewModel: LoyPosViewModel,
    transactionViewModel: TransactionViewModel,
    session: UiSessionState,
    data: LoyPosDataState,
    onBack: () -> Unit,
    onNavigateToPayment: () -> Unit,
) {
    var editingLine by remember { mutableStateOf<CartLine?>(null) }
    var showDiscountSheet by remember { mutableStateOf(false) }
    var showCustomerSheet by remember { mutableStateOf(false) }
    var showSaveTicketDialog by remember { mutableStateOf(false) }
    var showNoteDialog by remember { mutableStateOf(false) }

    val cartSession by transactionViewModel.cartSession.collectAsStateWithLifecycle()
    val totals by transactionViewModel.cartTotals.collectAsStateWithLifecycle()
    val sym = data.settings.currencySymbol

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Current Ticket (${cartSession.items.size})", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back to catalog")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = LoySurface),
            )
        },
        containerColor = LoyBackground,
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            ReceiptPanelContent(
                cartSession = cartSession,
                data = data,
                totals = totals,
                onEditLine = { editingLine = it },
                onOpenDiscount = { showDiscountSheet = true },
                onOpenCustomer = { showCustomerSheet = true },
                onOpenNote = { showNoteDialog = true },
                onSaveTicket = { showSaveTicketDialog = true },
                onClear = { transactionViewModel.clearCart() },
                onCharge = onNavigateToPayment,
            )
        }
    }

    editingLine?.let { line ->
        LineEditorBottomSheet(
            line = line,
            currencySymbol = sym,
            onUpdateQty = { transactionViewModel.updateCartItemQty(line.itemId, it) },
            onDelete = { transactionViewModel.removeFromCart(line.itemId) },
            onDismiss = { editingLine = null },
        )
    }
    if (showDiscountSheet) {
        DiscountBottomSheet(
            current = cartSession.discount,
            currencySymbol = sym,
            onApply = { transactionViewModel.setDiscount(it) },
            onDismiss = { showDiscountSheet = false },
        )
    }
    if (showCustomerSheet) {
        CustomerPickerSheet(
            customers = data.customers,
            selectedId = cartSession.customerId,
            onSelect = { transactionViewModel.setCustomer(it?.id) },
            onCreateCustomer = { name, phone ->
                val c = CustomerEntity(id = newId(), name = name, phone = phone, createdAt = nowMs())
                viewModel.saveCustomer(c)
                transactionViewModel.setCustomer(c.id)
            },
            onDismiss = { showCustomerSheet = false },
        )
    }
    if (showSaveTicketDialog) {
        val currentCustomer = data.customers.firstOrNull { it.id == cartSession.customerId }
        SaveTicketDialog(
            initialName = "Ticket ${data.tickets.size + 1}",
            onSave = { name ->
                transactionViewModel.saveCurrentTicket(name, currentCustomer, session.activeBranchId)
                showSaveTicketDialog = false
                onBack()
            },
            onDismiss = { showSaveTicketDialog = false },
        )
    }
    if (showNoteDialog) {
        NoteInputDialog(
            initialNote = cartSession.note,
            onSave = { note ->
                transactionViewModel.setCartNote(note)
                showNoteDialog = false
            },
            onDismiss = { showNoteDialog = false },
        )
    }
}

@Composable
fun ReceiptPanelContent(
    cartSession: CartSessionState,
    data: LoyPosDataState,
    totals: com.loypos.app.core.SaleTotals,
    onEditLine: (CartLine) -> Unit,
    onOpenDiscount: () -> Unit,
    onOpenCustomer: () -> Unit,
    onOpenNote: () -> Unit,
    onSaveTicket: () -> Unit,
    onClear: () -> Unit,
    onCharge: () -> Unit,
) {
    val sym = data.settings.currencySymbol
    val customer = data.customers.firstOrNull { it.id == cartSession.customerId }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Outlined.ShoppingCart, contentDescription = null, tint = LoyGreen, modifier = Modifier.size(20.dp))
                Text(
                    text = "Shopping Cart Summary",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
            PillBadge(text = "${cartSession.items.size} ${if (cartSession.items.size == 1) "item" else "items"}")
        }
        HorizontalDivider(color = LoyBorder)
        // Quick action chips row
        LazyRow(
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                FilterChip(
                    selected = customer != null,
                    onClick = onOpenCustomer,
                    leadingIcon = { Icon(Icons.Outlined.PersonOutline, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    label = { Text(customer?.name ?: "Customer") },
                )
            }
            item {
                FilterChip(
                    selected = cartSession.discount != null,
                    onClick = onOpenDiscount,
                    leadingIcon = { Icon(Icons.Outlined.LocalOffer, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    label = {
                        Text(
                            cartSession.discount?.let {
                                if (it.type == "percent") "${formatQty(it.value)}% off" else "-${formatCents((it.value * 100).toLong(), sym)}"
                            } ?: "Discount"
                        )
                    },
                )
            }
            item {
                FilterChip(
                    selected = cartSession.note.isNotEmpty(),
                    onClick = onOpenNote,
                    leadingIcon = { Icon(Icons.AutoMirrored.Outlined.NoteAdd, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    label = { Text(if (cartSession.note.isEmpty()) "Note" else "Note ✓") },
                )
            }
            if (cartSession.items.isNotEmpty()) {
                item {
                    FilterChip(
                        selected = false,
                        onClick = onClear,
                        leadingIcon = { Icon(Icons.Outlined.DeleteSweep, contentDescription = null, tint = LoyError, modifier = Modifier.size(16.dp)) },
                        label = { Text("Clear", color = LoyError) },
                    )
                }
            }
        }
        HorizontalDivider(color = LoyBorder)

        if (cartSession.items.isEmpty()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                EmptyState(
                    icon = Icons.Outlined.ShoppingBag,
                    title = "Ticket is empty",
                    subtitle = "Tap items from the catalog to build a sale.",
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(cartSession.items, key = { it.itemId }) { line ->
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = LoySurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onEditLine(line) }
                            .testTag("cart_line_${line.itemId}"),
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(line.name, fontWeight = FontWeight.SemiBold)
                                Text(
                                    "${formatQty(line.qty)} × ${formatCents(line.priceCents, sym)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = LoyTextSecondary,
                                )
                            }
                            Text(
                                text = formatCents(line.lineTotalCents, sym),
                                style = TabularMoneyStyle.merge(MaterialTheme.typography.titleMedium),
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }
        }

        HorizontalDivider(color = LoyBorder)
        Surface(color = LoySurface) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Subtotal", color = LoyTextSecondary, style = MaterialTheme.typography.bodyMedium)
                    Text(formatCents(totals.subtotalCents, sym), style = TabularMoneyStyle.merge(MaterialTheme.typography.bodyMedium))
                }
                if (totals.discountCents > 0L) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Discount", color = LoyGreen, style = MaterialTheme.typography.bodyMedium)
                        Text("-${formatCents(totals.discountCents, sym)}", color = LoyGreen, style = TabularMoneyStyle.merge(MaterialTheme.typography.bodyMedium))
                    }
                }
                if (totals.taxCents > 0L) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(
                            if (data.settings.taxIncluded) "Tax (included)" else "Tax (${formatQty(data.settings.taxRatePercent)}%)",
                            color = LoyTextSecondary,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(formatCents(totals.taxCents, sym), style = TabularMoneyStyle.merge(MaterialTheme.typography.bodyMedium))
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Total", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(
                        formatCents(totals.totalCents, sym),
                        style = TabularMoneyStyle.merge(MaterialTheme.typography.titleLarge),
                        fontWeight = FontWeight.Bold,
                    )
                }
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = onSaveTicket,
                        enabled = cartSession.items.isNotEmpty(),
                        modifier = Modifier.weight(1f).height(50.dp).testTag("save_ticket_button"),
                    ) {
                        Icon(Icons.Outlined.BookmarkBorder, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Save")
                    }
                    Button(
                        onClick = onCharge,
                        enabled = cartSession.items.isNotEmpty(),
                        modifier = Modifier.weight(1.6f).height(50.dp).testTag("panel_charge_button"),
                    ) {
                        Text("Charge ${formatCents(totals.totalCents, sym)}")
                    }
                }
            }
        }
    }
}

@Composable
private fun SaveTicketDialog(
    initialName: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Save Open Ticket") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Ticket name / table") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            Button(onClick = { onSave(name) }, modifier = Modifier.heightIn(min = 48.dp)) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) {
                Text("Cancel")
            }
        },
    )
}

@Composable
private fun NoteInputDialog(
    initialNote: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var note by remember { mutableStateOf(initialNote) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sale Note") },
        text = {
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text("Note printed on receipt") },
                maxLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            Button(onClick = { onSave(note.trim()) }, modifier = Modifier.heightIn(min = 48.dp)) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) {
                Text("Cancel")
            }
        },
    )
}
