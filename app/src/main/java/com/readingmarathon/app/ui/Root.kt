package com.readingmarathon.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.readingmarathon.app.AppViewModel
import com.readingmarathon.app.ui.screens.BannedScreen
import com.readingmarathon.app.ui.screens.LoadingScreen
import com.readingmarathon.app.ui.screens.NameScreen
import com.readingmarathon.app.ui.screens.Shell
import com.readingmarathon.app.ui.screens.SignInScreen
import com.readingmarathon.app.ui.screens.Splash
import com.readingmarathon.app.ui.theme.MarathonTheme
import kotlinx.coroutines.launch

/** The view model, for every screen. */
val LocalVm = staticCompositionLocalOf<AppViewModel> { error("no view model") }

@Composable
fun MarathonRoot(vm: AppViewModel) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val restoring by vm.restoring.collectAsStateWithLifecycle()
    val account by vm.account.collectAsStateWithLifecycle()
    val profile by vm.profile.collectAsStateWithLifecycle()
    val banned by vm.banned.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        vm.messages.collect { m ->
            snackbar.currentSnackbarData?.dismiss()
            launch {
                val r = snackbar.showSnackbar(m.text, m.actionLabel, duration = if (m.action != null) SnackbarDuration.Long else SnackbarDuration.Short)
                if (r == SnackbarResult.ActionPerformed) m.action?.invoke()
            }
        }
    }

    MarathonTheme(settings.theme) {
        // The app is written in Arabic: always right-to-left, whatever the phone's language.
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl, LocalVm provides vm) {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                val stage = when {
                    restoring -> Stage.SPLASH
                    // An old guest account must link Google first; linking keeps its data.
                    account == null || account?.isGuest == true -> Stage.SIGN_IN
                    banned -> Stage.BANNED
                    profile == null -> Stage.LOADING
                    profile?.name.isNullOrBlank() -> Stage.NAME
                    else -> Stage.APP
                }
                AnimatedContent(stage, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "stage") { s ->
                    when (s) {
                        Stage.SPLASH -> Splash()
                        Stage.SIGN_IN -> SignInScreen(guest = account?.isGuest == true)
                        Stage.BANNED -> BannedScreen()
                        Stage.LOADING -> LoadingScreen()
                        Stage.NAME -> NameScreen()
                        Stage.APP -> Shell()
                    }
                }
                SnackbarHost(
                    snackbar,
                    Modifier.align(Alignment.BottomCenter).navigationBarsPadding().imePadding()
                        .padding(bottom = if (stage == Stage.APP) 76.dp else 12.dp),
                )
            }
        }
    }
}

private enum class Stage { SPLASH, SIGN_IN, BANNED, LOADING, NAME, APP }
