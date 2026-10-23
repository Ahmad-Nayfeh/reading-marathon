package com.readingmarathon.app.ui.screens

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.Badge
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.readingmarathon.app.AppViewModel
import com.readingmarathon.app.data.Session
import com.readingmarathon.app.ui.admin.AdminScreen
import com.readingmarathon.app.ui.admin.ChallengeEditor
import com.readingmarathon.app.ui.theme.Brand
import com.readingmarathon.core.MarathonData
import java.time.LocalDate

/** Everything a screen needs, passed down once. */
class Ctx(
    val vm: AppViewModel,
    val data: MarathonData,
    val session: Session,
    val today: LocalDate,
    val nav: NavHostController,
    /** Opens the log sheet for a member (null = me) on a date (null = today). */
    val log: (memberId: String?, date: LocalDate?) -> Unit,
) {
    val me get() = session.myMemberId?.let(data::member)
    val isOrganizer get() = session.isOrganizer
    fun openChallenge(id: String) = nav.navigate("challenge/$id")
    fun openMember(id: String) = nav.navigate("member/$id")
    fun editChallenge(id: String?) = nav.navigate("edit-challenge/${id ?: "new"}")
}

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab("home", "الرئيسية", Icons.Rounded.Home),
    Tab("board", "المتصدرون", Icons.Rounded.EmojiEvents),
    Tab("challenges", "التحديات", Icons.Rounded.AutoStories),
    Tab("me", "بطاقتي", Icons.Rounded.Badge),
)

@Composable
fun MainScreen(vm: AppViewModel) {
    val data by vm.data.collectAsStateWithLifecycle()
    val session by vm.session.collectAsStateWithLifecycle()
    val d = data ?: return
    val s = session ?: return
    val nav = rememberNavController()
    var logRequest by remember { mutableStateOf<Pair<String?, LocalDate?>?>(null) }
    val ctx = Ctx(vm, d, s, vm.today(), nav) { m, date -> logRequest = m to date }

    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val showBar = tabs.any { it.route == route }

    Scaffold(
        bottomBar = {
            if (showBar) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
                    tabs.forEach { t ->
                        NavigationBarItem(
                            selected = route == t.route,
                            onClick = {
                                nav.navigate(t.route) {
                                    popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(t.icon, null) },
                            label = { Text(t.label) },
                            colors = NavigationBarItemDefaults.colors(
                                indicatorColor = Brand.colors.goldSoft,
                                selectedIconColor = Brand.colors.gold,
                            ),
                        )
                    }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { pad ->
        NavHost(
            nav, startDestination = "home",
            modifier = Modifier.padding(bottom = pad.calculateBottomPadding()),
            enterTransition = { fadeIn() }, exitTransition = { fadeOut() },
        ) {
            composable("home") { HomeScreen(ctx) }
            composable("board") { LeaderboardScreen(ctx) }
            composable("challenges") { ChallengesScreen(ctx) }
            composable("me") { MyCardScreen(ctx) }
            composable("challenge/{id}") { b -> ChallengeDetailScreen(ctx, b.arguments?.getString("id").orEmpty()) }
            composable("member/{id}") { b -> MemberScreen(ctx, b.arguments?.getString("id").orEmpty()) }
            composable("admin") { AdminScreen(ctx) }
            composable("edit-challenge/{id}") { b -> ChallengeEditor(ctx, b.arguments?.getString("id")?.takeIf { it != "new" }) }
            composable("settings") { SettingsScreen(ctx) }
        }
    }

    logRequest?.let { (memberId, date) ->
        LogSheet(ctx, memberId ?: s.myMemberId, date ?: ctx.today) { logRequest = null }
    }
}

/** Standard page: a top bar, then a scrolling column with comfortable margins. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Page(
    title: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable () -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "رجوع") }
                },
                actions = { actions() },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0),
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(top = pad.calculateTopPadding())) {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(PaddingValues(horizontal = 16.dp)),
                content = {
                    content()
                    Spacer(Modifier.height(96.dp))
                },
            )
        }
    }
}
