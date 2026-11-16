package com.loypos.app.data

import android.content.Context
import androidx.room.withTransaction
import com.loypos.app.core.AuthCrypto
import com.loypos.app.core.DemoCatalog
import com.loypos.app.core.DiscountInput
import com.loypos.app.core.computeTotals
import com.loypos.app.core.newId
import com.loypos.app.core.nowMs
import com.loypos.app.core.pointsForTotal
import com.loypos.app.core.startOfTodayMs
import com.loypos.app.data.local.BranchEntity
import com.loypos.app.data.local.CartLine
import com.loypos.app.data.local.CashMovementEntity
import com.loypos.app.data.local.CategoryEntity
import com.loypos.app.data.local.CustomerEntity
import com.loypos.app.data.local.DailyRevenuePoint
import com.loypos.app.data.local.DbStats
import com.loypos.app.data.local.ItemEntity
import com.loypos.app.data.local.LoyPosDatabase
import com.loypos.app.data.local.ReceiptEntity
import com.loypos.app.data.local.ReceiptLineEntity
import com.loypos.app.data.local.ReceiptWithLines
import com.loypos.app.data.local.SaleEntity
import com.loypos.app.data.local.SettingEntity
import com.loypos.app.data.local.SettingsModel
import com.loypos.app.data.local.ShiftEntity
import com.loypos.app.data.local.ShiftStats
import com.loypos.app.data.local.StockMovementEntity
import com.loypos.app.data.local.TicketEntity
import com.loypos.app.data.local.TicketLineEntity
import com.loypos.app.data.local.TicketWithLines
import com.loypos.app.data.local.UserEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Calendar

class LoyPosRepository(private val context: Context) {
    private val db = LoyPosDatabase.getInstance(context)
    val dao = db.dao()
    val productDao = db.productDao()
    val categoryDao = db.categoryDao()
    val saleDao = db.saleDao()
    val saleItemDao = db.saleItemDao()
    private val seedMutex = Mutex()

    suspend fun ensureInitialized() = seedMutex.withLock {
        val existingSettings = dao.getAllSettings()
        if (existingSettings.none { it.key == "store_name" }) {
            LoyPosDatabase.seedDefaults(db)
        }
    }

    val settingsFlow: Flow<SettingsModel> = dao.observeSettings().map { list ->
        val map = list.associate { it.key to it.value }
        SettingsModel(
            storeName = map["store_name"] ?: "My Store",
            currencySymbol = map["currency_symbol"] ?: "Br",
            taxRatePercent = map["tax_rate"]?.toDoubleOrNull() ?: 0.0,
            taxIncluded = map["tax_included"] == "true",
            receiptFooter = map["receipt_footer"] ?: "Thank you for shopping with us!",
            nextReceiptNumber = map["next_receipt_number"]?.toIntOrNull() ?: 1,
        )
    }

    val rawSettingsFlow: Flow<Map<String, String>> =
        dao.observeSettings().map { list -> list.associate { it.key to it.value } }

    val branchesFlow: Flow<List<BranchEntity>> = dao.observeActiveBranches()
    val categoriesFlow: Flow<List<CategoryEntity>> = categoryDao.observeCategories()
    val itemsFlow: Flow<List<ItemEntity>> = productDao.observeProducts()

    fun observeProductsFiltered(query: String): Flow<List<ItemEntity>> {
        val trimmed = query.trim()
        return if (trimmed.isEmpty()) productDao.observeProducts() else productDao.searchProducts(trimmed)
    }
    val customersFlow: Flow<List<CustomerEntity>> = dao.observeCustomers()
    val staffFlow: Flow<List<UserEntity>> = dao.observeStaff()
    val stockMovementsFlow: Flow<List<StockMovementEntity>> = dao.observeRecentStockMovements()
    val shiftsFlow: Flow<List<ShiftEntity>> = dao.observeShifts()
    val cashMovementsFlow: Flow<List<CashMovementEntity>> = dao.observeCashMovements()

    val receiptsWithLinesFlow: Flow<List<ReceiptWithLines>> = saleDao.observeSalesWithItems()

    fun observeCompletedSalesSince(sinceMs: Long): Flow<List<SaleEntity>> =
        saleDao.observeCompletedSalesSince(sinceMs)

    fun computeDailyRevenueTrend(
        sales: List<SaleEntity>,
        days: Int,
        branchId: String?,
    ): List<DailyRevenuePoint> {
        val clampedDays = days.coerceIn(1, 90)
        val todayStart = startOfTodayMs()
        val cal = Calendar.getInstance()
        val dayLabels = arrayOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
        val monthNames = arrayOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
        val filtered = sales.filter { sale ->
            sale.isCompleted && (branchId == null || sale.branchId == null || sale.branchId == branchId)
        }
        return ((clampedDays - 1) downTo 0).map { offset ->
            val start = todayStart - offset * 86_400_000L
            val end = start + 86_400_000L
            cal.timeInMillis = start
            val label = if (clampedDays <= 7) {
                dayLabels[cal.get(Calendar.DAY_OF_WEEK) - 1]
            } else {
                "${monthNames[cal.get(Calendar.MONTH)]} ${cal.get(Calendar.DAY_OF_MONTH)}"
            }
            val daySales = filtered.filter { it.createdAt in start until end }
            DailyRevenuePoint(
                dayStartMs = start,
                label = label,
                totalCents = daySales.sumOf { it.totalCents },
                salesCount = daySales.size,
            )
        }
    }

    val ticketsWithLinesFlow: Flow<List<TicketWithLines>> =
        combine(dao.observeTickets(), dao.observeAllTicketLines()) { tickets, lines ->
            val byTicket = lines.groupBy { it.ticketId }
            tickets.map { t -> TicketWithLines(t, byTicket[t.id].orEmpty()) }
        }

    // --- Settings ---
    suspend fun loadRawSettings(): Map<String, String> =
        dao.getAllSettings().associate { it.key to it.value }

    suspend fun saveSetting(key: String, value: String) {
        dao.upsertSetting(SettingEntity(key, value))
    }

    suspend fun saveSettings(
        storeName: String? = null,
        currencySymbol: String? = null,
        taxRatePercent: Double? = null,
        taxIncluded: Boolean? = null,
        receiptFooter: String? = null,
    ) {
        val now = nowMs()
        if (storeName != null) saveSetting("store_name", storeName)
        if (currencySymbol != null) saveSetting("currency_symbol", currencySymbol)
        if (taxRatePercent != null) saveSetting("tax_rate", taxRatePercent.toString())
        if (taxIncluded != null) saveSetting("tax_included", taxIncluded.toString())
        if (receiptFooter != null) saveSetting("receipt_footer", receiptFooter)
        saveSetting("cloud_settings_ts", now.toString())
    }

    // --- Auth ---
    suspend fun currentSessionUser(): UserEntity? {
        val id = dao.getSetting("session_user") ?: return null
        if (id.isEmpty()) return null
        val user = dao.getUserById(id)
        if (user == null || !user.active) {
            dao.upsertSetting(SettingEntity("session_user", ""))
            return null
        }
        return user
    }

    suspend fun anyUsersExist(): Boolean = dao.countUsers() > 0

    suspend fun signUpOwner(
        emailRaw: String,
        password: String,
        shopName: String,
        ownerName: String,
        branchName: String,
    ): Result<UserEntity> {
        val email = AuthCrypto.normalizeEmail(emailRaw)
        if (!AuthCrypto.isValidEmail(email)) {
            return Result.failure(IllegalArgumentException("Enter a valid email address"))
        }
        if (password.length < 8) {
            return Result.failure(IllegalArgumentException("Password must be at least 8 characters"))
        }
        if (dao.getUserByEmail(email) != null) {
            return Result.failure(IllegalArgumentException("An account with this email already exists"))
        }
        val salt = AuthCrypto.newSalt()
        val hash = AuthCrypto.hashPassword(password, salt)
        val now = nowMs()
        val user = UserEntity(
            id = newId(),
            email = email,
            shopName = shopName.trim(),
            ownerName = ownerName.trim(),
            passwordHash = hash,
            salt = salt,
            createdAt = now,
            role = UserEntity.ROLE_OWNER,
            branchId = null,
            active = true,
            updatedAt = now,
        )
        db.withTransaction {
            dao.upsertUser(user)
            dao.upsertSetting(SettingEntity("session_user", user.id))
            if (shopName.trim().isNotEmpty()) {
                dao.upsertSetting(SettingEntity("store_name", shopName.trim()))
            }
            val cleanBranch = branchName.trim().ifEmpty { "Main branch" }
            val branches = dao.getActiveBranches()
            if (branches.isNotEmpty()) {
                val first = branches.first()
                dao.upsertBranch(first.copy(name = cleanBranch, updatedAt = now))
                dao.upsertSetting(SettingEntity("active_branch", first.id))
            } else {
                val b = BranchEntity(id = newId(), name = cleanBranch, createdAt = now, updatedAt = now)
                dao.upsertBranch(b)
                dao.upsertSetting(SettingEntity("active_branch", b.id))
            }
        }
        return Result.success(user)
    }

    suspend fun signIn(emailRaw: String, password: String): Result<UserEntity> {
        val email = AuthCrypto.normalizeEmail(emailRaw)
        if (email.isEmpty() || password.isEmpty()) {
            return Result.failure(IllegalArgumentException("Enter email and password"))
        }
        val user = dao.getUserByEmail(email)
            ?: return Result.failure(IllegalArgumentException("Incorrect email or password"))
        val computed = AuthCrypto.hashPassword(password, user.salt)
        if (!AuthCrypto.safeEquals(computed, user.passwordHash)) {
            return Result.failure(IllegalArgumentException("Incorrect email or password"))
        }
        if (!user.active) {
            return Result.failure(IllegalArgumentException("This account has been deactivated by the owner"))
        }
        dao.upsertSetting(SettingEntity("session_user", user.id))
        if (user.isCashier && !user.branchId.isNullOrEmpty()) {
            dao.upsertSetting(SettingEntity("active_branch", user.branchId))
        }
        return Result.success(user)
    }

    suspend fun signOut() {
        dao.upsertSetting(SettingEntity("session_user", ""))
    }

    suspend fun addCashier(
        name: String,
        emailRaw: String,
        password: String,
        branchId: String,
        shopName: String,
    ): Result<UserEntity> {
        val cleanName = name.trim()
        val email = AuthCrypto.normalizeEmail(emailRaw)
        if (cleanName.isEmpty()) {
            return Result.failure(IllegalArgumentException("Seller name is required"))
        }
        if (!AuthCrypto.isValidEmail(email)) {
            return Result.failure(IllegalArgumentException("Enter a valid email or username@shop format"))
        }
        if (password.length < 6) {
            return Result.failure(IllegalArgumentException("PIN/password must be at least 6 characters"))
        }
        if (dao.getUserByEmail(email) != null) {
            return Result.failure(IllegalArgumentException("A user with this email already exists"))
        }
        val salt = AuthCrypto.newSalt()
        val now = nowMs()
        val user = UserEntity(
            id = newId(),
            email = email,
            shopName = shopName,
            ownerName = cleanName,
            passwordHash = AuthCrypto.hashPassword(password, salt),
            salt = salt,
            createdAt = now,
            role = UserEntity.ROLE_CASHIER,
            branchId = branchId,
            active = true,
            updatedAt = now,
        )
        dao.upsertUser(user)
        return Result.success(user)
    }

    suspend fun setStaffActive(id: String, active: Boolean) {
        dao.setUserActive(id, active, nowMs())
    }

    suspend fun deleteStaff(id: String) {
        dao.deleteUserById(id)
    }

    // --- Branches ---
    suspend fun saveBranch(branch: BranchEntity) {
        dao.upsertBranch(branch.copy(updatedAt = nowMs()))
    }

    suspend fun archiveBranch(id: String): Result<Unit> {
        val active = dao.getActiveBranches()
        if (active.size <= 1) {
            return Result.failure(IllegalStateException("Keep at least one active branch"))
        }
        val now = nowMs()
        dao.archiveBranch(id, now)
        val remaining = active.firstOrNull { it.id != id }
        if (remaining != null) {
            val currentActive = dao.getSetting("active_branch")
            if (currentActive == id) {
                saveSetting("active_branch", remaining.id)
            }
        }
        return Result.success(Unit)
    }

    // --- Categories ---
    suspend fun saveCategory(category: CategoryEntity) {
        dao.upsertCategory(category.copy(updatedAt = nowMs()))
    }

    suspend fun deleteCategory(id: String) {
        db.withTransaction {
            dao.clearCategoryOnItems(id, nowMs())
            dao.deleteCategoryById(id)
        }
    }

    // --- Items & Stock (via ProductDao CRUD) ---
    suspend fun saveItem(item: ItemEntity) {
        productDao.upsertProduct(item.copy(updatedAt = nowMs()))
    }

    suspend fun deleteItem(id: String) {
        productDao.archiveProduct(id, nowMs())
    }

    suspend fun adjustStock(itemId: String, newStock: Double, type: String = "adjust", note: String = "") {
        val current = productDao.getProductById(itemId) ?: return
        val delta = newStock - current.stock
        val now = nowMs()
        db.withTransaction {
            productDao.updateProductStock(itemId, newStock, now)
            dao.insertStockMovement(
                StockMovementEntity(
                    id = newId(),
                    itemId = itemId,
                    qty = delta,
                    type = type,
                    createdAt = now,
                    note = note,
                )
            )
        }
    }

    suspend fun receiveStock(itemId: String, addedQty: Double, note: String = "") {
        val current = productDao.getProductById(itemId) ?: return
        val now = nowMs()
        db.withTransaction {
            productDao.updateProductStock(itemId, current.stock + addedQty, now)
            dao.insertStockMovement(
                StockMovementEntity(
                    id = newId(),
                    itemId = itemId,
                    qty = addedQty,
                    type = "receive",
                    createdAt = now,
                    note = note,
                )
            )
        }
    }

    // --- Customers ---
    suspend fun saveCustomer(customer: CustomerEntity) {
        dao.upsertCustomer(customer.copy(updatedAt = nowMs()))
    }

    suspend fun deleteCustomer(id: String) {
        dao.deleteCustomerById(id)
    }

    // --- Tickets ---
    suspend fun saveTicket(
        ticketId: String?,
        name: String,
        lines: List<CartLine>,
        discount: DiscountInput?,
        customer: CustomerEntity?,
        note: String,
        branchId: String?,
    ): TicketEntity {
        val id = ticketId ?: newId()
        val now = nowMs()
        val ticket = TicketEntity(
            id = id,
            createdAt = now,
            name = name,
            discountType = discount?.type,
            discountValue = discount?.value,
            customerId = customer?.id,
            customerName = customer?.name,
            note = note,
            branchId = branchId,
        )
        val ticketLines = lines.map {
            TicketLineEntity(
                id = newId(),
                ticketId = id,
                itemId = it.itemId,
                name = it.name,
                priceCents = it.priceCents,
                qty = it.qty,
            )
        }
        db.withTransaction {
            dao.upsertTicket(ticket)
            dao.deleteTicketLines(id)
            dao.insertTicketLines(ticketLines)
        }
        return ticket
    }

    suspend fun deleteTicket(ticketId: String) {
        db.withTransaction {
            dao.deleteTicketLines(ticketId)
            dao.deleteTicketById(ticketId)
        }
    }

    // --- Complete Sale & Refund ---
    suspend fun completeSale(
        lines: List<CartLine>,
        discount: DiscountInput?,
        customer: CustomerEntity?,
        note: String,
        paymentType: String,
        paidCents: Long,
        settings: SettingsModel,
        branchId: String?,
        cashier: UserEntity?,
        openTicketId: String?,
    ): ReceiptWithLines {
        val totals = computeTotals(
            lineTotalsCents = lines.map { it.lineTotalCents },
            discount = discount,
            taxRatePercent = settings.taxRatePercent,
            taxIncluded = settings.taxIncluded,
        )
        val changeCents = (paidCents - totals.totalCents).coerceAtLeast(0L)
        val now = nowMs()
        val receiptId = newId()

        return db.withTransaction {
            val number = (dao.getSetting("next_receipt_number")?.toIntOrNull() ?: settings.nextReceiptNumber)
                .coerceAtLeast(1)
            dao.upsertSetting(SettingEntity("next_receipt_number", (number + 1).toString()))

            val receipt = ReceiptEntity(
                id = receiptId,
                number = number,
                createdAt = now,
                subtotalCents = totals.subtotalCents,
                discountCents = totals.discountCents,
                discountType = discount?.type,
                discountValue = discount?.value,
                taxCents = totals.taxCents,
                totalCents = totals.totalCents,
                paidCents = paidCents,
                changeCents = changeCents,
                paymentType = paymentType,
                customerId = customer?.id,
                customerName = customer?.name,
                status = "completed",
                note = note,
                branchId = branchId,
                cashierId = cashier?.id,
                cashierName = cashier?.ownerName?.ifEmpty { cashier.email },
                updatedAt = now,
            )
            val receiptLines = lines.map {
                ReceiptLineEntity(
                    id = newId(),
                    receiptId = receiptId,
                    itemId = it.itemId,
                    name = it.name,
                    priceCents = it.priceCents,
                    qty = it.qty,
                    lineTotalCents = it.lineTotalCents,
                )
            }
            saleDao.insertSale(receipt)
            saleItemDao.insertSaleItems(receiptLines)

            for (l in lines) {
                productDao.adjustProductStock(l.itemId, -l.qty, now)
                dao.insertStockMovement(
                    StockMovementEntity(
                        id = newId(),
                        itemId = l.itemId,
                        qty = -l.qty,
                        type = "sale",
                        createdAt = now,
                        note = "Receipt #${number.toString().padStart(4, '0')}",
                        receiptId = receiptId,
                    )
                )
            }

            if (customer != null) {
                val pts = pointsForTotal(totals.totalCents)
                if (pts > 0) dao.addCustomerPoints(customer.id, pts, now)
            }

            if (!openTicketId.isNullOrEmpty()) {
                dao.deleteTicketLines(openTicketId)
                dao.deleteTicketById(openTicketId)
            }

            ReceiptWithLines(receipt, receiptLines)
        }
    }

    suspend fun refundReceipt(receiptId: String) {
        val now = nowMs()
        db.withTransaction {
            val saleWithItems = saleDao.getSaleWithItems(receiptId) ?: return@withTransaction
            val receipt = saleWithItems.receipt
            if (!receipt.isCompleted) return@withTransaction
            val lines = saleItemDao.getSaleItemsForSale(receiptId)
            saleDao.markSaleRefunded(receiptId, now)
            for (l in lines) {
                productDao.adjustProductStock(l.itemId, l.qty, now)
                dao.insertStockMovement(
                    StockMovementEntity(
                        id = newId(),
                        itemId = l.itemId,
                        qty = l.qty,
                        type = "refund",
                        createdAt = now,
                        note = "Refund #${receipt.number.toString().padStart(4, '0')}",
                        receiptId = receiptId,
                    )
                )
            }
        }
    }

    // --- Shifts ---
    suspend fun openShift(openingFloatCents: Long, branchId: String?): ShiftEntity {
        val shift = ShiftEntity(
            id = newId(),
            openedAt = nowMs(),
            openingFloatCents = openingFloatCents,
            branchId = branchId,
        )
        dao.upsertShift(shift)
        return shift
    }

    suspend fun addCashMovement(shiftId: String, type: String, amountCents: Long, note: String) {
        dao.insertCashMovement(
            CashMovementEntity(
                id = newId(),
                shiftId = shiftId,
                type = type,
                amountCents = amountCents,
                note = note.trim(),
                createdAt = nowMs(),
            )
        )
    }

    suspend fun closeShift(shiftId: String, countedCents: Long, expectedCents: Long) {
        dao.closeShift(shiftId, nowMs(), countedCents, expectedCents)
    }

    fun computeShiftStats(
        shift: ShiftEntity,
        receipts: List<ReceiptEntity>,
        movements: List<CashMovementEntity>,
    ): ShiftStats {
        val endMs = shift.closedAt ?: Long.MAX_VALUE
        val branchReceipts = receipts.filter { r ->
            r.createdAt >= shift.openedAt && r.createdAt <= endMs &&
                (shift.branchId == null || r.branchId == null || r.branchId == shift.branchId)
        }
        var cashSales = 0L
        var cardSales = 0L
        var salesCount = 0
        var refundsCount = 0
        for (r in branchReceipts) {
            if (r.isCompleted) {
                salesCount++
                if (r.paymentType == "card") cardSales += r.totalCents
                else cashSales += r.totalCents
            } else {
                refundsCount++
            }
        }
        val shiftMoves = movements.filter { it.shiftId == shift.id }
        val cashIn = shiftMoves.filter { it.isCashIn }.sumOf { it.amountCents }
        val cashOut = shiftMoves.filter { !it.isCashIn }.sumOf { it.amountCents }
        return ShiftStats(
            cashSalesCents = cashSales,
            cardSalesCents = cardSales,
            salesCount = salesCount,
            refundsCount = refundsCount,
            cashInCents = cashIn,
            cashOutCents = cashOut,
            movements = shiftMoves,
        )
    }

    // --- Storage Health, Import Replace, Reset ---
    suspend fun getDbStats(): DbStats = withContext(Dispatchers.IO) {
        val dbFile = context.getDatabasePath(LoyPosDatabase.DB_NAME)
        val sizeBytes = if (dbFile.exists()) dbFile.length() else 0L
        DbStats(
            branches = dao.countBranches(),
            categories = dao.countCategories(),
            items = dao.countItems(),
            customers = dao.countCustomers(),
            receipts = dao.countReceipts(),
            tickets = dao.countTickets(),
            movements = dao.countMovements(),
            shifts = dao.countShifts(),
            fileBytes = sizeBytes,
        )
    }

    suspend fun checkIntegrity(): String = withContext(Dispatchers.IO) {
        val writable = db.openHelper.writableDatabase
        writable.query("PRAGMA integrity_check").use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) ?: "unknown" else "unknown"
        }
    }

    suspend fun optimiseDatabase() = withContext(Dispatchers.IO) {
        val writable = db.openHelper.writableDatabase
        writable.execSQL("VACUUM")
        writable.execSQL("ANALYZE")
    }

    suspend fun importReplace(
        settings: Map<String, String>,
        branches: List<BranchEntity>,
        categories: List<CategoryEntity>,
        items: List<ItemEntity>,
        customers: List<CustomerEntity>,
        receipts: List<ReceiptEntity>,
        receiptLines: List<ReceiptLineEntity>,
        tickets: List<TicketEntity>,
        ticketLines: List<TicketLineEntity>,
        movements: List<StockMovementEntity>,
    ) {
        db.withTransaction {
            dao.deleteAllTicketLines()
            dao.deleteAllTickets()
            dao.deleteAllReceiptLines()
            dao.deleteAllReceipts()
            dao.deleteAllStockMovements()
            dao.deleteAllItems()
            dao.deleteAllCategories()
            dao.deleteAllCustomers()
            dao.deleteAllBranches()
            dao.deleteShopSettings()

            val skipKeys = setOf("session_user", "cloud_team_id")
            val settingEntities = settings.entries
                .filter { it.key !in skipKeys }
                .map { SettingEntity(it.key, it.value) }
            if (settingEntities.isNotEmpty()) dao.upsertSettings(settingEntities)

            if (branches.isNotEmpty()) {
                dao.upsertBranches(branches)
            } else {
                dao.upsertBranch(
                    BranchEntity(id = "branch-main", name = "Main branch", createdAt = nowMs())
                )
            }
            val validCategoryIds = categories.map { it.id }.toSet()
            val sanitizedItems = items.map { item ->
                if (item.categoryId != null && item.categoryId !in validCategoryIds) {
                    item.copy(categoryId = null)
                } else {
                    item
                }
            }
            val validCustomerIds = customers.map { it.id }.toSet()
            val sanitizedReceipts = receipts.map { receipt ->
                if (receipt.customerId != null && receipt.customerId !in validCustomerIds) {
                    receipt.copy(customerId = null)
                } else {
                    receipt
                }
            }

            if (categories.isNotEmpty()) categoryDao.upsertCategories(categories)
            if (sanitizedItems.isNotEmpty()) productDao.upsertProducts(sanitizedItems)
            if (customers.isNotEmpty()) dao.upsertCustomers(customers)
            if (sanitizedReceipts.isNotEmpty()) dao.upsertReceipts(sanitizedReceipts)
            if (receiptLines.isNotEmpty()) saleItemDao.insertSaleItems(receiptLines)
            if (tickets.isNotEmpty()) dao.upsertTickets(tickets)
            if (ticketLines.isNotEmpty()) dao.insertTicketLines(ticketLines)
            if (movements.isNotEmpty()) dao.insertStockMovements(movements)
        }
    }

    suspend fun resetAllShopData() {
        db.withTransaction {
            dao.deleteAllTicketLines()
            dao.deleteAllTickets()
            dao.deleteAllReceiptLines()
            dao.deleteAllReceipts()
            dao.deleteAllStockMovements()
            dao.deleteAllCashMovements()
            dao.deleteAllShifts()
            dao.deleteAllItems()
            dao.deleteAllCategories()
            dao.deleteAllCustomers()
            dao.deleteAllBranches()
            dao.deleteShopSettings()
            LoyPosDatabase.seedDefaults(db)
        }
    }
}
