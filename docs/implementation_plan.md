# Train Search Agent — Android App Implementation Plan

Build a Kotlin/Jetpack Compose Android app that runs an LLM-based train-search agent on-device, with no custom backend. The phone hosts the agent loop, calls the ConfirmTkt API directly, and uses an OpenAI key provided by the user.

## User Review Required

> [!IMPORTANT]
> **OpenAI SDK vs. manual HTTP calls.** The OpenAI Java SDK (`com.openai:openai-java:4.50.0`) works on Android but pulls in Jackson (adds ~2 MB to APK) and requires ProGuard rules. An alternative is calling the OpenAI Chat Completions API directly with Ktor + kotlinx.serialization (already used for ConfirmTkt calls). The plan below uses the **official SDK** for reliability and easier tool-call parsing. Flag if you'd prefer the lightweight HTTP approach.

> [!IMPORTANT]
> **App ID / package name.** The plan uses `com.trainagent.search`. Change if you have a different preference.

> [!WARNING]
> **ConfirmTkt API stability.** The existing MCP server reverse-engineers ConfirmTkt's public web API with static headers (`clientid: ct-web`, `apikey: ct-web!2$`). This can break without notice. The Kotlin port will mirror the MCP server's exact request format, but there's no contractual guarantee of uptime.

## Open Questions

> [!IMPORTANT]
> **Model selection.** The spec mentions GPT-4o. Should the app default to `gpt-4o-mini` (cheaper, faster) for routine searches and only use `gpt-4o` for complex queries? Or always use one model? The plan defaults to `gpt-4o-mini` with a settings toggle.

> [!IMPORTANT]
> **Offline station lookup.** The spec's `find_station_code` tool currently calls ConfirmTkt's auto-suggestion API. Since the Rajasthan/Pune station groups are fixed and small, should we embed them as a local lookup table and skip the network call? The plan does both: local table first, API fallback for unknown locations.

> [!NOTE]
> **Google Sign-In.** The spec mentions optional Google Sign-In "for identity." This adds OAuth complexity with no clear benefit for Phase 1 (single-user, local app). The plan defers it. Flag if you want it included.

---

## Proposed Changes

The app will be created as a new Android project at `/Users/dev/codex/train-search/TrainSearchAgent/`. The project follows a clean architecture with four layers.

### Project Structure

```
TrainSearchAgent/
├── app/
│   ├── src/main/
│   │   ├── java/com/trainagent/search/
│   │   │   ├── TrainSearchApp.kt              # Application class
│   │   │   ├── MainActivity.kt                # Single-activity entry point
│   │   │   ├── data/
│   │   │   │   ├── api/
│   │   │   │   │   ├── ConfirmTktClient.kt     # HTTP client (port of MCP's confirmtkt.ts)
│   │   │   │   │   ├── ConfirmTktModels.kt     # API response data classes
│   │   │   │   │   └── StationLookup.kt        # Local + API station resolver
│   │   │   │   └── prefs/
│   │   │   │       └── SecurePrefs.kt          # Encrypted API key storage
│   │   │   ├── agent/
│   │   │   │   ├── AgentLoop.kt                # ReAct loop: LLM ↔ tools
│   │   │   │   ├── ToolRegistry.kt             # Fixed tool definitions
│   │   │   │   ├── ToolExecutor.kt             # Dispatches tool calls to implementations
│   │   │   │   ├── SystemPrompt.kt             # System prompt with station prefs & rules
│   │   │   │   └── RankingEngine.kt            # AGENTS.md ranking logic
│   │   │   ├── ui/
│   │   │   │   ├── theme/
│   │   │   │   │   ├── Theme.kt
│   │   │   │   │   ├── Color.kt
│   │   │   │   │   └── Type.kt
│   │   │   │   ├── navigation/
│   │   │   │   │   └── AppNavigation.kt
│   │   │   │   ├── onboarding/
│   │   │   │   │   └── ApiKeyScreen.kt         # First-run API key entry
│   │   │   │   ├── chat/
│   │   │   │   │   ├── ChatScreen.kt           # Main chat UI
│   │   │   │   │   ├── ChatViewModel.kt        # State management
│   │   │   │   │   ├── MessageBubble.kt        # User/assistant message composables
│   │   │   │   │   └── TrainResultCard.kt      # Structured train result display
│   │   │   │   └── settings/
│   │   │   │       └── SettingsScreen.kt       # Model selection, key management
│   │   │   └── speech/                         # Phase 2
│   │   │       └── SpeechManager.kt
│   │   ├── res/
│   │   │   ├── values/
│   │   │   │   ├── strings.xml
│   │   │   │   └── themes.xml
│   │   │   └── drawable/                       # App icon, mic icon
│   │   └── AndroidManifest.xml
│   ├── build.gradle.kts
│   └── proguard-rules.pro
├── build.gradle.kts                            # Root build file
├── settings.gradle.kts
└── gradle.properties
```

---

### Component 1: Project Scaffolding

Create the Android project with Kotlin DSL, Compose, and all dependencies.

#### [NEW] `settings.gradle.kts`
- Project name: `TrainSearchAgent`
- Plugin management with Google/Maven Central repositories

#### [NEW] Root `build.gradle.kts`
- AGP 8.7.x, Kotlin 2.0.21, KSP plugin

#### [NEW] `app/build.gradle.kts`
Core dependency matrix:

| Dependency | Version | Purpose |
|---|---|---|
| `compileSdk` / `targetSdk` | 36 | 2026 Play Store requirement |
| `minSdk` | 26 | Keystore AES, broad device coverage |
| `com.openai:openai-java` | 4.50.0 | LLM function calling |
| `androidx.compose:compose-bom` | 2026.08.00 | Compose UI framework |
| `androidx.compose.material3:material3` | 1.4.0 | Material Design 3 |
| `io.ktor:ktor-client-okhttp` | 3.0.3 | ConfirmTkt HTTP calls |
| `io.ktor:ktor-serialization-kotlinx-json` | 3.0.3 | JSON parsing |
| `androidx.security:security-crypto` | 1.1.0 | Encrypted key storage |
| `androidx.navigation:navigation-compose` | 2.8.x | Screen navigation |
| `androidx.lifecycle:lifecycle-viewmodel-compose` | 2.8.7 | ViewModel integration |
| `kotlinx-coroutines-android` | 1.10.1 | Async/parallel calls |
| `desugar_jdk_libs` | 2.1.4 | Java 8+ API support |

#### [NEW] `proguard-rules.pro`
- Retain OpenAI SDK model classes and Jackson annotations
- Retain kotlinx.serialization classes

---

### Component 2: ConfirmTkt API Client (port from MCP TypeScript)

Port the existing [confirmtkt.ts](file:///Users/dev/codex/train-search/vendor/confirmtkt-mcp/src/utils/confirmtkt.ts) to Kotlin. This is the most critical component — it replaces the MCP server with direct API calls from the phone.

#### [NEW] `ConfirmTktClient.kt`
- Ktor `HttpClient` with OkHttp engine
- Static headers matching the MCP server exactly:
  ```
  User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) ...
  Accept: application/json
  clientid: ct-web
  apikey: ct-web!2$
  deviceid: ct-mcp-0000-0000-0000-000000000000
  ```
- Two endpoint functions:
  - `searchTrains(src, dst, date)` → `GET /api/v1/trains/search?sourceStationCode={src}&destinationStationCode={dst}&dateOfJourney={DD-MM-YYYY}`
  - `findStation(query)` → `GET /api/v2/trains/stations/auto-suggestion?searchString={query}&...`
- Concurrency limiter: `Semaphore(5)` for parallel station-pair searches
- Timeout: 30s per request, 3 retries with exponential backoff
- Error handling mirrors the MCP's `apiGet()`: parse JSON, check `error` field, throw descriptive exceptions

#### [NEW] `ConfirmTktModels.kt`
Direct port of [types.ts](file:///Users/dev/codex/train-search/vendor/confirmtkt-mcp/src/types.ts):

```kotlin
@Serializable
data class ClassAvailability(
    val travelClass: String,        // "SL", "3A", "2A", etc.
    val status: String,             // "AVL 31", "WL 12", "RAC 5"
    val seats: Int?,                // numeric count when confirmed
    val fare: Int?,                 // INR
    val confirmStatus: String?,     // "Confirm" prediction
    val confirmChance: Int?,        // 0-100
    val quota: String?              // "GN"
)

@Serializable
data class Train(
    val trainNumber: String,
    val trainName: String,
    val fromStnCode: String,
    val fromStnName: String,
    val toStnCode: String,
    val toStnName: String,
    val departureTime: String,
    val arrivalTime: String,
    val duration: String,
    val durationFormatted: String,
    val distance: Int?,
    val runningDays: String?,
    val hasPantry: Boolean,
    val trainType: String?,
    val trainRating: Double?,
    val availability: List<ClassAvailability>
)
```

Availability parsing logic ported from the MCP's `parseAvailability()`:
- Parse `AVAILABLE-(\d+)` regex for seat count
- Sort by class order: `1A, 2A, 3A, 3E, CC, EC, SL, 2S`

#### [NEW] `StationLookup.kt`
- **Local table** (no network needed for known routes):
  ```kotlin
  val RAJASTHAN_STATIONS = listOf("AII", "KSG", "JP", "MTD", "JU")
  val PUNE_STATIONS = listOf("PUNE", "KK")
  val MUMBAI_STATIONS = listOf("BDTS", "MMCT", "DR", "LTT", "CSMT", "PNVL")
  ```
  Maps common names ("Rajasthan", "Pune", "Mumbai") to these groups.
- **API fallback**: for unknown station names, calls `findStation()` via ConfirmTkt auto-suggestion.
- Returns stations in priority order (most convenient first) matching `AGENTS.md`.

---

### Component 3: Secure Storage

#### [NEW] `SecurePrefs.kt`
- Uses `EncryptedSharedPreferences` with `MasterKey` (AES256-GCM)
- Functions: `saveApiKey(key)`, `getApiKey(): String?`, `clearApiKey()`, `hasApiKey(): Boolean`
- Key is never logged, never included in prompts, never sent to ConfirmTkt

---

### Component 4: Agent Loop & Tools

This is the brain of the app — a manual ReAct loop in Kotlin.

#### [NEW] `SystemPrompt.kt`
- Encodes the full system prompt as a constant string
- Includes:
  - Role: "You are a train search assistant for Indian Railways"
  - Safety: "Search and report only. Never book tickets, log in, or make payments."
  - Station preferences from `AGENTS.md` (Rajasthan group, Pune group, priority order)
  - Ranking rules: route convenience > availability status, SL > 3A > 2A, AVL > RAC > WL
  - Output format instructions: structured JSON for results, natural language for explanations
  - Tool usage instructions: when to call each tool, expected arguments

#### [NEW] `ToolRegistry.kt`
- Defines three OpenAI function/tool schemas:

| Tool Name | Parameters | Returns |
|---|---|---|
| `find_station_code` | `name: String` | `{ stations: [{code, name, city}] }` |
| `search_trains` | `origins: String[], destinations: String[], date: String, classes: String[]` | `{ results: [Train] }` |
| `get_seat_availability` | `trainNumber: String, from: String, to: String, date: String` | `{ train: Train }` |

- Each tool is a `ChatCompletionTool` with JSON schema derived from our data classes
- The registry is **immutable** — no tools can be added at runtime

#### [NEW] `ToolExecutor.kt`
- Switch on tool name → dispatch to the correct implementation
- `find_station_code` → `StationLookup.resolve(name)`
- `search_trains` → for each origin×destination pair, call `ConfirmTktClient.searchTrains()` in parallel via `supervisorScope` + `async`, aggregate results, apply `RankingEngine`, return top 10
- `get_seat_availability` → `ConfirmTktClient.searchTrains()` for that specific train (the API returns availability inline)
- **Guardrails**:
  - Max 20 station pairs per search (cap at 5 origins × 4 destinations)
  - 30s timeout per API call
  - Reject any unrecognized tool name
  - Validate argument types before execution

#### [NEW] `AgentLoop.kt`
- Core ReAct loop:
  ```
  1. Build messages: [system, ...history, user_message]
  2. Call OpenAI Chat Completions with tools
  3. If response has tool_calls → execute each, append results, goto 2
  4. If response is text → return as assistant message
  5. Max 7 iterations, then force a text response
  ```
- Uses `OpenAIOkHttpClient` initialized with user's API key
- Model: configurable, default `gpt-4o-mini`
- Emits `Flow<AgentEvent>` for the UI to observe:
  - `AgentEvent.Thinking(message)` — "Searching trains from Ajmer to Pune..."
  - `AgentEvent.ToolCall(name, args)` — tool execution in progress
  - `AgentEvent.Result(trains)` — structured train results
  - `AgentEvent.Reply(text)` — final LLM text response
  - `AgentEvent.Error(message)` — error state

#### [NEW] `RankingEngine.kt`
Implements the ranking rules from [AGENTS.md](file:///Users/dev/codex/train-search/AGENTS.md):

1. **Route convenience score**: Station priority index (AII=0, KSG=1, JP=2, MTD=3, JU=4 for origin; PUNE=0, KK=1, BDTS+=2 for destination). Lower combined index = more convenient.
2. **Within same route**, class preference: SL > 3A > 2A (index 0, 1, 2)
3. **Within same route/class**, availability: AVL > RAC > WL
   - AVL sorts by seat count descending (but a lone AVL 3 is still favorable)
   - RAC sorts by number ascending
   - WL sorts by number ascending
4. **Tiebreaker**: prefer daytime departures (06:00–22:00)
5. Returns top 10 ranked options, or all if fewer exist

---

### Component 5: UI Layer

#### [NEW] `Theme.kt`, `Color.kt`, `Type.kt`
- Material 3 dynamic color theme
- Clean, legible typography (Inter or system default)
- Light/dark mode support via `isSystemInDarkTheme()`

#### [NEW] `AppNavigation.kt`
- Two routes: `Onboarding` (API key) → `Chat` (main screen)
- `Settings` accessible from Chat's top bar
- If API key exists, skip onboarding

#### [NEW] `ApiKeyScreen.kt`
- Single-purpose screen: text field (password visibility toggle), "Save & Continue" button
- Validates key format (starts with `sk-`, reasonable length)
- Stores via `SecurePrefs`, navigates to Chat

#### [NEW] `ChatScreen.kt`
- `LazyColumn` of messages (user right-aligned, assistant left-aligned)
- Auto-scroll to bottom on new messages
- Input bar at bottom: `TextField` + Send `IconButton`
- While agent is processing: show typing indicator with current tool status ("Searching trains from Ajmer to Pune...")
- Keyboard auto-focus on screen entry

#### [NEW] `ChatViewModel.kt`
- Holds `StateFlow<List<ChatMessage>>` for conversation
- Holds `StateFlow<AgentState>` (Idle, Processing, Error)
- On send: launches coroutine, feeds user message to `AgentLoop`, collects `AgentEvent` flow, updates UI state
- Preserves conversation across configuration changes

#### [NEW] `MessageBubble.kt`
- User bubble: right-aligned, primary color background
- Assistant bubble: left-aligned, surface color background
- Supports both plain text and structured train results

#### [NEW] `TrainResultCard.kt`
- Displays a single `Train` result as a Material 3 card:
  - Train number + name (header)
  - Route: `FROM_STN → TO_STN`
  - Timing: departure → arrival, duration
  - Class/availability chips: e.g. `SL: AVL 24` (green), `3A: RAC 5` (amber), `2A: WL 12` (red)
  - Fare
  - Quota badge
- Color-coded availability status (green=AVL, amber=RAC, red=WL)

#### [NEW] `SettingsScreen.kt`
- Change/view API key (masked display)
- Model selector: `gpt-4o-mini` / `gpt-4o`
- Clear conversation history

---

### Component 6: Phase 2 — Voice Input (deferred)

#### [NEW] `SpeechManager.kt`
- Wraps `SpeechRecognizer` with lifecycle awareness
- API 31+: `createOnDeviceSpeechRecognizer()` for true offline
- API 26–30: `createSpeechRecognizer()` with `EXTRA_PREFER_OFFLINE`
- Emits `Flow<SpeechEvent>`: `Listening`, `PartialResult(text)`, `FinalResult(text)`, `Error(code)`

#### [MODIFY] `ChatScreen.kt`
- Add mic `IconButton` next to text input (visible when input is empty)
- While listening: pulsing mic animation, partial transcript in text field
- On final result: populate text field, user can edit before sending

#### [MODIFY] `AndroidManifest.xml`
- Add `<uses-permission android:name="android.permission.RECORD_AUDIO" />`

---

## Implementation Phases & Ordering

### Phase 1A: Foundation (files 1–8)
Build the app shell, API client, and secure storage — no LLM yet, just verifying ConfirmTkt API calls work from Android.

| Step | Component | Files |
|---|---|---|
| 1 | Project scaffolding | `settings.gradle.kts`, root `build.gradle.kts`, `app/build.gradle.kts`, `proguard-rules.pro`, `AndroidManifest.xml` |
| 2 | Data models | `ConfirmTktModels.kt` |
| 3 | API client | `ConfirmTktClient.kt`, `StationLookup.kt` |
| 4 | Secure storage | `SecurePrefs.kt` |

**Verification**: Write a simple `@Composable` test screen that calls `searchTrains("AII", "PUNE", "01-09-2026")` and displays raw JSON. Confirm the ConfirmTkt API responds correctly from the device.

### Phase 1B: Agent Core (files 9–14)
Wire up the LLM agent loop with tools and ranking.

| Step | Component | Files |
|---|---|---|
| 5 | System prompt | `SystemPrompt.kt` |
| 6 | Tool definitions | `ToolRegistry.kt` |
| 7 | Tool execution | `ToolExecutor.kt` |
| 8 | Ranking engine | `RankingEngine.kt` |
| 9 | Agent loop | `AgentLoop.kt` |

**Verification**: Unit test the agent loop with a mocked OpenAI response containing tool calls. Verify tool dispatch, ranking, and iteration limit.

### Phase 1C: UI (files 15–23)
Build the complete chat interface.

| Step | Component | Files |
|---|---|---|
| 10 | Theme | `Theme.kt`, `Color.kt`, `Type.kt` |
| 11 | Navigation | `AppNavigation.kt`, `MainActivity.kt`, `TrainSearchApp.kt` |
| 12 | Onboarding | `ApiKeyScreen.kt` |
| 13 | Chat | `ChatScreen.kt`, `ChatViewModel.kt`, `MessageBubble.kt`, `TrainResultCard.kt` |
| 14 | Settings | `SettingsScreen.kt` |

**Verification**: Full end-to-end test — enter API key, type "Rajasthan to Pune on Sep 1", see ranked train results displayed as cards.

### Phase 2: Voice Input (deferred, 2 files)
| Step | Component | Files |
|---|---|---|
| 15 | Speech | `SpeechManager.kt` |
| 16 | UI integration | Modify `ChatScreen.kt`, `AndroidManifest.xml` |

**Verification**: Press mic, say "Jaipur to Pune tomorrow", verify transcript appears in text field.

---

## Verification Plan

### Automated Tests
```bash
# Unit tests (ranking, station lookup, availability parsing)
./gradlew :app:testDebugUnitTest

# Build verification
./gradlew :app:assembleDebug
```

- **RankingEngineTest**: Given a fixed list of trains with various routes/classes/availability, verify sort order matches AGENTS.md rules
- **ConfirmTktModelsTest**: Parse sample JSON responses (copied from MCP test data) into Kotlin data classes
- **StationLookupTest**: Verify "Rajasthan" → `[AII, KSG, JP, MTD, JU]`, "Pune" → `[PUNE, KK]`
- **AgentLoopTest**: Mock OpenAI client, verify tool call → execution → re-prompt cycle, verify max-iteration safety

### Manual Verification
- Install debug APK on a physical device or emulator (API 26+)
- Enter a real OpenAI API key
- Test query: "I want to travel from Rajasthan to Pune on September 1"
- Verify: results show trains from AII/KSG/JP to PUNE/KK, ranked by route convenience then availability
- Verify: API key is not visible in Logcat or in the chat UI
- Verify: agent loop terminates within 7 iterations
- Test error cases: invalid API key, no internet, ConfirmTkt timeout
