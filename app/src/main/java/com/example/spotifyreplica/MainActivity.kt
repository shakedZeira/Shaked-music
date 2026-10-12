package com.example.spotifyreplica

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.example.spotifyreplica.ui.components.MiniPlayerBar
import com.example.spotifyreplica.ui.navigation.AppNavHost
import com.example.spotifyreplica.ui.navigation.BottomNav
import com.example.spotifyreplica.ui.navigation.Screen
import com.example.spotifyreplica.ui.theme.SpotifyReplicaTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SpotifyReplicaTheme {
                SpotifyReplicaApp()
            }
        }
    }
}

@Composable
private fun SpotifyReplicaApp() {
    val navController = rememberNavController()
    Scaffold(
        bottomBar = {
            Column {
                MiniPlayerBar(
                    onOpenPlayer = {
                        navController.navigate(Screen.Player.route) { launchSingleTop = true }
                    },
                )
                BottomNav(navController = navController)
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            AppNavHost(navController = navController)
        }
    }
}
