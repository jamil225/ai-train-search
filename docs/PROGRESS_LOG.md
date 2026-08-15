# Train Search — Project Progress Log
**Project**: Train Search Android App  
**Repository**: `/Users/dev/codex/train-search`

---

## 📅 Timeline of Work Completed

### **15 August 2026**

#### 1. Discovery & Workspace Setup
- **Documentation Migration**: Organized project documentation under `docs/` (`deep-research-report.md`, `implementation_plan.md`, `TRAIN_SEARCH_WORKFLOW.md`).
- **MCP Reference Analysis**: Analyzed vendored ConfirmTkt MCP TypeScript client in `vendor/confirmtkt-mcp` for direct Kotlin porting.

#### 2. Core Data & Domain Engine Implementation
- **Domain Models (`Models.kt`)**: Created Kotlin data classes for `Train`, `ClassAvailability`, `ResultRow`, `TripQuery`, `StationGroup`, `Station`, and `StatusKind`.
- **Parsing Engine (`Parsing.kt`)**: Implemented status classification (`AVL`, `RAC`, `WL`, `OTHER`), seat number extractions, and date display formatting (`formatDateDisplay` → e.g. `15 Aug 2026`, `4 Sep 2026`).
- **HTTP Client (`ConfirmTkt.kt`)**: Ported direct OkHttp client querying unauthenticated endpoint `https://cttrainsapi.confirmtkt.com/api/v1/trains/search` with public client headers (`clientid: ct-web`, `apikey: ct-web!2$`).
- **Station Resolver (`Stations.kt`)**: Configured priority station groups (`Rajasthan` → `AII`, `KSG`, `JP`, `MTD`, `JU`; `Pune` → `PUNE`, `KK`; `Mumbai` → `BDTS`, `MMCT`, `DR`, `LTT`, `CSMT`, `PNVL`) with live autocomplete fallback.
- **Key Store (`KeyStore.kt`)**: Implemented hardware-backed Android Keystore AES-GCM encryption for resting OpenAI API key in SharedPreferences (avoiding deprecated `EncryptedSharedPreferences`).

#### 3. Agent & Search Logic
- **Availability Ranker (`Ranking.kt`)**: Built availability-first sorting (`AVL` → `RAC` → `WL` → `OTHER`), seat count ordering, daytime preference tiebreaker, and deduplication by `(trainNumber, fromStnCode, date, travelClass)`.
- **LLM Parser (`Llm.kt`)**: Implemented deterministic `gpt-4o-mini` JSON mode for parsing natural language trip sentences into structured `TripQuery` (`MAX_DATES` set to 31 to support month-long date ranges).
- **Search Orchestrator (`Search.kt`)**: Implemented `channelFlow` multi-coroutine search fan-out over station pairs with live progress reporting.

#### 4. UI Layer & Jetpack Compose
- **Theme (`Theme.kt`)**: Configured Material3 theme tokens.
- **Key Screen (`KeyScreen.kt`)**: Created secure initial API key entry screen with input masking.
- **Board Screen (`BoardScreen.kt` & `BoardViewModel.kt`)**: Created departure board screen with live progress indicator, result list, and search input box.

#### 5. Build System & Toolchain Setup
- **Android Studio & OpenJDK 21**: Configured build environment using OpenJDK 21 LTS (`/opt/homebrew/opt/openjdk@21`) and Android SDK 37/36.
- **Gradle & AGP Upgrade**: Generated Gradle 9.7.0 wrapper and configured Android Gradle Plugin 9.2.1 with Kotlin 2.1.20 and Compose BOM 2026.08.00.
- **Unit Testing**: Executed `./gradlew :app:testDebugUnitTest` — **41 / 41 tests passed** across all test suites.
- **Debug APK Assembly**: Successfully compiled `TrainSearch/app/build/outputs/apk/debug/app-debug.apk` (18 MB).

#### 6. Device Deployment & Physical Mobile Verification
- **USB & Wireless Debugging**: Enabled debugging on physical Android 16 mobile (`I2404 - 16`).
- **Installation & Launch**: Installed and launched `com.trainsearch` directly on the physical mobile screen via `adb`.
- **UI Inset Fixes**: Added `statusBarsPadding()` and `navigationBarsPadding()` to clear top status bar clock/icons and bottom gesture bar.

#### 7. Live Results Verification & UI Redesign Approvals
- **Live Search Verification**: Ran live query for *Jodhpur (`JU`) → Pune (`PUNE`/`HDP`)* from 15 Aug to 4 Sep 2026. Exported full results table to `docs/jodhpur_to_pune_results.md`.
- **Rationale Removal**: Removed bottom AI rationale card and `llm.explain()` call to eliminate latency and unclutter the interface.
- **Approved Senior-Accessible Light Theme**:
  - Background: Off-white `#F8F9FA`.
  - Header: Dark Navy `#1E293B`.
  - Row Columns: `DATE` (`4 Sep 2026`) | `TRAIN & ROUTE` (`20495 JU HDP SF EXP`) | `DEP → ARR` (`22:00 → 16:40`) | `CLS` (`3E`) | `STATUS` (`AVL 37`) | `PRICE` (`₹1,365`).
  - Duration column omitted.
- **Mockup Artifact**: Generated light UI mockup image `train_search_light_ui_mockup_1786811479900.jpg`.

---

## 📁 Key Document Index
- **Progress Log**: `docs/PROGRESS_LOG.md` *(This file)*
- **Agent Handoff**: [`docs/PROGRESS_AND_HANDOFF.md`](file:///Users/dev/codex/train-search/docs/PROGRESS_AND_HANDOFF.md)
- **Verified Search Output**: [`docs/jodhpur_to_pune_results.md`](file:///Users/dev/codex/train-search/docs/jodhpur_to_pune_results.md)
- **Installation Guide**: [`docs/INSTALL.md`](file:///Users/dev/codex/train-search/docs/INSTALL.md)
