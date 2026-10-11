# Spotify Replica (Android)

Personal, sideloaded Android music app (Kotlin + Jetpack Compose + Hilt + Room + Media3).
See [`IMPLEMENTATION_PLAN.md`](./IMPLEMENTATION_PLAN.md) for the full phased plan.

> **Legal / policy:** This is a private, personal-use build. Do **not** publish it. Spotify's
> Web API returns metadata only — audio is DRM-protected. See §2 of the plan.

## Current state

Phases 0–1: buildable foundation only.

- Single `:app` module (Kotlin, Compose Material 3, Hilt, KSP).
- Bottom-navigation shell with placeholder screens (Library, Search, Downloads, Settings,
  Player, Playlist, Login).
- DataStore plumbing via `SettingsManager` and DI in `di/AppModule.kt`.

No features yet — everything beyond the nav shell lands in later phases.

## Prerequisites

- JDK 17+ (JDK 21 is fine; the project targets Java 17 bytecode).
- Android SDK with platform **android-36** and build-tools **36.0.0**.
- An `ANDROID_HOME`/SDK path (see below).

## Configure secrets

Create `spotify.properties` in the repo root (it is **git-ignored**):

```properties
SPOTIFY_CLIENT_ID=<your Spotify Web API client id>
SPOTIFY_REDIRECT_URI=spotifyreplica://callback
```

`SPOTIFY_REDIRECT_URI` defaults to `spotifyreplica://callback`. Register that exact value in
the Spotify developer dashboard. The values are baked into `BuildConfig`, so rebuild +
reinstall after changing them.

`local.properties` (also git-ignored) must point at your SDK:

```properties
sdk.dir=C\:\\Users\\<you>\\AppData\\Local\\Android\\Sdk
```

See `spotify.properties.example` for a template.

## Build

```bat
gradlew.bat :app:assembleDebug
```

The Gradle wrapper pins Gradle 8.14.3; no system Gradle is required.
