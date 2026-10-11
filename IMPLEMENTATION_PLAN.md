# Spotify Replica for Android — Implementation Plan

> **Target executor:** OpenCode "Big Pickle" agent.
> **Reference codebase (patterns to reuse):** `D:\AI Projects\DayToDayProject` (Android module `composeApp`).
> **Working directory:** `D:\AI Projects\Spotify Replica` (currently empty).
> **Authored:** 2026-10-11.
> **Status:** Ready to execute phase-by-phase. One subagent per phase (see §9).

---

## 1. Executive summary

Build a personal Android music app (Kotlin + Jetpack Compose) that:

1. Signs the user into **Spotify** via PKCE to read their **playlists and tracks metadata**.
2. Enumerates a playlist's tracks and **resolves each track to a downloadable audio stream** behind a swappable interface.
3. **Downloads the audio to local storage**, stamps correct metadata (title/artist/artwork), and records it in a local **Room** database.
4. **Plays the downloaded files offline** with a Media3/ExoPlayer `MediaSessionService` (background playback + notification).
5. Provides a **search screen** that queries Spotify and lets the user download any result.

The Gemini plan is directionally right but **out of date on three points** that this plan corrects:

- The Spotify endpoint `GET /playlists/{id}/tracks` is **renamed to `/items`** and the playlist `tracks` field is renamed to `items` (Feb 2026 dev-mode migration).
- Spotify `GET /search` now caps `limit` at **10** (was 50) — pagination is required.
- Android 15+ imposes a **6-hour/24-hour budget on `dataSync` foreground services**, which affects long download queues.

> **Read §2 before writing any code.** The audio-sourcing step is the legally sensitive part; the architecture deliberately isolates it.

---

## 2. Legal & policy notice (read first)

- **Spotify's Web API returns metadata only.** Audio is DRM-protected and cannot be fetched through the API. There is no supported way to download Spotify audio.
- **Downloading audio from YouTube / YouTube Music violates YouTube's Terms of Service and is copyright infringement** unless the content is licensed for it. Apps like NewPipe are GPL/open-source but still operate outside YouTube's ToS. `yt-dlp`/InnerTube scraping breaks frequently and requires ongoing maintenance.
- **Distribution risk:** Do **not** publish this to Google Play, F-Droid, or anywhere public. Google Play policy explicitly forbids downloading copyrighted media from unauthorized sources. Treat this strictly as a **private, personal-use, sideloaded build** for music you already have rights to.
- **Recommended posture for this project:** Keep `AudioSourceResolver` as an **interface** with multiple implementations. Ship with a compliant default (`LocalFileResolver` — index audio already on the device, e.g. `MediaStore`), and treat the YouTube resolver as an optional, user-supplied module. This keeps the whole app legitimate and lets the sourcing engine be swapped or removed without touching download/playback/UI code.
- **Spotify Developer Mode (2026):** The app owner **must have active Spotify Premium**; each app allows **max 5 authorized users**; endpoint/field changes below apply. This is fine for a personal app (owner + a couple of accounts).

**Decision needed from the user (§14, Q3):** confirm the intended audio source before Phase 4 is executed.

---

## 3. Research findings (verified 2026-10-11)

| Topic | Finding | Impact on plan |
|---|---|---|
| Spotify Dev Mode (Feb 11 / Mar 9, 2026) | Premium owner required; ≤5 authorized users; 1 client ID (raised to 25 in July 2026); smaller endpoint set | Add Premium + allowlist prerequisite; handle 403 |
| Playlist items endpoint | `GET /playlists/{id}/tracks` → **`GET /playlists/{id}/items`**; response field `tracks` → **`items`** (`items.items[].item`) | DTOs in the Gemini plan are wrong — use `items` |
| Playlist contents visibility | `items` only returned for playlists the user **owns or collaborates on** | Handle absent `items`; fall back to metadata-only |
| Search | `limit` max **10**, default 5; paginate via `offset` | Search UI must paginate |
| Removed endpoints | `GET /tracks?ids=`, `/albums?ids=`, `/artists?ids=`, `/browse/new-releases`, `/browse/categories`, `/artists/{id}/top-tracks`, `/markets`, `/users/{id}`, `/users/{id}/playlists` | Don't use batch fetch; fetch `GET /tracks/{id}` individually |
| Track field removed | `available_markets`, `popularity` (removed); `external_ids` **reverted** (still present) | Don't rely on `popularity` |
| Spotify PKCE on Android | Public client, **no client secret**; redirect URI can be a custom scheme (`spotifyreplica://callback`) | Reuse reference implementation exactly |
| Media3 | Latest stable **1.11.1** (Sep 2026). Background playback via `MediaSessionService` with `foregroundServiceType="mediaPlayback"`; needs `FOREGROUND_SERVICE_MEDIA_PLAYBACK` (API 34+) | Playback layer = Media3 |
| Media3 downloads | `DownloadService` uses `foregroundServiceType="dataSync"` | Relevant to download layer |
| Android 15 FGS timeout | `dataSync`/`mediaProcessing` FGS limited to **6h per 24h** (shared per app/type); `Service.onTimeout(int,int)` (API 35+) then `stopSelf()`; `ForegroundServiceStartNotAllowedException` when exhausted | Prefer `WorkManager` for the queue; chunk work; handle `onTimeout` |
| Audio tag writing | `jaudiotagger` (maintained fork `RouHim/jaudiotagger`) reads/writes MP3 ID3 and **MP4/M4A** tags incl. cover art. `SaltAudioTag` is a KMP alternative | Metadata step = jaudiotagger |
| Extractor options | `NewPipeExtractor` (`com.github.teamnewpipe:NewPipeExtractor`, JitPack, GPLv3, Java 11+, needs `desugar_jdk_libs_nio` if minSdk<33); KMP fork `io.github.yushosei:newpipe-extractor-kmp` (GPLv3, supports YouTube + SoundCloud) | Optional resolver impl |
| Stream URL freshness | InnerTube/YouTube stream URLs are **time-limited** and tied to client state | Resolve URL **at download time**, not at enqueue time |
| Artwork loader | Coil 3 (`io.coil-kt.coil3:coil-compose` + `coil-network-okhttp`) — exactly as reference project | Reuse |

---

## 4. Product scope

### In scope (v1)
- Spotify PKCE login / logout, token refresh, secure-ish token storage (DataStore).
- Browse the signed-in user's playlists; view playlist tracks.
- "Download all" a playlist and "download" a single track.
- Search Spotify tracks, paginate, download a result.
- Download queue with per-item progress, cancel, retry; survives process death (WorkManager).
- Embedded metadata (title, artist, album, duration, artwork) written into files.
- Offline library screen (downloaded tracks + playlists) backed by Room.
- Background playback with notification + lock-screen controls; queue, next/prev, seek.
- Settings: logout, storage usage, "clear downloads".

### Out of scope (v1)
- Spotify streaming/playback via Spotify Connect (the reference project's playback trick — not needed).
- User accounts/cloud sync, sharing, public release.
- Lyrics, podcasts, audiobooks, social features.
- Desktop/iOS (do **not** copy the reference project's KMP + Termux local-server complexity).

---

## 5. Tech stack

Single-module Android app (no KMP). Follow the reference project's *conventions*, not its *structure*.

| Layer | Choice |
|---|---|
| Language / UI | Kotlin + Jetpack Compose (Material 3) |
| Build | Gradle (KTS) + version catalog (`gradle/libs.versions.toml`) |
| DI | Hilt (`@HiltAndroidApp`, `@Module @InstallIn(SingletonComponent)`) |
| Async | Coroutines + Flow/StateFlow |
| Spotify network | Retrofit + OkHttp + Moshi (matches reference) + `BearerAuthInterceptor` |
| Local DB | Room (KSP) — entities/DAOs/providers mirror `DatabaseModule.kt` |
| Settings/tokens | DataStore Preferences via a `SettingsManager` (mirror reference) |
| Images | Coil 3 (`coil-compose` + `coil-network-okhttp`) |
| Downloads | WorkManager (`CoroutineWorker`) + OkHttp streaming to app-specific storage |
| Playback | AndroidX Media3: `media3-exoplayer`, `media3-session` |
| Metadata tags | `jaudiotagger` (MP3 + M4A/MP4) |
| Extractor (optional) | NewPipeExtractor (or KMP fork) behind `AudioSourceResolver` |
| Test | JUnit4 + MockK + Robolectric (mirror reference); `work-testing` for workers |

### Version baseline

Prefer latest stable; verify at Phase 0. Reference-project versions are a **known-good floor**.

| Artifact | Known-good (reference) | Recommended (verify latest) |
|---|---|---|
| Gradle | 8.11.1 | 8.13+ |
| AGP | 8.9.1 | 8.12+ |
| Kotlin | 1.9.24 | **2.1.x** (+ `org.jetbrains.kotlin.plugin.compose`) |
| compileSdk / targetSdk | 36 | 36 |
| minSdk | 26 | 26 |
| Java/JVM target | 17 | 17 |
| Compose BOM | 2024.06.00 | 2026.09.xx (`compose.ui` 1.12.x) |
| Media3 | n/a | **1.11.1** |
| WorkManager | n/a | **2.12.0** |
| Room | 2.6.1 | latest stable |
| Hilt | 2.52 | latest stable |
| Retrofit / OkHttp / Moshi | 2.11.0 / 4.12.0 / 1.15.1 | latest stable |
| Coil | 3.0.4 | latest stable |
| DataStore | 1.1.1 | latest stable |
| Navigation Compose | 2.8.3 | latest stable |

> If Kotlin 2.x is used, the Compose compiler moves to the Kotlin plugin — remove `composeOptions { kotlinCompilerExtensionVersion }` and add `alias(libs.plugins.compose.compiler)`.

---

## 6. Reference project conventions to reuse

These are the patterns actually present in `D:\AI Projects\DayToDayProject\composeApp`. Port them; do not invent new styles.

1. **Hilt application + DI modules** — `DayTodayApplication` (`@HiltAndroidApp`) and one `@Module @InstallIn(SingletonComponent::class)` per concern: `DatabaseModule`, `NetworkModule`, `SpotifyModule`, `RepositoryModule`.
2. **Retrofit + Moshi + OkHttp** — central `provideXxxApi()`; a `BearerAuthInterceptor(accessTokenProvider)` that adds `Authorization: Bearer` when a token exists.
3. **Spotify PKCE flow (port nearly verbatim):**
   - `SpotifyAuthManager` — verifier/challenge (S256), state, `loginUrl()`, `completePendingLogin()`, `ensureFreshToken()`.
   - `SpotifyAuthService` (Retrofit, `POST api/token`) + `SpotifyAuthClient`.
   - `SpotifyAuthActivity` (`@AndroidEntryPoint`, `singleTop`) handling the custom-scheme redirect.
   - Manifest intent-filter for `scheme` + `host="callback"`.
   - Scopes in the reference: `user-modify-playback-state user-read-playback-state playlist-read-private playlist-read-collaborative user-read-private`. **For this app use read-only:** `playlist-read-private playlist-read-collaborative user-library-read user-read-private`.
4. **Token persistence via DataStore** — `SettingsManager` exposes `spotifyAccessToken`, `spotifyRefreshToken`, `spotifyTokenExpiresAtMs`, `spotifyLoggedIn`, pending verifier/state, plus `saveSpotifyTokens()/clearSpotifyTokens()`. Port as-is.
5. **Room** — one `@Database` abstract class exposing one accessor per DAO; `DatabaseModule` provides the DB + each DAO with `@Provides`; `fallbackToDestructiveMigration()`; a `RoomDatabase.Callback` seed.
6. **Navigation** — sealed `Screen` interface (`route`), a `NavHost` file with `composable(...)`, `MainActivity` with `Scaffold(bottomBar = ...)`.
7. **ViewModels** — `@HiltViewModel`, `StateFlow<UiState>`, collected with `collectAsStateWithLifecycle()`.
8. **Images** — Coil 3.
9. **playlist → tracks domain types** — define clean models; don't leak DTOs to UI.

Fields to add that the reference does **not** have (new work): `media3-*`, `work-runtime-ktx`, `hilt-work`, `jaudiotagger`, `room-testing`/`work-testing`.

---

## 7. Target architecture

```
presentation (Compose screens + ViewModels)
        │  StateFlow<UiState>
        ▼
domain (models, repository interfaces, use cases)
        │
        ▼
data
 ├── remote/spotify     SpotifyApi, SpotifyAuthService, DTOs (Moshi)
 ├── remote/source      AudioSourceResolver  ──▶ LocalFileResolver (default)
 │                                            └─▶ YouTubeResolver (optional, isolated)
 ├── download           DownloadQueue, TrackDownloadWorker, TagWriter, StoragePaths
 ├── playback           PlaybackService (MediaSessionService), PlaybackRepository,
 │                      MediaControllerHolder
 ├── db                 AppDatabase, entities, DAOs
 └── settings           SettingsManager (DataStore)
```

**Key interfaces (lock these early — subagents depend on them):**

```kotlin
// domain/source/AudioSourceResolver.kt
data class ResolvedAudio(val url: String, val container: AudioContainer, val bitrateKbps: Int?, val expiresAtMs: Long?)

interface AudioSourceResolver {
    /** Resolve a Spotify track to a playable/downloadable audio source, or null if none. */
    suspend fun resolve(track: Track): ResolvedAudio?
}

// domain/download/TrackDownloader.kt
interface TrackDownloader {
    fun enqueue(track: Track, playlistId: String?): UUID           // returns WorkRequest id
    fun enqueuePlaylist(playlist: PlaylistSummary, tracks: List<Track>): UUID
    fun cancel(id: UUID)
    fun observeQueue(): Flow<List<DownloadItem>>                   // Room-backed
}

// domain/repository/MusicCatalogRepository.kt
interface MusicCatalogRepository {
    suspend fun myPlaylists(): Result<List<PlaylistSummary>>
    suspend fun playlistTracks(playlistId: String, offset: Int, limit: Int): Result<Page<Track>>
    suspend fun searchTracks(query: String, offset: Int, limit: Int): Result<Page<Track>>  // limit <= 10
}
```

`PlaybackRepository` exposes `MediaController` (built from a `SessionToken` once the `PlaybackService` is up) plus `play(tracks, startIndex)`, `togglePlayPause()`, `seekTo()`, `next()/previous()`.

---

## 8. Repository / file layout

```
D:\AI Projects\Spotify Replica\
├─ IMPLEMENTATION_PLAN.md            ← this file
├─ README.md                         ← created in Phase 1
├─ settings.gradle.kts
├─ build.gradle.kts
├─ gradle.properties
├─ gradle/libs.versions.toml
├─ gradle/wrapper/…
├─ gradlew, gradlew.bat
├─ local.properties                  ← NOT committed (SDK path)
├─ spotify.properties                ← NOT committed (SPOTIFY_CLIENT_ID, SPOTIFY_REDIRECT_URI)
├─ .gitignore
└─ app/
   ├─ build.gradle.kts
   ├─ proguard-rules.pro
   └─ src/main/
      ├─ AndroidManifest.xml
      ├─ res/… (themes, strings, icons)
      └─ java/com/example/spotifyreplica/
         ├─ SpotifyReplicaApplication.kt        (@HiltAndroidApp)
         ├─ MainActivity.kt
         ├─ di/        AppModule, NetworkModule, SpotifyModule, DatabaseModule,
         │             RepositoryModule, WorkModule, PlaybackModule
         ├─ domain/
         │  ├─ model/        Track, PlaylistSummary, Page, DownloadItem, DownloadStatus
         │  ├─ repository/   MusicCatalogRepository, PlaybackRepository, LibraryRepository
         │  ├─ source/       AudioSourceResolver, ResolvedAudio, AudioContainer
         │  └─ usecase/      SyncPlaylistsUseCase, DownloadPlaylistUseCase, SearchTracksUseCase
         ├─ data/
         │  ├─ remote/spotify/   SpotifyApi, SpotifyAuthService, SpotifyAuthClient,
         │  │                    SpotifyAuthManager, SpotifyDtos, BearerAuthInterceptor
         │  ├─ remote/source/    LocalFileResolver, (YouTubeResolver — optional)
         │  ├─ download/         TrackDownloadWorker, DownloadQueue, TagWriter, StoragePaths
         │  ├─ playback/         PlaybackService, MediaControllerHolder, PlaybackRepositoryImpl
         │  ├─ db/               AppDatabase, entity/…, dao/…
         │  ├─ repository/       MusicCatalogRepositoryImpl, LibraryRepositoryImpl
         │  └─ settings/         SettingsManager
         └─ ui/
            ├─ theme/      Theme.kt, Color.kt, Type.kt
            ├─ navigation/ Screen.kt, AppNavHost.kt, BottomNav.kt
            └─ screen/     login/, library/, playlist/, search/, player/, downloads/, settings/
```

Test sources: `app/src/test/` (JVM/Robolectric) and `app/src/androidTest/` (instrumented).

---

## 9. Subagent orchestration protocol  ⟵ (explicitly requested)

**Rule: one subagent per phase.** Phases are numbered `0…10`. The lead agent (Big Pickle) never implements a whole phase inline if a subagent can do it; it **plans, dispatches, reviews, verifies, and commits.**

### 9.1 Why a protocol is needed
Subagents start with **fresh context and cannot see this conversation.** Every dispatched brief must therefore be **self-contained**: repo root, path to this plan, phase number/name, exact deliverables, acceptance criteria, and a "touch only these paths" constraint.

### 9.2 Dispatch template (use for every phase)

```
You are implementing Phase <N> — <PHASE NAME> of the Spotify Replica Android app.

Repo root: D:\AI Projects\Spotify Replica
Plan file: D:\AI Projects\Spotify Replica\IMPLEMENTATION_PLAN.md  (read §5, §7, §9, and Phase <N>)
Reference project (patterns only, do not copy wholesale):
  D:\AI Projects\DayToDayProject\composeApp

Prerequisites already done (assume true, verify by reading files):
  - <list of prior-phase artifacts>

Deliverables (create/modify only these paths):
  - <exact file list>

Acceptance criteria:
  - <bulleted, testable>

Required verification before you return:
  - Run `<command>` and paste the tail of the output.
  - Run `<test command>` and paste the result.

Constraints:
  - Do NOT touch files outside the deliverables list.
  - Follow the reference project's conventions (Hilt modules, Retrofit+Moshi, DataStore
    SettingsManager, Room DatabaseModule, Compose nav + StateFlow).
  - Keep the Spotify track/playlist DTOs aligned with the 2026 API (items, not tracks; search limit <= 10).
  - Return: summary of changes, exact verification output, and any blockers.
```

### 9.3 Lead-agent loop per phase

1. **Dispatch** the subagent with the brief above (OpenCode `task`/subagent tool).
2. **Review** the returned diff/relative paths; reject if out-of-scope files changed.
3. **Verify** independently: run the build (and phase tests) yourself — never trust a summary alone.
4. **Commit** to git: `git add -A && git commit -m "phase <N>: <name>"`.
5. Only then dispatch the next phase in the dependency graph (§11).

### 9.4 Parallelism rules
- **Default: sequential.** All phases share one working tree.
- Two phases may run **in parallel only if their file sets are disjoint** (see §11 matrix).
- For genuinely parallel work with overlapping files, use **git worktrees** (one worktree + branch per phase) and merge after review. Do not run two editing subagents in the same tree.
- Never run parallel subagents on Gradle-level files (`build.gradle.kts`, `libs.versions.toml`) — serialize those.

### 9.5 Git setup (Phase 0)
`git init`, `.gitignore` (Android + `local.properties` + `spotify.properties` + `*.keystore`), initial commit of the plan. This makes every phase reversible.

---

## 10. Phase plan

Each phase: **Objective / Depends on / Deliverables / Steps / Acceptance / Verification / Subagent brief.**
The brief body is what you paste into the dispatch template (§9.2).

---

### Phase 0 — Repo bootstrap, toolchain, git

**Objective:** A buildable, empty-but-correct Android project skeleton with version catalog and secrets handling. No features.

**Depends on:** nothing.
**Parallel with:** nothing (everything depends on it).

**Deliverables**
- `settings.gradle.kts`, root `build.gradle.kts`, `gradle.properties`, `gradle/libs.versions.toml`, Gradle wrapper (`8.11.1`+), `.gitignore`
- `app/build.gradle.kts` (plugins: android application, kotlin, ksp, hilt, compose plugin)
- `app/src/main/AndroidManifest.xml` (no features yet; `@HiltAndroidApp` application)
- `app/src/main/java/.../SpotifyReplicaApplication.kt`, minimal `MainActivity.kt`
- `app/src/main/res/values/{strings,themes,colors}.xml`
- `README.md` (how to build; where to put `spotify.properties`)
- `spotify.properties.example`
- git initialized + first commit

**Steps**
1. Create wrapper (`gradle wrapper --gradle-version 8.13`), then `libs.versions.toml` with all deps from Appendix A.
2. `buildConfigField`s for Spotify from `spotify.properties` (mirror reference `spotifyProps()`):
   `SPOTIFY_CLIENT_ID`, `SPOTIFY_REDIRECT_URI` (default `spotifyreplica://callback`), auth/token/api base URLs.
3. `namespace = "com.example.spotifyreplica"`, `compileSdk 36`, `minSdk 26`, `targetSdk 36`, Java 17.
4. Enable `buildFeatures { compose = true; buildConfig = true }`.
5. Run `./gradlew :app:assembleDebug` until green.

**Acceptance**
- `./gradlew :app:assembleDebug` succeeds on a clean checkout (with a dummy `spotify.properties`).
- `spotify.properties` and `local.properties` are git-ignored.
- No feature code yet.

**Subagent brief**
```
Implement Phase 0 — Repo bootstrap, toolchain, git.
Deliverables: settings.gradle.kts, build.gradle.kts, gradle.properties, gradle/libs.versions.toml,
gradle wrapper, .gitignore, app/build.gradle.kts, minimal AndroidManifest.xml, Application + MainActivity,
res/values (strings/themes/colors), README.md, spotify.properties.example, git repo + first commit.
Use the deps in Appendix A of the plan. BuildConfig fields for SPOTIFY_CLIENT_ID/SPOTIFY_REDIRECT_URI
via a spotifyProps() helper reading root spotify.properties (mirror DayToDayProject/build.gradle.kts areas).
Acceptance: ./gradlew :app:assembleDebug succeeds; secrets ignored by git.
Verification: run ./gradlew :app:assembleDebug and paste the last 20 lines.
```

---

### Phase 1 — Foundation: theme, DI skeleton, navigation shell

**Objective:** App renders a bottom-nav shell with placeholder screens; Hilt wired end-to-end.

**Depends on:** Phase 0.
**Parallel with:** —

**Deliverables**
- `di/AppModule.kt` (DataStore instance + `CoroutineScope`), `settings/SettingsManager.kt` (base)
- `ui/theme/*`, `ui/navigation/Screen.kt`, `ui/navigation/AppNavHost.kt`, `ui/navigation/BottomNav.kt`
- Placeholder screens: `LibraryScreen`, `SearchScreen`, `DownloadsScreen`, `SettingsScreen`, `PlayerScreen`
- `MainActivity` with `Scaffold(bottomBar = BottomNav)` + `AppNavHost`

**Steps**
1. Port the sealed `Screen` pattern from reference `ui/navigation/Screen.kt`.
2. Port the `MainActivity` scaffold structure (reference `MainActivity.kt`) minus the local-server gate.
3. `AppModule` provides `DataStore<Preferences>` (`preferencesDataStore("spotify_replica_settings")`) and a singleton `CoroutineScope(SupervisorJob() + Dispatchers.IO)`.
4. Add a `MiniPlayerBar` composable placeholder above the bottom bar.

**Acceptance**
- App launches and navigates between 4+ placeholder destinations.
- `hiltViewModel()` resolves a trivial ViewModel.

**Subagent brief**
```
Implement Phase 1 — Foundation: theme, DI skeleton, nav shell. Depends on Phase 0 (project builds).
Deliverables: di/AppModule.kt; data/settings/SettingsManager.kt (empty shell + DataStore);
ui/theme/*; ui/navigation/{Screen,AppNavHost,BottomNav}.kt; placeholder screens
library/search/downloads/settings/player; updated MainActivity + Application.
Patterns: copy structure from DayToDayProject ui/navigation/Screen.kt, ui/navigation/NavHost.kt,
MainActivity.kt (minus the local-server StartupGate), di/DatabaseModule.kt (module style).
Acceptance: app launches; bottom nav navigates between placeholders; hiltViewModel works.
Verification: ./gradlew :app:assembleDebug; paste tail.
```

---

### Phase 2 — Spotify authentication & catalog (metadata)

**Objective:** Log in with Spotify (PKCE), list playlists, list playlist tracks (2026 `items` shape), search tracks with pagination.

**Depends on:** Phase 1.
**Parallel with:** Phase 3 (file sets are disjoint: `data/remote/spotify/**`, `ui/screen/login/**`, `di/SpotifyModule.kt` vs `data/db/**`, `di/DatabaseModule.kt`).

**Deliverables**
- `data/remote/spotify/`: `SpotifyAuthService.kt`, `SpotifyAuthClient.kt`, `SpotifyAuthManager.kt`, `SpotifyApi.kt`, `SpotifyDtos.kt`, `BearerAuthInterceptor.kt`
- `data/spotify/SpotifyAuthActivity.kt` + manifest intent-filter
- `di/SpotifyModule.kt`, `di/NetworkModule.kt`
- `domain/model/{Track,PlaylistSummary,Page}.kt`
- `data/repository/MusicCatalogRepositoryImpl.kt`, `domain/repository/MusicCatalogRepository.kt`
- `ui/screen/login/LoginScreen.kt` + `LoginViewModel.kt`
- Extend `SettingsManager` with Spotify keys (mirror reference)

**Steps**
1. Port `SpotifyAuthManager`, `SpotifyAuthService`, `SpotifyAuthClient`, `BearerAuthInterceptor`, `SpotifyAuthActivity`, and the manifest `<activity>` + intent-filter from the reference. **Change the scheme** to `spotifyreplica` (host `callback`).
2. Scopes: `playlist-read-private playlist-read-collaborative user-library-read user-read-private`.
3. `SpotifyApi`:
   - `@GET("me/playlists")` with `limit`/`offset` → `Paging<PlaylistDto>`
   - `@GET("playlists/{id}/items")` with `limit`/`offset` → `Paging<PlaylistItemDto>` — **note `items`, not `tracks`**
   - `@GET("search")` with `q`, `type=track`, `limit` (≤10), `offset`
   - `@GET("me")` for the profile name
4. DTOs must tolerate: `items` present/absent; nested `items.items[].item`; removed `popularity`; `external_ids` present.
5. `MusicCatalogRepositoryImpl` maps Spotify DTOs → domain `Track` (`id, title, artists, album, artworkUrl, durationMs, isrc, spotifyUri`) using `ensureFreshToken()` before each call and mapping failures to `Result`.
6. Login screen shows the login button (Custom Tabs), pending/complete state, and error message from `SettingsManager.spotifyLoginMessage`.

**Acceptance**
- Real Spotify login works on device/emulator with a registered redirect URI.
- Playlists load; a playlist's tracks load (owned playlist).
- Search returns ≤10 results and paginates.

**Subagent brief**
```
Implement Phase 2 — Spotify auth + catalog metadata. Depends on Phase 1.
Base the whole implementation on D:\AI Projects\DayToDayProject\composeApp\src\androidMain\kotlin\com\daytoday\
data\spotify\* and di\SpotifyModule.kt — port them, changing the scheme to spotifyreplica://callback
and the scopes to read-only (playlist-read-private playlist-read-collaborative user-library-read user-read-private).
2026 API rules you MUST follow: playlist tracks endpoint is GET /playlists/{id}/items; playlist field is
"items" ("items.items[].item"); GET /search limit max is 10 (paginate with offset); do not use removed
batch endpoints; do not read "popularity".
Deliverables: data/remote/spotify/*, SpotifyAuthActivity + manifest entry, di/SpotifyModule.kt + NetworkModule.kt,
domain/model/*, domain/repository/MusicCatalogRepository.kt, data/repository/MusicCatalogRepositoryImpl.kt,
ui/screen/login/*, SettingsManager Spotify keys.
Acceptance: PKCE login succeeds; myPlaylists() works; playlistTracks() works for an owned playlist;
searchTracks() respects limit<=10 and paginates; unwritable playlists degrade gracefully.
Verification: ./gradlew :app:assembleDebug and ./gradlew :app:testDebugUnitTest (for DTO mapping tests).
```

---

### Phase 3 — Local database & library repository

**Objective:** Room schema + DAOs + repository for downloaded tracks/playlists and the download queue.

**Depends on:** Phase 1 (and Phase 2 only for the domain `Track` type — coordinate: if run in parallel, define `domain/model/Track.kt` in Phase 1 or add it here and merge).
**Parallel with:** Phase 2.

**Deliverables**
- `data/db/entity/{TrackEntity, PlaylistEntity, PlaylistTrackCrossRef, DownloadEntity}.kt`
- `data/db/dao/{TrackDao, PlaylistDao, DownloadDao}.kt`
- `data/db/AppDatabase.kt`
- `di/DatabaseModule.kt`
- `domain/model/{DownloadItem,DownloadStatus,LibraryTrack}.kt`
- `domain/repository/LibraryRepository.kt` + `data/repository/LibraryRepositoryImpl.kt`
- Unit tests for DAO mapping (Robolectric)

**Schema (Appendix D)**
- `tracks`: `spotifyId PK`, `title`, `artistsCsv`, `album`, `durationMs`, `artworkUrl`, `localPath?`, `sizeBytes?`, `container`, `bitrateKbps?`, `downloadedAtMs?`
- `playlists`: `spotifyId PK`, `name`, `ownerName`, `artworkUrl?`, `trackCount`, `syncedAtMs?`
- `playlist_track`: `(playlistId, trackSpotifyId)` PK, `position`
- `downloads`: `id PK (UUID string)`, `spotifyId`, `playlistId?`, `status`, `bytesDownloaded`, `totalBytes?`, `error?`, `updatedAtMs`

**Acceptance**
- Insert/query round-trips for all entities.
- `LibraryRepository.observeDownloads(): Flow<List<DownloadItem>>` reacts to DB writes.

**Subagent brief**
```
Implement Phase 3 — Room DB + library repository. Depends on Phase 1.
Mirror the module/DAO/provider style of DayToDayProject di/DatabaseModule.kt and data/database/*.
Entities/DAOs/DB exactly per Appendix D of the plan. Provide DB + all DAOs via Hilt @Provides.
Deliverables: data/db/** , di/DatabaseModule.kt, domain/model/{DownloadItem,DownloadStatus,LibraryTrack}.kt,
domain/repository/LibraryRepository.kt, data/repository/LibraryRepositoryImpl.kt, unit tests.
Acceptance: DAO round-trip tests pass; observeDownloads() emits on writes.
Verification: ./gradlew :app:testDebugUnitTest and paste results.
```

---

### Phase 4 — Audio source resolver (the swappable engine)

**Objective:** Implement `AudioSourceResolver` with a **compliant default** and an **optional, isolated** YouTube implementation. Gate behind a settings flag; default OFF.

**Depends on:** Phase 2 (domain `Track`), Phase 3 (optional, for caching resolved URLs).

**Deliverables**
- `domain/source/{AudioSourceResolver,ResolvedAudio,AudioContainer}.kt`
- `data/remote/source/LocalFileResolver.kt` (default — indexes `MediaStore` audio by title/artist fuzzy match)
- `data/remote/source/YouTubeResolver.kt` (**optional**, guarded by `SettingsManager.audioSource = "youtube"`)
- `data/remote/source/ResolvedAudioCache.kt` (in-memory + Room TTL cache; **do not** cache past `expiresAtMs`)
- `di/SourceModule.kt`
- Unit tests: query building, best-audio-stream selection, cache expiry

**Steps**
1. Define `AudioContainer { M4A, WEBM_OPUS, MP3, LOCAL }`.
2. `LocalFileResolver`: query `MediaStore.Audio` for `[title] [artist]`, score matches, return a `file://`/content URI. This makes a fully legal app possible.
3. `YouTubeResolver` (only if user opts in):
   - Use NewPipeExtractor (or the KMP fork). Add JitPack repo; enable core-library desugaring if minSdk < 33.
   - Search `"<title> <artist> official audio"`; pick the top music match; select the best **audio-only** stream (prefer `M4A`/`AAC` for tagging + ExoPlayer compatibility, else Opus).
   - **Resolve at download time** (URLs expire). Return `expiresAtMs`.
4. `ResolvedAudioCache` keyed by `spotifyId`, invalidated on expiry.

**Acceptance**
- `LocalFileResolver` returns hits for tracks present in `MediaStore`.
- `YouTubeResolver` (opt-in) returns a resolvable audio URL for a known track, and the URL still downloads when used immediately.
- Resolver selection is driven by a settings flag; default is `local`.

**Subagent brief**
```
Implement Phase 4 — AudioSourceResolver with a compliant LocalFileResolver (default) and an OPTIONAL
YouTubeResolver behind a settings flag (default off). Depends on Phase 2 (domain Track).
Add NewPipeExtractor from JitPack only for the optional impl; enable desugar_jdk_libs_nio if minSdk<33.
IMPORTANT: resolve stream URLs at download time (they expire); return expiresAtMs and never serve cached
URLs past expiry. Prefer M4A/AAC audio-only streams (tagging + ExoPlayer compatibility).
Deliverables: domain/source/*, data/remote/source/*, di/SourceModule.kt, unit tests.
Acceptance: LocalFileResolver finds MediaStore tracks; YouTubeResolver (opt-in) yields a usable URL;
flag defaults to local. Verification: unit tests + ./gradlew :app:assembleDebug; paste output.
```

---

### Phase 5 — Download engine (WorkManager) + tag writing

**Objective:** Robust background download queue that writes audio to app-specific storage, tags metadata, and updates Room — surviving process death, respecting Android 15 FGS limits.

**Depends on:** Phase 3, Phase 4.

**Deliverables**
- `data/download/StoragePaths.kt` — `context.getExternalFilesDir(Environment.DIRECTORY_MUSIC)/<playlistId>/<trackId>.<ext>`
- `data/download/TrackDownloadWorker.kt` (`CoroutineWorker` + Hilt `@HiltWorker`/`HiltWorkerFactory`)
- `data/download/DownloadQueue.kt` (unique work per track `spotifyId`; `ExistingWorkPolicy.KEEP`; cancellable)
- `data/download/TagWriter.kt` (jaudiotagger: title, artist, album, artwork from cover URL/bytes)
- `di/WorkModule.kt` (`Configuration.Provider` with `HiltWorkerFactory`)
- `domain/repository/DownloadRepository.kt`
- `ui/screen/downloads/DownloadsScreen.kt` + `DownloadsViewModel.kt`
- Worker tests with `work-testing`

**Steps**
1. `TrackDownloadWorker`:
   - inputs: `spotifyId`, `title`, `artists`, `album`, `artworkUrl`, `durationMs`, `playlistId?`
   - resolve URL via `AudioSourceResolver` **inside** `doWork()`
   - stream download with OkHttp to a `.part` file, `setProgress()` periodically
   - on completion: `TagWriter` stamps metadata, atomic rename `.part` → final, insert/update Room, delete `.part` on failure
   - return `Result.retry()` on transient network errors (max attempts), `Result.failure()` on permanent (no source)
2. Foreground behavior: use `setForeground()`/`ForegroundInfo` with `foregroundServiceType = dataSync` **only while actively downloading**; implement **chunking** so a large queue doesn't rely on one 6-hour FGS (see §3). Enqueue with `NetworkType.CONNECTED` constraint.
3. `DownloadQueue.enqueue*` returns the WorkManager `UUID`; writes a `DownloadEntity` row so the UI can observe progress from Room (not just `WorkInfo`).
4. `DownloadsScreen`: list from `observeDownloads()`, show progress/size/state, cancel + retry.

**Acceptance**
- Downloading a single search result produces a tagged, playable file and a Room row.
- Killing the process mid-download → work resumes/retries; no orphan `.part` files.
- Cancelling removes the row and any partial file.
- Queue progress is visible and monotonic.

**Subagent brief**
```
Implement Phase 5 — download engine + tagging. Depends on Phases 3 and 4.
Use WorkManager (@HiltWorker + HiltWorkerFactory via Configuration.Provider). One unique work per track
(spotifyId) with ExistingWorkPolicy.KEEP. Resolve the audio URL INSIDE doWork() (URLs expire). Stream with
OkHttp to a .part file, setProgress() periodically, then stamp metadata with jaudiotagger (title/artist/
album/artwork), atomically rename, and upsert Room. Write a DownloadEntity row so the UI observes progress
from Room.
Android 15+: dataSync foreground services are limited to 6h/24h and get Service.onTimeout(); only enter
the foreground while actively downloading and chunk the queue — do not hold one FGS for the whole library.
Deliverables: data/download/*, di/WorkModule.kt, domain/repository/DownloadRepository.kt,
ui/screen/downloads/*, worker tests.
Acceptance: single-download produces a tagged playable file + DB row; cancel removes file+row; progress visible;
process death resumes. Verification: ./gradlew :app:testDebugUnitTest; paste results.
```

---

### Phase 6 — Playback (Media3 ExoPlayer + MediaSessionService)

**Objective:** Offline playback of downloaded files with background service, notification, and Compose controls.

**Depends on:** Phase 3. (Phase 5 not strictly required — can play any local file.)

**Deliverables**
- `data/playback/PlaybackService.kt` (`MediaSessionService`, `ExoPlayer`, `onTimeout`/task-removed handling)
- `data/playback/MediaControllerHolder.kt` (builds `MediaController` from `SessionToken`)
- `data/playback/PlaybackRepositoryImpl.kt`
- `di/PlaybackModule.kt`
- `ui/screen/player/PlayerScreen.kt` + `PlayerViewModel.kt`, `ui/components/MiniPlayerBar.kt`
- Manifest: `<service>` with `foregroundServiceType="mediaPlayback"` + `androidx.media3.session.MediaSessionService` intent-filter

**Steps**
1. `PlaybackService.onCreate` builds `ExoPlayer` + `MediaSession`; release in `onDestroy`; keep playing when task removed if playing.
2. `MediaControllerHolder` connects asynchronously to the session, exposes `Flow<PlayerState>` (isPlaying, position, duration, current media).
3. Build `MediaItem`s from Room `LibraryTrack.localPath` (file URI); support queue, next/prev, seek, shuffle/repeat.
4. `PlayerScreen` + persistent `MiniPlayerBar` above the bottom nav; use `collectAsStateWithLifecycle`.
5. Android Auto/通知: rely on Media3 defaults; ensure `FOREGROUND_SERVICE_MEDIA_PLAYBACK` permission.

**Acceptance**
- Tap a downloaded track → plays offline with correct metadata/artwork shown by the system notification.
- Background playback continues with screen off; lock-screen controls work; seek/next/prev work.

**Subagent brief**
```
Implement Phase 6 — playback with Media3. Depends on Phase 3.
Create a MediaSessionService with ExoPlayer; manifest service android:foregroundServiceType="mediaPlayback"
with intent-filter action androidx.media3.session.MediaSessionService; add permissions FOREGROUND_SERVICE and
FOREGROUND_SERVICE_MEDIA_PLAYBACK. Build MediaItems from Room LibraryTrack.localPath (file URIs).
Expose playback state to Compose via a MediaController built from a SessionToken (PlaybackRepository).
Add a persistent MiniPlayerBar above the bottom nav and a PlayerScreen (queue, seek, next/prev).
Deliverables: data/playback/*, di/PlaybackModule.kt, ui/screen/player/*, ui/components/MiniPlayerBar.kt, manifest updates.
Acceptance: offline playback with metadata + system notification; background/lock-screen controls; seek/next/prev.
Verification: ./gradlew :app:assembleDebug; paste tail. (Manual device check noted as a follow-up.)
```

---

### Phase 7 — Library & playlist browsing UI (+ "download all")

**Objective:** Present the user's Spotify playlists and their tracks; download whole playlists.

**Depends on:** Phase 2, Phase 3, Phase 5.

**Deliverables**
- `ui/screen/library/{LibraryScreen,LibraryViewModel}.kt`
- `ui/screen/playlist/{PlaylistScreen,PlaylistViewModel}.kt`
- `domain/usecase/{SyncPlaylistsUseCase,DownloadPlaylistUseCase}.kt`
- `ui/components/{TrackRow,DownloadButton,PlaylistCard}.kt`

**Steps**
1. `LibraryScreen`: grid/list of playlists from `MusicCatalogRepository.myPlaylists()`; pull-to-refresh; login CTA when logged out.
2. `PlaylistScreen`: paged track list from `playlistTracks()`; per-row download button showing state from `DownloadEntity`; header "Download all".
3. `DownloadPlaylistUseCase` enqueues one work per track (bounded concurrency handled by WorkManager) and upserts `playlist` + `playlist_track` locally.
4. Show which tracks are already downloaded (join Room).

**Acceptance**
- Playlists render with artwork; tapping opens tracks; "download all" populates the downloads screen.
- Downloaded state persists across relaunch.

**Subagent brief**
```
Implement Phase 7 — library & playlist UI with download-all. Depends on Phases 2, 3, 5.
Build LibraryScreen (playlists, pull-to-refresh, login CTA) and PlaylistScreen (paged tracks,
per-row download state joined from Room DownloadEntity, header "Download all"). Add
SyncPlaylistsUseCase + DownloadPlaylistUseCase. Reuse TrackRow/PlaylistCard components.
8 results per search page pattern is irrelevant here; use Spotify paging limit 50 for playlists.
Deliverables: ui/screen/library/*, ui/screen/playlist/*, domain/usecase/*, ui/components/*.
Acceptance: playlists render; tracks open; download-all populates Downloads; downloaded state persists.
Verification: ./gradlew :app:assembleDebug; paste tail.
```

---

### Phase 8 — Search & add-download UI

**Objective:** Search Spotify and download results, honoring the 2026 `limit<=10` cap.

**Depends on:** Phase 2, Phase 5.

**Deliverables**
- `ui/screen/search/{SearchScreen,SearchViewModel}.kt`
- `domain/usecase/SearchTracksUseCase.kt`
- `ui/components/SearchResultRow.kt`

**Steps**
1. Debounced query (≈350 ms) → `searchTracks(query, offset, limit=10)`.
2. `LazyColumn` results with a trailing download icon; state from `DownloadEntity`.
3. Infinite scroll: load more with `offset += 10` until fewer than 10 returned.
4. Empty/error/loading states; keyboard "search" action.

**Acceptance**
- Searching returns Spotify tracks; scrolling loads more pages; tapping download enqueues and reflects status.

**Subagent brief**
```
Implement Phase 8 — search & add-download. Depends on Phases 2 and 5.
Debounced search (350ms) calling GET /search?type=track with limit=10 (hard cap in 2026) and offset
pagination on scroll. Results list with per-row download button whose state comes from Room DownloadEntity.
Deliverables: ui/screen/search/*, domain/usecase/SearchTracksUseCase.kt, ui/components/SearchResultRow.kt.
Acceptance: search works, paginates with offset, download enqueues and status updates.
Verification: ./gradlew :app:assembleDebug; paste tail.
```

---

### Phase 9 — Settings, storage management, polish

**Objective:** Settings screen, logout, storage usage, clear-downloads, source toggle, notifications, theme.

**Depends on:** Phases 2, 5, 6, 7, 8.

**Deliverables**
- `ui/screen/settings/{SettingsScreen,SettingsViewModel}.kt`
- storage accounting (`StoragePaths.totalBytes()`, per-playlist sizes)
- "clear downloads" (delete files + rows + cancel work)
- source selector (`local` / `youtube`-if-enabled) + explicit disclaimer text
- POST_NOTIFICATIONS runtime request (API 33+)

**Acceptance**
- Logout clears tokens; storage usage accurate; clearing removes files and rows; source toggle works.

**Subagent brief**
```
Implement Phase 9 — settings/storage/polish. Depends on 2,5,6,7,8.
SettingsScreen: account (login state, logout), audio source selector with a clear disclaimer, storage usage
(computed from StoragePaths + Room), clear-downloads (files + rows + cancel pending WorkManager), theme,
notification permission (POST_NOTIFICATIONS on API 33+).
Deliverables: ui/screen/settings/*, storage helpers.
Acceptance: logout clears tokens; usage accurate; clear removes files+rows; toggle persists.
Verification: ./gradlew :app:assembleDebug; paste tail.
```

---

### Phase 10 — QA, hardening, release build

**Objective:** End-to-end verification on a device/emulator; release build; documentation.

**Depends on:** all phases.

**Deliverables**
- `README.md` finalized (setup, Spotify dashboard config, build, run, limitations, legal note)
- release config: `minifyEnabled true`, `proguard-rules.pro` (keep Moshi/Room/Hilt/Media3/jaudiotagger/newpipe if used)
- `docs/QA_CHECKLIST.md` with the manual E2E script (Appendix E)
- bug fixes found in QA

**Acceptance (Definition of Done)**
- Clean `./gradlew clean :app:assembleRelease :app:testDebugUnitTest` passes.
- Manual E2E checklist (Appendix E) fully passes on one physical device + one emulator.
- No SECRETS committed (`git log -p | grep SPOTIFY_CLIENT_ID` is empty; only `.example` present).

**Subagent brief**
```
Implement Phase 10 — QA + release hardening. Depends on all phases.
Finalize README (Spotify dashboard setup: redirect URI spotifyreplica://callback, scopes, Premium owner,
allowlist users; build/run; limitations + legal note). Enable minify + write proguard rules for
Moshi/Room/Hilt/Media3/jaudiotagger/(NewPipe if used). Write docs/QA_CHECKLIST.md using Appendix E.
Fix any bugs found. Acceptance: clean assembleRelease + unit tests pass; E2E checklist passes.
Verification: ./gradlew clean :app:assembleRelease :app:testDebugUnitTest; paste tail.
```

---

## 11. Dependency waves & parallelism matrix

Safe execution order (default sequential):

```
Wave A:  Phase 0
Wave B:  Phase 1
Wave C:  Phase 2  ∥  Phase 3          (disjoint files — safe to parallelize)
Wave D:  Phase 4                       (needs 2; 3 optional)
Wave E:  Phase 5                       (needs 3,4)
Wave F:  Phase 6                       (needs 3)  ∥  Phase 7 (needs 2,3,5 → run AFTER 5)
Wave G:  Phase 8                       (needs 2,5)
Wave H:  Phase 9                       (needs 2,5,6,7,8)
Wave I:  Phase 10                      (needs all)
```

Practical routing:
- **Parallelizable:** C (2 ∥ 3) and, after Phase 5, **F (6 ∥ 8)** — file sets are disjoint.
- **Must be sequential:** 0→1, →4, →5, →7, →9, →10.
- **Never parallelize** edits to `build.gradle.kts` / `libs.versions.toml` — one phase owns each.

For true parallel edits with overlap, create a git worktree per phase (§9.4) and merge after review.

---

## 12. Verification gates & Definition of Done

Every phase must pass these before the next dependent phase starts:

| Gate | Command | Requirement |
|---|---|---|
| Compile | `./gradlew :app:assembleDebug` | succeeds |
| Unit tests | `./gradlew :app:testDebugUnitTest` | all green |
| Scope | `git diff --name-only` | only phase deliverables changed |
| Secrets | `git status --porcelain` | no `spotify.properties` / `local.properties` |
| Phase acceptance | manual/automated test in the brief | passes |

Project-level DoD:
- Clean `assembleRelease` + `testDebugUnitTest` pass.
- Manual E2E (Appendix E) passes on device + emulator.
- README documents Spotify dashboard setup and the legal caveat.
- No secrets committed.

---

## 13. Risk register

| # | Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|---|
| R1 | Audio-source approach is ToS-infringing / breaks often | High | High | `AudioSourceResolver` interface + `LocalFileResolver` default; optional impl isolated & opt-in; document clearly |
| R2 | Spotify Dev Mode restrictions (Premium, 5 users) block testing | Medium | High | Verify Premium + allowlist in Phase 2; surface helpful errors; document in README |
| R3 | `items` vs `tracks` API drift | High if ignored | Medium | Use 2026 shape now (Appendix C); tolerant DTOs; unit tests |
| R4 | InnerTube stream URL expiry during download | Medium | Medium | Resolve URL inside `doWork()`; retry on 403/410 |
| R5 | Android 15 `dataSync` 6h FGS limit / `ForegroundServiceStartNotAllowedException` | Medium | High | WorkManager queue; enter FGS only while active; chunk; handle `onTimeout()` |
| R6 | Unwritable playlists (not owned/collaborated) return no `items` | High | Low | Graceful metadata-only fallback; show a notice |
| R7 | Extractor breaks after YouTube change | High | Medium | Pin version; isolate; document upgrade path; never cache URLs |
| R8 | Artwork licensing/storage bloat | Low | Low | Downscale before embedding; store one cover per album |
| R9 | Copyright/boxed in by app store rejection | Low (personal) | High | Do not publish; sideload only |
| R10 | Kotlin 2.x + Compose compiler plugin mismatch | Medium | Medium | Phase 0 spikes the toolchain; verify build before features |

---

## 14. Open decisions (confirm with the user before/at the flagged phases)

| # | Question | Default in this plan | Needed by |
|---|---|---|---|
| Q1 | Application/package id | `com.example.spotifyreplica` | Phase 0 |
| Q2 | Spotify redirect URI (custom scheme) | `spotifyreplica://callback` | Phase 2 |
| Q3 | **Audio source strategy** — approve the browser-extraction approach, or ship `LocalFileResolver` only? | Ship local-only default; YouTube impl opt-in, off by default | Phase 4 |
| Q4 | Preferred download container | Prefer M4A/AAC (tagging + ExoPlayer), else Opus | Phase 4 |
| Q5 | Playback engine | Media3 `MediaSessionService` | Phase 6 |
| Q6 | Download engine | WorkManager (not Media3 `DownloadManager`) | Phase 5 |
| Q7 | Distribute anywhere? | No — sideload, personal use | Phase 10 |
| Q8 | minSdk | 26 (matches reference) | Phase 0 |

---

## Appendix A — Dependency list (`gradle/libs.versions.toml` seed)

Core: `androidx.core:core-ktx`, `androidx.activity:activity-compose`, `androidx.lifecycle:{lifecycle-runtime-ktx,lifecycle-viewmodel-compose,lifecycle-runtime-compose,lifecycle-process}`.
Compose: BOM + `compose.ui`, `compose.ui-tooling`, `compose.foundation`, `compose.material3`, `compose.material-icons-extended`, `compose.animation`, `androidx.navigation:navigation-compose`.
DI: `com.google.dagger:hilt-android` + ksp `hilt-android-compiler`, `androidx.hilt:hilt-navigation-compose`, `androidx.hilt:hilt-work`.
Network: `com.squareup.retrofit2:retrofit`, `converter-moshi`, `com.squareup.moshi:moshi-kotlin` (+ ksp `moshi-kotlin-codegen` optional), `com.squareup.okhttp3:{okhttp,logging-interceptor}`, `androidx.browser:browser` (Custom Tabs).
DB: `androidx.room:{room-runtime,room-ktx}` + ksp `room-compiler`.
Settings: `androidx.datastore:datastore-preferences`.
Work: `androidx.work:work-runtime-ktx`.
Media: `androidx.media3:{media3-exoplayer,media3-session,media3-common-ktx}` (`media3-exoplayer` includes offline classes if needed).
Images: `io.coil-kt.coil3:{coil-compose,coil-network-okhttp}`.
Tags: `net.jthink:jaudiotagger` (or the `RouHim` fork coordinate — verify) .
Optional extractor: `com.github.teamnewpipe:NewPipeExtractor:<ver>` (JitPack) or `io.github.yushosei:newpipe-extractor-kmp-android` (Maven Central); desugaring `com.android.tools:desugar_jdk_libs_nio` if minSdk < 33.
Serialization/coroutines: `kotlinx-serialization-json`, `kotlinx-coroutines-android`.
Test: `junit:junit`, `io.mockk:mockk`, `org.robolectric:robolectric`, `androidx.work:work-testing`, `androidx.room:room-testing`, `androidx.compose.ui:ui-test-junit4`, `androidx.test.espresso:*`.

---

## Appendix B — Manifest additions

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_DATA_SYNC" />
<uses-permission android:name="android.permission.WAKE_LOCK" />
<!-- Optional YouTube resolver: query com.google.android.youtube if needed -->
<queries><package android:name="com.google.android.youtube" /></queries>

<application android:name=".SpotifyReplicaApplication" ...>

  <!-- Spotify PKCE redirect -->
  <activity android:name=".data.remote.spotify.SpotifyAuthActivity"
      android:exported="true" android:launchMode="singleTop">
    <intent-filter>
      <action android:name="android.intent.action.VIEW" />
      <category android:name="android.intent.category.DEFAULT" />
      <category android:name="android.intent.category.BROWSABLE" />
      <data android:scheme="spotifyreplica" android:host="callback" />
    </intent-filter>
  </activity>

  <!-- Playback -->
  <service android:name=".data.playback.PlaybackService"
      android:exported="true" android:foregroundServiceType="mediaPlayback">
    <intent-filter>
      <action android:name="androidx.media3.session.MediaSessionService" />
    </intent-filter>
  </service>

</application>
```

`WorkManager` uses the default `SystemJobService`; configure `HiltWorkerFactory` via `Configuration.Provider` in the Application (no custom service needed).

---

## Appendix C — Spotify endpoint reference (2026)

| Need | Endpoint | Notes |
|---|---|---|
| Profile | `GET /me` | `country/email/product/followers` removed in dev mode |
| Playlists | `GET /me/playlists?limit=50&offset=` | paged |
| Playlist tracks | `GET /playlists/{id}/items?limit=50&offset=` | **`items`**; only for owned/collaborated playlists |
| Track details | `GET /tracks/{id}` | no batch `GET /tracks?ids=` in dev mode |
| Search | `GET /search?q=&type=track&limit=10&offset=` | **limit max 10** |
| Token | `POST https://accounts.spotify.com/api/token` | PKCE: `grant_type=authorization_code` / `refresh_token`, `client_id`, no secret |
| Authorize | `https://accounts.spotify.com/authorize` | `response_type=code`, `code_challenge_method=S256` |

Scopes: `playlist-read-private playlist-read-collaborative user-library-read user-read-private`.

Playlist response shape (2026):
```json
{ "items": { "total": 42, "items": [ { "item": { "id": "...", "name": "...", "artists": [{"name":"..."}],
  "album": {"name":"...","images":[{"url":"..."}]}, "duration_ms": 200000,
  "external_ids": {"isrc":"..."} } } ] } }
```
(Handle: `items` may be **absent** for playlists the user doesn't own.)

---

## Appendix D — Room schema

```kotlin
@Entity(tableName = "tracks")
data class TrackEntity(
  @PrimaryKey val spotifyId: String,
  val title: String, val artistsCsv: String, val album: String,
  val durationMs: Long, val artworkUrl: String?,
  val localPath: String?, val sizeBytes: Long?, val container: String,
  val bitrateKbps: Int?, val downloadedAtMs: Long?
)

@Entity(tableName = "playlists")
data class PlaylistEntity(
  @PrimaryKey val spotifyId: String, val name: String, val ownerName: String,
  val artworkUrl: String?, val trackCount: Int, val syncedAtMs: Long?
)

@Entity(tableName = "playlist_track", primaryKeys = ["playlistId","trackSpotifyId"])
data class PlaylistTrackCrossRef(val playlistId: String, val trackSpotifyId: String, val position: Int)

@Entity(tableName = "downloads")
data class DownloadEntity(
  @PrimaryKey val id: String, val spotifyId: String, val playlistId: String?,
  val status: String, val bytesDownloaded: Long, val totalBytes: Long?,
  val error: String?, val updatedAtMs: Long
)
```

`status` ∈ `QUEUED | RUNNING | COMPLETED | FAILED | CANCELLED`.

---

## Appendix E — Manual E2E acceptance script

1. Fresh install → app launches to login/library with clear "Connect Spotify" CTA.
2. Log in with a Premium-owner account (allowlisted). Token stored; survives relaunch; refreshed after expiry.
3. Library shows playlists (artwork, names). Open a playlist → tracks load with paging.
4. "Download all" → Downloads screen shows queued items; progress advances; app survives being backgrounded; kill+relaunch resumes.
5. Verify on disk: `Android/data/<pkg>/files/Music/...` contains tagged files (title/artist/artwork visible in a third-party player).
6. Cancel a download → row + `.part` file removed.
7. Search "Daft Punk" → results page, scroll loads more; download one → appears in library and plays offline.
8. Play offline with airplane mode on: audio plays; notification shows artwork/title; lock-screen seek/next/prev work.
9. Settings: storage usage accurate; "clear downloads" empties storage and library; logout clears tokens.
10. Re-run build: `./gradlew clean :app:assembleRelease :app:testDebugUnitTest` passes; `git status` clean of secrets.

---

## Appendix F — Research sources (verified 2026-10-11)

- Spotify — Feb 2026 Dev Mode migration guide & changelog; Dev Mode: Premium owner, ≤5 users, 1 client ID (→25 July 2026), search limit ≤10, `tracks`→`items`, removed batch/browse/artist-top-tracks endpoints.
  `developer.spotify.com/documentation/web-api/tutorials/february-2026-migration-guide`, `.../references/changes/february-2026`, `.../references/changes/march-2026`.
- Android — Media3 release notes (stable 1.11.1, Sep 2026); `MediaSessionService` background playback (`foregroundServiceType="mediaPlayback"`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK`); `DownloadService` (`dataSync`).
  `developer.android.com/jetpack/androidx/releases/media3`, `.../media/media3/session/background-playback`, `.../media/media3/exoplayer/downloading-media`.
- Android — Foreground service timeouts: `dataSync`/`mediaProcessing` 6h per 24h, `Service.onTimeout(int,int)` (API 35+).
  `developer.android.com/develop/background-work/services/fgs/timeout`, `.../about/versions/15/behavior-changes-15`.
- Android — WorkManager 2.12.0 stable (Sep 2026). `developer.android.com/jetpack/androidx/releases/work`.
- Audio tagging — `jaudiotagger` (MP3 ID3 + MP4/M4A); `SaltAudioTag` (KMP). `github.com/RouHim/jaudiotagger`.
- Extraction — `NewPipeExtractor` (JitPack, GPLv3, Java 11+, desugar `desugar_jdk_libs_nio` for minSdk<33); KMP fork `io.github.yushosei:newpipe-extractor-kmp-android`. `github.com/teamnewpipe/NewPipeExtractor`.
- Reference patterns — `D:\AI Projects\DayToDayProject\composeApp` (`data/spotify/*`, `di/{DatabaseModule,SpotifyModule,RepositoryModule,NetworkModule}.kt`, `settings/SettingsManager.kt`, `data/database/AppDatabase.kt`, `ui/navigation/*`, `MainActivity.kt`, `build.gradle.kts`, `AndroidManifest.xml`).
