# Handoff to Antigravity — Train Search Android app

You are taking over implementation of an Android app. The design work is finished
and approved. **Do not redesign it.** Your job is to execute an existing plan.

## 1. Install Superpowers first

```bash
agy plugin install https://github.com/obra/superpowers
```

Antigravity runs the plugin's session-start hook, so Superpowers is active from your
first message. Re-run the same command to update it later.

**Do not copy skill files by hand.** A manual copy puts the files on disk but skips
the bootstrap that makes skills auto-trigger, which makes them dead weight. If the
install command fails, stop and tell your human partner rather than working around it.

Reference copy of the same skills, for reading only — not an install:
`/Users/jamil.ahmad/.claude/plugins/cache/claude-plugins-official/superpowers/6.3.0/skills/`

Antigravity-specific tool mapping (subagents, task tracking) is at
`skills/using-superpowers/references/antigravity-tools.md` inside that plugin. Read it —
Antigravity has no todo tool, so skills that say "create a todo list" mean a **task
artifact** (`write_to_file` with `IsArtifact: true`, `ArtifactType: "task"`).

## 2. Use `superpowers:executing-plans`

The plan is already written. Invoke `superpowers:executing-plans` and work through it
task by task. Do **not** invoke `brainstorming` or `writing-plans` — that work is done
and re-opening it wastes your human partner's time.

`superpowers:test-driven-development` applies within each task. Every task in the plan
is already written as a TDD cycle: failing test → run it → implement → run it → commit.

## 3. Read these two documents before writing any code

| Document | What it is |
|---|---|
| `docs/superpowers/specs/2026-08-15-train-search-android-design.md` | The approved design. Answers *why*. |
| `docs/superpowers/plans/2026-08-15-train-search-android.md` | 12 tasks with exact code, tests and commits. Answers *how*. |

Also read `AGENTS.md` in this repo for the original workflow the app reproduces, and
`vendor/confirmtkt-mcp/src/utils/confirmtkt.ts`, which is the known-working reference
implementation the Kotlin client is a port of.

## 4. Facts you must not "correct"

Earlier research passes on this project were done without repository access and
produced confident, wrong statements. Those errors are listed in the spec's appendix.
Do not reintroduce them. In particular:

- **The ConfirmTkt host is `https://cttrainsapi.confirmtkt.com`**, path
  `/api/v1/trains/search`. It is **not** `api.confirmtkt.com` and **not** `/v2/rail/search`.
- **The endpoint is unauthenticated.** `clientid: ct-web` and `apikey: ct-web!2$` are
  public constants from ConfirmTkt's own web bundle. They are not secrets, not per-user,
  and there is no train-API credential to store anywhere.
- **Ranking is availability-only: AVL → RAC → WL → OTHER.** Station convenience is a
  final tiebreak. Travel class is not a ranking axis at all. This is a deliberate
  decision by the project owner and overrides the class-preference rules in `AGENTS.md`.
- **A result row is one train in one class.** The API returns exactly one status string
  per class — never an available count and a waitlist count for the same class.
- **No `EncryptedSharedPreferences`** (`androidx.security:security-crypto`). It is
  deprecated for keystore corruption on some OEM devices. Use the Keystore-wrapped
  AES-GCM implementation in the plan's Task 6.
- **No OpenAI SDK, no Ktor, no Jackson, no agent loop, no function calling.** The model
  has exactly two jobs: parse a sentence into a trip struct, and write one explanatory
  sentence. Everything else is deterministic Kotlin.

If you believe one of these is wrong, say so to your human partner and stop. Do not
silently change it.

## 5. Two tasks need the human

- **Task 1** runs the Android Studio new-project wizard.
- **Task 12** runs `keytool` and sets a keystore password.

Pause and hand back at both. Do not attempt to script around them.

The machine currently has Homebrew but **no JDK, no Android SDK and no Android Studio**.
If they are still missing, the human needs to run
`brew install --cask android-studio` and open it once before Task 1 can proceed.
Tasks 2–8 are pure Kotlin and can be built and tested without a device or emulator.

## 6. Definition of done for each task

A task is complete when its tests pass and it is committed. Run the suite from the
`TrainSearch/` directory:

```bash
./gradlew :app:testDebugUnitTest
```

By the end of Task 8 that is 41 passing tests. Do not mark a task done on the strength
of code that looks right — run the command and read the output. If a test fails, use
`superpowers:systematic-debugging` before proposing a fix.

## 7. Scope

Build Tasks 1–12 as written. Deliberately out of scope: voice input, a settings screen,
filter chips, booking, login, and quota selection. Each has a named home in the spec for
later. Do not build them now.
