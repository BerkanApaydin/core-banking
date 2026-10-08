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
