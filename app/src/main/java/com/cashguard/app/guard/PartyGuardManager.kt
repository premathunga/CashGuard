package com.cashguard.app.guard

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import com.cashguard.app.MainActivity
import com.cashguard.app.R
import com.cashguard.app.data.CashGuardRepository
import com.cashguard.app.data.TransactionEntity
import kotlinx.coroutines.flow.first
import java.util.Calendar

/**
 * Party Guard — protection against impulsive night-out spending.
 *
 * Two independent layers, both driven from every parsed DEBIT:
 *  1. Armed session: the user sets a cap before going out. Warnings fire at
 *     50% / 80%, and an alarm-style alert fires at 100% and on EVERY further
 *     transaction until disarmed or snoozed.
 *  2. Velocity detection: even when not armed, 3+ debits or a large sum
 *     within one hour late at night triggers a warning (rate-limited).
 *
 * A third-party app cannot disable the bank card itself, so escalation means
 * loud, repeated, hard-to-ignore alerts — not actual blocking.
 */
class PartyGuardManager(
    private val context: Context,
    private val repository: CashGuardRepository
) {

    suspend fun onDebit(transaction: TransactionEntity) {
        val settings = repository.settingsRepository.settingsFlow.first()
        val now = System.currentTimeMillis()

        if (settings.partyGuardArmed) {
            if (now < settings.partyGuardSnoozeUntil) return

            val sessionSpent = repository
                .getDebitsSince(settings.partyGuardSessionStart)
                .sumOf { it.amount }
            when (val alert = evaluateCap(sessionSpent, settings.partyGuardCap, settings.partyGuardLastThreshold)) {
                CapAlert.NONE -> Unit
                CapAlert.WARN_50, CapAlert.WARN_80 -> {
                    val pct = if (alert == CapAlert.WARN_50) 50 else 80
                    repository.settingsRepository.setPartyGuardLastThreshold(pct)
                    notify(
                        id = NOTIF_CAP_WARN,
                        title = "🎉 Party Guard: $pct% of your cap used",
                        text = "Spent Rs %,.0f of your Rs %,.0f night-out budget.".format(
                            sessionSpent, settings.partyGuardCap
                        ),
                        alarm = false
                    )
                }
                CapAlert.CAP_EXCEEDED -> {
                    if (settings.partyGuardLastThreshold < 100) {
                        repository.settingsRepository.setPartyGuardLastThreshold(100)
                    }
                    // Alarm-style alert on EVERY transaction past the cap — unique
                    // id per transaction so each one demands attention.
                    notify(
                        id = NOTIF_CAP_EXCEEDED_BASE + (transaction.id % 1000).toInt(),
                        title = "🚨 STOP! Party budget exceeded",
                        text = "Rs %,.0f spent — Rs %,.0f OVER your Rs %,.0f cap. Think about the card!".format(
                            sessionSpent, sessionSpent - settings.partyGuardCap, settings.partyGuardCap
                        ),
                        alarm = true
                    )
                }
            }
            scheduleMorningSummary(context)
        } else if (settings.velocityAlertsEnabled) {
            val recentDebits = repository.getDebitsSince(now - VELOCITY_WINDOW_MS)
            val shouldAlert = evaluateVelocity(
                debitCount = recentDebits.size,
                debitSum = recentDebits.sumOf { it.amount },
                hourOfDay = Calendar.getInstance().get(Calendar.HOUR_OF_DAY),
                lastAlertAt = settings.lastVelocityAlertAt,
                now = now
            )
            if (shouldAlert) {
                repository.settingsRepository.setLastVelocityAlertAt(now)
                notify(
                    id = NOTIF_VELOCITY,
                    title = "⚠ You're spending fast tonight",
                    text = "%d transactions (Rs %,.0f) in the last hour. Keep an eye on the card!".format(
                        recentDebits.size, recentDebits.sumOf { it.amount }
                    ),
                    alarm = true
                )
            }
        }
    }

    private fun notify(id: Int, title: String, text: String, alarm: Boolean) {
        createChannels(context)
        val pendingIntent = PendingIntent.getActivity(
            context, id, MainActivity.newIntent(context),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val builder = NotificationCompat.Builder(
            context,
            if (alarm) CHANNEL_ALARM else CHANNEL_WARN
        )
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setColor(Color.parseColor("#FF6B6B"))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
        if (alarm) {
            // Pre-O fallback; on O+ the alarm channel's sound/vibration applies
            builder.setSound(
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
                android.media.AudioManager.STREAM_ALARM
            )
            builder.setVibrate(longArrayOf(0, 600, 300, 600, 300, 600))
        }
        context.getSystemService(NotificationManager::class.java)
            ?.notify(id, builder.build())
    }

    companion object {
        const val CHANNEL_WARN = "cashguard_party_guard_warn"
        const val CHANNEL_ALARM = "cashguard_party_guard_alarm"

        private const val NOTIF_CAP_WARN = 800001
        private const val NOTIF_CAP_EXCEEDED_BASE = 801000
        private const val NOTIF_VELOCITY = 800002
        const val NOTIF_MORNING_SUMMARY = 800003

        const val VELOCITY_WINDOW_MS = 60 * 60 * 1000L          // 1 hour
        const val VELOCITY_MIN_COUNT = 3
        const val VELOCITY_MIN_SUM = 15_000.0
        const val VELOCITY_COOLDOWN_MS = 30 * 60 * 1000L        // 30 min between alerts
        const val NIGHT_START_HOUR = 20                          // 8 PM
        const val NIGHT_END_HOUR = 5                             // 5 AM

        enum class CapAlert { NONE, WARN_50, WARN_80, CAP_EXCEEDED }

        /** Pure decision logic so it's unit-testable without Android. */
        fun evaluateCap(sessionSpent: Double, cap: Double, lastThreshold: Int): CapAlert {
            if (cap <= 0) return CapAlert.NONE
            val pct = (sessionSpent / cap * 100).toInt()
            return when {
                pct >= 100 -> CapAlert.CAP_EXCEEDED
                pct >= 80 && lastThreshold < 80 -> CapAlert.WARN_80
                pct >= 50 && lastThreshold < 50 -> CapAlert.WARN_50
                else -> CapAlert.NONE
            }
        }

        fun isNightTime(hourOfDay: Int): Boolean =
            hourOfDay >= NIGHT_START_HOUR || hourOfDay < NIGHT_END_HOUR

        /** Pure decision logic so it's unit-testable without Android. */
        fun evaluateVelocity(
            debitCount: Int,
            debitSum: Double,
            hourOfDay: Int,
            lastAlertAt: Long,
            now: Long
        ): Boolean {
            if (!isNightTime(hourOfDay)) return false
            if (now - lastAlertAt < VELOCITY_COOLDOWN_MS) return false
            return debitCount >= VELOCITY_MIN_COUNT || debitSum >= VELOCITY_MIN_SUM
        }

        fun createChannels(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            if (manager.getNotificationChannel(CHANNEL_WARN) == null) {
                manager.createNotificationChannel(
                    NotificationChannel(
                        CHANNEL_WARN, "Party Guard warnings", NotificationManager.IMPORTANCE_HIGH
                    ).apply {
                        description = "Budget-cap progress warnings on a night out"
                        enableVibration(true)
                    }
                )
            }
            if (manager.getNotificationChannel(CHANNEL_ALARM) == null) {
                manager.createNotificationChannel(
                    NotificationChannel(
                        CHANNEL_ALARM, "Party Guard alarms", NotificationManager.IMPORTANCE_HIGH
                    ).apply {
                        description = "Loud alarm when the night-out cap is blown"
                        enableVibration(true)
                        vibrationPattern = longArrayOf(0, 600, 300, 600, 300, 600)
                        setSound(
                            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
                            AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_ALARM)
                                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                                .build()
                        )
                    }
                )
            }
        }

        /**
         * Schedules the morning-after summary at the next 8:00 AM.
         * Repeated calls just overwrite the same PendingIntent.
         */
        fun scheduleMorningSummary(context: Context) {
            val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
            val intent = Intent(context, MorningSummaryReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val next8am = Calendar.getInstance().apply {
                if (get(Calendar.HOUR_OF_DAY) >= 8) add(Calendar.DAY_OF_MONTH, 1)
                set(Calendar.HOUR_OF_DAY, 8)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
            }.timeInMillis
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next8am, pendingIntent)
        }
    }
}
