package com.example.spotifyreplica.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.example.spotifyreplica.ui.screen.downloads.DownloadsScreen
import com.example.spotifyreplica.ui.screen.library.LibraryScreen
import com.example.spotifyreplica.ui.screen.login.LoginScreen
import com.example.spotifyreplica.ui.screen.player.PlayerScreen
import com.example.spotifyreplica.ui.screen.playlist.PlaylistScreen
import com.example.spotifyreplica.ui.screen.search.SearchScreen
import com.example.spotifyreplica.ui.screen.settings.SettingsScreen

/** Navigation argument carrying a Spotify playlist id into the playlist screen. */
const val PLAYLIST_ID_ARG = "playlistId"

@Composable
fun AppNavHost(
    navController: NavHostController,
    startDestination: String = Screen.Library.route,
) {
    NavHost(navController = navController, startDestination = startDestination) {
        composable(Screen.Library.route) {
            LibraryScreen()
        }
        composable(Screen.Search.route) {
            SearchScreen()
        }
        composable(Screen.Downloads.route) {
            DownloadsScreen()
        }
        composable(Screen.Settings.route) {
            SettingsScreen()
        }
        composable(Screen.Player.route) {
            PlayerScreen()
        }
        composable(Screen.Login.route) {
            LoginScreen()
        }
        composable(
            route = "${Screen.Playlist.route}/{${PLAYLIST_ID_ARG}}",
            arguments = listOf(
                navArgument(PLAYLIST_ID_ARG) {
                    type = NavType.StringType
                    defaultValue = ""
                },
            ),
        ) {
            PlaylistScreen()
        }
    }
}
