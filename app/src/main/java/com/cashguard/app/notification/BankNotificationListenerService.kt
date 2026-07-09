package com.cashguard.app.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.graphics.Color
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.app.NotificationCompat
import com.cashguard.app.MainActivity
import com.cashguard.app.R
import com.cashguard.app.data.CashGuardRepository
import com.cashguard.app.data.CycleCalculator
import com.cashguard.app.data.TransactionEntity
import com.cashguard.app.data.TxType
import com.cashguard.app.guard.PartyGuardManager
import com.cashguard.app.overlay.TransactionPopupWindow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Listens to ALL posted notifications on the device (SMS apps, the BOC
 * banking app, telco apps, etc.) and picks out bank transaction alerts.
 *
 * The user must manually enable this under:
 *   Settings > Apps > Special app access > Notification access > CashGuard
 *
 * We intentionally listen broadly (not just to one messaging app package)
 * because bank alerts can arrive as SMS (via Messages/Samsung Messages) OR
 * as native push notifications from the BOC app itself.
 */
class BankNotificationListenerService : NotificationListenerService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.i(TAG, "Notification listener connected")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        super.onNotificationPosted(sbn)

        // Ignore our own alerts so a parsed transaction can't loop back in
        if (sbn.packageName == packageName) return

        val extras = sbn.notification.extras
        var title = extras.getCharSequence("android.title")?.toString()
        var text = extras.getCharSequence("android.text")?.toString()
            ?: extras.getCharSequence("android.bigText")?.toString()

        // MessagingStyle notifications (Google/Samsung Messages) keep the real
        // content in the messages array — android.title / android.text can be
        // blank when an existing conversation notification is updated.
        @Suppress("DEPRECATION")
        val lastMessage = extras.getParcelableArray("android.messages")
            ?.lastOrNull() as? android.os.Bundle
        if (lastMessage != null) {
            lastMessage.getCharSequence("text")?.toString()
                ?.takeIf { it.isNotBlank() }?.let { text = it }
            if (title.isNullOrBlank()) {
                title = lastMessage.getCharSequence("sender")?.toString()
            }
        }

        // Android 15+ redacts notification content for listeners unless the
        // RECEIVE_SENSITIVE_NOTIFICATIONS app-op is granted (see README).
        if (text?.contains("Sensitive notification content hidden") == true) {
            Log.w(TAG, "Bank alert content was redacted by Android 15 sensitive-notification protection")
            return
        }

        val parsed = BankMessageParser.parse(sbn.packageName, title, text)
        // Never log the message body — it contains account and balance details
        Log.d(TAG, "Notification from ${sbn.packageName} title=$title parsed=${parsed != null}")
        if (parsed == null) return

        scope.launch {
            val repo = CashGuardRepository(applicationContext)
            val settings = repo.settingsRepository.settingsFlow.first()

            // Card purchase alerts often omit the running balance — estimate it
            // from the last known balance instead of storing a bogus figure.
            val balanceAfter = parsed.balanceAfter
                ?: repo.getLatestTransaction()?.let { last ->
                    if (parsed.type == TxType.DEBIT) last.balanceAfter - parsed.amount
                    else last.balanceAfter + parsed.amount
                }
                ?: 0.0

            val entity = TransactionEntity(
                amount = parsed.amount,
                type = parsed.type,
                balanceAfter = balanceAfter,
                merchant = parsed.merchant,
                timestamp = System.currentTimeMillis(),
                source = parsed.source,
                rawMessage = text.orEmpty()
            )

            val insertedId = repo.insertTransaction(entity)
            val savedEntity = entity.copy(id = insertedId)

            val cycle = CycleCalculator.currentCycle(settings.payDays, System.currentTimeMillis())
            repo.tryAutoMatchBudgetItem(savedEntity, cycle.key)

            if (settings.smsAlertsEnabled) {
                val overlayShown = settings.overlayPopupEnabled &&
                    TransactionPopupWindow.canShow(applicationContext)
                if (overlayShown) {
                    // The branded card is the only thing that should appear on
                    // screen. Skip our own notification, AND clear the source
                    // app's banner (e.g. the Messages SMS notification) so it
                    // doesn't sit alongside our card — one alert, not three.
                    TransactionPopupWindow.show(applicationContext, savedEntity)
                    runCatching { cancelNotification(sbn.key) }
                } else {
                    showTransactionAlert(applicationContext, savedEntity)
                }
                // Low balance is a distinct safety warning, shown either way.
                maybeShowLowBalanceWarning(applicationContext, savedEntity, settings.lowBalanceThreshold)
            }

            if (savedEntity.type == TxType.DEBIT) {
                PartyGuardManager(applicationContext, repo).onDebit(savedEntity)
            }
        }
    }

    private fun showTransactionAlert(context: Context, transaction: TransactionEntity) {
        createChannelIfNeeded(context)

        val openIntent = MainActivity.newIntent(context)
        val pendingIntent = android.app.PendingIntent.getActivity(
            context, transaction.id.toInt(), openIntent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )

        val isDebit = transaction.type == TxType.DEBIT
        val amountText = "Rs %,.2f".format(transaction.amount)
        val title = if (isDebit) "$amountText spent" else "$amountText received"

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText("${transaction.source} • Balance: Rs %,.2f".format(transaction.balanceAfter))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setColor(if (isDebit) Color.parseColor("#FF6B6B") else Color.parseColor("#00D9A3"))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        context.getSystemService(NotificationManager::class.java)
            ?.notify(transaction.id.toInt(), builder.build())
    }

    private fun maybeShowLowBalanceWarning(
        context: Context,
        transaction: TransactionEntity,
        lowBalanceThreshold: Double
    ) {
        if (transaction.balanceAfter > lowBalanceThreshold) return
        createChannelIfNeeded(context)
        val warnBuilder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("⚠ Low balance warning")
            .setContentText("Your balance dropped to Rs %,.2f".format(transaction.balanceAfter))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setColor(Color.parseColor("#FF6B6B"))
            .setAutoCancel(true)
            .build()
        context.getSystemService(NotificationManager::class.java)
            ?.notify((transaction.id + 900000).toInt(), warnBuilder)
    }

    private fun createChannelIfNeeded(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            if (manager.getNotificationChannel(CHANNEL_ID) == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "Transaction Alerts",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Real-time bank transaction and low balance alerts"
                    enableVibration(true)
                }
                manager.createNotificationChannel(channel)
            }
        }
    }

    companion object {
        private const val TAG = "CashGuardListener"
        const val CHANNEL_ID = "cashguard_transaction_alerts"
    }
}
