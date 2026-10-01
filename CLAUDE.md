# CLAUDE.md

Android travel-diary app (Kotlin + Jetpack Compose). Read before working:
- `docs/PRD.md`: what to build (requirement IDs like H-1, D-3, T-2; P0 = MVP)
- `docs/DEVELOPMENT_NOTES.md`: confirmed decisions, chosen stack, rendering rule, MVP build order
- `docs/references/`: the visual style to match (paper journal, torn edges, washi tape, cut-out photos, receipts)

Conventions:
- The Android Studio project lives in `app/` at the repo root (single-module to start)
- Element geometry is in page units on a 1080-wide reference page. Never store screen pixels
- Motion is a core feature: springs, interruptible transitions, and a reduced-motion fallback for every animation
- Offline-first: no account and no backend. Don't add API keys for the MVP
- Build and run after each feature (`./gradlew assembleDebug`, install on a device or emulator) before calling it done
