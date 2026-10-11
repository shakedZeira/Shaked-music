package com.example.spotifyreplica.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/** Spotify-like accent green. */
val SpotifyGreen = Color(0xFF1DB954)
val SpotifyGreenDark = Color(0xFF1AA34A)

/** Near-black backgrounds used by the dark (default) theme. */
val NearBlack = Color(0xFF0B0B0B)
val SurfaceDark = Color(0xFF181818)
val SurfaceDarkElevated = Color(0xFF282828)
val TextPrimary = Color(0xFFFFFFFF)
val TextSecondary = Color(0xFFB3B3B3)

val DarkColorScheme = darkColorScheme(
    primary = SpotifyGreen,
    onPrimary = Color.Black,
    primaryContainer = SpotifyGreenDark,
    onPrimaryContainer = Color.Black,
    secondary = SpotifyGreen,
    onSecondary = Color.Black,
    secondaryContainer = SurfaceDarkElevated,
    onSecondaryContainer = TextPrimary,
    tertiary = SpotifyGreen,
    onTertiary = Color.Black,
    background = NearBlack,
    onBackground = TextPrimary,
    surface = SurfaceDark,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceDarkElevated,
    onSurfaceVariant = TextSecondary,
    error = Color(0xFFF15E6C),
    onError = Color.Black,
    outline = Color(0xFF535353),
    outlineVariant = Color(0xFF2A2A2A),
    inverseSurface = TextPrimary,
    inverseOnSurface = NearBlack,
    inversePrimary = SpotifyGreenDark,
)

val LightColorScheme = lightColorScheme(
    primary = SpotifyGreenDark,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFC8F0D4),
    onPrimaryContainer = Color(0xFF00210F),
    secondary = SpotifyGreen,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD7F5DF),
    onSecondaryContainer = Color(0xFF00210F),
    tertiary = Color(0xFF146C36),
    onTertiary = Color.White,
    background = Color(0xFFFAFAFA),
    onBackground = Color(0xFF101010),
    surface = Color.White,
    onSurface = Color(0xFF101010),
    surfaceVariant = Color(0xFFF0F0F0),
    onSurfaceVariant = Color(0xFF4D4D4D),
    error = Color(0xFFB3261E),
    onError = Color.White,
    outline = Color(0xFFBDBDBD),
    outlineVariant = Color(0xFFE0E0E0),
    inverseSurface = Color(0xFF101010),
    inverseOnSurface = Color(0xFFFAFAFA),
    inversePrimary = SpotifyGreen,
)
