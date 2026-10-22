# DragonView Code Learning Guide

This guide is a map for relearning the project. Source comments explain *why* a
method or non-obvious block exists; the code itself remains the source of truth.

## Main execution flow

1. `LoginActivity` validates credentials and delegates authentication.
2. `LocalRepository` runs work off the UI thread and coordinates cloud, cache,
   workspace, and offline-outbox behavior.
3. `SupabaseClient` performs authenticated Supabase calls and refreshes tokens.
4. `SessionManager` stores the user, tokens, and active workspace securely.
5. `MainActivity` hosts navigation; each feature Fragment renders one screen.
6. `SyncWorker` replays eligible offline mutations when connectivity returns.

## UI feature locations

- `ui/dashboard`: home metrics and quick actions.
- `ui/inventory`: harvest stock, batch details, and inventory adjustments.
- `ui/sales`: sale entry, price lookup, totals, and sale history.
- `ui/planting`: planting groups, grafting, stages, and guidance.
- `ui/scanner`: CameraX capture, detection, and offline quality grading.
- `ui/analytics`: period reports, trends, comparisons, and historical records.
- `ui/settings` and `ui/workspace`: account and workspace management.

## How to read a feature

Start with its Fragment, follow repository method calls into `LocalRepository`,
then inspect the related model and Supabase function name. XML layouts describe
placement and styling; generated View Binding classes connect each XML ID to
Java without `findViewById`.

## Comment convention

- Class comments describe ownership and boundaries.
- Method comments describe purpose, important state changes, or threading.
- Inline comments explain business rules and non-obvious safeguards.
- Trivial getters, setters, and obvious Android overrides are intentionally not
  narrated line-by-line, because excessive comments make the real logic harder
  to study.
