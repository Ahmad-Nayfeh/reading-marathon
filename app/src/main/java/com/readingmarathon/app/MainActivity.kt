package com.readingmarathon.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.readingmarathon.app.ui.MarathonRoot

class MainActivity : ComponentActivity() {

    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handle(intent)
        setContent { MarathonRoot(vm) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    /** Invite links carry the code: https://ahmad-nayfeh.github.io/marathon/join/?code=ABC123 */
    private fun handle(intent: Intent?) {
        val uri = intent?.data ?: return
        vm.offerCode(uri.getQueryParameter("code"))
    }
}
