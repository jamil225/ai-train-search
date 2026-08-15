# Train Search Workflow

This document is intentionally tool-neutral. It lets any AI agent or human operator use this project’s local, read-only train-search workflow. The detailed preference rules live in `AGENTS.md` and should be read first when the agent supports that convention.

## Purpose and boundary

Search live Indian Railways availability for the user’s family travel between Rajasthan and Pune/Mumbai. Report results only. Never book, log in, create an account, submit passenger data, make payments, or carry out another transaction.

Availability is a live snapshot and can change. Do not promise confirmation.

## Local MCP server

The local source checkout is at:

```text
vendor/confirmtkt-mcp
```

Build it from the lockfile when `dist/server.js` is missing or stale:

```sh
cd vendor/confirmtkt-mcp
npm ci --include=dev
npm run build
```

Register the following as a local stdio MCP server in the host agent’s own configuration format:

```text
name: confirmtkt-trains
command: node
args: /Users/dev/codex/train-search/vendor/confirmtkt-mcp/dist/server.js
```

The server exposes only read-only tools:

- `search_trains`: live trains and class availability for a route/date.
- `get_seat_availability`: availability for one train/route/date.
- `find_station_code`: station autocomplete.

For Codex CLI, the registration command is:

```sh
codex mcp add confirmtkt-trains -- node /Users/dev/codex/train-search/vendor/confirmtkt-mcp/dist/server.js
```

MCP registration is normally stored in an individual agent/user profile, not the repository. A different computer or AI product must perform its equivalent local registration using the command above.

## Search procedure

1. Accept a direction and an exact date or date range. Expand the station groups and priorities from `AGENTS.md` automatically.
2. Search viable station-pair combinations with `search_trains` for every requested date.
3. Apply the route, class, availability, and timing ranking rules in `AGENTS.md`.
4. Return 5–10 distinct, useful options where possible. Do not duplicate one train merely because it matches multiple destination station queries; use its actual returned stop details.
5. Include confirmed seats, then RAC, then waitlist only when needed. Clearly display route, date, train, timing, class, status, fare, and returned quota.
6. State that the result is a live General-quota snapshot unless another verified quota response is explicitly returned.

## Ladies quota

The ConfirmTkt endpoint accepts `quota=LD`, but the tested responses returned no class availability. Do not silently fall back to General quota and do not claim an empty `LD` response means the train does or does not support Ladies quota in the actual booking system. The precise status is: “Ladies quota was selected, but this API returned no class availability for this train/route.”

Only add Ladies-quota support to the MCP if a repeated read-only test finds a non-empty `availabilityCache` whose entries explicitly say `quota: "LD"`. The more detailed test record is in `AGENTS.md`.

## Portability note

`AGENTS.md` is a common project-instruction convention, but not every AI product loads it automatically. For an agent that does not, explicitly provide or point it to both `AGENTS.md` and this file. No document can force an unrelated AI product to follow project instructions; this pair makes the intended behavior explicit and reproducible.
