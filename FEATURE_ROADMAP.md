# Spotify Replica — Feature Roadmap (v2): closing the gap with Spotify

> **Target executor:** OpenCode "Big Pickle" agent (one subagent per phase; see §7).
> **Working directory:** `C:\Users\IMOE001\Desktop\Shaked Projects\Shaked-music`
> **Predecessor:** `IMPLEMENTATION_PLAN.md` (phases 0–10 are **complete**: PKCE auth, playlists, search,
> downloads, local playback, settings, the reaching player + ±10 s timeline, shuffle-play).
> **Authored:** 2026-10-12.
> **Status:** Ready to execute phase-by-phase. Each phase is independently buildable/testable.
> **North star:** this app stays a **personal, sideloaded** build. It is a metadata browser + a
> local/YouTube player, and — new in v2 — a **remote control for the real Spotify app** on Premium.
> It will never stream Spotify's DRM audio itself (that is impossible through the Web API).

---

## 1. Why this plan exists

Phase 0–10 shipped a working app. A gap analysis against real Spotify (2026) identified ten areas where
the app falls short. This plan converts those into ordered, testable phases.

| # | Gap from the analysis | Covered by phase |
|---|---|---|
| 1 | No Liked Songs / Albums / Artists / Recently Played / Top content | **R1** |
| 2 | No real Spotify playback control (Spotify Connect / remote) | **R2** |
| 3 | Library is read-only (no create/edit playlists, no save/follow) | **R3** |
| 4 | No local queue management, sleep timer, resume-where-left-off | **R4** |
| 5 | No crossfade / gapless / equalizer / volume normalization / speed | **R5** |
| 6 | No download quality, Wi-Fi-only, and a "clear downloads" bug | **R6** |
| 7 | No discovery/stats shelf (naive radio, top/recent, auto-play) | **R7** |
| 8 | Now Playing is thin (no Queue/Lyrics/Related tabs) | **R8** |
| 9 | No platform integrations (widget, Android Auto, Cast, share) | **R9** |
| 10 | Search is tracks-only; no history; no localization/dynamic theme | **R10** |

Phases are ordered so the cheap, high-value wins land first and each phase is shippable on its own.

---

## 2. Ground rules & constraints (verified 2026-10-12)

**Spotify Web API, Development Mode:**
- The app owner **must have Spotify Premium**; the app stops working if it lapses.
- **≤5 authorized users** per app; ≤25 client IDs per developer (Jul 2026).
- **Search `limit` max = 10** (paginate with `offset`).
- **Playlist contents only return for playlists the user owns or collaborates on.**
- **Removed:** batch `GET /tracks|/albums|/artists|...`, `GET /browse/*`,
  `GET /artists/{id}/top-tracks`, `GET /users/{id}`, `GET /users/{id}/playlists`, `GET /markets`.
- **Dead since Nov 2024 (403 for new apps):** `audio-features`, `audio-analysis`, `/recommendations`,
  `related-artists`, featured playlists. **Do not design anything around these.**
- **Player endpoints are alive** (`/me/player/*`) — playback control + read state; **Premium only**.

**Codebase rules:**
- Stay on the existing architecture: Hilt DI modules, Retrofit+Moshi, Room, DataStore `SettingsManager`,
  Compose + `StateFlow`, Media3 `MediaSessionService`. Do **not** introduce new patterns.
- `spotify.properties` (root, git-ignored) holds `SPOTIFY_CLIENT_ID` / `SPOTIFY_REDIRECT_URI`. Changing
  **scopes** (below) changes `BuildConfig` only in the sense of the auth URL — but tokens already issued
  do **not** gain new scopes. You must **re-consent** (see R0).
- Gradle-level files (`app/build.gradle.kts`, `gradle/libs.versions.toml`) are **serialized**: one phase
  owns them at a time; never edit them from two parallel subagents.

**Legal / policy (unchanged from the original plan, restated):**
- Do **not** publish. Sideload only.
- The YouTube resolver is opt-in and off by default; keep it isolated.
- **Lyrics (R8)** come from a third-party source (e.g. LRCLIB), not Spotify. Gate it behind an explicit
  opt-in setting with a disclaimer, exactly like the audio-source toggle. Treat it as best-effort/gray-area.

---

## 3. Definition of Done (per phase and project-wide)

Per phase, before the next dependent phase starts:

| Gate | Command | Requirement |
|---|---|---|
| Compile | `.\gradlew.bat :app:assembleDebug` | succeeds |
| Unit tests | `.\gradlew.bat :app:testDebugUnitTest` | all green |
| Scope | `git diff --name-only` | only the phase's deliverables changed |
| Secrets | `git status --porcelain` | no `spotify.properties` / `local.properties` |
| Acceptance | the phase's own criteria | pass |

Project DoD additions for v2: the app still builds on a clean checkout with a dummy
`spotify.properties`; no new runtime permission is added without a manifest entry **and** an in-app
request; every new Spotify scope is requested in `SpotifyAuthManager.SCOPES` and reflected in the
README's dashboard checklist.

---

## 4. Phase plan

Each phase follows the predecessor's shape: **Objective / Depends on / Parallel with / Deliverables /
Steps / Acceptance / Verification / Subagent brief.**

---

### Phase R0 — Foundations: scope expansion, API surface, reconnect flow

**Objective:** Widen OAuth scopes and the Retrofit surface so later phases only add screens/repos.
Introduce a first-class "reconnect required" state, because existing tokens keep their old scopes.

**Depends on:** nothing (works against the shipped app).
**Parallel with:** nothing (it edits `SpotifyApi.kt`, `SpotifyAuthManager.kt`, `SpotifyDtos.kt` — shared).

**Deliverables**
- `data/remote/spotify/SpotifyAuthManager.kt` — new `SCOPES` (Appendix A).
- `data/remote/spotify/SpotifyApi.kt` — add endpoint groups (Appendix B) either here or in new
  interfaces `SpotifyLibraryApi`, `SpotifyPlayerApi`, `SpotifyCatalogApi` (preferred: keep `SpotifyApi`
  for auth-independent calls; add the rest as separate interfaces bound in `SpotifyModule`).
- `data/remote/spotify/SpotifyDtos.kt` — DTOs: saved tracks/albums/shows/episodes, album/artist detail,
  artist albums, top items, recently played, player state, device, queue, currently-playing.
- `data/remote/spotify/ScopeChecker.kt` (new) — maps a `403`/`HttpException` with
  `insufficient scope`/`permissions` to a `NeedsReconnect` signal.
- `data/settings/SettingsManager.kt` — add `spotifyNeedsReconnect: Flow<Boolean>` + setter, and
  `setSpotifyNeedsReconnect(Boolean)`.
- `data/repository/MusicCatalogRepositoryImpl.kt` — a private generic `page<TReq,TRes>()` helper so
  every paged endpoint shares token-refresh + error mapping.
- `ui/screen/settings/SettingsScreen.kt` + `SettingsViewModel.kt` — show a **"Reconnect Spotify"** row
  when `spotifyNeedsReconnect` is true, wired to the existing login flow.
- Tests: `ScopeCheckerTest`, DTO mapping tests for the new pages.

**Steps**
1. Update `SCOPES` in `SpotifyAuthManager` to the union in **Appendix A**. Keep it a single
   space-separated constant. Order doesn't matter but keep it stable for readability.
2. Add the new Retrofit interfaces + DTOs. **Do not** use any removed endpoint. Handle absent
   `items` (ownership rules) and the 2026 `playlist.items.items[].item` envelope.
   *Verify the envelope shape of `/me/tracks`, `/me/albums`, `/me/shows`, `/me/episodes` against current
   docs* — the `item` rename applies to the **playlist** object; saved-item pages historically use
   `items[].track` / `items[].album` / `items[].show` / `items[].episode`. Write tolerant Moshi DTOs
   (`@JsonClass(generateAdapter = true)`, nullable fields).
3. Wire `ScopeChecker` into the repository's error path: on an auth/scope `403`, set
   `spotifyNeedsReconnect = true` and return a `Result.failure` with a clear message.
4. Reconnect UX: Settings shows "Your Spotify permissions changed — reconnect to enable new features."
   Tapping it runs the same `LoginViewModel` flow (which re-requests the full scope set and re-consents).
5. Add unit tests for scope detection + the new DTO envelopes.

**Acceptance**
- App builds; existing features unchanged.
- A fresh login requests the full scope set and shows Spotify's consent with the new permissions.
- Simulating a `403 insufficient scope` surfaces the reconnect row (no crash) and no feature is
  permanently broken.

**Verification**
- `.\gradlew.bat :app:assembleDebug`
- `.\gradlew.bat :app:testDebugUnitTest`
- Manual: `git grep -n "SCOPES"` shows the new constant; paste its value.

**Subagent brief**
```
Implement Phase R0 — Foundations: scope expansion + API surface + reconnect flow, in the Spotify
Replica Android app. Repo root: C:\Users\IMOE001\Desktop\Shaked Projects\Shaked-music
Read FEATURE_ROADMAP.md §2, §3, Phase R0 and Appendix A/B.
Deliverables: SpotifyAuthManager SCOPES update; new SpotifyLibraryApi/SpotifyPlayerApi/SpotifyCatalogApi
interfaces + DTOs; ScopeChecker; SettingsManager reconnect flag; MusicCatalogRepositoryImpl page helper;
Settings "Reconnect Spotify" row; unit tests.
Rules: 2026 API only (no removed endpoints; search limit<=10; playlist items only for owned playlists).
Do NOT touch Gradle files. Do NOT change any UI other than the Settings reconnect row.
Acceptance: assembleDebug + testDebugUnitTest green; reconnect row appears on simulated 403.
Verification: run both Gradle commands; paste the tails.
```

---

### Phase R1 — Library expansion: Liked Songs, Albums, Artists, Recently Played, Top

**Objective:** Make the app a real *library*, not just a playlist list. Add the reachable content types.

**Depends on:** R0.
**Parallel with:** R3 (different screens; coordinate `MusicCatalogRepository` — R3 adds mutations, R1 adds
reads; if run together, split into `LibraryCatalogRepository` (R1) and keep `MusicCatalogRepository`
mutations in a new `PlaylistEditorRepository` (R3) to stay disjoint).

**Deliverables**
- `domain/model/{AlbumSummary,ArtistSummary,ShowSummary,EpisodeSummary,SavedTrack,PlayHistoryEntry}.kt`
- `domain/repository/LibraryCatalogRepository.kt` + `data/repository/LibraryCatalogRepositoryImpl.kt`
  - `likedSongs(offset,limit)`, `savedAlbums(...)`, `savedShows(...)`, `savedEpisodes(...)`,
    `followedArtists(...)`, `album(id)`, `albumTracks(id,...)`, `artist(id)`, `artistAlbums(id,...)`,
    `recentlyPlayed(...)`, `topTracks(range,...)`, `topArtists(range,...)`
- `ui/screen/library/LibraryScreen.kt` — add a **segmented top bar**: `Playlists · Liked · Albums ·
  Artists` (plus the existing "Your Downloads"), each a lazy list.
- New screens:
  - `ui/screen/liked/LikedSongsScreen.kt` + `LikedSongsViewModel.kt`
  - `ui/screen/album/AlbumScreen.kt` + `AlbumViewModel.kt`
  - `ui/screen/artist/ArtistScreen.kt` + `ArtistViewModel.kt`
- `ui/navigation/Screen.kt` + `AppNavHost.kt` — add `LikedSongs`, `Album(albumId)`, `Artist(artistId)`.
- `ui/components/{AlbumCard,ArtistCard,MediaHeader}.kt`
- A `LikeButton` on track rows (wired in R3 to actually mutate; here it can render state from
  `GET /me/library/contains` or `GET /me/tracks` membership).
- Tests: viewmodel paging tests; repository mapping tests.

**Steps**
1. Build `LibraryCatalogRepository` on the R0 `page()` helper. `user-library-read` and `user-top-read`
   are already requested by R0.
2. Restructure `LibraryScreen`: a `PrimaryTabRow`/segmented control; preserve pull-to-refresh and the
   logged-out CTA. Keep "Your Downloads" reachable.
3. `LikedSongsScreen`: paged `/me/tracks`; tapping a row plays it if downloaded, else offers download
   (reuse `TrackRow` + `DownloadButton`), and shows a like state.
4. `AlbumScreen`: header (artwork/name/artist/year) + `/albums/{id}/tracks`. `ArtistScreen`: header +
   `/artists/{id}/albums` + "Top tracks" is **not available** — omit it.
5. Recently Played + Top: fold into R7's Home/Stats surface **or** add a small "Recently played" strip
   on `LibraryScreen`. Do whichever keeps R1 self-contained; full stats UI is R7.
6. Tolerant handling: `/me/tracks` etc. may need `market`/country; if content is "unavailable for the
   client", render an empty state rather than an error.

**Acceptance**
- Library shows Liked Songs / Albums / Artists tabs populated from the real account.
- Opening an album/artist loads its content; back navigation works.
- Liking is reflected (persisted once R3 lands); no crashes on playlists/albums with zero items.

**Verification**
- `.\gradlew.bat :app:assembleDebug` + `.\gradlew.bat :app:testDebugUnitTest`
- Manual: sign in, confirm each new tab loads; screenshot or note counts.

**Subagent brief**
```
Implement Phase R1 — Library expansion. Depends on R0 (new API interfaces/DTOs exist).
Add LibraryCatalogRepository (+impl) for liked songs, saved albums/shows/episodes, followed artists,
album/artist detail, recently played, top tracks/artists. Add Library segmented tabs and screens
LikedSongs/Album/Artist, plus navigation routes. Reuse TrackRow/DownloadButton.
IMPORTANT: /artists/{id}/top-tracks is REMOVED — do not use it. Search limit<=10 (not relevant here but
keep the rule). Album/artist detail endpoints are available.
Deliverables: domain/model additions; LibraryCatalogRepository(+Impl); ui/screen/{library,liked,album,artist};
ui/components additions; Screen/AppNavHost routes; tests.
Acceptance: new tabs load real data; album/artist open; no crash on empty.
Verification: assembleDebug + testDebugUnitTest; paste tails.
```

---

### Phase R2 — Real Spotify playback control (`/me/player/*`, Premium)

**Objective:** Remote-control the user's **real Spotify app** — devices, state, queue, transport — the
closest thing to "real playback" a sideloaded app can offer. This does **not** stream audio.

**Depends on:** R0.
**Parallel with:** R1, R3 (distinct files: new `spotifyconnect` package + player UI additions).

**Deliverables**
- `domain/spotifyconnect/SpotifyConnectRepository.kt` + `data/repository/SpotifyConnectRepositoryImpl.kt`
  - reads: `devices()`, `playbackState()`, `currentlyPlaying()`, `queue()`
  - writes: `play(contextUri?, uris?, offset?)`, `pause()`, `next()`, `previous()`, `seek(ms)`,
    `setVolume(%)`, `setShuffle(bool)`, `setRepeat(mode)`, `transfer(deviceId)`, `addToQueue(uri)`
- `data/settings/SettingsManager.kt` — `playbackMode: Flow<String>` (`local` | `spotify`) + setter.
- `ui/components/SpotifyConnectSheet.kt` — device picker (from `devices()`), "Playing on <device>".
- `ui/screen/player/PlayerScreen.kt` + `PlayerViewModel.kt` — a **mode switch**: when
  `playbackMode == spotify`, transport controls call `SpotifyConnectRepository` and the progress/track
  come from `playbackState()` instead of the local ExoPlayer.
- `ui/components/MiniPlayerBar.kt` — reflect the remote state when in Spotify mode.
- Error handling: `403` (not Premium), `404` (no active device), `429` (rate limit → backoff).
- Tests: repository mapping; a state-mapping test for `spotify → PlayerState`.

**Steps**
1. Build `SpotifyConnectRepository`. All writes are `204 No Content`; treat non-2xx as `Result.failure`.
2. Add a **"Play on Spotify"** mode. When the user has Spotify installed + Premium and an active device,
   the player shows remote controls; otherwise it falls back to local playback (current behaviour).
3. **Ordering caveat (from Spotify docs):** the order of `/me/player/*` executions isn't guaranteed;
   poll `playbackState()` every ~1 s while the app is foregrounded and reconcile optimistically.
4. Device sheet: list `devices()`, show active device, allow `transfer(deviceId)`.
5. Handle rate limits with exponential backoff; never spam.
6. Add a `Settings` row: "Playback target: This app / Spotify".

**Acceptance**
- With Premium + an active Spotify device, the app can play/pause/skip/seek/volume/shuffle/repeat and
  transfer playback; the Now Playing metadata reflects the real Spotify state.
- Without Premium or a device, the feature degrades to an explanatory message and local mode still works.

**Verification**
- `.\gradlew.bat :app:assembleDebug` + `.\gradlew.bat :app:testDebugUnitTest`
- Manual (Premium): start Spotify on another device, then drive it from the app; confirm the change
  appears in the real Spotify app.

**Subagent brief**
```
Implement Phase R2 — Spotify remote playback control. Depends on R0 (SpotifyPlayerApi exists).
Add SpotifyConnectRepository (+Impl) over /me/player/{devices,playback-state,currently-playing,queue} and
the PUT/POST transport endpoints. Add a playbackMode setting (local|spotify). Add a device-picker sheet
and a player mode switch so transport controls target the real Spotify app when enabled.
Handle 403 (Premium required), 404 (no active device), 429 (backoff). Poll playback state ~1s only while
foreground. Order of commands is not guaranteed — reconcile by polling.
Deliverables: domain/spotifyconnect/*; data/repository/SpotifyConnectRepositoryImpl.kt;
SettingsManager.playbackMode; SpotifyConnectSheet; Player/MiniPlayer mode support; tests.
Acceptance: remote control works on Premium; graceful fallback otherwise.
Verification: assembleDebug + testDebugUnitTest; paste tails.
```

---

### Phase R3 — Playlist editing + library save/follow

**Objective:** Make the library writable: create/rename/edit playlists, add/remove/reorder items, upload
cover; save/unsave any item via the generic library endpoint.

**Depends on:** R0 (new scopes + APIs). Pairs with R1 (like buttons).
**Parallel with:** R1, R2 (files disjoint if R1 reads live in `LibraryCatalogRepository` and R3 mutations
live in a new `PlaylistEditorRepository` + `LibraryEditorRepository`).

**Deliverables**
- `domain/repository/PlaylistEditorRepository.kt` + `data/repository/PlaylistEditorRepositoryImpl.kt`
  - `createPlaylist(name, description, public)`, `updateDetails(id, name?, description?)`,
    `addItems(id, uris, position?)`, `removeItems(id, uris, snapshotId?)`,
    `reorder(id, rangeStart, insertBefore, rangeLength)`, `uploadCover(id, imageBytes)`
- `domain/repository/LibraryEditorRepository.kt` + impl
  - `save(uris)`, `remove(uris)`, `contains(uris)` — via `PUT/DELETE /me/library`,
    `GET /me/library/contains`
- UI:
  - `ui/screen/playlist/PlaylistScreen.kt` — overflow menu: **Edit details**, **Delete/remove items**,
    **Reorder** (long-press drag), and a header "Add to playlist".
  - `ui/screen/playlist/EditPlaylistScreen.kt` (+ VM) — name/description/public toggle; cover upload.
  - `ui/components/AddToPlaylistSheet.kt` — pick one of the user's playlists (or create new).
  - `ui/screen/library/LibraryScreen.kt` — a **"New playlist"** FAB/action.
  - `ui/components/LikeButton.kt` — wired to `LibraryEditorRepository.save/remove`.
- Tests: repository call shape (uris prefixed `spotify:track:`), reorder math, error handling.

**Steps**
1. Enforce the **2026 ownership rule**: editing is only valid for owned/collaborative playlists; hide
   edit affordances otherwise (based on the playlist's owner id vs `GET /me`).
2. `add/remove` take **URIs** (`spotify:track:<id>`), not ids. `reorder` uses the `PUT /playlists/{id}/items`
   body `{ range_start, insert_before, range_length }`.
3. `PUT /playlists/{id}/images` needs a base64 JPEG ≤256 KB — downscale before upload (reuse the
   artwork downscaler from `TagWriter`).
4. Optimistic UI + refresh on `Result.failure`; show a snackbar.
5. Coalesce rapid like/unlike taps (debounce ~400 ms) to avoid burst rate limits.

**Acceptance**
- Create a playlist, add tracks from search/liked, reorder, remove, rename, set a cover.
- Like/unlike a track updates Liked Songs.
- Editing is hidden for playlists you don't own.

**Verification**
- `.\gradlew.bat :app:assembleDebug` + `.\gradlew.bat :app:testDebugUnitTest`
- Manual: perform each mutation and confirm it in the real Spotify app.

**Subagent brief**
```
Implement Phase R3 — playlist editing + library save. Depends on R0.
Add PlaylistEditorRepository (POST /me/playlists; PUT /playlists/{id}; POST/PUT/DELETE /playlists/{id}/items;
PUT /playlists/{id}/images) and LibraryEditorRepository (PUT/DELETE /me/library, GET /me/library/contains).
Add EditPlaylist screen, AddToPlaylist sheet, New-playlist action, LikeButton, and long-press reorder.
URIs (spotify:track:<id>), not ids. Editing only for owned/collaborative playlists (2026 rule).
Cover upload: base64 JPEG <=256KB, downscale first.
Deliverables: repositories(+Impl); ui/screen/playlist/EditPlaylist*; AddToPlaylistSheet; LikeButton;
LibraryScreen new-playlist; tests.
Acceptance: mutations work and are visible in the real Spotify app; edit hidden when not owner.
Verification: assembleDebug + testDebugUnitTest; paste tails.
```

---

### Phase R4 — Local player: queue management, sleep timer, resume

**Objective:** Bring the local player up to Spotify's queue/timer basics — all local, no API needed.

**Depends on:** nothing beyond the shipped app (touches playback + player UI).
**Parallel with:** R5 (both touch the player/playback); **serialize R4 and R5** — run R4 first.

**Deliverables**
- `playback/PlaybackConnection.kt` — queue ops: `addToQueue(mediaItem)`, `playNext(mediaItem)`,
  `moveQueueItem(from,to)`, `removeQueueItem(index)`, `clearQueue()`, `setQueue(list, startIndex)` (exists).
- `playback/PlaybackService.kt` — persist queue + position on change; restore on start.
- `data/settings/SettingsManager.kt` — `lastQueueJson`, `lastPositionMs`, `lastTrackId` (or a Room table
  `playback_state` — prefer Room for the queue).
- `ui/screen/player/PlayerScreen.kt` — queue tab becomes editable: drag-reorder, swipe-remove,
  "Play next"/"Add to queue" actions from track rows' overflow menus.
- `ui/components/SleepTimerSheet.kt` — presets (5/15/30/60 min, "End of track") + cancel; pauses playback
  when it fires. State survives process death.
- `ui/components/TrackOverflowMenu.kt` — shared 3-dot menu (Play next, Add to queue, Go to album/artist,
  Download, Like, Share once R9 lands).
- Tests: queue mutation logic; sleep-timer countdown (fake clock).

**Steps**
1. Use Media3 `MediaController` queue APIs (`addMediaItem`, `moveMediaItem`, `removeMediaItem`,
   `clearMediaItems`, `seekToNextMediaItem`). Keep the app-wide singleton controller.
2. Persist on `onMediaItemTransition`/`onIsPlayingChanged`; restore in `PlaybackService.onCreate` if the
   queue is empty. "Resume where you left off": restore position for the last track.
3. Sleep timer: a coroutine in the service (survives config changes; re-armed on service restart),
   `Handler`-free; "End of track" listens for `onMediaItemTransition`.
4. Wire the overflow menu into `TrackRow`, `SearchResultRow`, queue rows.
5. Add a Settings toggle: "Resume playback on app open".

**Acceptance**
- Add-to-queue / play-next / reorder / remove / clear all work and are visible in the player.
- Killing and reopening the app restores the queue and (optionally) the position.
- Sleep timer stops playback at the set time / end of track and can be cancelled.

**Verification**
- `.\gradlew.bat :app:assembleDebug` + `.\gradlew.bat :app:testDebugUnitTest`
- Manual: exercise queue ops + sleep timer.

**Subagent brief**
```
Implement Phase R4 — local queue management, sleep timer, resume. Depends on the shipped app.
Use Media3 MediaController queue APIs in PlaybackConnection; persist queue+position (Room or DataStore);
restore on PlaybackService start. Make the Player queue tab editable (drag reorder, swipe remove) and add
a shared TrackOverflowMenu (Play next / Add to queue / Go to album|artist / Download / Like). Add a
SleepTimerSheet (presets + End of track) driven by the service.
Deliverables: PlaybackConnection queue ops; PlaybackService persistence; SettingsManager keys or Room
playback_state; PlayerScreen queue editing; SleepTimerSheet; TrackOverflowMenu; tests.
Acceptance: all queue ops work; queue/position restore; sleep timer fires and cancels.
Verification: assembleDebug + testDebugUnitTest; paste tails.
```

---

### Phase R5 — Audio polish: gapless, equalizer, normalization, speed, crossfade

**Objective:** Spotify-grade listening controls on the local player. **Crossfade is the hard one** — do
the easy wins first and treat crossfade as its own sub-step.

**Depends on:** R4 (both touch playback; serialize after R4).
**Parallel with:** nothing that touches playback.

**Deliverables**
- `playback/PlaybackService.kt` / `PlaybackConnection.kt`:
  - **Gapless:** `exoPlayer.setSkipSilenceEnabled(true)`; ensure consecutive same-format items aren't
    re-buffered (ExoPlayer is gapless for supported formats).
  - **Speed:** `MediaController.setPlaybackParameters(speed)` (0.5×–2×).
  - **Equalizer + normalization:** attach `android.media.audiofx.Equalizer` and
    `android.media.audiofx.LoudnessEnhancer` via `AudioListener.onAudioSessionIdChanged`.
- `playback/CrossfadeAudioProcessor.kt` (new) — optional custom `AudioProcessor` that ramps gain across
  item boundaries; behind a setting, default off, with a clear "experimental" note.
- `data/settings/SettingsManager.kt` — `gaplessEnabled`, `equalizerEnabled`, `equalizerPreset`,
  `normalizationEnabled`, `playbackSpeed`, `crossfadeMs`.
- `ui/screen/settings/SettingsScreen.kt` — an **Audio** section (Playback) mirroring Spotify's.
- `ui/screen/player/PlayerScreen.kt` — speed control in the player overflow; optional EQ shortcut.
- Tests: settings persistence; speed clamping; crossfade ramp math (unit-testable in isolation).

**Steps**
1. Ship gapless + speed + EQ + normalization first (all low-risk, well-supported).
2. Equalizer: guard for device support (`Equalizer.getNumberOfPresets()`), release the effect on
   session change and in `onDestroy`; requests audio permission? **No** — `MODIFY_AUDIO_SETTINGS` only
   (add to manifest if required for `LoudnessEnhancer`/`Equalizer`).
3. Crossfade: implement as a custom `AudioProcessor` in the ExoPlayer audio sink, or (simpler, accepted
   fallback) a **software fade** that ramps `volume` in the last/first N seconds of adjacent tracks.
   Mark it experimental; it can interact oddly with gapless.
4. Ensure effects are **bypassed** in Spotify remote mode (R2) — they only apply to local playback.

**Acceptance**
- Gapless removes inter-track silence where the format allows.
- Speed, EQ presets, and normalization audibly work and persist.
- Crossfade (if enabled) fades between tracks without crashing or breaking the queue.

**Verification**
- `.\gradlew.bat :app:assembleDebug` + `.\gradlew.bat :app:testDebugUnitTest`
- Manual listening checks; note the format/deviation constraints of crossfade.

**Subagent brief**
```
Implement Phase R5 — audio polish. Depends on R4.
Gapless: setSkipSilenceEnabled(true). Speed: setPlaybackParameters. Equalizer + LoudnessEnhancer via
AudioListener.onAudioSessionIdChanged, released on session change/destroy. Crossfade: a custom
AudioProcessor OR a volume-ramp fade, behind an off-by-default "experimental" setting.
Add an Audio section to Settings and a speed control in the player. Bypass audio effects in Spotify
remote mode. Add MODIFY_AUDIO_SETTINGS to the manifest if required.
Deliverables: playback additions; CrossfadeAudioProcessor (optional); SettingsManager keys; Settings
Audio section; player speed control; tests.
Acceptance: gapless/speed/EQ/normalization work and persist; crossfade does not break the queue.
Verification: assembleDebug + testDebugUnitTest; paste tails.
```

---

### Phase R6 — Downloads: quality, Wi-Fi-only, and fix "clear downloads"

**Objective:** Finish the offline feature set and fix the known data bug.

**Depends on:** the shipped download engine.
**Parallel with:** R1/R2/R3 (different files). Serialize vs R4/R5 only if they touch `SettingsManager`
(prefer R6 after R4/R5 to avoid concurrent `SettingsManager` edits).

**Deliverables**
- `data/settings/SettingsManager.kt` — `downloadQuality: Flow<String>` (`low|normal|high|original`),
  `downloadWifiOnly: Flow<Boolean>`, `autoDownloadLiked: Flow<Boolean>`.
- `data/download/DownloadQueue.kt` — apply `NetworkType.UNMETERED` when Wi-Fi-only is on
  (`Constraints`); re-enqueue when the setting changes.
- `data/remote/source/YouTubeResolver.kt` / `ResolvedAudio` — honor a **quality cap** by preferring a
  lower-bitrate stream (YouTube itags) when `downloadQuality` is limited; keep "original" = best.
- `data/repository/LibraryRepositoryImpl.kt` — **fix** `clearAll()` / "clear downloads" so it also
  deletes `tracks` rows (and any `playlist_track` rows that only existed for downloads), not just the
  `downloads` table. Verify no dangling `localPath`.
- `ui/screen/settings/SettingsScreen.kt` — Download section: quality, Wi-Fi-only, auto-download Liked.
- `domain/usecase/DownloadPlaylistUseCase.kt` — reuse for "download all liked" when auto-download is on.
- Tests: constraint selection; quality preference; clear-downloads removes tracks + files.

**Steps**
1. Add the settings + UI.
2. Make `DownloadQueue` read settings at enqueue time and set `Constraints` accordingly.
3. Quality: map the setting to a stream-choice policy in the resolver; document that Spotify tracks
   aren't downloadable (this only affects YouTube/local sources).
4. Fix the clear bug and add a regression test asserting `TrackDao` count == 0 afterwards.

**Acceptance**
- Wi-Fi-only blocks cellular downloads and resumes on Wi-Fi.
- Quality setting changes the chosen source bitrate where available.
- "Clear downloads" leaves **no** dangling tracks and frees storage.

**Verification**
- `.\gradlew.bat :app:assembleDebug` + `.\gradlew.bat :app:testDebugUnitTest`
- Manual: toggle Wi-Fi-only on cellular; clear downloads; inspect Room + filesystem.

**Subagent brief**
```
Implement Phase R6 — download quality, Wi-Fi-only, and fix clear-downloads.
Add SettingsManager keys (downloadQuality, downloadWifiOnly, autoDownloadLiked) and a Settings Download
section. Apply NetworkType.UNMETERED in DownloadQueue when Wi-Fi-only. Map downloadQuality to a
stream-choice policy in the resolver (affects YouTube/local sources only). FIX LibraryRepositoryImpl
clearAll()/clear-downloads to also delete `tracks` rows (currently only `downloads`), preventing dangling
localPath. Add regression tests (TrackDao count == 0 after clear).
Deliverables: SettingsManager keys; DownloadQueue constraints; resolver quality policy; LibraryRepository
fix; Settings Download section; tests.
Acceptance: Wi-Fi-only works; quality applies; clear leaves no tracks/files.
Verification: assembleDebug + testDebugUnitTest; paste tails.
```

---

### Phase R7 — Explore + Stats: recently played, top, naive radio, local autoplay

**Objective:** Add the personalization the API *does* expose, plus honest local substitutes for what it
doesn't. No reliance on dead endpoints.

**Depends on:** R1 (recently/top reads).
**Parallel with:** R8, R9, R10 (distinct screens/packages).

**Deliverables**
- `ui/screen/stats/StatsScreen.kt` + `StatsViewModel.kt` — top tracks/artists by range
  (`short|medium|long`), recently played; a "Your 2026" style summary built from that data (not Wrapped).
- `ui/screen/explore/ExploreScreen.kt` + VM (optional Home tab) — shelves: Recently played, Top this
  month, Downloaded.
- `domain/usecase/StartRadioUseCase.kt` — **naive radio**: seed from a track/artist → `GET /search`
  (artist/album) + `/artists/{id}/albums` → build a queue. Respect search `limit<=10`.
- `domain/usecase/AutoplayUseCase.kt` — when the queue ends, append similar tracks **from the local
  downloaded library** (same artist/album first), then optionally extend via radio. Behind a setting.
- Navigation: decide between adding a 5th bottom tab **Home** vs. folding Explore into Library. Default:
  add **Home** as the first tab and move Downloads into Settings? No — keep 4 tabs; put Explore as a
  section on Library, and Stats as a Settings entry. (See §8 Q-R7.)
- Tests: radio seeding; autoplay selection; range → endpoint mapping.

**Steps**
1. Build Stats from `recentlyPlayed()` + `topTracks()/topArtists()`.
2. Radio: seed + search + artist albums; dedupe; cap queue length (~50); resolve playback via the
   existing local/YouTube pipeline (downloads may be needed first → offer "download these").
3. Autoplay: purely local over Room; no API. If the library is empty, no-op.
4. Make it clear in the UI that this is **your library's** data, not Spotify's recommendations.

**Acceptance**
- Stats shows real top/recent data with working range switching.
- "Start radio" builds a playable queue from downloaded tracks.
- Autoplay appends local tracks when the queue ends (toggleable).

**Verification**
- `.\gradlew.bat :app:assembleDebug` + `.\gradlew.bat :app:testDebugUnitTest`

**Subagent brief**
```
Implement Phase R7 — Explore + Stats + naive radio + local autoplay. Depends on R1.
StatsScreen: top tracks/artists by range + recently played. Explore shelves: Recently played, Top month,
Downloaded. StartRadioUseCase: seed via /search + /artists/{id}/albums (search limit<=10). AutoplayUseCase:
append similar LOCAL tracks when the queue ends (toggleable). No dead endpoints (no recommendations /
related-artists / top-tracks-of-artist).
Deliverables: ui/screen/stats/*; ui/screen/explore/*; domain/usecase/{StartRadioUseCase,AutoplayUseCase};
navigation; tests.
Acceptance: stats work; radio builds a queue; autoplay appends local tracks.
Verification: assembleDebug + testDebugUnitTest; paste tails.
```

---

### Phase R8 — Now Playing: full-screen with Queue / Lyrics / Related tabs

**Objective:** Bring the Now Playing screen closer to Spotify's — tabs, full-screen art, and an opt-in
lyrics view.

**Depends on:** R4 (queue UI), R2 (remote queue when in Spotify mode).
**Parallel with:** R7, R9, R10.

**Deliverables**
- `ui/screen/player/PlayerScreen.kt` — full-screen layout with a **tab row**: **Queue · Lyrics · Related**
  (mirrors Spotify's Now Playing), swipe between tabs; large artwork; expand/collapse.
- `ui/screen/player/LyricsTab.kt` + `domain/lyrics/LyricsProvider.kt` + `data/remote/lyrics/LrcLibProvider.kt`
  (third-party, opt-in). Untimed lyrics are acceptable; timed if the provider returns LRC.
- `ui/screen/player/RelatedTab.kt` — shows **artist albums** (`/artists/{id}/albums`) and "More from
  album" (`/albums/{id}/tracks`). **Do not** use related-artists (dead).
- `data/settings/SettingsManager.kt` — `lyricsEnabled: Flow<Boolean>` + setter.
- `ui/screen/settings/SettingsScreen.kt` — "Lyrics (third-party)" toggle with disclaimer.
- `ui/components/{NowPlayingHeader,CanvasArt}.kt` — static cover with a subtle animated background
  (no Canvas video; that isn't exposed).
- Tests: lyrics provider parsing; tab state.

**Steps**
1. Refactor `PlayerScreen` into a scaffold with tabs; keep the existing timeline + ±10 s controls.
2. Lyrics: fetch by `title + artist` (+ duration for matching) from the opt-in provider; cache in Room;
   show "Lyrics not available" cleanly. Clearly label it as third-party and unofficial.
3. Related: reuse R1's `LibraryCatalogRepository.artistAlbums()`; tapping plays/searches.
4. In Spotify remote mode (R2), the Queue tab reads `/me/player/queue`.

**Acceptance**
- Now Playing has Queue/Lyrics/Related tabs; navigation is smooth.
- Lyrics appear when enabled and available, never crash when offline/unavailable.
- Related shows artist albums.

**Verification**
- `.\gradlew.bat :app:assembleDebug` + `.\gradlew.bat :app:testDebugUnitTest`

**Subagent brief**
```
Implement Phase R8 — Now Playing with Queue/Lyrics/Related tabs. Depends on R4 (queue UI) and R2 (remote).
Refactor PlayerScreen into a full-screen tabbed layout (Queue / Lyrics / Related). Lyrics: an opt-in,
clearly-labeled THIRD-PARTY provider (e.g. LRCLIB), cached in Room, graceful when unavailable. Related:
artist albums (/artists/{id}/albums) and album tracks. DO NOT use related-artists (dead endpoint).
Add a Settings toggle for lyrics with a disclaimer.
Deliverables: PlayerScreen tabs; LyricsTab + LyricsProvider + LrcLibProvider; RelatedTab; SettingsManager
lyricsEnabled; Settings toggle; components; tests.
Acceptance: tabs work; lyrics opt-in and resilient; related shows albums.
Verification: assembleDebug + testDebugUnitTest; paste tails.
```

---

### Phase R9 — Platform integrations: widget, Android Auto, share, quick tile, (Cast optional)

**Objective:** Put the app on the surfaces Spotify occupies on Android.

**Depends on:** R4 (queue/player state to expose).
**Parallel with:** R7, R8, R10 — but **owns the Gradle edit** for Glance/Cast (serialize Gradle).

**Deliverables**
- **Home-screen widget** — `androidx.glance:glance-appwidget` (`MiniPlayerWidget.kt`, `WidgetReceiver`,
  manifest + `appwidget-provider` XML). Shows artwork/title + play-pause; taps open the app.
- **Android Auto / Automotive** — verify Media3 `MediaSessionService` is discoverable; add the media
  browser intent-filter/`queries` as needed; test in the Desktop Head Unit (DHU) if available.
- **Share** — `ACTION_SEND` from `TrackOverflowMenu`: share the `open.spotify.com/track/<id>` link.
- **Quick Settings tile** — `TileService` for play/pause.
- **(Optional) Chromecast** — `androidx.mediarouter` + Cast SDK `OptionsProvider`; larger; do only if
  requested (§8 Q-R9). Mark as a stretch.
- Gradle: add Glance (and Cast if chosen). **One phase owns this edit.**

**Steps**
1. Widget first (highest value, lowest risk). Use Glance + a `CoroutineWorker`/`RemoteViews` update on
   playback-state changes.
2. Android Auto: mostly configuration; confirm the session advertises the media browser correctly.
3. Share: trivial; add to the shared overflow menu from R4.
4. Quick Settings tile: `TileService` toggling play/pause through `PlaybackConnection`.
5. Cast: only if greenlit.

**Acceptance**
- Widget shows the current track and play/pause from the home screen.
- Android Auto shows the app and can browse/play local content.
- Sharing produces a valid Spotify link. Quick tile toggles playback.

**Verification**
- `.\gradlew.bat :app:assembleDebug` + `.\gradlew.bat :app:testDebugUnitTest`
- Manual: add the widget, test the tile, verify the share sheet.

**Subagent brief**
```
Implement Phase R9 — platform integrations. Depends on R4. This phase OWNS the Gradle edit (Glance;
Cast only if chosen). Add a Glance home-screen widget (artwork/title + play-pause, taps open app),
verify Android Auto media browse, add a share action (open.spotify.com link) to TrackOverflowMenu, and a
Quick Settings TileService for play/pause. Cast is optional/stretch.
Deliverables: widget + receiver + XML + manifest; Auto config; share action; tile service; Gradle edits;
tests where feasible.
Acceptance: widget works; tile toggles; share emits a valid link; Auto lists the app.
Verification: assembleDebug + testDebugUnitTest; paste tails.
```

---

### Phase R10 — Unified search, history, dynamic theme, localization

**Objective:** Round out the everyday UX toggles Spotify has.

**Depends on:** R1 (album/artist screens to deep-link results into).
**Parallel with:** R7, R8, R9.

**Deliverables**
- `ui/screen/search/SearchScreen.kt` — **type tabs** (Tracks / Albums / Artists / Playlists / Podcasts &
  Audiobooks) using `type=track,album,artist,playlist,show,episode,audiobook` (comma-separated), each
  paged with `limit<=10`.
- `data/settings/SettingsManager.kt` + `data/db` — **search history** (last ~20 queries) and local
  suggestions; clear-history action.
- `ui/theme/Theme.kt` — **Material You dynamic color** (`dynamicDarkColorScheme`/`dynamicLightColorScheme`
  on API 31+) with a manual override in Settings (System / Light / Dark / Dynamic).
- **Localization scaffolding** — move user-facing strings from code into `res/values/strings.xml`
  (start with the new screens; don't boil the ocean); add a `values-<lang>` for one extra language if
  the user wants it.
- Tests: search-type query building; history persistence.

**Steps**
1. Generalize the search response DTOs to carry all types (tolerant, nullable).
2. Tabs use one query with `type=` all-types and `limit=10`; deep-link album/artist/playlist results to
   R1's screens.
3. History: Room table `search_history(query, timestamp)`; show on empty query.
4. Dynamic color: `Build.VERSION.SDK_INT >= 31` branch; persist the user's theme choice.
5. Strings: extract opportunistically; ensure no hardcoded new strings in R10.

**Acceptance**
- Search returns and renders albums/artists/playlists/episodes, not just tracks.
- History appears on an empty query and can be cleared.
- Theme choice persists; dynamic color applies on Android 12+.

**Verification**
- `.\gradlew.bat :app:assembleDebug` + `.\gradlew.bat :app:testDebugUnitTest`

**Subagent brief**
```
Implement Phase R10 — unified search + history + dynamic theme + localization scaffolding. Depends on R1.
Search: type tabs (track/album/artist/playlist/show/episode/audiobook) via comma-separated type=, paged
with limit<=10; deep-link album/artist to R1 screens. Add a Room search_history table + suggestions +
clear action. Theme: Material You dynamic color (API 31+) with a persisted System/Light/Dark/Dynamic
choice. Extract new user-facing strings into res/values/strings.xml.
Deliverables: SearchScreen tabs; SearchResponse DTOs; search_history + DAO; Theme + Settings theme row;
strings.xml additions; tests.
Acceptance: multi-type search renders; history works; theme persists + dynamic color applies.
Verification: assembleDebug + testDebugUnitTest; paste tails.
```

---

## 5. Dependency waves & parallelism

```
Wave A:  R0
Wave B:  R1  ∥  R2  ∥  R3           (disjoint: reads vs remote vs mutations)
Wave C:  R4                          (playback; serialize before R5)
Wave D:  R5                          (playback; after R4)
Wave E:  R6                          (downloads; after R4/R5 to avoid SettingsManager races)
Wave F:  R7  ∥  R8  ∥  R9  ∥  R10    (disjoint screens/packages; R9 owns its Gradle edit)
```

Rules:
- **Never** run two editing subagents in the same working tree on overlapping files. Use git worktrees
  for genuine parallel edits (see the predecessor plan §9.4).
- **Serialize Gradle edits** (`app/build.gradle.kts`, `gradle/libs.versions.toml`): R9 (and only R9)
  owns the widget/Cast dependency until it merges.
- R4 → R5 → R6 share `PlaybackConnection`/`SettingsManager`; run them sequentially.

## 6. Cross-cutting: subagent dispatch protocol

Reuse the predecessor plan's §9 verbatim (self-contained briefs; repo root; exact deliverables; a
"touch only these paths" constraint; independent verification by the lead; commit per phase as
`phase R<N>: <name>`). Include in every brief:
- the working directory `C:\Users\IMOE001\Desktop\Shaked Projects\Shaked-music`;
- the instruction to read `FEATURE_ROADMAP.md` (§2, §3, the phase, and the appendices);
- the 2026 API rules (no removed endpoints; `limit<=10`; playlist items only for owned playlists);
- the "do not touch Gradle unless this phase owns it" rule;
- the required verification commands (`.\gradlew.bat :app:assembleDebug`,
  `.\gradlew.bat :app:testDebugUnitTest`) with pasted output.

## 7. Verification gates

Use §3 as the per-phase gate table. Additionally:
- Every new Spotify scope must be exercised at least once manually (login consent shows it).
- Every phase that adds a permission must include the manifest entry **and** a runtime request.
- `git log -p | grep -i SPOTIFY_CLIENT_ID` must stay empty (secrets policy unchanged).

## 8. Risk register (delta vs the predecessor plan)

| # | Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|---|
| V1 | Added scopes don't apply to existing tokens | High | Medium | R0 reconnect flow; detect `403 insufficient scope`; prompt re-consent |
| V2 | Premium required for `/me/player/*`; owner lapses | Medium | High | Graceful 403 handling; local mode remains the fallback |
| V3 | `/me/player/*` command ordering is not guaranteed | High | Low | Poll playback state while foregrounded; reconcile optimistically |
| V4 | Playlist editing limited to owned/collaborative playlists | High | Low | Hide edit affordances when not owner; explain in UI |
| V5 | Reorder/snapshotId races on busy playlists | Medium | Medium | Send `snapshot_id`; refresh on failure; retry once |
| V6 | Crossfade conflicts with gapless / format limits | High | Medium | Ship gapless/EQ/speed first; crossfade experimental + off by default |
| V7 | Rate limits from burst like/unlike or rapid queue ops | Medium | Medium | Debounce (~400 ms); exponential backoff on 429 |
| V8 | Lyrics provider is third-party / gray-area / can break | Medium | Medium | Opt-in + disclaimer; cache; graceful "unavailable" |
| V9 | `clearAll()` bug left unfixed → dangling `localPath` | High if skipped | Medium | R6 fixes it with a regression test |
| V10 | Glance/Cast dependency conflicts with existing Compose/Media3 | Low | Medium | R9 owns the Gradle edit; build after each change |

## 9. Open decisions (confirm with the user before the flagged phase)

| # | Question | Default in this plan | Needed by |
|---|---|---|---|
| Q-R1 | Put Liked/Albums/Artists as tabs **inside** Library, or a new **Home** bottom tab? | Tabs inside Library | R1 |
| Q-R2 | Enable Spotify **remote-control** mode (needs Premium)? | Yes, opt-in in Settings | R2 |
| Q-R3 | Enable **playlist editing** (needs write scopes + re-consent)? | Yes | R3 |
| Q-R5 | Attempt **crossfade** (experimental, may be imperfect)? | Build gapless/EQ/speed; crossfade optional | R5 |
| Q-R7 | Where do **Stats/Explore** live (Settings entry vs new tab)? | Settings entry + Library shelves | R7 |
| Q-R8 | Enable **third-party lyrics** (gray-area, opt-in)? | Off by default, toggle with disclaimer | R8 |
| Q-R9 | Include **Chromecast**, or widget/Auto/share/tile only? | Widget/Auto/share/tile only; Cast is a stretch | R9 |
| Q-R10 | Add a second UI language? If so, which? | English only; scaffold extraction | R10 |

---

## Appendix A — OAuth scopes to request (union for v2)

```
user-read-private
user-read-email
user-library-read
user-library-modify
user-follow-read
user-top-read
user-read-recently-played
user-read-playback-state
user-modify-playback-state
user-read-currently-playing
playlist-read-private
playlist-read-collaborative
playlist-modify-public
playlist-modify-private
```

Notes:
- `user-read-email` is optional (`email` was removed from `/me` in dev mode; drop it unless needed).
- Adding scopes requires **re-consent** (R0). Keep the constant stable and documented in the README.
- Remove any scope a phase doesn't actually use, to minimize the consent screen.

## Appendix B — New endpoints used by v2 (all Dev-Mode-available)

| Need | Endpoint |
|---|---|
| Liked songs | `GET /me/tracks` |
| Saved albums / shows / episodes / audiobooks | `GET /me/albums`, `/me/shows`, `/me/episodes`, `/me/audiobooks` |
| Followed artists | `GET /me/following` |
| Save / remove / contains | `PUT /me/library`, `DELETE /me/library`, `GET /me/library/contains` |
| Album detail / tracks | `GET /albums/{id}`, `GET /albums/{id}/tracks` |
| Artist detail / albums | `GET /artists/{id}`, `GET /artists/{id}/albums` |
| Track detail | `GET /tracks/{id}` |
| Top items | `GET /me/top/{type}?time_range=` |
| Recently played | `GET /me/player/recently-played` |
| Create / edit playlist | `POST /me/playlists`, `PUT /playlists/{id}` |
| Playlist items | `GET|POST|DELETE|PUT /playlists/{id}/items` |
| Playlist cover | `PUT /playlists/{id}/images` |
| Devices / state / currently playing / queue | `GET /me/player/devices`, `/me/player`, `/me/player/currently-playing`, `/me/player/queue` |
| Transport | `PUT /me/player/{play,pause,seek,volume,shuffle,repeat}`, `POST /me/player/{next,previous,queue}`, `PUT /me/player` (transfer) |

**Do not use:** `GET /tracks|/albums|/artists|/shows|/episodes|/audiobooks|/chapters` (batch),
`GET /browse/*`, `GET /artists/{id}/top-tracks`, `GET /users/{id}`, `GET /users/{id}/playlists`,
`GET /markets`, `GET /audio-features*`, `GET /audio-analysis*`, `GET /recommendations`,
`GET /artists/{id}/related-artists`, featured playlists.

## Appendix C — New/changed files (by phase)

```
R0  data/remote/spotify/{SpotifyAuthManager,SpotifyApi,SpotifyDtos,ScopeChecker}.kt
    data/settings/SettingsManager.kt · data/repository/MusicCatalogRepositoryImpl.kt
    ui/screen/settings/{SettingsScreen,SettingsViewModel}.kt
R1  domain/model/{AlbumSummary,ArtistSummary,ShowSummary,EpisodeSummary,SavedTrack,PlayHistoryEntry}.kt
    domain/repository/LibraryCatalogRepository.kt · data/repository/LibraryCatalogRepositoryImpl.kt
    ui/screen/{library,liked,album,artist}/* · ui/components/{AlbumCard,ArtistCard,MediaHeader}.kt
    ui/navigation/{Screen,AppNavHost}.kt
R2  domain/spotifyconnect/SpotifyConnectRepository.kt · data/repository/SpotifyConnectRepositoryImpl.kt
    ui/components/SpotifyConnectSheet.kt · ui/screen/player/{PlayerScreen,PlayerViewModel}.kt
R3  domain/repository/{PlaylistEditorRepository,LibraryEditorRepository}.kt (+Impls)
    ui/screen/playlist/EditPlaylistScreen.kt (+VM) · ui/components/{AddToPlaylistSheet,LikeButton}.kt
R4  playback/{PlaybackConnection,PlaybackService}.kt · ui/components/{SleepTimerSheet,TrackOverflowMenu}.kt
    ui/screen/player/PlayerScreen.kt · data/db (playback_state, optional)
R5  playback/CrossfadeAudioProcessor.kt · playback/{PlaybackService,PlaybackConnection}.kt
    ui/screen/settings/SettingsScreen.kt · ui/screen/player/PlayerScreen.kt
R6  data/settings/SettingsManager.kt · data/download/DownloadQueue.kt · data/remote/source/*
    data/repository/LibraryRepositoryImpl.kt · ui/screen/settings/SettingsScreen.kt
R7  ui/screen/{stats,explore}/* · domain/usecase/{StartRadioUseCase,AutoplayUseCase}.kt
R8  ui/screen/player/{PlayerScreen,LyricsTab,RelatedTab}.kt · domain/lyrics/LyricsProvider.kt
    data/remote/lyrics/LrcLibProvider.kt · data/settings/SettingsManager.kt
R9  widget/*, tile/*, manifest + appwidget XML · ui/components/TrackOverflowMenu.kt · Gradle (owns)
R10 ui/screen/search/SearchScreen.kt · data/db (search_history) · ui/theme/Theme.kt · res/values/strings.xml
```

## Appendix D — Sources (verified 2026-10-12)

- Spotify — Feb 2026 Dev Mode migration guide & changelog; "endpoints still available" list; Search
  `limit` ≤ 10; playlist `/items` rename; removed batch/browse/artist-top-tracks/user endpoints.
- Spotify — Nov 2024 deprecations (`audio-features`, `audio-analysis`, `/recommendations`,
  `related-artists`, featured playlists) — 403 for apps created after that date.
- Spotify — Web API Player endpoints (`/me/player/*`) and Premium requirement for playback control.
- Spotify Support — Play Queue, Shuffle play (Smart Shuffle / fewer repeats), Transitions (crossfade,
  Automix, gapless), Equalizer, Audio quality, Now Playing view, Lyrics, Your Library, Spotify Connect,
  Jam/Blend (social — not exposed via API).
- Android — Media3 (ExoPlayer `MediaSessionService`, `setSkipSilence`, `setPlaybackParameters`,
  `AudioListener.onAudioSessionIdChanged`), `android.media.audiofx.{Equalizer,LoudnessEnhancer}`;
  Glance app widgets; `TileService`; Android Auto media apps; Cast/MediaRouter.
