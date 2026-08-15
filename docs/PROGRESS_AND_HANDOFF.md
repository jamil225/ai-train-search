# Train Search Project — Progress & Handoff Document
**Date**: 15 August 2026  
**Status**: Environment Configured · 41 Unit Tests Passing · App Running on Mobile (`Android 16`)

---

## 1. Environment & Environment Setup
- **Android Studio**: Installed & active.
- **JDK**: OpenJDK 21 (`/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home`).
- **Android SDK**: `compileSdk = 37`, `minSdk = 26`, `targetSdk = 36` in `app/build.gradle.kts`.
- **Gradle & AGP**: Gradle 9.7.0 wrapper (`gradle/wrapper/gradle-wrapper.properties`), Android Gradle Plugin 9.2.1, Kotlin 2.1.20.
- **Device & Debugging**: Physical Android 16 mobile connected with USB & Wireless Debugging active. App verified running on real device (`I2404 - 16`).

---

## 2. Work Accomplished & File Architecture

All 19 Kotlin files under `TrainSearch/app/` are written and version-controlled:

### `app/src/main/java/com/trainsearch/`
- **`MainActivity.kt`**: Main entry activity linking Keystore auth state to `KeyScreen` or `BoardScreen`.
- **`data/`**:
  - `Models.kt`: Core data structures (`Train`, `ClassAvailability`, `ResultRow`, `TripQuery`, `StatusKind`).
  - `Parsing.kt`: Response parsers from ConfirmTkt API, `formatDateDisplay()` ("15 Aug 2026"), and `classifyStatus()`.
  - `ConfirmTkt.kt`: HTTP client for unauthenticated `https://cttrainsapi.confirmtkt.com/api/v1/trains/search`.
  - `Stations.kt`: Priority station group tables (`Rajasthan`, `Pune`, `Mumbai`) and live autocomplete lookup fallback.
  - `KeyStore.kt`: Keystore-wrapped AES-GCM hardware key encryption for OpenAI API key.
- **`agent/`**:
  - `Ranking.kt`: Availability-only ranking (`AVL` → `RAC` → `WL` → `OTHER`), seat count ordering, daytime preference, and `dedup()`.
  - `Llm.kt`: Parses user natural language into `TripQuery` struct (`MAX_DATES = 31`). Rationale explanation LLM call removed.
  - `Search.kt`: Orchestrates parallel queries over station pairs with live progress reporting via `channelFlow`.
- **`ui/`**:
  - `Theme.kt`: Color scheme and Material3 theme.
  - `KeyScreen.kt`: Initial API key entry screen with `statusBarsPadding()` & `navigationBarsPadding()`.
  - `BoardScreen.kt` & `BoardViewModel.kt`: Departure board screen with input bar and status padding.

### `app/src/test/java/com/trainsearch/`
- **Unit Tests**: 41 unit tests in `ParsingTest`, `ConfirmTktTest`, `StationsTest`, `RankingTest`, `LlmTest`, `SearchTest`. All 41 tests pass cleanly via `./gradlew :app:testDebugUnitTest`.

---

## 3. Key Architectural Decisions Made & Verified

1. **Date Range Expansion (`MAX_DATES = 31`)**:
   - Expanded date range parsing limit from 7 to 31 dates to ensure full date ranges (e.g. 15 Aug to 4 Sep = 21 days) are queried completely without dropping available dates.
2. **Clean Deduplication**:
   - `dedup` in `Ranking.kt` deduplicates by `(trainNumber, fromStnCode, date, travelClass)` to prevent duplicate entries for `PUNE` vs `HDP` terminal stops while preserving different boarding points.
3. **Date Display Formatting (`formatDateDisplay`)**:
   - Dates are formatted as `15 Aug 2026` / `4 Sep 2026` (word format, short month name) instead of numeric `15-08-2026`.
4. **Rationale / Explanation Removal**:
   - The LLM rationale explanation function `llm.explain()` and the UI explanation card at the bottom of the screen were completely removed to eliminate latency, cost, and clutter.
5. **UI Redesign Direction (Light Theme & Senior Accessibility)**:
   - Switching from dark green board look to a clean, light off-white background (`#F8F9FA`) with dark navy header card (`#1E293B`) and high-contrast typography designed for readability by older adults.
   - Redesigning result rows into clean white cards with exact columns:
     `DATE` (`4 Sep 2026`) | `TRAIN & ROUTE` (`20495 JU HDP SF EXP`) | `DEP → ARR` (`22:00 → 16:40`) | `CLS` (`3E`) | `STATUS` (`AVL 37` in green, `RAC 7` in amber) | `PRICE` (`₹1,365`).
   - Omit duration column.

---

## 4. References & Artifacts
- **Verified Results File**: [`docs/jodhpur_to_pune_results.md`](file:///Users/dev/codex/train-search/docs/jodhpur_to_pune_results.md)
- **Approved Light UI Mockup**: `train_search_light_ui_mockup_1786811479900.jpg`
- **Installation Guide**: [`docs/INSTALL.md`](file:///Users/dev/codex/train-search/docs/INSTALL.md)
