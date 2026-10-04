# Development notes & handoff

Context carried over from the planning session, so development can continue on any machine.

## Product decisions (confirmed by the owner)

| Topic | Decision |
|---|---|
| Platform | Android phones only (owner chose Android, even though the team's usual rule is iOS/macOS) |
| Diary scope | One diary per **trip**. A trip can cover **multiple cities**; every city is its own map pin and all of a diary's pins open the same book |
| Book layout | **Two-page spreads** for every page format, Story (9:16) included |
| Page formats | Instagram 4:5 (1080×1350, default), 1:1 (1080×1080), **3:4 (1080×1440)**, Story 9:16 (1080×1920) |
| Monetisation | Deferred until after launch. Build nothing paywall-related for now |
| Video | Out of scope for MVP (v2) |
| Target user | Ages 18–30, travel 2–6×/year, save journaling posts on Pinterest/Instagram but don't keep a paper journal. Not yet confirmed by the owner |

## Chosen stack

| Area | Choice | Why |
|---|---|---|
| Language / UI | Kotlin + Jetpack Compose (Material 3, custom themed) | Shared-element transitions, `graphicsLayer` 3D rotations and springs cover the motion spec |
| Navigation | One `SharedTransitionLayout` with screen state driven by `AnimatedContent`, plus `PredictiveBackHandler` | Full control over the book-open, page-zoom and shelf⇄map morphs |
| Persistence | Room (Diary, DiaryPlace, Page, Element). Element payloads stored as JSON via kotlinx.serialization. Images in app-private storage | Offline-first, no account |
| Map | MapLibre Android SDK + OpenFreeMap tiles, custom paper-look style | No API key or billing. Built-in clustering |
| Location search | Android `Geocoder` for the MVP | No API key. Swap for Photon or Places later |
| Background removal | ML Kit Subject Segmentation (`play-services-mlkit-subject-segmentation`) | On-device and free |
| Receipt scanning | ML Kit Document Scanner (`play-services-mlkit-document-scanner`) | Edge detection and crop with no camera permission |
| Images | Coil 3 | |
| Photo access | Android Photo Picker (`PickVisualMedia`) | No storage permission |
| Fonts | Bundled OFL `.ttf` files from github.com/google/fonts: Caveat, Dancing Script, Homemade Apple, Sacramento, Great Vibes, La Belle Aurore, Nothing You Could Do, Reenie Beanie, Shadows Into Light, Kalam (+Devanagari), Special Elite, Playfair Display Italic | Must work offline |
| Export | Render the page composable off-screen at exactly 1080 px width via `rememberGraphicsLayer()` → `toImageBitmap()`, then share through `FileProvider` | Editor and export use the **same** composable, so output matches what you see |
| Share | `ACTION_SEND`. "Instagram" button targets `com.instagram.android`; the generic share sheet is the fallback | The Stories-specific intent needs a Facebook App ID. Add it later |

### Rendering rule (important)
All element geometry is stored in **page units on a 1080-wide reference page** (x, y, font size, tape width…). The page composable computes `scale = pageWidthPx / 1080f` and draws everything through it. This makes the editor, book thumbnails and export pixel-consistent at any size.

### SDK levels
- `minSdk 26`, `compileSdk`/`targetSdk` = latest stable installed in Android Studio
- The page-curl AGSL shader needs API 33+. Fall back to a `graphicsLayer` rotationY flip below that

### Version notes (checked on Maven Central, Oct 2026)
- MapLibre `org.maplibre.gl:android-sdk` latest stable is **13.6.1**, built with Kotlin 2.2.10. Use Kotlin **≥ 2.2** for the project
- Kotlin 2.4.x, Coil 3.6.x, kotlinx-serialization 1.11.0 and coroutines 1.11.0 are available
- Use whatever AGP, Compose BOM and AndroidX versions Android Studio's new-project template suggests. Those live on Google Maven, which the planning environment couldn't reach

## MVP build order (suggested)

1. **Project skeleton**: Compose project in `app/`, theme (paper palette, fonts), Room schema, repository layer
2. **Home shelf**: book covers grid, empty state, create-diary sheet (Geocoder search, multiple cities, cover preset, default format)
3. **Book view**: cover-open animation, two-page spreads, page flip, add page, overview grid with reorder, predictive back
4. **Page zoom**: shared-element zoom from the spread to full screen
5. **Editor core**: canvas, select / drag / pinch-rotate, snapping with haptics, undo/redo, autosave
6. **Elements**: text with cursive fonts and note-card styles → photos with frames → cut-outs (ML Kit and refine brush) → receipts (Document Scanner) → backgrounds (procedural paper, grid, lined, photo) → washi tape
7. **Export & share**: off-screen render, save to gallery, share sheet, Instagram target
8. **Map view**: MapLibre with polaroid pins, clustering, peek card, shelf⇄map morph
9. **Polish**: element drop-in springs, background-removal "peel" animation, reduced-motion fallbacks, performance pass

After each step: build, run on a device or emulator, and check the animations hold 60 fps.

## Environment note
The cloud planning session couldn't build Android code because its network policy blocked `dl.google.com` (Google Maven / Android SDK). Development should happen locally in Android Studio, or in a cloud environment that allows `dl.google.com` and `maven.google.com`.

## Progress log

### Steps 1–2 (2 Oct 2026)
Built and checked on an API 36 emulator: empty shelf, create sheet (search, dates, cover, format), duplicate-location notice, the drop onto the shelf, and the long-press menu (rename, change cover, duplicate, delete with undo).

Decisions made while building:
- **Versions** match a known-good local setup instead of the newest ones: AGP 8.13.1, Kotlin 2.2.10, Gradle 9.0.0, Compose BOM 2025.11.01, Room 2.8.2, Coil 3.3.0. `compileSdk`/`targetSdk` 36.
- **Package** is `com.wanderpage.app` (follows the working title; change it before the first Play upload if the name changes).
- **No Hilt yet.** `AppContainer` in `WanderpageApp.kt` holds the two singletons. Add Hilt when the graph grows.
- **Element x/y are page units**, per the rendering rule above (the PRD's data-model sketch says 0..1; the rule wins).
- **Delete with undo** hides the diary and only deletes when the snackbar goes away, so undo never has to rebuild rows.
- **Room schema export is off** until the first release; turn it on before writing the first migration.

### Steps 3–9 (2–4 Oct 2026)
Built and checked on API 34 and API 36 emulators, in the debug build and the minified release build.

How it is put together:
- **One renderer.** `PageSurface` replaces the density inside a page so that `1.dp` and `1.sp` are one page unit. The book, the zoomed page, the editor and the export all draw through it, so they match.
- **Export** lays a second, unseen `PageSurface` out at exactly 1080 px wide and records it into a `GraphicsLayer`. It waits for every image on the page to load first.
- **Editor gestures** are handled in one place on the page and hit-tested by hand, so a two-finger gesture works on the selected element from anywhere.
- **Autosave** replaces the page's elements in one transaction after every change. Undo keeps 60 steps.
- **Book** is two `Animatable`s: `open` (cover closed to open, with the cover growing from its place on the shelf) and `position` (which spread, with fractions as a page in mid-turn).
- **Papers and tapes are drawn, not stored**: 20 papers and 17 tapes as code, sharp at any export size.
- **MapLibre 11.8.0** (the version in the notes above, 13.6.1, was not tried). The paper map style is `app/src/main/assets/map_style.json`.
- **Release build** is minified with R8, limited to `arm64-v8a` and `armeabi-v7a`, and signed with the debug key.

Checked by hand: create diary with several cities, cover open and close, page turn, add / duplicate / delete / reorder pages, zoomed page, text with fonts and note styles, photo with frames, tape, papers, photo background with blur, receipt scan through the ML Kit scanner, refine brush, undo, export to the gallery at 1080 × 1350, map pins, clusters and peek cards, settings.

## Known gaps

Simplified against the PRD:
- **Page turn** is a 3D leaf turning on the gutter, on every Android version. The AGSL page-curl shader for Android 13+ is not built.
- **Shelf ⇄ map** cross-fades. Covers do not fly to their pins.
- **Deleting an element** removes it at once; there is no crumple animation. A new pin does not bounce onto the map.
- **Instagram** is one button that hands the image to the Instagram app, which then offers Story, Feed or Reel. The Stories-only intent needs a Facebook App ID.
- **Colour picker** is a hue slider next to the ink presets, not a full picker.
- **No Hilt** and no tests yet.
- **Files are not cleaned up** when an element or page that used them is deleted.

Not verified:
- **Cut-outs with the real model.** ML Kit Subject Segmentation crashes with an illegal-instruction fault (SIGILL) inside Google's native library on the arm64 emulator, so it could not run there. The subject picker, the peel, the sticker outline and the refine brush were checked with a stand-in mask. Test on a real phone first.
- **Camera capture**, **use current location**, **photo covers**, and **sharing to Instagram** (not installed on the emulator).
- **Frame rate.** Nothing was measured against the 60 fps target; the emulator is not a fair test.
- **TalkBack.** Controls have labels and 48 dp targets, but no screen-reader pass was done.
