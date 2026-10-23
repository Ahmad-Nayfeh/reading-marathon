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
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.readingmarathon.app.AppViewModel
import com.readingmarathon.app.ui.admin.PlatformAdminScreen
import com.readingmarathon.app.ui.screens.LoadingScreen
import com.readingmarathon.app.ui.screens.MainScreen
import com.readingmarathon.app.ui.screens.MarathonsScreen
import com.readingmarathon.app.ui.screens.PickMemberScreen
import com.readingmarathon.app.ui.screens.SignInScreen
import com.readingmarathon.app.ui.screens.Splash
import com.readingmarathon.app.ui.theme.MarathonTheme

/** Snackbar host shared by every screen. */
val LocalSnackbar = androidx.compose.runtime.staticCompositionLocalOf { SnackbarHostState() }

@Composable
fun MarathonRoot(vm: AppViewModel) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val booted by vm.booted.collectAsStateWithLifecycle()
    val account by vm.account.collectAsStateWithLifecycle()
    val adminPanel by vm.adminPanel.collectAsStateWithLifecycle()
    val session by vm.session.collectAsStateWithLifecycle()
    val data by vm.data.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        vm.messages.collect { snackbar.currentSnackbarData?.dismiss(); snackbar.showSnackbar(it) }
    }

    MarathonTheme(settings.theme) {
        // The app is written in Arabic: always right-to-left, whatever the phone's language.
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl, LocalSnackbar provides snackbar) {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                val stage = when {
                    !booted -> Stage.SPLASH
                    account == null -> Stage.SIGN_IN
                    session == null && adminPanel -> Stage.ADMIN
                    session == null -> Stage.MARATHONS
                    data == null -> Stage.LOADING
                    session?.isOrganizer == false && session?.myMemberId == null -> Stage.PICK_MEMBER
                    else -> Stage.MAIN
                }
                AnimatedContent(stage, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "stage") { s ->
                    when (s) {
                        Stage.SPLASH -> Splash()
                        Stage.SIGN_IN -> SignInScreen(vm)
                        Stage.ADMIN -> PlatformAdminScreen(vm)
                        Stage.MARATHONS -> MarathonsScreen(vm)
                        Stage.LOADING -> LoadingScreen(vm)
                        Stage.PICK_MEMBER -> PickMemberScreen(vm)
                        Stage.MAIN -> MainScreen(vm)
                    }
                }
                SnackbarHost(
                    snackbar,
                    Modifier.align(Alignment.BottomCenter).navigationBarsPadding().imePadding()
                        .padding(bottom = if (stage == Stage.MAIN) 84.dp else 12.dp),
                )
            }
        }
    }
}

private enum class Stage { SPLASH, SIGN_IN, ADMIN, MARATHONS, LOADING, PICK_MEMBER, MAIN }
