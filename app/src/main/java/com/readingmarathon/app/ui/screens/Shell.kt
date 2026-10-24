package com.readingmarathon.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.LibraryBooks
import androidx.compose.material.icons.automirrored.rounded.LibraryBooks
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.readingmarathon.app.ui.LocalVm
import com.readingmarathon.app.ui.theme.Brand
import java.time.LocalDate

/** Navigation for every screen, plus the day editor that any screen can open. */
class Nav(val controller: NavHostController, private val openDay: (LocalDate, String?) -> Unit) {
    fun back() { controller.popBackStack() }
    fun group(id: String) = controller.navigate("group/$id")
    fun groupSettings(id: String) = controller.navigate("group/$id/settings")
    fun book(groupId: String, challengeId: String?) = controller.navigate("group/$groupId/book" + (challengeId?.let { "?cid=$it" } ?: ""))
    fun create() = controller.navigate("create")
    fun join(code: String? = null) = controller.navigate("join" + (code?.let { "?code=$it" } ?: ""))
    fun admin() = controller.navigate("admin")
    fun day(date: LocalDate, bookId: String? = null) = openDay(date, bookId)
    fun addBook() = controller.navigate("shelf/add")

    /** After leaving or deleting a group: back to the main tabs. */
    fun home() { controller.popBackStack("main", inclusive = false) }
}

val LocalNav = staticCompositionLocalOf<Nav> { error("no nav") }

enum class Tab { HOME, SHELF, GROUPS, ME }

@Composable
fun Shell() {
    val vm = LocalVm.current
    val controller = rememberNavController()
    var editing by remember { mutableStateOf<Pair<LocalDate, String?>?>(null) }
    val nav = remember(controller) { Nav(controller) { d, b -> editing = d to b } }
    val pending by vm.pendingCode.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(Tab.HOME) }

    LaunchedEffect(pending) {
        pending?.let { code ->
            vm.consumeCode()
            nav.join(code)
        }
    }

    CompositionLocalProvider(LocalNav provides nav) {
        NavHost(controller, startDestination = "main", modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            composable("main") {
                Column(Modifier.fillMaxSize()) {
                    Box(Modifier.weight(1f)) {
                        when (tab) {
                            Tab.HOME -> HomeScreen(onGroups = { tab = Tab.GROUPS })
                            Tab.SHELF -> ShelfScreen()
                            Tab.GROUPS -> GroupsScreen()
                            Tab.ME -> MeScreen()
                        }
                    }
                    BottomBar(tab) { tab = it }
                }
            }
            composable("group/{id}") { e -> GroupScreen(e.arguments?.getString("id").orEmpty()) }
            composable("group/{id}/settings") { e -> GroupSettingsScreen(e.arguments?.getString("id").orEmpty()) }
            composable(
                "group/{id}/book?cid={cid}",
                arguments = listOf(navArgument("cid") { type = NavType.StringType; nullable = true; defaultValue = null }),
            ) { e -> ChallengeEditor(e.arguments?.getString("id").orEmpty(), e.arguments?.getString("cid")) }
            composable("create") { CreateGroupScreen() }
            composable(
                "join?code={code}",
                arguments = listOf(navArgument("code") { type = NavType.StringType; nullable = true; defaultValue = null }),
            ) { e -> JoinScreen(e.arguments?.getString("code")) }
            composable("admin") { PlatformAdminScreen() }
            composable("shelf/add") { AddBookScreen() }
        }
        editing?.let { (date, book) -> DaySheet(date, book, onDismiss = { editing = null }) }
    }
}

@Composable
private fun BottomBar(tab: Tab, onSelect: (Tab) -> Unit) {
    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).navigationBarsPadding()) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(Modifier.fillMaxWidth().height(64.dp)) {
            BarItem("الرئيسية", Icons.Rounded.Home, Icons.Outlined.Home, tab == Tab.HOME) { onSelect(Tab.HOME) }
            BarItem("رفّي", Icons.AutoMirrored.Rounded.LibraryBooks, Icons.AutoMirrored.Outlined.LibraryBooks, tab == Tab.SHELF) { onSelect(Tab.SHELF) }
            BarItem("المجموعات", Icons.Rounded.Groups, Icons.Outlined.Groups, tab == Tab.GROUPS) { onSelect(Tab.GROUPS) }
            BarItem("أنا", Icons.Rounded.Person, Icons.Outlined.Person, tab == Tab.ME) { onSelect(Tab.ME) }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.BarItem(label: String, on: ImageVector, off: ImageVector, selected: Boolean, onClick: () -> Unit) {
    Column(
        Modifier.weight(1f).fillMaxSize().clickable(role = Role.Tab, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
    ) {
        Icon(
            if (selected) on else off, contentDescription = null,
            tint = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp),
        )
        Spacer(Modifier.height(2.dp))
        Text(
            label, style = MaterialTheme.typography.labelMedium,
            color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(3.dp))
        Box(Modifier.size(4.dp).clip(CircleShape).background(if (selected) Brand.colors.gold else Color.Transparent))
    }
}
