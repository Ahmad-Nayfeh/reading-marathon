package com.readingmarathon.app

import android.app.Activity
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.readingmarathon.app.data.Account
import com.readingmarathon.app.data.AdminOverview
import com.readingmarathon.app.data.Backend
import com.readingmarathon.app.data.GroupPreview
import com.readingmarathon.app.data.Profile
import com.readingmarathon.app.data.Settings
import com.readingmarathon.app.data.ThemeMode
import com.readingmarathon.app.notify.Reminder
import com.readingmarathon.core.Day
import com.readingmarathon.core.Fmt
import com.readingmarathon.core.GroupData
import com.readingmarathon.core.Ids
import com.readingmarathon.core.Limits
import com.readingmarathon.core.RuleViolation
import com.readingmarathon.core.Visibility
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

/** A snackbar message, optionally with one action (e.g. undo). */
data class Message(val text: String, val actionLabel: String? = null, val action: (() -> Unit)? = null)

/** App state: account, the reader's log and groups, phone settings, and messages for the snackbar. */
class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val marathonApp = app as MarathonApp
    private val prefs = marathonApp.prefs
    val backend: Backend = marathonApp.backend

    val configured: Boolean get() = backend.configured
    val account: StateFlow<Account?> = backend.account
    val restoring: StateFlow<Boolean> = backend.restoring
    val profile: StateFlow<Profile?> = backend.profile
    val days: StateFlow<List<Day>> = backend.days
    val groups: StateFlow<Map<String, GroupData>> = backend.groups

    /** The reader's groups in their own order, as far as loaded. */
    val myGroups: StateFlow<List<GroupData>> = combine(backend.profile, backend.groups) { p, g -> p?.groups.orEmpty().mapNotNull { g[it] } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val settings: StateFlow<Settings> = prefs.settings.stateIn(viewModelScope, SharingStarted.Eagerly, Settings())

    private val _messages = MutableSharedFlow<Message>(extraBufferCapacity = 4)
    val messages: SharedFlow<Message> = _messages

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy

    /** An invite code that arrived through a link, waiting for the join screen. */
    private val _pendingCode = MutableStateFlow<String?>(null)
    val pendingCode: StateFlow<String?> = _pendingCode

    private val _admin = MutableStateFlow<AdminOverview?>(null)
    val admin: StateFlow<AdminOverview?> = _admin

    fun today(): LocalDate = LocalDate.now()

    fun say(text: String, actionLabel: String? = null, action: (() -> Unit)? = null) {
        _messages.tryEmit(Message(text, actionLabel, action))
    }

    /** Runs an action; a broken rule becomes a message instead of a crash. */
    fun run(success: String? = null, quiet: Boolean = false, then: () -> Unit = {}, block: suspend () -> Unit) {
        viewModelScope.launch {
            if (!quiet) _busy.value = true
            try {
                block()
                success?.let { say(it) }
                then()
            } catch (e: CancellationException) {
                throw e
            } catch (e: RuleViolation) {
                say(e.message ?: "تعذّر الإكمال.")
            } catch (e: Exception) {
                say(friendly(e))
            } finally {
                if (!quiet) _busy.value = false
            }
        }
    }

    private fun friendly(e: Exception): String {
        val m = e.message.orEmpty()
        return when {
            "PERMISSION_DENIED" in m -> "لا تملك صلاحية هذا الإجراء."
            "UNAVAILABLE" in m || "network" in m.lowercase() -> "لا يوجد اتصال بالإنترنت. حاول مرة أخرى."
            else -> "حدث خطأ غير متوقع. حاول مرة أخرى."
        }
    }

    // ---- Account ----

    fun signInWithGoogle(activity: Activity) = run { backend.signInWithGoogle(activity) }
    fun signInAsGuest() = run { backend.signInAsGuest() }

    fun signOut() = run {
        cancelTimer()
        backend.signOut()
    }

    fun saveProfile(name: String, goal: Int, success: String? = null) = run(success) { backend.saveProfile(name, goal) }

    // ---- Logging ----

    fun minutesOn(date: LocalDate): Int = days.value.firstOrNull { it.date == date }?.minutes ?: 0

    /** One tap: adds to today, with an undo. */
    fun quickAdd(minutes: Int) {
        val date = today()
        val before = minutesOn(date)
        run(quiet = true) {
            val total = backend.addMinutes(date, minutes)
            say("أُضيفت ${Fmt.minutes(minutes)}. مجموع اليوم ${Fmt.minutes(total)}", "تراجع") {
                run(quiet = true) { backend.setDay(date, before) }
            }
        }
    }

    fun setDay(date: LocalDate, minutes: Int, then: () -> Unit = {}) {
        val before = minutesOn(date)
        run(quiet = true, then = then) {
            backend.setDay(date, minutes)
            if (before != minutes) say("حُفظ: ${Fmt.minutes(minutes)} ${if (date == today()) "اليوم" else "في هذا اليوم"}.")
        }
    }

    // ---- Timer ----

    fun startTimer() = viewModelScope.launch {
        val now = System.currentTimeMillis()
        prefs.setTimer(now)
        Reminder.showTimer(getApplication(), now)
    }

    fun stopTimer() = viewModelScope.launch {
        val started = settings.value.timerStartedAt ?: return@launch
        prefs.setTimer(null)
        Reminder.hideTimer(getApplication())
        val minutes = Reminder.timerMinutes(started).coerceAtMost(Limits.MAX_MINUTES_PER_DAY)
        if (minutes < 1) say("أقل من دقيقة، لم يُسجَّل شيء.") else quickAdd(minutes)
    }

    fun cancelTimer() = viewModelScope.launch {
        prefs.setTimer(null)
        Reminder.hideTimer(getApplication())
    }

    // ---- Groups ----

    fun offerCode(code: String?) {
        if (!code.isNullOrBlank()) _pendingCode.value = Ids.normalizeCode(code)
    }

    fun consumeCode() { _pendingCode.value = null }

    fun createGroup(name: String, about: String, visibility: Visibility, then: (String) -> Unit) {
        var id = ""
        run("أُنشئت المجموعة. ادعُ القرّاء برمزها.", then = { then(id) }) { id = backend.createGroup(name, about, visibility) }
    }

    fun findByCode(code: String, then: (GroupPreview) -> Unit) {
        var p: GroupPreview? = null
        run(then = { p?.let(then) }) { p = backend.findByCode(code) }
    }

    fun loadPublic(then: (List<GroupPreview>) -> Unit) {
        var list: List<GroupPreview> = emptyList()
        run(quiet = true, then = { then(list) }) { list = backend.publicGroups() }
    }

    fun join(preview: GroupPreview, code: String?, then: () -> Unit) =
        run("انضممت إلى «${preview.name}».", then = then) { backend.joinGroup(preview.id, code) }

    fun leave(groupId: String, then: () -> Unit) = run("غادرت المجموعة.", then = then) { backend.leaveGroup(groupId) }

    // ---- Platform admin ----

    fun loadAdmin() = run(quiet = true) { _admin.value = backend.adminOverview() }

    // ---- Settings ----

    fun setTheme(t: ThemeMode) = viewModelScope.launch { prefs.setTheme(t) }

    fun setReminder(enabled: Boolean, hour: Int, minute: Int) = viewModelScope.launch {
        prefs.setReminder(enabled, hour, minute)
        Reminder.schedule(getApplication(), enabled, hour, minute)
    }
}
