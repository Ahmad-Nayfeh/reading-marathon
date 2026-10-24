package com.readingmarathon.app.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.store by preferencesDataStore("marathon_prefs")

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Settings that belong to this phone, not to the account. */
data class Settings(
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val reminderEnabled: Boolean = false,
    val reminderHour: Int = 21,
    val reminderMinute: Int = 0,
    /** Epoch millis when the reading timer started, or null when it isn't running. */
    val timerStartedAt: Long? = null,
    /** Announcements the reader has closed. */
    val dismissed: Set<String> = emptySet(),
)

class Prefs(private val context: Context) {
    private object K {
        val theme = stringPreferencesKey("theme")
        val reminderOn = booleanPreferencesKey("reminder_on")
        val reminderH = intPreferencesKey("reminder_h")
        val reminderM = intPreferencesKey("reminder_m")
        val timer = longPreferencesKey("timer_started_at")
        val dismissed = stringSetPreferencesKey("dismissed_announcements")
    }

    val settings: Flow<Settings> = context.store.data.map { p -> p.toSettings() }

    suspend fun current(): Settings = settings.first()

    private fun Preferences.toSettings() = Settings(
        theme = this[K.theme]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM,
        reminderEnabled = this[K.reminderOn] ?: false,
        reminderHour = this[K.reminderH] ?: 21,
        reminderMinute = this[K.reminderM] ?: 0,
        timerStartedAt = this[K.timer],
        dismissed = this[K.dismissed].orEmpty(),
    )

    suspend fun setTheme(t: ThemeMode) { context.store.edit { it[K.theme] = t.name } }

    suspend fun setReminder(enabled: Boolean, hour: Int, minute: Int) {
        context.store.edit { it[K.reminderOn] = enabled; it[K.reminderH] = hour; it[K.reminderM] = minute }
    }

    suspend fun dismiss(id: String) { context.store.edit { it[K.dismissed] = it[K.dismissed].orEmpty() + id } }

    suspend fun setTimer(startedAt: Long?) {
        context.store.edit { if (startedAt == null) it.remove(K.timer) else it[K.timer] = startedAt }
    }
}
