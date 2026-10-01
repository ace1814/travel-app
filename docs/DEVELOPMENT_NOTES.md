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
