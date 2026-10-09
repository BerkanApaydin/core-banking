# DB-2: Keyset Pagination Contract

**Decision:** Report/history pagination is cursor-based:
`(created_at, id) < (cursorCreatedAt, cursorId)` with
`ORDER BY created_at DESC, id DESC`. No OFFSET; the V43 covering indexes
(`sender/created/id`, `receiver/created/id`) feed the ordering.

**Contract:**
- `cursorCreatedAt=null, cursorId=null` = first page (or legacy offset path).
- The response carries `nextCursorCreatedAt/nextCursorId`; null when
  `hasNext=false`.
- The cursor is not opaque (ISO-8601 + id) — clients store it, never mint it.
- `/report/combined` accepts the same cursor; totals are whole-range
  aggregates independent of the cursor.

**Why:** OFFSET + `ORDER BY created_at` sorts the full match set
(`O(n log n)` + `work_mem` spill). Keyset does one range scan + over-fetch
(`size+1`) and derives `hasNext` with no second query.

**Backward compatibility:** `page/size` is still accepted; a supplied cursor wins.

**R6 guard:** pure-offset windows with `page*size > 10_000`
(`ReportCriteria.MAX_OFFSET_WINDOW`, shared by `GetTransferHistoryQueryImpl`)
are rejected with 400 — OFFSET cost is bounded by construction, and unbounded
scrolling must use the cursor. The V43 covering indexes serve both the cursor
range scan and the surviving shallow-offset path.

**UI adoption:** the bundled report UI loads page 0 by offset and every deeper
page by cursor (`buildReportQuery`/`storeReportCursor` in `app.js`, pinned by
`frontend_contract.test.js`), and sends `If-None-Match` from the ETag the
server renders per page — an unchanged re-read is a 304 with no server
re-hash. Mutations clear the client ETag cache, so post-transfer balances
never go stale.
