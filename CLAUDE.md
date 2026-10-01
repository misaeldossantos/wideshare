# WideShare

## Code rules

- **No file may have more than 150 lines** (applies to `src/main` and `src/test`). If it goes over, split it into files with a single responsibility each, in the same package, instead of leaving everything in one file.
- One file per component or main class; small helper functions can stay next to whoever uses them.
- When splitting, keep only the imports that are used and prefer `internal` over `private` for what other files in the module need.
- Run `./gradlew test` after touching `core` or `platform`.

## UI (Compose Desktop)

- Colors only via `Palette` (`ui/Palette.kt`), which switches between the dark and light themes. Do not use loose `Color(...)` in components.
- Cards and buttons have no border: contrast comes from the background. No gradients and no icons inside rounded squares.
- A single accent color (blue); red only for destructive actions (`PrimaryButton(danger = true)`).
