package com.readingmarathon.app

import android.app.Application
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.PersistentCacheSettings
import com.readingmarathon.app.data.Backend
import com.readingmarathon.app.data.FirebaseBackend
import com.readingmarathon.app.data.Prefs
import com.readingmarathon.app.notify.Reminder

class MarathonApp : Application() {

    lateinit var prefs: Prefs
        private set

    /** Accounts and data. Tests replace it through [backendOverride] before the first screen opens. */
    val backend: Backend by lazy {
        backendOverride ?: FirebaseBackend(this, BuildConfig.FIREBASE_CONFIGURED)
    }

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        if (BuildConfig.FIREBASE_CONFIGURED && backendOverride == null) {
            runCatching {
                FirebaseApp.initializeApp(this)
                // Offline cache: logging works without a connection and syncs later.
                FirebaseFirestore.getInstance().firestoreSettings = FirebaseFirestoreSettings.Builder()
                    .setLocalCacheSettings(PersistentCacheSettings.newBuilder().build())
                    .build()
            }
        }
        Reminder.ensureChannel(this)
    }

    companion object {
        @Volatile
        var backendOverride: Backend? = null
    }
}
