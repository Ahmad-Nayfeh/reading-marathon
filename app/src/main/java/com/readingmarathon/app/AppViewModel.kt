package com.readingmarathon.app

import android.app.Activity
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.readingmarathon.app.data.Account
import com.readingmarathon.app.data.AdminOverview
import com.readingmarathon.app.data.MarathonStore
import com.readingmarathon.app.data.MarathonSummary
import com.readingmarathon.app.data.Role
import com.readingmarathon.app.data.Session
import com.readingmarathon.app.data.Settings
import com.readingmarathon.app.data.ThemeMode
import com.readingmarathon.app.notify.Reminder
import com.readingmarathon.core.Ids
import com.readingmarathon.core.MarathonData
import com.readingmarathon.core.RuleViolation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDate

/** App state: the signed-in account, which marathon is open, its live data, and messages for the snackbar. */
@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val marathonApp = app as MarathonApp
    private val prefs = marathonApp.prefs
    private val backend = marathonApp.backend

    val configured: Boolean get() = backend.configured
    val account: StateFlow<Account?> = backend.account

    private val _sessionLoaded = MutableStateFlow(false)

    /** False while the saved sign-in and session are being restored. */
    val booted: StateFlow<Boolean> = combine(backend.restoring, _sessionLoaded) { restoring, loaded -> !restoring && loaded }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val _session = MutableStateFlow<Session?>(null)
    val session: StateFlow<Session?> = _session

    private val _store = MutableStateFlow<MarathonStore?>(null)
    val data: StateFlow<MarathonData?> = _store
        .flatMapLatest { it?.data ?: flowOf(null) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val settings: StateFlow<Settings> = prefs.settings.stateIn(viewModelScope, SharingStarted.Eagerly, Settings())

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy

    /** The account's marathons, for the picker. null = not loaded yet. */
    private val _mine = MutableStateFlow<List<MarathonSummary>?>(null)
    val mine: StateFlow<List<MarathonSummary>?> = _mine

    /** An invite code that arrived through a link, waiting for the join screen. */
    private val _pendingCode = MutableStateFlow<String?>(null)
    val pendingCode: StateFlow<String?> = _pendingCode

    private val _admin = MutableStateFlow<AdminOverview?>(null)
    val admin: StateFlow<AdminOverview?> = _admin

    /** The platform admin panel is showing (admin account only). */
    private val _adminPanel = MutableStateFlow(false)
    val adminPanel: StateFlow<Boolean> = _adminPanel

    fun today(): LocalDate = LocalDate.now()

    init {
        viewModelScope.launch {
            // Reopen the last marathon only once we know who is signed in.
            backend.restoring.first { !it }
            val saved = prefs.session()
            if (saved != null && backend.account.value != null) {
                runCatching { open(saved, persist = false) }
            }
            _sessionLoaded.value = true
        }
        viewModelScope.launch {
            // Signing out (here or elsewhere) closes whatever was open.
            backend.account.collect { acc ->
                if (acc == null) closeSession(forget = false)
                _mine.value = null
                _admin.value = null
                _adminPanel.value = false
            }
        }
    }

    private suspend fun open(s: Session, persist: Boolean = true) {
        _store.value?.close()
        _store.value = backend.openStore(s.marathonId)
        _session.value = s
        if (persist) prefs.saveSession(s)
    }

    private suspend fun closeSession(forget: Boolean) {
        _store.value?.close()
        _store.value = null
        _session.value = null
        if (forget) prefs.saveSession(null)
    }

    fun say(text: String) { _messages.tryEmit(text) }

    /** Runs a write; a broken rule becomes a message instead of a crash. */
    fun act(success: String? = null, then: () -> Unit = {}, block: suspend (MarathonStore) -> Unit) {
        val store = _store.value ?: return
        run({ block(store); success?.let(::say); then() })
    }

    private fun run(block: suspend () -> Unit, quiet: Boolean = false) {
        viewModelScope.launch {
            if (!quiet) _busy.value = true
            try {
                block()
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

    fun signInWithGoogle(activity: Activity) = run({ backend.signInWithGoogle(activity) })

    fun signInAsGuest() = run({ backend.signInAsGuest() })

    fun signOut() = run({
        closeSession(forget = true)
        backend.signOut()
    })

    // ---- Marathons ----

    fun loadMine() = run({ _mine.value = backend.myMarathons() }, quiet = true)

    fun openMarathon(summary: MarathonSummary) = run({
        // Re-read the role: the organiser may have changed it, or the account was removed.
        val role = backend.roleIn(summary.id) ?: throw RuleViolation("لم تعد عضواً في هذا الماراثون.")
        open(Session(summary.id, role, myMemberIdIn(summary.id)))
    })

    /** The member linked to this account, read once the store has loaded. */
    private suspend fun myMemberIdIn(marathonId: String): String? {
        val uid = backend.account.value?.uid ?: return null
        val store = backend.openStore(marathonId)
        return try {
            withTimeoutOrNull(10_000) { store.data.first { it != null } }?.members?.firstOrNull { it.linkedUid == uid }?.id
        } finally {
            store.close()
        }
    }

    fun createMarathon(name: String, organizerName: String) = run({
        val (id, memberId) = backend.createMarathon(name, organizerName)
        open(Session(id, Role.ORGANIZER, memberId))
    })

    fun joinMarathon(code: String) = run({
        val id = backend.joinMarathon(code)
        _pendingCode.value = null
        val role = backend.roleIn(id) ?: Role.MEMBER
        open(Session(id, role, myMemberIdIn(id)))
    })

    fun offerCode(code: String?) {
        if (!code.isNullOrBlank()) _pendingCode.value = Ids.normalizeCode(code)
    }

    /** Back to the marathon list, or to the admin panel if the marathon was opened from it. */
    fun switchMarathon() = run({
        val fromAdmin = _session.value?.asAdmin == true
        closeSession(forget = true)
        if (fromAdmin) _adminPanel.value = true else loadMine()
    })

    /** A member who just joined says who they are in the marathon. */
    fun pickMember(memberId: String) = run({
        val s = _session.value ?: return@run
        _store.value?.linkMember(memberId)
        open(s.copy(myMemberId = memberId))
    })

    /** A new reader joining by name (the organiser hasn't added them yet). */
    fun joinAsNewMember(name: String) = run({
        val s = _session.value ?: return@run
        val store = _store.value ?: return@run
        val m = store.addMember(name)
        store.linkMember(m.id)
        open(s.copy(myMemberId = m.id))
    })

    /** The organiser reads as one of the members (or stops). */
    fun setMyMember(memberId: String?) = run({
        val s = _session.value ?: return@run
        if (memberId != null) _store.value?.linkMember(memberId)
        open(s.copy(myMemberId = memberId))
    })

    // ---- Platform admin ----

    fun loadAdmin() = run({ _admin.value = backend.adminOverview() }, quiet = true)

    fun openAdminPanel() {
        if (backend.account.value?.isAdmin != true) return
        _adminPanel.value = true
        loadAdmin()
    }

    fun closeAdminPanel() { _adminPanel.value = false }

    /** Opens any marathon from the admin panel, with organiser rights. */
    fun adminOpen(marathonId: String) = run({
        _adminPanel.value = false
        open(Session(marathonId, backend.roleIn(marathonId) ?: Role.ORGANIZER, myMemberIdIn(marathonId), asAdmin = true), persist = false)
    })

    // ---- Settings ----

    fun setTheme(t: ThemeMode) = viewModelScope.launch { prefs.setTheme(t) }

    fun setReminder(enabled: Boolean, hour: Int, minute: Int) = viewModelScope.launch {
        prefs.setReminder(enabled, hour, minute)
        Reminder.schedule(getApplication(), enabled, hour, minute)
    }

    override fun onCleared() {
        _store.value?.close()
    }
}
