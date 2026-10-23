package com.readingmarathon.app

import android.app.Application
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.PersistentCacheSettings
import com.readingmarathon.app.data.FirestoreStore
import com.readingmarathon.app.data.LocalStore
import com.readingmarathon.app.data.MarathonStore
import com.readingmarathon.app.data.Mode
import com.readingmarathon.app.data.Prefs
import com.readingmarathon.app.data.Session
import com.readingmarathon.app.notify.Reminder
import com.readingmarathon.core.DemoData
import com.readingmarathon.core.Ids
import com.readingmarathon.core.Marathon
import com.readingmarathon.core.MarathonData
import java.io.File
import java.time.LocalDate

class MarathonApp : Application() {

    lateinit var prefs: Prefs
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        prefs = Prefs(this)
        if (BuildConfig.FIREBASE_CONFIGURED) {
            runCatching {
                FirebaseApp.initializeApp(this)
                FirebaseFirestore.getInstance().firestoreSettings = FirebaseFirestoreSettings.Builder()
                    .setLocalCacheSettings(PersistentCacheSettings.newBuilder().build())
                    .build()
            }
        }
        Reminder.ensureChannel(this)
    }

    fun localFile(marathonId: String): File = File(filesDir, "marathons/$marathonId.json")

    /** Opens the store for a session. Local files are created on first use. */
    fun openStore(session: Session): MarathonStore = when (session.mode) {
        Mode.DEMO -> LocalStore(null, DemoData.build(LocalDate.now()))
        Mode.LOCAL -> {
            val f = localFile(session.marathonId)
            LocalStore(f, LocalStore.load(f) ?: MarathonData(Marathon(session.marathonId, "ماراثون القراءة", Ids.inviteCode(), createdAt = System.currentTimeMillis())))
        }
        Mode.SHARED -> FirestoreStore(FirebaseFirestore.getInstance(), session.marathonId)
    }

    companion object {
        var instance: MarathonApp? = null
            private set
    }
}
