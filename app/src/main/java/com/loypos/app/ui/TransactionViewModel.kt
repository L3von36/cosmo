package com.loypos.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.loypos.app.core.DiscountInput
import com.loypos.app.core.LowStockWorkScheduler
import com.loypos.app.core.SaleTotals
import com.loypos.app.core.computeTotals
import com.loypos.app.core.newId
import com.loypos.app.core.nowMs
import com.loypos.app.core.pointsForTotal
import com.loypos.app.data.LoyPosRepository
import com.loypos.app.data.local.CartLine
import com.loypos.app.data.local.CustomerEntity
import com.loypos.app.data.local.LoyPosDatabase
import com.loypos.app.data.local.ProductEntity
import com.loypos.app.data.local.SaleEntity
import com.loypos.app.data.local.SaleItemEntity
import com.loypos.app.data.local.SaleWithItems
import com.loypos.app.data.local.SettingEntity
import com.loypos.app.data.local.SettingsModel
import com.loypos.app.data.local.StockMovementEntity
import com.loypos.app.data.local.TicketWithLines
import com.loypos.app.data.local.UserEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CartSessionState(
    val items: List<CartLine> = emptyList(),
    val discount: DiscountInput? = null,
    val customerId: String? = null,
    val note: String = "",
    val openTicketId: String? = null,
)

class TransactionViewModel(application: Application) : AndroidViewModel(application) {
    private val db = LoyPosDatabase.getInstance(application.applicationContext)
    private val repo = LoyPosRepository(application.applicationContext)
    private val saleDao = db.saleDao()
    private val saleItemDao = db.saleItemDao()
    private val productDao = db.productDao()
    private val coreDao = db.dao()

    private val _cartSession = MutableStateFlow(CartSessionState())
    val cartSession: StateFlow<CartSessionState> = _cartSession.asStateFlow()

    val cartItems: StateFlow<List<CartLine>> = _cartSession
        .map { it.items }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList(),
        )

    val cartTotals: StateFlow<SaleTotals> = combine(
        _cartSession,
        repo.settingsFlow,
    ) { session, settings ->
        computeTotals(
            lineTotalsCents = session.items.map { it.lineTotalCents },
            discount = session.discount,
            taxRatePercent = settings.taxRatePercent,
            taxIncluded = settings.taxIncluded,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SaleTotals(0L, 0L, 0L, 0L),
    )

    val recentSales: StateFlow<List<SaleWithItems>> = saleDao.observeSalesWithItems()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList(),
        )

    private val _lastCompletedSale = MutableStateFlow<SaleWithItems?>(null)
    val lastCompletedSale: StateFlow<SaleWithItems?> = _lastCompletedSale.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val transactionMessage: StateFlow<String?> = _message.asStateFlow()

    fun consumeMessage() {
        _message.value = null
    }

    fun addProductToCart(product: ProductEntity, qty: Double = 1.0) {
        if (product.trackStock && product.stock <= 0.0) {
            _message.value = "${product.name} is out of stock"
            return
        }
        _cartSession.update { state ->
            val updated = state.items.toMutableList()
            val idx = updated.indexOfFirst { it.itemId == product.id }
            if (idx >= 0) {
                val existing = updated[idx]
                updated[idx] = existing.copy(qty = existing.qty + qty)
            } else {
                updated.add(CartLine(product.id, product.name, product.priceCents, qty))
            }
            state.copy(items = updated)
        }
    }

    fun updateCartItemQty(productId: String, newQty: Double) {
        _cartSession.update { state ->
            val updated = if (newQty <= 0.0) {
                state.items.filterNot { it.itemId == productId }
            } else {
                state.items.map { if (it.itemId == productId) it.copy(qty = newQty) else it }
            }
            state.copy(items = updated)
        }
    }

    fun removeFromCart(productId: String) {
        _cartSession.update { state ->
            state.copy(items = state.items.filterNot { it.itemId == productId })
        }
    }

    fun setDiscount(discount: DiscountInput?) {
        _cartSession.update { it.copy(discount = discount) }
    }

    fun setCustomer(customerId: String?) {
        _cartSession.update { it.copy(customerId = customerId) }
    }

    fun setCartNote(note: String) {
        _cartSession.update { it.copy(note = note) }
    }

    fun clearCart() {
        _cartSession.value = CartSessionState()
    }

    fun loadTicket(ticketWithLines: TicketWithLines) {
        _cartSession.value = CartSessionState(
            items = ticketWithLines.toCartLines(),
            discount = ticketWithLines.discount,
            customerId = ticketWithLines.ticket.customerId,
            note = ticketWithLines.ticket.note,
            openTicketId = ticketWithLines.ticket.id,
        )
    }

    fun saveCurrentTicket(name: String, customer: CustomerEntity?, branchId: String?) {
        val session = _cartSession.value
        if (session.items.isEmpty()) return
        viewModelScope.launch {
            repo.saveTicket(
                ticketId = session.openTicketId,
                name = name.trim().ifEmpty { "Ticket" },
                lines = session.items,
                discount = session.discount,
                customer = customer,
                note = session.note,
                branchId = branchId,
            )
            clearCart()
            _message.value = "Ticket saved"
        }
    }

    fun deleteTicket(ticketId: String) {
        viewModelScope.launch {
            repo.deleteTicket(ticketId)
            if (_cartSession.value.openTicketId == ticketId) {
                clearCart()
            }
        }
    }

    fun finalizeSale(
        paymentType: String,
        paidCents: Long,
        settings: SettingsModel,
        customer: CustomerEntity?,
        branchId: String?,
        cashier: UserEntity?,
        onCompleted: (SaleWithItems) -> Unit = {},
    ) {
        val session = _cartSession.value
        if (session.items.isEmpty()) return

        val totals = computeTotals(
            lineTotalsCents = session.items.map { it.lineTotalCents },
            discount = session.discount,
            taxRatePercent = settings.taxRatePercent,
            taxIncluded = settings.taxIncluded,
        )
        val changeCents = (paidCents - totals.totalCents).coerceAtLeast(0L)
        val now = nowMs()
        val saleId = newId()

        viewModelScope.launch {
            val completedSale = db.withTransaction {
                val number = (coreDao.getSetting("next_receipt_number")?.toIntOrNull()
                    ?: settings.nextReceiptNumber)
                    .coerceAtLeast(1)
                coreDao.upsertSetting(SettingEntity("next_receipt_number", (number + 1).toString()))

                val sale = SaleEntity(
                    id = saleId,
                    number = number,
                    createdAt = now,
                    subtotalCents = totals.subtotalCents,
                    discountCents = totals.discountCents,
                    discountType = session.discount?.type,
                    discountValue = session.discount?.value,
                    taxCents = totals.taxCents,
                    totalCents = totals.totalCents,
                    paidCents = paidCents,
                    changeCents = changeCents,
                    paymentType = paymentType,
                    customerId = customer?.id,
                    customerName = customer?.name,
                    status = "completed",
                    note = session.note,
                    branchId = branchId,
                    cashierId = cashier?.id,
                    cashierName = cashier?.ownerName?.ifEmpty { cashier.email },
                    updatedAt = now,
                )

                val saleItems = session.items.map { line ->
                    SaleItemEntity(
                        id = newId(),
                        receiptId = saleId,
                        itemId = line.itemId,
                        name = line.name,
                        priceCents = line.priceCents,
                        qty = line.qty,
                        lineTotalCents = line.lineTotalCents,
                    )
                }

                saleDao.insertSale(sale)
                saleItemDao.insertSaleItems(saleItems)

                for (line in session.items) {
                    productDao.adjustProductStock(line.itemId, -line.qty, now)
                    coreDao.insertStockMovement(
                        StockMovementEntity(
                            id = newId(),
                            itemId = line.itemId,
                            qty = -line.qty,
                            type = "sale",
                            createdAt = now,
                            note = "Receipt #${number.toString().padStart(4, '0')}",
                            receiptId = saleId,
                        )
                    )
                }

                if (customer != null) {
                    val pts = pointsForTotal(totals.totalCents)
                    if (pts > 0) coreDao.addCustomerPoints(customer.id, pts, now)
                }

                if (!session.openTicketId.isNullOrEmpty()) {
                    coreDao.deleteTicketLines(session.openTicketId)
                    coreDao.deleteTicketById(session.openTicketId)
                }

                SaleWithItems(sale, saleItems)
            }

            _lastCompletedSale.value = completedSale
            clearCart()
            if (settings.lowStockAlertsEnabled) {
                LowStockWorkScheduler.runImmediateCheck(getApplication())
            }
            onCompleted(completedSale)
        }
    }

    fun refundSale(saleId: String) {
        viewModelScope.launch {
            repo.refundReceipt(saleId)
            _message.value = "Receipt refunded and tracked stock restored"
        }
    }
}
