package com.example.spotifyreplica.ui.navigation

/**
 * Top-level navigation destinations. Mirrors the reference project's sealed-interface pattern.
 */
sealed interface Screen {
    val route: String

    data object Library : Screen {
        override val route: String = "Library"
    }

    data object Playlist : Screen {
        override val route: String = "Playlist"
    }

    data object Search : Screen {
        override val route: String = "Search"
    }

    data object Downloads : Screen {
        override val route: String = "Downloads"
    }

    data object Player : Screen {
        override val route: String = "Player"
    }

    data object Settings : Screen {
        override val route: String = "Settings"
    }

    data object Login : Screen {
        override val route: String = "Login"
    }
}
