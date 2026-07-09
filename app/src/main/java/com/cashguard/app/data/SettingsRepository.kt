package com.cashguard.app.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore by preferencesDataStore(name = "cashguard_settings")

data class AppSettings(
    val smsAlertsEnabled: Boolean = true,
    val dailySummaryEnabled: Boolean = false,
    val lowBalanceThreshold: Double = 5000.0,
    /** Day-of-month paydays (e.g. [10, 25]). Empty = plain calendar month cycle. */
    val payDays: List<Int> = emptyList(),
    // Party Guard
    val partyGuardArmed: Boolean = false,
    val partyGuardCap: Double = 8000.0,
    val partyGuardSessionStart: Long = 0L,
    val partyGuardLastThreshold: Int = 0,
    val partyGuardSnoozeUntil: Long = 0L,
    val velocityAlertsEnabled: Boolean = true,
    val lastVelocityAlertAt: Long = 0L,
    // Truecaller-style popup
    val overlayPopupEnabled: Boolean = true,
    // Onboarding & language ("en" | "si" | "ta")
    val onboardingDone: Boolean = false,
    val language: String = "en"
)

class SettingsRepository(private val context: Context) {

    private object Keys {
        val SMS_ALERTS = booleanPreferencesKey("sms_alerts")
        val DAILY_SUMMARY = booleanPreferencesKey("daily_summary")
        val LOW_BALANCE = doublePreferencesKey("low_balance_threshold")
        val PAY_DAYS = stringPreferencesKey("pay_days") // comma-separated, e.g. "10,25"
        val PG_ARMED = booleanPreferencesKey("party_guard_armed")
        val PG_CAP = doublePreferencesKey("party_guard_cap")
        val PG_SESSION_START = longPreferencesKey("party_guard_session_start")
        val PG_LAST_THRESHOLD = intPreferencesKey("party_guard_last_threshold")
        val PG_SNOOZE_UNTIL = longPreferencesKey("party_guard_snooze_until")
        val VELOCITY_ALERTS = booleanPreferencesKey("velocity_alerts")
        val LAST_VELOCITY_ALERT = longPreferencesKey("last_velocity_alert_at")
        val OVERLAY_POPUP = booleanPreferencesKey("overlay_popup")
        val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
        val LANGUAGE = stringPreferencesKey("language")
    }

    val settingsFlow: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        AppSettings(
            smsAlertsEnabled = prefs[Keys.SMS_ALERTS] ?: true,
            dailySummaryEnabled = prefs[Keys.DAILY_SUMMARY] ?: false,
            lowBalanceThreshold = prefs[Keys.LOW_BALANCE] ?: 5000.0,
            payDays = prefs[Keys.PAY_DAYS]
                ?.split(",")
                ?.mapNotNull { it.trim().toIntOrNull() }
                ?.filter { it in 1..31 }
                ?.distinct()?.sorted()
                ?: emptyList(),
            partyGuardArmed = prefs[Keys.PG_ARMED] ?: false,
            partyGuardCap = prefs[Keys.PG_CAP] ?: 8000.0,
            partyGuardSessionStart = prefs[Keys.PG_SESSION_START] ?: 0L,
            partyGuardLastThreshold = prefs[Keys.PG_LAST_THRESHOLD] ?: 0,
            partyGuardSnoozeUntil = prefs[Keys.PG_SNOOZE_UNTIL] ?: 0L,
            velocityAlertsEnabled = prefs[Keys.VELOCITY_ALERTS] ?: true,
            lastVelocityAlertAt = prefs[Keys.LAST_VELOCITY_ALERT] ?: 0L,
            overlayPopupEnabled = prefs[Keys.OVERLAY_POPUP] ?: true,
            onboardingDone = prefs[Keys.ONBOARDING_DONE] ?: false,
            language = prefs[Keys.LANGUAGE] ?: "en"
        )
    }

    suspend fun setSmsAlerts(enabled: Boolean) {
        context.dataStore.edit { it[Keys.SMS_ALERTS] = enabled }
    }

    suspend fun setDailySummary(enabled: Boolean) {
        context.dataStore.edit { it[Keys.DAILY_SUMMARY] = enabled }
    }

    suspend fun setLowBalanceThreshold(value: Double) {
        context.dataStore.edit { it[Keys.LOW_BALANCE] = value }
    }

    suspend fun setPayDays(days: List<Int>) {
        context.dataStore.edit {
            it[Keys.PAY_DAYS] = days.filter { d -> d in 1..31 }.distinct().sorted().joinToString(",")
        }
    }

    suspend fun armPartyGuard(cap: Double) {
        context.dataStore.edit {
            it[Keys.PG_ARMED] = true
            it[Keys.PG_CAP] = cap
            it[Keys.PG_SESSION_START] = System.currentTimeMillis()
            it[Keys.PG_LAST_THRESHOLD] = 0
            it[Keys.PG_SNOOZE_UNTIL] = 0L
        }
    }

    suspend fun disarmPartyGuard() {
        context.dataStore.edit {
            it[Keys.PG_ARMED] = false
            it[Keys.PG_LAST_THRESHOLD] = 0
            it[Keys.PG_SNOOZE_UNTIL] = 0L
        }
    }

    suspend fun setPartyGuardLastThreshold(pct: Int) {
        context.dataStore.edit { it[Keys.PG_LAST_THRESHOLD] = pct }
    }

    suspend fun snoozePartyGuardUntil(timestamp: Long) {
        context.dataStore.edit { it[Keys.PG_SNOOZE_UNTIL] = timestamp }
    }

    suspend fun setVelocityAlerts(enabled: Boolean) {
        context.dataStore.edit { it[Keys.VELOCITY_ALERTS] = enabled }
    }

    suspend fun setLastVelocityAlertAt(timestamp: Long) {
        context.dataStore.edit { it[Keys.LAST_VELOCITY_ALERT] = timestamp }
    }

    suspend fun setOverlayPopup(enabled: Boolean) {
        context.dataStore.edit { it[Keys.OVERLAY_POPUP] = enabled }
    }

    suspend fun setOnboardingDone() {
        context.dataStore.edit { it[Keys.ONBOARDING_DONE] = true }
    }

    suspend fun setLanguage(code: String) {
        context.dataStore.edit { it[Keys.LANGUAGE] = code }
    }
}
