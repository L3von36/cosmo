package com.loypos.app.core

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.loypos.app.MainActivity
import com.loypos.app.R
import com.loypos.app.data.LoyPosRepository
import com.loypos.app.data.local.ProductEntity
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

object LowStockNotifier {
    const val CHANNEL_ID = "loypos_low_stock_alerts"
    private const val NOTIFICATION_ID = 4102

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Low Stock Alerts",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "Periodic alerts when inventory falls below your configured threshold."
            }
            manager.createNotificationChannel(channel)
        }
    }

    fun hasNotificationPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return false
        }
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    fun showLowStockNotification(
        context: Context,
        lowStockItems: List<ProductEntity>,
        threshold: Double,
    ): Boolean {
        if (lowStockItems.isEmpty() || !hasNotificationPermission(context)) return false
        ensureChannel(context)

        val tapIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentIntent = PendingIntent.getActivity(
            context,
            0,
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val count = lowStockItems.size
        val title = if (count == 1) {
            "Low stock alert: ${lowStockItems.first().name}"
        } else {
            "$count products below ${formatQty(threshold)} in stock"
        }
        val summaryLine = if (count == 1) {
            val item = lowStockItems.first()
            "Only ${formatQty(item.stock)} left (threshold: ${formatQty(threshold)})"
        } else {
            val preview = lowStockItems.take(3).joinToString(", ") { "${it.name} (${formatQty(it.stock)})" }
            if (count > 3) "$preview +${count - 3} more" else preview
        }

        val inboxStyle = NotificationCompat.InboxStyle()
            .setBigContentTitle(title)
            .setSummaryText("Threshold: < ${formatQty(threshold)}")
        lowStockItems.take(5).forEach { item ->
            val stockText = if (item.stock <= 0.0) "Out of stock" else "${formatQty(item.stock)} left"
            inboxStyle.addLine("${item.name} — $stockText")
        }
        if (count > 5) {
            inboxStyle.addLine("+${count - 5} more items need restocking")
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_low_stock_alert)
            .setContentTitle(title)
            .setContentText(summaryLine)
            .setStyle(inboxStyle)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .build()

        return try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
            true
        } catch (_: SecurityException) {
            false
        }
    }
}

class LowStockCheckWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val repo = LoyPosRepository(applicationContext)
        repo.ensureInitialized()
        val settings = repo.settingsFlow.first()
        if (!settings.lowStockAlertsEnabled) {
            return Result.success()
        }
        val threshold = settings.lowStockThreshold.coerceAtLeast(0.0)
        val lowItems = repo.getLowStockProducts(threshold)
        if (lowItems.isNotEmpty()) {
            LowStockNotifier.showLowStockNotification(applicationContext, lowItems, threshold)
        }
        return Result.success()
    }
}

object LowStockWorkScheduler {
    private const val PERIODIC_WORK_NAME = "loypos_low_stock_periodic_check"
    private const val IMMEDIATE_WORK_NAME = "loypos_low_stock_immediate_check"

    fun schedulePeriodicCheck(context: Context) {
        LowStockNotifier.ensureChannel(context)
        val request = PeriodicWorkRequestBuilder<LowStockCheckWorker>(15, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PERIODIC_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    fun runImmediateCheck(context: Context) {
        LowStockNotifier.ensureChannel(context)
        val request = OneTimeWorkRequestBuilder<LowStockCheckWorker>().build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            IMMEDIATE_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    fun cancelPeriodicCheck(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(PERIODIC_WORK_NAME)
    }
}
