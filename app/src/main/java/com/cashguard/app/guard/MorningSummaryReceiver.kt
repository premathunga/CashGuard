package com.cashguard.app.guard

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.Color
import androidx.core.app.NotificationCompat
import com.cashguard.app.MainActivity
import com.cashguard.app.R
import com.cashguard.app.data.CashGuardRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Fires at 8:00 AM after a Party Guard night: posts the "morning after"
 * summary of what was spent and auto-disarms the guard so a forgotten
 * session doesn't keep alarming for days.
 */
class MorningSummaryReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val repo = CashGuardRepository(context.applicationContext)
                val settings = repo.settingsRepository.settingsFlow.first()
                if (!settings.partyGuardArmed) return@launch

                val debits = repo.getDebitsSince(settings.partyGuardSessionStart)
                repo.settingsRepository.disarmPartyGuard()

                if (debits.isEmpty()) return@launch
                val total = debits.sumOf { it.amount }
                val merchants = debits.map { it.merchant }.distinct().take(4).joinToString(", ")
                val balance = debits.maxByOrNull { it.timestamp }?.balanceAfter

                PartyGuardManager.createChannels(context)
                val openApp = PendingIntent.getActivity(
                    context, PartyGuardManager.NOTIF_MORNING_SUMMARY,
                    MainActivity.newIntent(context),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                val text = buildString {
                    append("Last night: Rs %,.0f across %d transactions".format(total, debits.size))
                    if (merchants.isNotBlank()) append(" (%s)".format(merchants))
                    if (balance != null) append(". Balance now Rs %,.0f.".format(balance))
                }
                val notification = NotificationCompat.Builder(context, PartyGuardManager.CHANNEL_WARN)
                    .setSmallIcon(R.drawable.ic_notification)
                    .setContentTitle("☀ Morning summary — Party Guard off")
                    .setContentText(text)
                    .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .setColor(Color.parseColor("#6C5CE7"))
                    .setContentIntent(openApp)
                    .setAutoCancel(true)
                    .build()
                context.getSystemService(NotificationManager::class.java)
                    ?.notify(PartyGuardManager.NOTIF_MORNING_SUMMARY, notification)
            } finally {
                pending.finish()
            }
        }
    }
}
