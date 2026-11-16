package com.loypos.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.loypos.app.core.DemoCatalog
import com.loypos.app.core.nowMs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        UserEntity::class,
        BranchEntity::class,
        CategoryEntity::class,
        ProductEntity::class,
        CustomerEntity::class,
        SaleEntity::class,
        SaleItemEntity::class,
        TicketEntity::class,
        TicketLineEntity::class,
        StockMovementEntity::class,
        ShiftEntity::class,
        CashMovementEntity::class,
        SettingEntity::class,
    ],
    version = 3,
    exportSchema = false,
)
abstract class LoyPosDatabase : RoomDatabase() {
    abstract fun dao(): LoyPosDao
    abstract fun productDao(): ProductDao
    abstract fun categoryDao(): CategoryDao
    abstract fun saleDao(): SaleDao

    companion object {
        const val DB_NAME = "loypos.db"

        @Volatile
        private var INSTANCE: LoyPosDatabase? = null

        fun getInstance(context: Context): LoyPosDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    LoyPosDatabase::class.java,
                    DB_NAME,
                )
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .addCallback(object : Callback() {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            super.onCreate(db)
                            CoroutineScope(Dispatchers.IO).launch {
                                seedDefaults(getInstance(context))
                            }
                        }
                    })
                    .build()
                    .also { INSTANCE = it }
            }
        }

        suspend fun seedDefaults(db: LoyPosDatabase) {
            val dao = db.dao()
            val now = nowMs()
            val defaults = SettingsModel()
            dao.upsertSettings(
                listOf(
                    SettingEntity("store_name", defaults.storeName),
                    SettingEntity("currency_symbol", defaults.currencySymbol),
                    SettingEntity("tax_rate", defaults.taxRatePercent.toString()),
                    SettingEntity("tax_included", defaults.taxIncluded.toString()),
                    SettingEntity("receipt_footer", defaults.receiptFooter),
                    SettingEntity("next_receipt_number", defaults.nextReceiptNumber.toString()),
                    SettingEntity("active_branch", "branch-main"),
                )
            )
            dao.upsertBranch(
                BranchEntity(
                    id = "branch-main",
                    name = "Main branch",
                    createdAt = now,
                    archived = false,
                    updatedAt = 0L,
                )
            )
            db.categoryDao().upsertCategories(DemoCatalog.categories)
            db.productDao().upsertProducts(DemoCatalog.items)
            dao.upsertCustomer(DemoCatalog.defaultCustomer(now))
        }
    }
}
