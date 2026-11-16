package com.loypos.app.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.loypos.app.core.BackupManager
import com.loypos.app.core.BackupResult
import com.loypos.app.core.CloudSyncClient
import com.loypos.app.core.DiscountInput
import com.loypos.app.core.ItemPhotos
import com.loypos.app.core.LocalBackup
import com.loypos.app.core.ReceiptPdfGenerator
import com.loypos.app.core.SaleTotals
import com.loypos.app.core.computeTotals
import com.loypos.app.core.newId
import com.loypos.app.core.nowMs
import com.loypos.app.core.startOfTodayMs
import com.loypos.app.data.LoyPosRepository
import com.loypos.app.data.local.BranchEntity
import com.loypos.app.data.local.CartLine
import com.loypos.app.data.local.CashMovementEntity
import com.loypos.app.data.local.CategoryEntity
import com.loypos.app.data.local.CustomerEntity
import com.loypos.app.data.local.DailyRevenuePoint
import com.loypos.app.data.local.DaySalesSummary
import com.loypos.app.data.local.DbStats
import com.loypos.app.data.local.ItemEntity
import com.loypos.app.data.local.ReceiptWithLines
import com.loypos.app.data.local.SettingsModel
import com.loypos.app.data.local.ShiftEntity
import com.loypos.app.data.local.ShiftStats
import com.loypos.app.data.local.StockMovementEntity
import com.loypos.app.data.local.TicketWithLines
import com.loypos.app.data.local.UserEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

enum class AuthStage { SPLASH, LOGGED_OUT, LOGGED_IN }

data class LoyPosDataState(
    val settings: SettingsModel = SettingsModel(),
    val rawSettings: Map<String, String> = emptyMap(),
    val branches: List<BranchEntity> = emptyList(),
    val categories: List<CategoryEntity> = emptyList(),
    val items: List<ItemEntity> = emptyList(),
    val customers: List<CustomerEntity> = emptyList(),
    val receipts: List<ReceiptWithLines> = emptyList(),
    val tickets: List<TicketWithLines> = emptyList(),
    val staff: List<UserEntity> = emptyList(),
    val movements: List<StockMovementEntity> = emptyList(),
    val shifts: List<ShiftEntity> = emptyList(),
    val cashMovements: List<CashMovementEntity> = emptyList(),
)

data class UiSessionState(
    val authStage: AuthStage = AuthStage.SPLASH,
    val currentUser: UserEntity? = null,
    val hasAnyUser: Boolean = false,
    val activeBranchId: String? = null,
    val cart: List<CartLine> = emptyList(),
    val cartDiscount: DiscountInput? = null,
    val cartCustomerId: String? = null,
    val cartNote: String = "",
    val openTicketId: String? = null,
    val lastCompletedReceipt: ReceiptWithLines? = null,
    val sellSearchQuery: String = "",
    val sellCategoryId: String? = null,
    val syncing: Boolean = false,
    val lastSyncMessage: String? = null,
    val lastSyncOk: Boolean? = null,
    val dbStats: DbStats = DbStats(),
    val localBackups: List<LocalBackup> = emptyList(),
    val integrityResult: String? = null,
    val message: String? = null,
)

class LoyPosViewModel(application: Application) : AndroidViewModel(application) {
    private val appContext = application.applicationContext
    val repo = LoyPosRepository(appContext)

    private val _session = MutableStateFlow(UiSessionState())
    val session: StateFlow<UiSessionState> = _session.asStateFlow()

    private val coreDataFlow = combine(
        repo.settingsFlow,
        repo.rawSettingsFlow,
        repo.branchesFlow,
        repo.categoriesFlow,
        repo.itemsFlow,
    ) { settings, raw, branches, categories, items ->
        LoyPosDataState(
            settings = settings,
            rawSettings = raw,
            branches = branches,
            categories = categories,
            items = items,
        )
    }

    private val salesDataFlow = combine(
        repo.customersFlow,
        repo.receiptsWithLinesFlow,
        repo.ticketsWithLinesFlow,
        repo.staffFlow,
        repo.stockMovementsFlow,
    ) { customers, receipts, tickets, staff, movements ->
        Triple(customers, receipts, Triple(tickets, staff, movements))
    }

    private val shiftDataFlow = combine(
        repo.shiftsFlow,
        repo.cashMovementsFlow,
    ) { shifts, cashMoves -> shifts to cashMoves }

    private val _inventorySearchQuery = MutableStateFlow("")
    val inventorySearchQuery: StateFlow<String> = _inventorySearchQuery.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val inventoryProducts: StateFlow<List<ItemEntity>> = _inventorySearchQuery
        .flatMapLatest { query -> repo.observeProductsFiltered(query) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList(),
        )

    fun setInventorySearch(query: String) {
        _inventorySearchQuery.value = query
    }

    private val _revenueTrendDays = MutableStateFlow(7)
    val revenueTrendDays: StateFlow<Int> = _revenueTrendDays.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val dailyRevenueTrend: StateFlow<List<DailyRevenuePoint>> = combine(
        _revenueTrendDays,
        _session.map { it.activeBranchId }.distinctUntilChanged(),
    ) { days, branchId -> days to branchId }
        .flatMapLatest { (days, branchId) ->
            val sinceMs = startOfTodayMs() - (days - 1).coerceAtLeast(0) * 86_400_000L
            repo.observeCompletedSalesSince(sinceMs).map { sales ->
                repo.computeDailyRevenueTrend(sales, days, branchId)
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList(),
        )

    fun setRevenueTrendDays(days: Int) {
        _revenueTrendDays.value = days.coerceIn(7, 30)
    }

    val dataState: StateFlow<LoyPosDataState> = combine(
        coreDataFlow,
        salesDataFlow,
        shiftDataFlow,
    ) { core, sales, shiftPair ->
        core.copy(
            customers = sales.first,
            receipts = sales.second,
            tickets = sales.third.first,
            staff = sales.third.second,
            movements = sales.third.third,
            shifts = shiftPair.first,
            cashMovements = shiftPair.second,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = LoyPosDataState(),
    )

    init {
        viewModelScope.launch {
            repo.ensureInitialized()
            val user = repo.currentSessionUser()
            val anyUsers = repo.anyUsersExist()
            val raw = repo.loadRawSettings()
            val branches = repo.dao.getActiveBranches()
            val savedBranch = raw["active_branch"]
            val resolvedBranch = when {
                user?.isCashier == true && !user.branchId.isNullOrEmpty() -> user.branchId
                !savedBranch.isNullOrEmpty() && branches.any { it.id == savedBranch } -> savedBranch
                else -> branches.firstOrNull()?.id
            }
            _session.update {
                it.copy(
                    authStage = if (user != null) AuthStage.LOGGED_IN else AuthStage.LOGGED_OUT,
                    currentUser = user,
                    hasAnyUser = anyUsers,
                    activeBranchId = resolvedBranch,
                    lastSyncMessage = raw["last_sync_msg"],
                )
            }
            refreshHealthAndBackups()
        }
    }

    fun consumeMessage() {
        _session.update { it.copy(message = null) }
    }

    fun showMessage(msg: String) {
        _session.update { it.copy(message = msg) }
    }

    // --- Auth ---
    fun signIn(email: String, password: String, onDone: (String?) -> Unit) {
        viewModelScope.launch {
            val res = repo.signIn(email, password)
            res.onSuccess { user ->
                val branches = repo.dao.getActiveBranches()
                val raw = repo.loadRawSettings()
                val branchId = if (user.isCashier && !user.branchId.isNullOrEmpty()) {
                    user.branchId
                } else {
                    raw["active_branch"]?.takeIf { id -> branches.any { it.id == id } }
                        ?: branches.firstOrNull()?.id
                }
                _session.update {
                    it.copy(
                        authStage = AuthStage.LOGGED_IN,
                        currentUser = user,
                        hasAnyUser = true,
                        activeBranchId = branchId,
                    )
                }
                refreshHealthAndBackups()
                onDone(null)
            }.onFailure { err ->
                onDone(err.message ?: "Sign in failed")
            }
        }
    }

    fun signUp(
        email: String,
        password: String,
        shopName: String,
        ownerName: String,
        branchName: String,
        onDone: (String?) -> Unit,
    ) {
        viewModelScope.launch {
            val res = repo.signUpOwner(email, password, shopName, ownerName, branchName)
            res.onSuccess { user ->
                val raw = repo.loadRawSettings()
                _session.update {
                    it.copy(
                        authStage = AuthStage.LOGGED_IN,
                        currentUser = user,
                        hasAnyUser = true,
                        activeBranchId = raw["active_branch"],
                    )
                }
                refreshHealthAndBackups()
                onDone(null)
            }.onFailure { err ->
                onDone(err.message ?: "Sign up failed")
            }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            repo.signOut()
            clearCart()
            val anyUsers = repo.anyUsersExist()
            _session.update {
                it.copy(
                    authStage = AuthStage.LOGGED_OUT,
                    currentUser = null,
                    hasAnyUser = anyUsers,
                )
            }
        }
    }

    // --- Branch & Search ---
    fun selectBranch(branchId: String) {
        val user = _session.value.currentUser
        if (user?.isCashier == true) return
        viewModelScope.launch {
            repo.saveSetting("active_branch", branchId)
            _session.update { it.copy(activeBranchId = branchId) }
        }
    }

    fun setSellSearch(query: String) {
        _session.update { it.copy(sellSearchQuery = query) }
    }

    fun setSellCategory(categoryId: String?) {
        _session.update { it.copy(sellCategoryId = categoryId) }
    }

    // --- Cart & Tickets ---
    fun addToCart(item: ItemEntity, qty: Double = 1.0) {
        if (item.trackStock && item.stock <= 0.0) {
            showMessage("${item.name} is out of stock")
            return
        }
        _session.update { state ->
            val list = state.cart.toMutableList()
            val idx = list.indexOfFirst { it.itemId == item.id }
            if (idx >= 0) {
                val existing = list[idx]
                list[idx] = existing.copy(qty = existing.qty + qty)
            } else {
                list.add(CartLine(item.id, item.name, item.priceCents, qty))
            }
            state.copy(cart = list)
        }
    }

    fun updateCartLineQty(itemId: String, newQty: Double) {
        _session.update { state ->
            val list = if (newQty <= 0.0) {
                state.cart.filterNot { it.itemId == itemId }
            } else {
                state.cart.map { if (it.itemId == itemId) it.copy(qty = newQty) else it }
            }
            state.copy(cart = list)
        }
    }

    fun removeFromCart(itemId: String) {
        _session.update { state ->
            state.copy(cart = state.cart.filterNot { it.itemId == itemId })
        }
    }

    fun setDiscount(discount: DiscountInput?) {
        _session.update { it.copy(cartDiscount = discount) }
    }

    fun setCustomer(customerId: String?) {
        _session.update { it.copy(cartCustomerId = customerId) }
    }

    fun setCartNote(note: String) {
        _session.update { it.copy(cartNote = note) }
    }

    fun clearCart() {
        _session.update {
            it.copy(
                cart = emptyList(),
                cartDiscount = null,
                cartCustomerId = null,
                cartNote = "",
                openTicketId = null,
            )
        }
    }

    fun totalsForCart(data: LoyPosDataState = dataState.value): SaleTotals {
        val state = _session.value
        return computeTotals(
            lineTotalsCents = state.cart.map { it.lineTotalCents },
            discount = state.cartDiscount,
            taxRatePercent = data.settings.taxRatePercent,
            taxIncluded = data.settings.taxIncluded,
        )
    }

    fun saveCurrentTicket(name: String) {
        val state = _session.value
        if (state.cart.isEmpty()) return
        val customer = dataState.value.customers.firstOrNull { it.id == state.cartCustomerId }
        viewModelScope.launch {
            repo.saveTicket(
                ticketId = state.openTicketId,
                name = name.trim().ifEmpty { "Ticket" },
                lines = state.cart,
                discount = state.cartDiscount,
                customer = customer,
                note = state.cartNote,
                branchId = state.activeBranchId,
            )
            clearCart()
            showMessage("Ticket saved")
        }
    }

    fun loadTicket(ticketWithLines: TicketWithLines) {
        _session.update {
            it.copy(
                cart = ticketWithLines.toCartLines(),
                cartDiscount = ticketWithLines.discount,
                cartCustomerId = ticketWithLines.ticket.customerId,
                cartNote = ticketWithLines.ticket.note,
                openTicketId = ticketWithLines.ticket.id,
            )
        }
    }

    fun deleteTicket(ticketId: String) {
        viewModelScope.launch {
            repo.deleteTicket(ticketId)
            if (_session.value.openTicketId == ticketId) {
                clearCart()
            }
        }
    }

    fun completeSale(paymentType: String, paidCents: Long, onSuccess: () -> Unit = {}) {
        val state = _session.value
        val data = dataState.value
        if (state.cart.isEmpty()) return
        val customer = data.customers.firstOrNull { it.id == state.cartCustomerId }
        viewModelScope.launch {
            val receipt = repo.completeSale(
                lines = state.cart,
                discount = state.cartDiscount,
                customer = customer,
                note = state.cartNote,
                paymentType = paymentType,
                paidCents = paidCents,
                settings = data.settings,
                branchId = state.activeBranchId,
                cashier = state.currentUser,
                openTicketId = state.openTicketId,
            )
            _session.update {
                it.copy(
                    cart = emptyList(),
                    cartDiscount = null,
                    cartCustomerId = null,
                    cartNote = "",
                    openTicketId = null,
                    lastCompletedReceipt = receipt,
                )
            }
            refreshHealthAndBackups()
            onSuccess()
        }
    }

    fun refundReceipt(receiptId: String) {
        viewModelScope.launch {
            repo.refundReceipt(receiptId)
            showMessage("Receipt refunded and tracked stock restored")
        }
    }

    // --- PDF & Sharing ---
    fun shareReceiptPdf(receipt: ReceiptWithLines) {
        val data = dataState.value
        val branch = data.branches.firstOrNull { it.id == receipt.receipt.branchId }
        viewModelScope.launch {
            runCatching {
                val file: File = ReceiptPdfGenerator.createReceiptPdf(
                    context = appContext,
                    receiptWithLines = receipt,
                    settings = data.settings,
                    branchName = branch?.name,
                    branchAddress = branch?.address,
                )
                ReceiptPdfGenerator.sharePdfFile(appContext, file)
            }.onFailure {
                showMessage("Could not generate PDF: ${it.message}")
            }
        }
    }

    fun shareReceiptText(receipt: ReceiptWithLines) {
        val data = dataState.value
        val branch = data.branches.firstOrNull { it.id == receipt.receipt.branchId }
        ReceiptPdfGenerator.shareReceiptText(appContext, receipt, data.settings, branch?.name)
    }

    // --- Shifts ---
    fun openShift(openingFloatCents: Long) {
        val branchId = _session.value.activeBranchId
        viewModelScope.launch {
            repo.openShift(openingFloatCents, branchId)
            showMessage("Shift opened")
        }
    }

    fun addCashMovement(shiftId: String, type: String, amountCents: Long, note: String) {
        viewModelScope.launch {
            repo.addCashMovement(shiftId, type, amountCents, note)
            showMessage(if (type == "in") "Cash in recorded" else "Cash out recorded")
        }
    }

    fun closeShift(shiftId: String, countedCents: Long, expectedCents: Long, onDone: () -> Unit) {
        viewModelScope.launch {
            repo.closeShift(shiftId, countedCents, expectedCents)
            onDone()
        }
    }

    fun computeTodaySummary(data: LoyPosDataState, branchId: String?): DaySalesSummary {
        val start = startOfTodayMs()
        val today = data.receipts.map { it.receipt }.filter { r ->
            r.createdAt >= start && (branchId == null || r.branchId == null || r.branchId == branchId)
        }
        var net = 0L
        var count = 0
        var refunds = 0
        var cash = 0L
        var card = 0L
        for (r in today) {
            if (r.isCompleted) {
                net += r.totalCents
                count++
                if (r.paymentType == "card") card += r.totalCents else cash += r.totalCents
            } else {
                refunds++
            }
        }
        return DaySalesSummary(net, count, refunds, cash, card)
    }

    fun currentBranchShift(data: LoyPosDataState, branchId: String?): ShiftEntity? =
        data.shifts.firstOrNull { s ->
            s.isOpen && (branchId == null || s.branchId == null || s.branchId == branchId)
        }

    fun shiftStatsFor(shift: ShiftEntity, data: LoyPosDataState): ShiftStats =
        repo.computeShiftStats(shift, data.receipts.map { it.receipt }, data.cashMovements)

    // --- Catalog / Staff / Branches / Customers ---
    fun saveItem(item: ItemEntity, pickedPhotoUri: Uri? = null, removePhoto: Boolean = false) {
        viewModelScope.launch {
            var finalPath = item.imagePath
            if (removePhoto) {
                ItemPhotos.deleteFile(finalPath)
                finalPath = ""
            } else if (pickedPhotoUri != null) {
                runCatching {
                    val saved = ItemPhotos.saveFromUri(appContext, pickedPhotoUri)
                    if (finalPath.isNotEmpty() && finalPath != saved) ItemPhotos.deleteFile(finalPath)
                    finalPath = saved
                }.onFailure {
                    showMessage("Could not save photo: ${it.message}")
                }
            }
            repo.saveItem(item.copy(imagePath = finalPath))
            showMessage("Item saved")
        }
    }

    fun deleteItem(id: String) {
        viewModelScope.launch {
            repo.deleteItem(id)
            showMessage("Item deleted")
        }
    }

    fun saveCategory(id: String?, name: String, position: Int) {
        viewModelScope.launch {
            repo.saveCategory(CategoryEntity(id = id ?: newId(), name = name.trim(), position = position))
            showMessage("Category saved")
        }
    }

    fun deleteCategory(id: String) {
        viewModelScope.launch {
            repo.deleteCategory(id)
            showMessage("Category deleted")
        }
    }

    fun receiveStock(itemId: String, qty: Double, note: String) {
        viewModelScope.launch {
            repo.receiveStock(itemId, qty, note)
            showMessage("Stock updated")
        }
    }

    fun setStockCount(itemId: String, newCount: Double, note: String) {
        viewModelScope.launch {
            repo.adjustStock(itemId, newCount, type = "adjust", note = note)
            showMessage("Stock count updated")
        }
    }

    fun saveCustomer(customer: CustomerEntity) {
        viewModelScope.launch {
            repo.saveCustomer(customer)
            showMessage("Customer saved")
        }
    }

    fun deleteCustomer(id: String) {
        viewModelScope.launch {
            repo.deleteCustomer(id)
            showMessage("Customer deleted")
        }
    }

    fun saveBranch(branch: BranchEntity) {
        viewModelScope.launch {
            repo.saveBranch(branch)
            showMessage("Branch saved")
        }
    }

    fun archiveBranch(id: String) {
        viewModelScope.launch {
            repo.archiveBranch(id).onSuccess {
                val active = repo.dao.getActiveBranches()
                if (_session.value.activeBranchId == id) {
                    _session.update { it.copy(activeBranchId = active.firstOrNull()?.id) }
                }
                showMessage("Branch deleted")
            }.onFailure {
                showMessage(it.message ?: "Could not remove branch")
            }
        }
    }

    fun addStaff(name: String, email: String, pin: String, branchId: String, onDone: (String?) -> Unit) {
        val shopName = dataState.value.settings.storeName
        viewModelScope.launch {
            val res = repo.addCashier(name, email, pin, branchId, shopName)
            res.onSuccess {
                showMessage("Seller added")
                onDone(null)
            }.onFailure { err ->
                onDone(err.message ?: "Could not add seller")
            }
        }
    }

    fun toggleStaffActive(id: String, active: Boolean) {
        viewModelScope.launch { repo.setStaffActive(id, active) }
    }

    fun deleteStaff(id: String) {
        viewModelScope.launch {
            repo.deleteStaff(id)
            showMessage("Seller removed")
        }
    }

    fun saveShopSettings(
        storeName: String? = null,
        currencySymbol: String? = null,
        taxRatePercent: Double? = null,
        taxIncluded: Boolean? = null,
        receiptFooter: String? = null,
    ) {
        viewModelScope.launch {
            repo.saveSettings(storeName, currencySymbol, taxRatePercent, taxIncluded, receiptFooter)
            showMessage("Settings saved")
        }
    }

    // --- Export / Import / Health / Reset / Cloud Sync ---
    fun refreshHealthAndBackups() {
        viewModelScope.launch {
            val stats = repo.getDbStats()
            val backups = BackupManager.listLocalBackups(appContext)
            _session.update { it.copy(dbStats = stats, localBackups = backups) }
        }
    }

    fun exportData() {
        viewModelScope.launch {
            runCatching {
                BackupManager.exportAndShare(appContext, repo)
                showMessage("Export ready")
            }.onFailure {
                showMessage("Export failed: ${it.message}")
            }
        }
    }

    fun saveLocalSafetyBackup() {
        viewModelScope.launch {
            runCatching {
                val backup = BackupManager.saveSafetyCopy(appContext, repo)
                refreshHealthAndBackups()
                showMessage("Safety copy saved (${backup.sizeLabel})")
            }.onFailure {
                showMessage("Backup failed: ${it.message}")
            }
        }
    }

    fun importFromUri(uri: Uri) {
        viewModelScope.launch {
            runCatching {
                val raw = appContext.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                    ?: throw IllegalStateException("Could not read selected file")
                val res: BackupResult = BackupManager.importRawJson(appContext, repo, raw)
                refreshHealthAndBackups()
                showMessage("Imported ${res.items} items, ${res.receipts} receipts")
            }.onFailure {
                showMessage("Import failed: ${it.message}")
            }
        }
    }

    fun restoreSafetyBackup(backup: LocalBackup) {
        viewModelScope.launch {
            runCatching {
                val raw = File(backup.path).readText()
                val res = BackupManager.importRawJson(appContext, repo, raw)
                refreshHealthAndBackups()
                showMessage("Restored ${res.items} items & ${res.receipts} receipts")
            }.onFailure {
                showMessage("Restore failed: ${it.message}")
            }
        }
    }

    fun checkIntegrity() {
        viewModelScope.launch {
            val res = repo.checkIntegrity()
            _session.update { it.copy(integrityResult = res) }
            showMessage("Database integrity: $res")
        }
    }

    fun optimiseDatabase() {
        viewModelScope.launch {
            repo.optimiseDatabase()
            refreshHealthAndBackups()
            showMessage("Database optimised")
        }
    }

    fun resetAllData() {
        viewModelScope.launch {
            BackupManager.saveSafetyCopy(appContext, repo)
            repo.resetAllShopData()
            clearCart()
            val branches = repo.dao.getActiveBranches()
            _session.update { it.copy(activeBranchId = branches.firstOrNull()?.id) }
            refreshHealthAndBackups()
            showMessage("Demo catalog restored (safety copy kept)")
        }
    }

    fun syncCloud() {
        if (_session.value.syncing) return
        viewModelScope.launch {
            _session.update { it.copy(syncing = true) }
            when (val outcome = CloudSyncClient.syncWithCloud(repo)) {
                is CloudSyncClient.SyncOutcome.Success -> {
                    repo.saveSetting("last_sync_msg", outcome.message)
                    _session.update {
                        it.copy(syncing = false, lastSyncOk = true, lastSyncMessage = outcome.message)
                    }
                    showMessage(outcome.message)
                }
                is CloudSyncClient.SyncOutcome.Offline -> {
                    repo.saveSetting("last_sync_msg", outcome.message)
                    _session.update {
                        it.copy(syncing = false, lastSyncOk = false, lastSyncMessage = outcome.message)
                    }
                    showMessage(outcome.message)
                }
            }
        }
    }
}
