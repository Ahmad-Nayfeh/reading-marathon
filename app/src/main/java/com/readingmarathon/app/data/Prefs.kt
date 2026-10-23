package com.readingmarathon.app.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.store by preferencesDataStore("marathon_prefs")

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class Settings(
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val reminderEnabled: Boolean = false,
    val reminderHour: Int = 21,
    val reminderMinute: Int = 0,
)

class Prefs(private val context: Context) {
    private object K {
        val mode = stringPreferencesKey("mode")
        val marathonId = stringPreferencesKey("marathon_id")
        val role = stringPreferencesKey("role")
        val memberId = stringPreferencesKey("member_id")
        val theme = stringPreferencesKey("theme")
        val reminderOn = booleanPreferencesKey("reminder_on")
        val reminderH = intPreferencesKey("reminder_h")
        val reminderM = intPreferencesKey("reminder_m")
    }

    suspend fun session(): Session? {
        val p = context.store.data.first()
        val mode = p[K.mode]?.let { runCatching { Mode.valueOf(it) }.getOrNull() } ?: return null
        val id = p[K.marathonId] ?: return null
        val role = p[K.role]?.let { runCatching { Role.valueOf(it) }.getOrNull() } ?: Role.ORGANIZER
        return Session(mode, id, role, p[K.memberId])
    }

    suspend fun saveSession(s: Session?) {
        context.store.edit { p ->
            if (s == null) {
                p.remove(K.mode); p.remove(K.marathonId); p.remove(K.role); p.remove(K.memberId)
            } else {
                p[K.mode] = s.mode.name
                p[K.marathonId] = s.marathonId
                p[K.role] = s.role.name
                if (s.myMemberId != null) p[K.memberId] = s.myMemberId else p.remove(K.memberId)
            }
        }
    }

    val settings: Flow<Settings> = context.store.data.map { p -> p.toSettings() }

    private fun Preferences.toSettings() = Settings(
        theme = this[K.theme]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM,
        reminderEnabled = this[K.reminderOn] ?: false,
        reminderHour = this[K.reminderH] ?: 21,
        reminderMinute = this[K.reminderM] ?: 0,
    )

    suspend fun setTheme(t: ThemeMode) { context.store.edit { it[K.theme] = t.name } }

    suspend fun setReminder(enabled: Boolean, hour: Int, minute: Int) {
        context.store.edit { it[K.reminderOn] = enabled; it[K.reminderH] = hour; it[K.reminderM] = minute }
    }
}
