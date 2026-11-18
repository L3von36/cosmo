package com.loypos.app.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One recorded salary payment to a staff member — an audit log, written once
 * by the owner in the Back Office and synced to the Plesk MySQL mirror.
 * Staff names are snapshotted so the log stays readable even after a seller
 * account is deleted.
 */
@Entity(tableName = "salary_payments")
data class SalaryPaymentEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "staff_id") val staffId: String,
    @ColumnInfo(name = "staff_name") val staffName: String = "",
    @ColumnInfo(name = "staff_email") val staffEmail: String = "",
    @ColumnInfo(name = "amount_cents") val amountCents: Long = 0L,
    @ColumnInfo(name = "currency_symbol") val currencySymbol: String = "",
    val note: String = "",
    @ColumnInfo(name = "paid_by_name") val paidByName: String = "",
    @ColumnInfo(name = "paid_at") val paidAt: Long = 0L,
)
