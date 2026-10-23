package com.readingmarathon.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.readingmarathon.app.data.MarathonStore
import com.readingmarathon.app.data.Mode
import com.readingmarathon.app.data.Role
import com.readingmarathon.app.data.Session
import com.readingmarathon.app.data.Settings
import com.readingmarathon.app.data.SharedSetup
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
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

/** App state: which marathon is open, its live data, settings, and one-line messages for the snackbar. */
@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val marathonApp = app as MarathonApp
    private val prefs = marathonApp.prefs

    /** null while the saved session is being read. */
    private val _boot = MutableStateFlow(false)
    val booted: StateFlow<Boolean> = _boot

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

    /** An invite code that arrived through a link, waiting for the join screen. */
    private val _pendingCode = MutableStateFlow<String?>(null)
    val pendingCode: StateFlow<String?> = _pendingCode

    val sharedAvailable: Boolean get() = SharedSetup.available

    fun today(): LocalDate = LocalDate.now()

    init {
        viewModelScope.launch {
            prefs.session()?.let { open(it, persist = false) }
            _boot.value = true
        }
    }

    private suspend fun open(s: Session, persist: Boolean = true) {
        if (s.mode == Mode.SHARED && !SharedSetup.available) {
            // A build without Firebase cannot open a shared marathon; fall back to the welcome screen.
            prefs.saveSession(null)
            return
        }
        _store.value?.close()
        _store.value = marathonApp.openStore(s)
        _session.value = s
        if (persist && s.mode != Mode.DEMO) prefs.saveSession(s)
    }

    fun say(text: String) { _messages.tryEmit(text) }

    /** Runs a write; a broken rule becomes a message instead of a crash. Returns true on success. */
    fun act(success: String? = null, then: () -> Unit = {}, block: suspend (MarathonStore) -> Unit) {
        val store = _store.value ?: return
        viewModelScope.launch {
            _busy.value = true
            try {
                block(store)
                success?.let(::say)
                then()
            } catch (e: CancellationException) {
                throw e
            } catch (e: RuleViolation) {
                say(e.message ?: "تعذّر الحفظ.")
            } catch (e: Exception) {
                say(friendly(e))
            } finally {
                _busy.value = false
            }
        }
    }

    private fun friendly(e: Exception): String {
        val m = e.message.orEmpty()
        return when {
            "PERMISSION_DENIED" in m -> "لا تملك صلاحية هذا التعديل."
            "UNAVAILABLE" in m || "network" in m.lowercase() -> "لا يوجد اتصال. سيُحفظ التعديل عند عودة الاتصال."
            else -> "حدث خطأ غير متوقع. حاول مرة أخرى."
        }
    }

    private fun launchSetup(block: suspend () -> Unit) {
        viewModelScope.launch {
            _busy.value = true
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: RuleViolation) {
                say(e.message ?: "تعذّر الإكمال.")
            } catch (e: Exception) {
                say(friendly(e))
            } finally {
                _busy.value = false
            }
        }
    }

    // ---- Starting ----

    /** A marathon kept on this phone. The organiser can also read in it under [readerName]. */
    fun startLocal(name: String, readerName: String?) = launchSetup {
        if (name.isBlank()) throw RuleViolation("اكتب اسم الماراثون.")
        val id = Ids.newId("local_")
        open(Session(Mode.LOCAL, id, Role.ORGANIZER, null))
        val store = _store.value ?: return@launchSetup
        store.renameMarathon(name.trim())
        if (!readerName.isNullOrBlank()) {
            val me = store.addMember(readerName.trim())
            open(Session(Mode.LOCAL, id, Role.ORGANIZER, me.id))
        }
    }

    fun startDemo() = launchSetup {
        open(Session(Mode.DEMO, "demo", Role.ORGANIZER, "m_demo_0"), persist = false)
    }

    fun createShared(name: String, organizerName: String) = launchSetup {
        val (id, memberId) = SharedSetup.create(name, organizerName)
        open(Session(Mode.SHARED, id, Role.ORGANIZER, memberId))
    }

    fun joinShared(code: String) = launchSetup {
        val id = SharedSetup.join(code)
        val role = SharedSetup.roleIn(id) ?: Role.MEMBER
        _pendingCode.value = null
        open(Session(Mode.SHARED, id, role, null))
    }

    fun offerCode(code: String?) {
        if (!code.isNullOrBlank()) _pendingCode.value = Ids.normalizeCode(code)
    }

    /** A member who just joined says who they are in the marathon. */
    fun pickMember(memberId: String) = launchSetup {
        val s = _session.value ?: return@launchSetup
        _store.value?.linkMember(memberId)
        open(s.copy(myMemberId = memberId))
    }

    /** A new reader joining by name (shared mode: the organiser hasn't added them yet). */
    fun joinAsNewMember(name: String) = launchSetup {
        val s = _session.value ?: return@launchSetup
        val store = _store.value ?: return@launchSetup
        val m = store.addMember(name)
        store.linkMember(m.id)
        open(s.copy(myMemberId = m.id))
    }

    /** The organiser reads as one of the members (or stops). */
    fun setMyMember(memberId: String?) = launchSetup {
        val s = _session.value ?: return@launchSetup
        if (memberId != null) _store.value?.linkMember(memberId)
        open(s.copy(myMemberId = memberId))
    }

    /** Marathons kept on this phone, newest first: (id, name). Leaving one never deletes it. */
    fun savedLocal(): List<Pair<String, String>> =
        (java.io.File(marathonApp.filesDir, "marathons").listFiles { f -> f.extension == "json" } ?: emptyArray())
            .sortedByDescending { it.lastModified() }
            .mapNotNull { f -> com.readingmarathon.app.data.LocalStore.load(f)?.let { f.nameWithoutExtension to it.marathon.name } }

    fun openLocal(id: String) = launchSetup {
        open(Session(Mode.LOCAL, id, Role.ORGANIZER, null))
    }

    /** Restores a JSON backup as a marathon on this phone (a fresh copy; the original is untouched). */
    fun restoreBackup(json: String) = launchSetup {
        val parsed = runCatching { com.readingmarathon.core.MarathonJson.decodeFromString(MarathonData.serializer(), json) }.getOrNull()
            ?: throw RuleViolation("الملف ليس نسخة احتياطية صالحة من ماراثون القراءة.")
        val id = Ids.newId("local_")
        com.readingmarathon.app.data.LocalStore.save(marathonApp.localFile(id), parsed.copy(marathon = parsed.marathon.copy(id = id)))
        open(Session(Mode.LOCAL, id, Role.ORGANIZER, null))
        say("استُعيدت النسخة: ${parsed.members.size} قارئاً و${parsed.entries.size} يوم قراءة.")
    }

    fun leave() = launchSetup {
        _store.value?.close()
        _store.value = null
        _session.value = null
        prefs.saveSession(null)
    }

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
