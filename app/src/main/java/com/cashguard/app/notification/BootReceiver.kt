package com.cashguard.app.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * NotificationListenerService is automatically re-bound by the system after
 * boot as long as notification access was previously granted, so this
 * receiver mainly exists as a hook for any future boot-time initialization
 * (e.g. re-scheduling daily summary alarms).
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            // Reserved for future: reschedule WorkManager daily summary job
        }
    }
}
