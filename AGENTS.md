# Train-search preferences

This workspace supports a personal, read-only Indian Railways train-search workflow. Apply these preferences whenever the user gives a direction plus an exact journey date or date range. Do not ask for station codes or passenger count unless they are specifically needed for a new request.

For tool-neutral setup and operating instructions (including the local stdio MCP command), read [`TRAIN_SEARCH_WORKFLOW.md`](TRAIN_SEARCH_WORKFLOW.md). Keep both documents aligned when changing this workflow.

## Safety and scope

- Search and report only. Never book tickets, log in, submit passenger details, make payments, or perform any other transaction.
- Treat every availability result as a live snapshot, not a guarantee. State the search time/date range in the response.
- Do not store passenger count, age, gender, identity, account details, or payment information.
- The locally registered `confirmtkt-trains` MCP is read-only. It does not currently expose Ladies quota selection or verification; state this limitation whenever quota matters, and report the quota returned by the tool.

## Input interpretation

The user supplies a Rajasthan-to-Pune or Pune-to-Rajasthan direction and an exact date or date range. Expand those locations automatically; do not require the user to restate the station preferences.

### Rajasthan station group — most to least convenient

1. Ajmer Jn (`AII`)
2. Kishangarh (`KSG`)
3. Jaipur (`JP`)
4. Merta Road (`MTD`)
5. Jodhpur Jn (`JU`)

### Pune/Mumbai destination or origin group — most to least convenient

1. Pune Jn (`PUNE`)
2. Khadki (`KK`)
3. Mumbai-area terminals only as a last resort. Consider all relevant terminals; Bandra Terminus (`BDTS`) is commonly useful, alongside `MMCT`, `DR`, `LTT`, `CSMT`, and `PNVL` when service is available.

Apply the same group priorities in reverse for Pune-to-Rajasthan searches.

## Search and ranking rules

- Search viable station-pair combinations across the supplied date/date range, then return 5–10 ranked options where possible. If fewer usable results exist, return all of them; do not pad the list with irrelevant routes.
- Route convenience ranks before availability status across different routes: a more convenient route with RAC may rank above a less convenient route with confirmed seats.
- Within the same route, class preference is Sleeper (`SL`), then 3A, then 2A.
- Within the same route/class/status, favor materially higher confirmed availability. Do not elevate a bare `AVL 3` merely because it qualifies; however, if it is the only confirmed option, treat it as favorable and rank it ahead of RAC.
- Availability order is confirmed (`AVL`) first, then RAC, then waitlist (`WL`). Rank waitlist alternatives from the smallest number upward.
- RAC is acceptable, particularly when the journey is more than 10 days away. If no confirmed option is suitable, show RAC directly; if there is no RAC, show waitlist options.
- When confirmed options exist, prefer daytime departures and arrivals (approximately 06:00–22:00) as a secondary tiebreaker only. Do not discard a better-ranked route solely for late timing.

## Result format

- Lead with the best 5–10 options, clearly showing route, date, train number/name, departure/arrival times, class, status (including AVL/RAC/WL number), fare where returned, and quota.
- Briefly call out why the top option ranks first, including any trade-off between route convenience and availability.
- Explicitly flag Ladies quota as unavailable for verification through the current MCP.

## Decision log

- Store these preferences in `AGENTS.md` so they apply automatically in this workspace.
- Route convenience outranks confirmed-vs-RAC status across different routes.
- A lone confirmed `AVL 3` is a favorable result; otherwise, favor stronger availability within the same route/class/status.

## Ladies-quota API sanity check (2026-08-13)

- The underlying ConfirmTkt endpoint is `GET /api/v1/trains/search`. General results are returned by default and with `quota=GN`.
- The endpoint advertises `quotaList` containing `SS`, `GN`, and `LD`. `quota=LD` is recognized: it changes the response instead of silently returning General (`GN`) results. Alternative parameter names (`quotaCode` and `quotaType`) were ignored in testing.
- In tests of JU → PUNE (1 Sep 2026), MMCT ↔ NDLS (1–7 Sep 2026), and 84 further common route/date pairs, `quota=LD` returned no class entries in `availabilityCache`. This means the endpoint accepted Ladies quota but exposed no usable availability for those samples; it does not establish whether a specific train supports Ladies quota in an actual booking flow.
- Do not claim Ladies quota is unavailable, available, or confirmed based on an empty `LD` result. Report it precisely as: “Ladies quota was selected, but this API returned no class availability for this train/route.” Never silently fall back to `GN`.
- Before adding quota support to the MCP, rerun a read-only comparison using identical route/date inputs with `quota=GN` and `quota=LD`. Add it only if `LD` returns a non-empty `availabilityCache` whose entries explicitly carry `quota: "LD"`; otherwise the enhancement adds no actionable availability information.
