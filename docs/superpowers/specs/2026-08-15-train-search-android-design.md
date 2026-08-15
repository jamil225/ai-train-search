# Train Search Android App — Design

**Status:** approved design, ready for implementation planning
**Date:** 2026-08-15

Replaces the Android sections of `docs/deep-research-report.md` and `docs/implementation_plan.md`. Corrections to those documents are listed in the appendix.

## Goal

A sideloadable Android APK that reproduces the workflow currently performed by an AI agent against the `confirmtkt-trains` MCP server: the user types a trip in plain language, the app searches every viable station pair across the requested dates, ranks the results, and shows them on one screen.

Distribution is by sharing the APK file directly with family. There is no account system. The app is inert until the user pastes an API key that the author gives them out of band.

## Non-goals

Booking, payment, login, passenger data, Google Sign-In, quota selection, voice input, offline caching. Voice and a station-preference settings screen are anticipated later and the design leaves room for them; neither is built now.

## Constraints

- No custom backend. The phone calls ConfirmTkt and OpenAI directly.
- Read-only. The app never books, authenticates to IRCTC, or submits passenger details.
- Every availability result is a live snapshot, never a guarantee.
- MVP scope. Where a simpler option is adequate, it wins.

---

## Architecture

```
sentence
  → parseTrip (LLM)            → {origin, destination, dates[], classes[]}
  → resolve origin/destination → ordered station-code lists
  → fan out searchTrains(src, dst, date) over pairs × dates
  → flatten to ResultRow                (one row per train AND class)
  → dedup
  → rank                                (pure Kotlin, unit-tested)
  → render board (top 10)
  → explain (LLM)              → one sentence under the board
```

Two LLM calls per query, both small. Raw train JSON never reaches the model — `explain` sees only the top five ranked rows.

### Why the LLM is not an agent

The model reads a sentence into a struct and writes one sentence of prose. It does not choose tools, see raw API output, or rank anything. Consequences:

- Identical queries produce identical results.
- Cost is a fraction of a cent per search, which matters on the user's own key.
- No tool-call layer, no function schemas, no agent loop, no OpenAI SDK.
- Adding per-user station preferences later is a config change, not a prompt change.

### Component boundaries

| File | Responsibility | Depends on |
|---|---|---|
| `data/Models.kt` | `Train`, `ClassAvailability`, `ResultRow`, `TripQuery`, `StationGroup` | nothing |
| `data/ConfirmTkt.kt` | the two HTTP GETs and their parsing | Models |
| `data/Stations.kt` | station-group table and resolution | Models, ConfirmTkt |
| `data/KeyStore.kt` | API key at rest | Android Keystore |
| `agent/Llm.kt` | `parseTrip()`, `explain()` | Models |
| `agent/Ranking.kt` | the comparator — pure function, no I/O | Models |
| `agent/Search.kt` | orchestration: fan-out, flatten, dedup, rank, progress | all of the above |
| `ui/BoardScreen.kt` | board, progress, input | Search |
| `ui/KeyScreen.kt` | first-run key entry | KeyStore |
| `ui/Theme.kt` | colors, type | nothing |
| `MainActivity.kt` | key present? Board : KeyScreen | KeyStore |

`Ranking.kt` has no Android, network, or LLM dependency, so the ranking rules are testable in milliseconds without a device or an API key. `Search.kt` is the only file that knows the whole flow; new features change it and nothing else.

---

## Data layer

### ConfirmTkt client

Port of [`vendor/confirmtkt-mcp/src/utils/confirmtkt.ts`](../../../vendor/confirmtkt-mcp/src/utils/confirmtkt.ts). Preserve its behavior exactly — it is the known-working reference.

Host: `https://cttrainsapi.confirmtkt.com`. **Unauthenticated.** The headers below are public constants from ConfirmTkt's own web bundle, not secrets:

```
User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36
Accept:     application/json
clientid:   ct-web
apikey:     ct-web!2$
deviceid:   ct-mcp-0000-0000-0000-000000000000
```

Two endpoints:

- `GET /api/v1/trains/search?sourceStationCode=&destinationStationCode=&dateOfJourney=DD-MM-YYYY`
- `GET /api/v2/trains/stations/auto-suggestion?searchString=&sourceStnCode=&popularStnListLimit=15&preferredStnListLimit=6&channel=mwebd&language=EN`

Port `normalizeDate` (accepts `YYYY-MM-DD`, `DD-MM-YYYY`, `DD/MM/YYYY`; emits `DD-MM-YYYY`), `formatDuration` (minutes → `"20h 45m"`), and `parseAvailability` (regex `AVAILABLE-(\d+)` for seat count) verbatim.

Error handling mirrors `apiGet()`: non-JSON body → descriptive exception including status and a truncated body; a present `error` field → exception carrying its code and message.

HTTP: OkHttp with kotlinx.serialization. 30s timeout, bounded to 5 concurrent requests.

**Known risk:** this is a reverse-engineered public web API with no uptime contract. It can change or start rejecting non-browser clients without notice. Failures must surface as a readable message on the board, never a crash.

### Availability shape

The API returns **one status string per class** — `AVL 24`, `RAC 5`, or `WL 12` — never a breakdown, because those are sequential states of a single queue. A class that is in RAC has no available-seat count. The UI must not imply otherwise.

### Station resolution

```kotlin
data class StationGroup(val name: String, val codes: List<String>)
```

Seeded from `AGENTS.md`:

| Group | Codes |
|---|---|
| Rajasthan | AII, KSG, JP, MTD, JU |
| Pune | PUNE, KK |
| Mumbai | BDTS, MMCT, DR, LTT, CSMT, PNVL |

Resolution: a case-insensitive group-name match expands to that group's codes; anything else goes to the auto-suggestion endpoint and yields one code (preferring `majorStn`, else the first result). A general user's "Delhi" is simply a group of one. The settings screen planned for later writes rows into this table; nothing else changes.

Group order is retained for display and as a final tiebreak. It is **not** a primary ranking axis.

### API key storage

`SharedPreferences` holding an AES-GCM ciphertext, with the key material generated in and never leaving the Android Keystore (`AndroidKeyStore` provider, `AES/GCM/NoPadding`, IV stored alongside). About 40 lines, no dependencies.

Explicitly **not** `androidx.security:security-crypto` / `EncryptedSharedPreferences` — deprecated since `1.1.0-alpha07` (April 2025) for keystore-corruption failures on some OEM devices, which would break the app for some recipients and not others.

The key is used only as an `Authorization` header to OpenAI. It is never logged, never placed in a prompt, and never sent to ConfirmTkt.

---

## Agent layer

### `parseTrip(sentence): TripQuery`

One POST to `/v1/chat/completions` with `response_format: {"type": "json_object"}`.

```kotlin
data class TripQuery(
    val origin: String,
    val destination: String,
    val dates: List<String>,      // ISO, expanded; never empty
    val classes: List<String>     // empty = all classes
)
```

The system prompt carries the device's **current date and IANA timezone**. Without it the model resolves "tomorrow" against an unknown date and silently produces the wrong year.

Ranges are expanded to explicit dates here ("first week of September" → seven ISO dates). Cap at 7 dates; if the model returns more, truncate and note it in the UI.

Validation before use: `dates` non-empty and each parseable; `origin`/`destination` non-blank; unknown classes dropped. On failure, show "Couldn't read that trip — try naming the two places and a date" rather than guessing.

### `explain(topRows): String`

One POST. Input is the top five ranked rows already rendered as compact text — never raw API JSON. Output is one sentence naming why the first row ranks first. Purely decorative: if the call fails or the key is rate-limited, the board still renders and the line is omitted.

### Model configuration

Default `gpt-4o-mini`, held as a single constant. `Llm.kt` is the only file that talks to a model provider, so swapping to Claude or Gemini later is a one-file change.

---

## Ranking

A `ResultRow` is one train in one class on one date:

```kotlin
data class ResultRow(
    val train: Train,
    val travelClass: String,   // "SL", "3A", "2A", ...
    val status: String,        // "AVL 24" | "RAC 5" | "WL 12" | "Regret"
    val kind: StatusKind,      // AVL | RAC | WL | OTHER
    val number: Int?,          // parsed count from the status
    val fare: Int?,
    val date: LocalDate
)
```

The same train appearing for both AII→PUNE and KSG→PUNE is two legitimate rows — different boarding points.

**Sort order, in full:**

1. `kind` — AVL, then RAC, then WL, then OTHER (`Regret`, unparseable)
2. within AVL: seat count **descending**; within RAC and WL: number **ascending**
3. daytime departure (06:00–22:00) preferred
4. shorter duration first
5. origin group index, then destination group index — final tiebreak only

Station convenience is deliberately **not** a primary axis in this version. The user will supply preferences through a settings screen later; until then, availability alone decides.

Class order (SL/3A/2A) is not a ranking axis either. Classes compete on availability like everything else. When the user names a class in their query, other classes are filtered out before ranking rather than down-ranked.

Dedup key: `(trainNumber, fromStnCode, toStnCode, date, travelClass)`.

Return the top 10, or all rows if fewer exist. Never pad.

---

## UI

Two screens. Direction A — "Departure Board" — from the reviewed mockups.

### KeyScreen

One field, one button. Masked input with a reveal toggle. Accepts the key, does a format sanity check (`sk-` prefix, plausible length), stores it, proceeds. Copy states plainly that the key was given to them by the sender and that usage bills to that key. No account, no email, no Google Sign-In.

### BoardScreen

A single screen in three parts.

**Header** — a station-board band: route, date or date range, result count.

**Body** — one of three states:

- *Empty*: a one-line prompt and two example queries.
- *Searching*: a progress report, not a spinner. `Search.kt` emits one event per completed station pair, so the screen names the pair being checked and how many trains it returned, with a determinate bar over `pairs × dates`.
- *Results*: a `LazyColumn` of uniform rows.

Row columns:

| TRAIN | NAME / ROUTE | DEP | CLS | STATUS |
|---|---|---|---|---|
| 11090 | Bhagat Ki Kothi Exp · AII→PUNE · 20h 45m | 18:30 | SL | AVL 24 |

The top row carries a left rail in the accent color. Below the list sits the `explain()` sentence. Status color is consistent throughout: green AVL, amber RAC, red WL.

**Footer** — a single-line text input.

Errors render in the body, in the same type as everything else: what failed and what to do. Network timeouts, ConfirmTkt errors, an invalid or rate-limited key, and an unparseable trip each get their own message. Nothing crashes the app.

### Extension points

These are anticipated, not built:

- Mic button — sits in the footer input row beside send.
- Settings — an icon in the header band; the station-preference editor writes to the `StationGroup` table.
- Filters — chips between header and body.

---

## Testing

**`RankingTest`** — hand-built `ResultRow` lists asserting each sort level and the dedup key. No device, no network, no key. This is where the rules are pinned.

**`ConfirmTktTest`** — a saved JSON response from the live API parsed into models; asserts seat-count regex, duration formatting, class ordering, and date normalization across all three accepted input formats.

**Manual, on a physical device:**

1. Paste a real key; confirm the board appears and the key survives a restart.
2. "Rajasthan to Pune on 1 September" → rows from AII/KSG/JP into PUNE/KK, sorted AVL before RAC before WL.
3. "Jaipur to Pune tomorrow" → the correct calendar date, correct year.
4. A range: "Rajasthan to Pune first week of September".
5. A general route with no group: "Delhi to Bangalore next Friday".
6. Airplane mode; a deliberately wrong key; an unparseable sentence — each shows a readable message.
7. `adb logcat | grep -i sk-` returns nothing.

---

## Distribution

Neither prior document covered this, and it is the actual deliverable.

**One-time setup:** Android Studio (bundles JDK, SDK, `adb`, emulator — none are currently installed on the development machine). Then a release keystore:

```sh
keytool -genkey -v -keystore ~/train-search-release.jks \
  -alias trainsearch -keyalg RSA -keysize 2048 -validity 10000
```

Back this file up. Losing it makes it impossible to ship an upgrade to anyone who has already installed the app.

Signing config reads the keystore path and password from `~/.gradle/gradle.properties`, never from a file in the repository.

**Iterating:** `./gradlew installDebug` over wireless debugging.

**Releasing:** `./gradlew assembleRelease`, then share `app/build/outputs/apk/release/app-release.apk` over WhatsApp or Drive.

**What recipients experience** — worth telling them in advance so nobody panics:

1. Tapping the APK prompts them to allow installs from that source.
2. Play Protect shows an "unsafe app" warning; they tap through it.
3. They paste the key and are in.

**Shelf life:** Google's developer-verification requirement for sideloaded apps begins September 2026 in Brazil, Indonesia, Singapore and Thailand, rolling out globally through 2027. India is not in the first wave, so this works now. Register under the free hobbyist tier before the global rollout.

**Key sharing, stated plainly:** one key shared across several people means any of them can extract it from their device, all usage bills to the key's owner, and there is no way to revoke one person without revoking everyone. Acceptable for family; worth knowing rather than discovering.

---

## Build configuration

| Item | Value | Reason |
|---|---|---|
| `compileSdk` / `targetSdk` | 36 | Current Play requirement |
| `minSdk` | 26 | Keystore AES-GCM; broad coverage. Resolves a 24-vs-26 contradiction in the prior documents. |
| Kotlin / AGP | Latest stable at implementation time | Verify against the toolchain, do not copy versions from the prior plan |
| Compose BOM | `2026.08.00` | Verified present on Google Maven |
| OkHttp + kotlinx.serialization | Latest stable | Both HTTP clients; no Ktor, no Jackson |
| Application ID | `com.trainsearch` | |

No OpenAI SDK, no Ktor, no `androidx.security-crypto`, no navigation library (two screens, one state flag).

---

## Appendix: corrections to the prior documents

Carried here so the errors are not reintroduced. These originated in a research pass performed without repository access.

1. **API host and path.** `deep-research-report.md` states `https://api.confirmtkt.com/v2/rail/search`. The real endpoint is `https://cttrainsapi.confirmtkt.com/api/v1/trains/search`.
2. **Authentication.** The same document describes calling ConfirmTkt "with our stored `apiKey`" and says train API credentials "stay in code/Keystore." The endpoint is unauthenticated; `apikey: ct-web!2$` is a public constant. There are no train API credentials.
3. **Deprecated storage.** `implementation_plan.md` Component 3 specifies `EncryptedSharedPreferences`, deprecated since April 2025.
4. **`minSdk`.** 24 in the research report, 26 in the plan. Resolved to 26.
5. **Google Sign-In.** Present in the research report; excluded — there is no login.
6. **Ranking ownership.** The research report has Kotlin ranking; the plan's `ToolRegistry` returns raw trains for the model to rank. Resolved: Kotlin ranks, always.
7. **Missing current date.** Neither document injects today's date, while the plan's own acceptance test uses "tomorrow."
8. **Missing token budget.** The plan returns aggregated train JSON to the model each iteration — plausibly 40–80k input tokens per turn. Removed by design.
9. **Missing date ranges.** Both documents handle a single date; `AGENTS.md` is written around ranges.
10. **Missing distribution.** Neither document covers signing, release builds, or installation.

Verified correct and retained: Compose BOM `2026.08.00`, `com.openai:openai-java` `4.50.0` (real, now superseded by `4.51.0` — but unused here), and the existence of ADK for Kotlin/Android 0.1.0, which both documents correctly decline to depend on.
