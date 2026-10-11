package com.example.spotifyreplica.ui.navigation

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState

data class BottomNavItem(
    val screen: Screen,
    val icon: ImageVector,
    val label: String,
)

@Composable
fun BottomNav(navController: NavController) {
    // Only material-icons-core icons are used here (Icons.Default/Icons.Filled).
    val items = listOf(
        BottomNavItem(Screen.Library, Icons.Default.List, "Library"),
        BottomNavItem(Screen.Search, Icons.Default.Search, "Search"),
        BottomNavItem(Screen.Downloads, Icons.Default.KeyboardArrowDown, "Downloads"),
        BottomNavItem(Screen.Settings, Icons.Default.Settings, "Settings"),
    )

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: ""

    NavigationBar(modifier = Modifier.fillMaxWidth()) {
        items.forEach { item ->
            val route = item.screen.route
            val selected = currentRoute == route
            NavigationBarItem(
                selected = selected,
                onClick = {
                    navController.navigate(route) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                icon = { Icon(item.icon, contentDescription = item.label) },
                label = { Text(item.label, fontSize = 12.sp) },
            )
        }
    }
}
