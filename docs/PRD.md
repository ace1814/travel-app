# Travel Diary App — Product Requirements Document

| | |
|---|---|
| **Status** | Draft v0.2 |
| **Platform** | Android (phone) |
| **Last updated** | 2026-10-01 |
| **Working title** | *Wanderpage* (placeholder) |

---

## 1. Summary

A travel diary app for Android that lets people build scrapbook-style journal pages for each place they visit: cursive handwritten-style notes, photo cut-outs with the background removed, scanned receipts and tickets, washi tape, and textured paper backgrounds. Each page is sized for an Instagram post or story, so a page can be shared the moment it's done or months later.

The app should feel like a physical journal that came to life. Diaries sit on a shelf or as pins on a map, open like a real book, and each page zooms up to fill the screen when you tap it. Motion is a core feature, not an extra.

### Reference aesthetic
The look comes from the paper-journaling trend on Pinterest and Instagram (see [`docs/references/`](references/)):
- Torn-paper edges, grid and lined note paper, kraft and cream textures
- Washi tape strips, binder clips, paper clips, stamps
- Real receipts, boarding passes, and entry tickets glued onto the page
- Photos cut out from their background (a person, a cup of coffee, a building) and placed like stickers
- Polaroid and film-strip frames, dotted route lines between places
- Handwriting in several scripts (English, Hindi, Russian, Japanese)

---

## 2. Problem & target user

### Ideal customer profile (ICP)
**People aged 18–30 who travel 2–6 times a year and save travel-journal and scrapbook posts on Pinterest or Instagram, but don't keep a physical journal** (no time, no supplies, don't want to carry one, or can't share it easily).

### Problem
- Physical travel journals look great, but they take hours, need printed photos, scissors, glue and stickers, and are hard to share. Most people who save these posts never make one.
- Camera-roll trips get lost among thousands of photos. Generic collage apps produce flat grids that don't look handmade.
- Receipts, tickets and boarding passes, the small things that make a trip personal, get thrown away.

### Solution
A phone-native journal that makes it quick to produce the handmade look:
- one-tap background removal turns any photo into a sticker
- the camera scans receipts and tickets into paper cut-outs
- curated cursive fonts and paper textures make every page look crafted
- pages are already sized for Instagram, so sharing is one tap

---

## 3. Goals & non-goals

### Goals (v1)
1. Create a diary for a location in under 20 seconds.
2. Make a page that looks handmade, with at least one cut-out photo, one text block and one decoration, in under 3 minutes.
3. Every page exports at Instagram resolution with no cropping surprises.
4. Animations run at 60 fps or better on a mid-range device (e.g. Pixel 6a, Galaxy A54).
5. Works fully offline. Everything is stored on the device.

### Non-goals (v1)
- Video on pages (planned for v2, see §11)
- Cloud sync, accounts, or multiple devices
- Collaborative or shared diaries
- A social feed inside the app (we share out to Instagram and others)
- Tablet or foldable-optimised layouts (they should work, but won't be tuned)
- iOS

---

## 4. Core concepts

| Concept | Definition |
|---|---|
| **Diary** | A book for **one trip**, anchored to a primary location but able to cover **multiple cities** (e.g. "Rajasthan" covering Udaipur and Jaipur). It has a cover, a title, one or more places with coordinates, a date range, a cover style, and an ordered list of pages. |
| **Page** | One canvas inside a diary, in one of the supported **formats** (§7). It has a background and a stack of elements. |
| **Element** | Anything placed on a page: text, cut-out image, photo, receipt, sticker or decoration, tape. Every element has a position, scale, rotation, z-order and opacity. |
| **Format** | The page's aspect ratio and export size: Instagram Post or Instagram Story. |

**One diary per trip, multi-city allowed.** A diary has a primary place plus any number of extra places ("Add another city"). Each place gets its own pin on the map, and all pins of a diary open the same book. When a user starts a new diary whose primary place matches an existing diary, ask whether to *open the existing diary* or *start a new trip* (e.g. "Tokyo 2025" and "Tokyo 2026").

---

## 5. Information architecture

```
Home
 ├─ Shelf view (default) ⇄ Map view        ← toggle in top bar
 │    └─ tap diary / pin
 │         └─ Diary (book view, page spreads)
 │              └─ tap page
 │                   └─ Page view (zoomed, read mode)
 │                        ├─ Edit → Page editor
 │                        └─ Share → Export sheet
 ├─ + New diary → Create diary flow
 └─ Settings (export defaults, fonts, storage, about)
```

### Primary flows
1. **Create & decorate:** New diary → pick location → add page → add elements → done.
2. **Relive & share:** Home (shelf or map) → open diary → flip pages → zoom into a page → share.

---

## 6. Screens & requirements

Priority: **P0** is MVP, **P1** follows soon after launch, **P2** is later.

### 6.1 Home — Shelf view
| ID | Requirement | Pri |
|---|---|---|
| H-1 | Diaries are shown as **3D-ish book covers** on a shelf or in a staggered grid, newest first. Each cover shows the title, location, dates and cover art (a user photo or preset). | P0 |
| H-2 | Empty state: an illustrated empty shelf with a prominent "Start your first diary" button. | P0 |
| H-3 | Long-press a cover for a menu: rename, change cover, duplicate, delete (with undo snackbar). | P0 |
| H-4 | Floating **+ New diary** button, which morphs into the create sheet (container transform). | P0 |
| H-5 | Sort by date created, trip date or name. Search by place name. | P1 |
| H-6 | Covers tilt slightly with device tilt (parallax), with a setting to turn this off. | P2 |

### 6.2 Home — Map view
| ID | Requirement | Pri |
|---|---|---|
| M-1 | Segmented toggle **Diaries / Map** in the top bar. Switching cross-fades and shared-element-morphs book covers into their map pins. | P0 |
| M-2 | World map with a **custom paper / vintage style** (muted land, cream water, hand-drawn-feel labels) matching the app's look. | P0 |
| M-3 | Each diary is a pin shaped like a **mini polaroid of its cover photo**. Nearby pins cluster into a stacked-polaroid cluster with a count. | P0 |
| M-4 | Tap a pin to show a peek card (title, dates, page count, first-page thumbnail). Tap the card to open the diary. | P0 |
| M-5 | On first open, the camera fits all pins. Afterwards it remembers the last viewport. | P0 |
| M-6 | Optional **dotted travel route** connecting diaries in chronological order (like the Udaipur reference). | P1 |
| M-7 | "Visited" stats overlay: countries, cities, pages made. | P2 |

### 6.3 Create diary flow
| ID | Requirement | Pri |
|---|---|---|
| C-1 | Bottom sheet with a location search box (autocomplete for cities, regions, countries and landmarks). | P0 |
| C-2 | "Use current location" shortcut (coarse location permission, asked only when tapped). | P0 |
| C-3 | Title is pre-filled from the location (e.g. "Udaipur"). Optional trip dates. | P0 |
| C-3b | **Add more cities** to the same diary, at creation or later from the book's menu. Each city becomes a map pin for this diary. | P0 |
| C-4 | Pick a cover style: 6–8 presets (leather, kraft, linen, pastel, film) plus "use a photo". | P0 |
| C-5 | Pick the default page format (Post or Story). See §7. | P0 |
| C-6 | Duplicate-location check (§4). | P0 |
| C-7 | On create, the new book animates onto the shelf (or drops onto the map as a pin) and opens to its first blank page. | P0 |
| C-8 | Optional starter template for page 1 (title page with the location name in large cursive and a "Places I visited" checklist, like the Sikkim reference). | P1 |

### 6.4 Diary (book view)
| ID | Requirement | Pri |
|---|---|---|
| D-1 | Opening animation: the cover **swings open on its spine** (3D rotation around the left edge) while the book scales up from its shelf or pin position to fill the screen. | P0 |
| D-2 | Pages are always shown as **two-page spreads** (left and right page), for every format including Story. Narrow Story pages make a tall, slim book. | P0 |
| D-3 | Swipe horizontally to **flip pages** with a page-curl animation that follows the finger and can be released partway and cancelled. | P0 |
| D-4 | Paper texture, gutter shadow and a slight page stack thickness on the edges sell the "real book" feel. | P0 |
| D-5 | A "+" page at the end adds a new page in the diary's format. | P0 |
| D-6 | Overview mode: pinch out to see all pages as a grid of thumbnails. Drag to reorder, long-press to delete or duplicate. | P0 |
| D-7 | Back gesture closes the book (reverse of D-1) and returns to shelf or map, supporting Android **predictive back** so the user sees the close preview while dragging. | P0 |
| D-8 | Edit the diary's title, cover and dates from the book's overflow menu. | P1 |

### 6.5 Page view (zoomed)
| ID | Requirement | Pri |
|---|---|---|
| P-1 | Tapping a page **zooms it** out of the book to fill the screen (shared-element transform). The surrounding book dims and blurs. | P0 |
| P-2 | Read mode: pinch to zoom further into details, double-tap to reset. Swipe left and right moves to the adjacent page. | P0 |
| P-3 | Actions: **Edit**, **Share**, more (duplicate, change format, delete). | P0 |
| P-4 | Elements gently settle in when the page first appears (a small stagger of scale and rotation, about 30 ms apart) so the page feels freshly glued. Respects reduced motion. | P1 |

### 6.6 Page editor
The heart of the app. A canvas fixed to the page's aspect ratio, with a bottom toolbar for adding elements.

**Canvas interactions (all P0)**
- Tap an element to select it (handles plus a subtle lift shadow). Tap empty space to deselect.
- One finger drags. Two fingers pinch to scale and twist to rotate, at the same time.
- Snap guides to page centre and edges, plus soft rotation snapping at 0°, 90° and ±15°, with haptic ticks.
- Selected-element toolbar: bring forward / send back, duplicate, opacity, flip, lock, delete.
- Undo and redo (at least 50 steps). Autosave on every change.
- Elements may overhang the page edge; export clips to the page bounds.

**Add menu (bottom toolbar)**

#### 6.6.1 Text
| ID | Requirement | Pri |
|---|---|---|
| T-1 | Add a text block and type directly on the canvas. | P0 |
| T-2 | **Curated font set of 10–14 handwriting and cursive fonts**, previewed live in a horizontal carousel. Suggested (all open-licence via Google Fonts): Caveat, Dancing Script, Homemade Apple, Sacramento, Great Vibes, La Belle Aurore, Nothing You Could Do, Reenie Beanie, Shadows Into Light, Kalam (Latin and Devanagari), plus 1–2 typewriter or serif accents (Special Elite, Playfair Display Italic). Fonts are bundled so they work offline. | P0 |
| T-3 | Colour (ink presets: black, sepia, navy, forest, red, plus a picker), size, alignment, line spacing, letter spacing. | P0 |
| T-4 | Text box styles: none, highlighter strip, a torn-paper note, grid note card, lined note card (as in the Sikkim and Tokyo references). | P0 |
| T-5 | Curved text along an arc. | P1 |
| T-6 | Multi-script handwriting fonts (Cyrillic, Japanese, Devanagari) with fallback that picks a matching handwriting font, not a system sans-serif. | P1 |
| T-7 | "Handwriting jitter": tiny per-letter baseline and rotation variation so text looks less digital. | P2 |

#### 6.6.2 Cut-out images (background removed)
| ID | Requirement | Pri |
|---|---|---|
| I-1 | Pick from the gallery (Android Photo Picker, so no storage permission is needed) or take a photo with the camera. | P0 |
| I-2 | **Automatic background removal on the device**. Show a satisfying "peel" animation as the subject lifts off the photo. | P0 |
| I-3 | If several subjects are found, let the user tap which to keep, or keep all. | P0 |
| I-4 | Manual refine: an erase / restore brush with adjustable size and a magnifier loupe under the finger. | P0 |
| I-5 | Sticker outline: none, thin white border (die-cut sticker look), or thick white border, plus a soft drop shadow toggle. | P0 |
| I-6 | Option to keep the full photo instead (no removal) with frames: plain, polaroid with caption, film strip, torn edges, stamp edges. | P0 |
| I-7 | Recent cut-outs tray so a subject can be reused on other pages. | P1 |
| I-8 | Simple filters on photos: warm, faded, B&W, blue-tone print (like the cyanotype Udaipur palace in the reference). | P1 |

#### 6.6.3 Receipts & tickets
| ID | Requirement | Pri |
|---|---|---|
| R-1 | "Scan receipt" opens the camera with **automatic document edge detection, perspective correction and cropping**. | P0 |
| R-2 | The scanned receipt is placed as a paper element with a realistic finish: off-white paper tone, slight curl shadow, and a zig-zag or torn bottom edge. | P0 |
| R-3 | Also works from an existing photo of a receipt or ticket (gallery). | P0 |
| R-4 | Presets for ticket types: receipt (zig-zag), boarding pass, entry ticket (perforated stub), train ticket. | P1 |
| R-5 | Optional OCR to pull out the merchant, date and total as a small handwritten-style caption ("₹400 · City Palace · 9 Oct"). | P2 |

#### 6.6.4 Backgrounds
| ID | Requirement | Pri |
|---|---|---|
| B-1 | Background library: paper textures (cream, kraft, recycled, watercolour), grid, dot grid, lined, and solid colours. At least 20 bundled, high resolution. | P0 |
| B-2 | Photo background: any photo, with adjustable position, zoom, blur and dim. This makes the "journal floating over a scenic photo" look (like the Tokyo reference) possible. | P0 |
| B-3 | Background tint and texture strength sliders. | P1 |
| B-4 | Two-layer background: a scenic photo plus a centred paper "spread" on top. | P1 |

#### 6.6.5 Decorations (supporting the look)
| ID | Requirement | Pri |
|---|---|---|
| S-1 | **Washi tape**: 15+ patterns. Drag to draw a strip of any length. Ends are torn and slightly translucent. | P0 |
| S-2 | Clips and fasteners: binder clips, paper clips, push pins, stamps. | P1 |
| S-3 | Doodle sticker packs: hearts, stars, arrows, mountains, plane, coffee, flowers, dotted route line, map pins. | P1 |
| S-4 | Freehand pen and marker drawing layer. | P1 |
| S-5 | Location-aware sticker suggestions (e.g. landmarks or local script for the diary's place). | P2 |

### 6.7 Share & export
| ID | Requirement | Pri |
|---|---|---|
| E-1 | **Share button** on Page view and in the editor. Opens an export sheet with a preview of the final image. | P0 |
| E-2 | Export renders the page at full resolution (§7) as PNG or high-quality JPEG. | P0 |
| E-3 | Destinations: **Instagram Story** (direct), **Instagram Feed**, Android share sheet (WhatsApp, etc.), and **Save to gallery** (in a "Travel Diary" album). | P0 |
| E-4 | Optional small watermark (off by default; toggle in settings). | P0 |
| E-5 | Export multiple pages at once as an Instagram carousel (up to 20 images). | P1 |
| E-6 | Export a whole diary as a PDF (for printing). | P2 |
| E-7 | Export as a short animated video (elements settle in one by one), ready for Reels and Stories. | P2 |

### 6.8 Settings
- Default page format, export format and quality, watermark toggle (P0)
- Reduce motion (follows the system setting by default) (P0)
- Storage used, clear cached cut-outs (P1)
- Backup and restore to a local file (P1)

---

## 7. Page formats & export sizes

| Format | Aspect | Export size (px) | Use |
|---|---|---|---|
| **Instagram Post (portrait)** | 4:5 | 1080 × 1350 | Feed post (default) |
| **Instagram Post (square)** | 1:1 | 1080 × 1080 | Feed post |
| **Instagram Post (grid)** | 3:4 | 1080 × 1440 | Feed post matching Instagram's profile grid |
| **Instagram Story** | 9:16 | 1080 × 1920 | Stories, Reels cover, WhatsApp status |

- Pages are stored as **resolution-independent** layouts (element coordinates normalised to page width), so export can also produce 2× sizes (e.g. 2160 × 2700) for printing later.
- The **Story format shows safe-zone guides** in the editor (top roughly 250 px and bottom roughly 340 px at 1080 × 1920, where Instagram's UI overlays).
- Default format is set per diary. Per-page override is allowed; in book view, odd-sized pages are centred on a standard book page with a paper mat.

---

## 8. Motion & interaction design

Motion is a top priority. All animations must be **interruptible** (a new gesture takes over mid-animation), driven by **spring physics** wherever possible, and must respect **reduced motion** (fall back to fades and cross-fades).

| Moment | Motion | Target feel / spec |
|---|---|---|
| Shelf ⇄ Map toggle | Covers shrink and fly to their pin positions while the map fades in. Reverse on toggle back. | ~450 ms, emphasised easing |
| New diary button → create sheet | Container transform from the button into the sheet | ~350 ms |
| New diary created | The book drops onto the shelf with a small bounce, or the pin drops onto the map with a little bounce and dust puff | spring, medium bounce |
| Open diary | The book scales from its shelf or pin position to full screen while the cover rotates 0° → −180° around the spine, with a shadow under the cover moving with it | ~600 ms, spring, low bounce |
| Flip page | Finger-tracked page curl with a highlight and shadow on the fold. Flings complete the flip; slow releases snap back. | Follows finger 1:1; 120 Hz on supported devices |
| Tap page → Page view | Shared-element zoom from the book position to full screen. The book behind blurs and dims. | ~400 ms |
| Element added | Drops in with a slight overshoot and random ±3° tilt, as if placed by hand. Tape "sticks" with a small squash. | spring, medium bounce |
| Background removal | The subject lifts off the photo with a growing shadow while the background fades and falls away | ~800 ms, one-time "wow" moment |
| Select element | Lifts slightly (scale 1.03 plus a deeper shadow), with a light haptic | 150 ms |
| Delete element | Crumples or shrinks into the trash icon | ~300 ms |
| Back / close | Predictive back previews the reverse transition while dragging | System predictive back |
| Haptics | Light tick on snap, select and page-flip completion; medium on delete | Android haptic constants |

**Performance budget:** no dropped frames during transitions on the reference mid-range device; editor canvas stays at 60 fps with 40 elements on a page.

---

## 9. Technical approach (recommended)

| Area | Recommendation | Notes |
|---|---|---|
| Language / UI | **Kotlin + Jetpack Compose** (Material 3 as a base, heavily custom-themed) | Compose's shared-element transitions, `graphicsLayer` 3D rotation and spring animations cover most of §8. |
| Min / target SDK | minSdk 26 (Android 8), target latest | Page-curl shader needs Android 13+ (AGSL). Fall back to a 3D-rotation flip on older versions. |
| Architecture | MVVM + unidirectional data flow, Hilt for DI, Kotlin coroutines and Flow | |
| Storage | **Room** (diaries, pages, elements as JSON layout) + app-private files for images | Offline-first. All originals are kept so edits never lose quality. |
| Background removal | **ML Kit Subject Segmentation** (on-device, delivered through Google Play services) | Free, no network. Pair with a manual erase / restore brush for edge cases. |
| Receipt scanning | **ML Kit Document Scanner** | Gives edge detection, cropping and cleanup with a Google-provided UI, without the camera permission. |
| OCR (P2) | ML Kit Text Recognition v2 | On-device; supports Latin, Devanagari, Japanese, Chinese, Korean |
| Map | **MapLibre Android SDK** with free **OpenFreeMap** vector tiles and a custom paper-style map style | No API key or billing. Full control over the vintage look. Pins are a GeoJSON source + symbol layer using Compose-rendered polaroid bitmaps; MapLibre's built-in source clustering groups nearby pins. Google Maps Compose remains a fallback option. |
| Location search | MVP: Android's built-in `Geocoder` (no key). Later: Photon (OpenStreetMap) or Google Places Autocomplete for richer suggestions | Places is billed per session; Photon is free |
| Images | Coil 3 for loading. Downsampled previews in the editor, originals used for export. | |
| Export | Render the page off-screen at target size (Compose `GraphicsLayer` → bitmap, or a Canvas renderer that shares code with the editor) | Must produce identical results to what the editor shows |
| Sharing | `FileProvider` + `ACTION_SEND`. Instagram Story via the `com.instagram.share.ADD_TO_STORY` intent. | The Story intent needs a registered Facebook App ID. |
| Photo access | Android Photo Picker | No broad storage permission needed |
| Fonts | Bundled `.ttf` files (OFL licence) | Keep within roughly 5 MB total |

### Data model (sketch)

```kotlin
Diary(id, title, startDate?, endDate?, coverStyle, coverImageUri?,
      defaultFormat, createdAt, updatedAt)

DiaryPlace(id, diaryId, name, lat, lng, countryCode, isPrimary, order)

Page(id, diaryId, index, format, background: Background, thumbnailUri, updatedAt)

Element(id, pageId, type, z, x, y, scale, rotation, opacity, locked,
        payload: ElementPayload)   // x, y normalised 0..1 of page width

sealed ElementPayload {
  Text(text, fontId, colour, size, align, boxStyle)
  CutOut(originalUri, maskUri, outline, shadow)
  Photo(uri, crop, frameStyle, filter, caption?)
  Receipt(uri, edgeStyle, ticketType)
  Sticker(assetId, tint?)
  Tape(patternId, length, width)
  Drawing(pathsJson, colour, width)          // P1
}

Background = Paper(textureId, tint) | Solid(colour) | Photo(uri, crop, blur, dim)
```

---

## 10. Non-functional requirements

- **Performance:** cold start under 1.5 s on the reference device. Opening a diary under 300 ms before animation starts. Background removal under 2 s for a 12 MP photo.
- **Offline:** every feature except location search, map tiles and the first ML Kit model download works with no network.
- **Storage:** originals are stored compressed. Show storage used per diary. Warn below 500 MB free.
- **Privacy:** no account and no server. Photos never leave the device except when the user shares them. Location permission is only requested for "use current location".
- **Accessibility:** TalkBack labels on all controls, minimum 48 dp touch targets, reduced motion support, and contrast-checked UI outside the canvas.
- **Reliability:** autosave with crash-safe writes. Never lose a page.
- **Localisation-ready:** all strings in resources. Launch in English; Hindi soon after.

---

## 11. Release plan

### MVP (P0)
Shelf and map home, create diary, book open and page flip, page zoom, editor with text (cursive fonts), cut-outs, photos with frames, receipt scanning, backgrounds, washi tape, export and share (Instagram Post and Story, gallery, share sheet).

### v1.1 (P1)
Starter templates, doodle and clip sticker packs, freehand drawing, carousel export, travel route on the map, photo filters, ticket presets, multi-script fonts, local backup and restore.

### v2 (P2)
- **Video:** short clips and Live Photo-style motion on pages, plus exporting a page as an animated Reel or Story
- Cloud backup and sync, PDF / print export, receipt OCR captions, location-aware stickers, travel stats, iOS

---

## 12. Success metrics

| Metric | Target (first 90 days) |
|---|---|
| New users who create a diary | ≥ 70% |
| Users who finish their first page (3+ elements) | ≥ 50% |
| Pages shared or exported per active user per week | ≥ 1.5 |
| Exports that go to Instagram | tracked; expect ≥ 40% |
| Day-30 retention | ≥ 20% |
| Crash-free sessions | ≥ 99.5% |
| Cut-outs kept without manual refine | ≥ 80% (quality signal for background removal) |

Analytics must be privacy-respecting (event counts only, no content), with an opt-out.

---

## 13. Risks & mitigations

| Risk | Mitigation |
|---|---|
| Background removal quality on hair, glass or busy backgrounds | Manual refine brush, a "keep original" option, and sticker outlines that hide rough edges |
| Page-curl performance on older devices | AGSL shader on Android 13+, simpler 3D flip below that, auto-downgrade if frames drop |
| Instagram Story intent changes or needs an App ID | Fall back to the generic share sheet. Register a Facebook App ID early. |
| Places API cost at scale | Session tokens, debounce, cache results, or switch to OSM-based search |
| Editor output differing from export | One shared rendering path for editor and export; snapshot tests comparing them |
| Storage growth from high-res originals | Compress originals, clean up unused cut-outs, show storage per diary |
| Look feels "template-y" | Invest in assets (textures, tapes, fonts) and the hand-placed tilt and jitter details |

---

## 14. Decisions log

| Question | Decision |
|---|---|
| Location granularity | A diary can cover multiple cities; each city is a pin |
| Book layout for Story pages | Two-page spreads for all formats |
| Instagram 3:4 size | Yes, offered alongside 4:5, 1:1 and 9:16 |
| Monetisation | Deferred; decide after launch |

## 15. Open questions

1. **Templates:** how much should we guide layout (pre-made page templates) versus a blank canvas?
2. **Name and brand:** final app name and Play Store listing.
