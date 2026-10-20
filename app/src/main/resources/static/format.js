/* exported buildReportDateRange, buildReportQuery, escapeHtml, formatDate, formatDateInput, formatIbanDisplay, formatMoney, hasValidIbanChecksum, parseApiTimestamp, reportCursorForPage, sanitizeIban, storeReportCursor, transferStatusMeta */
// --- Utility Formatters ---
// Localized badge metadata for backend TransferStatus values.
// Unknown values render truthfully (escaped raw status, neutral style):
// defaulting to "Completed" would mislabel a future/foreign status as settled.
function transferStatusMeta(status) {
    switch (status) {
        case 'CANCELLED': return { label: __('report.status_cancelled'), cls: 'badge-inactive' };
        case 'PENDING': return { label: __('report.status_pending'), cls: 'badge-pending' };
        case 'FAILED': return { label: __('report.status_failed'), cls: 'badge-failed' };
        case 'COMPLETED': return { label: __('report.status_completed'), cls: 'badge-active' };
        default: return { label: escapeHtml(status), cls: 'badge-pending' };
    }
}
function escapeHtml(unsafe) {
    if (unsafe === null || unsafe === undefined) return '';
    return String(unsafe)
        .replace(/&/g, "&amp;")
        .replace(/</g, "&lt;")
        .replace(/>/g, "&gt;")
        .replace(/"/g, "&quot;")
        .replace(/'/g, "&#039;");
}
function formatMoney(amount) {
    // Defensive: backend always sends non-null numerics, but a null/NaN must
    // never render as "NaN" in a banking UI.
    const n = Number(amount);
    if (!Number.isFinite(n)) return '—';
    return new Intl.NumberFormat(locale(), { minimumFractionDigits: 2, maximumFractionDigits: 2 }).format(n);
}

function sanitizeIban(value) {
    // UX: the TR prefix is locked in and typed for the user; only digits can
    // follow (backend rule: ^TR[0-9]{24}$). Empty stays empty so the
    // placeholder and required-validation keep working.
    const raw = String(value || '');
    if (!raw.trim()) return '';
    return 'TR' + raw.toUpperCase().replace(/[^0-9]/g, '').slice(0, 24);
}

function isValidIban(value) {
    const v = String(value || '').toUpperCase().replace(/\s+/g, '');
    return /^TR[0-9]{24}$/.test(v);
}

function hasValidIbanChecksum(value) {
    const v = String(value || '').toUpperCase().replace(/\s+/g, '');
    if (!isValidIban(v)) return false;
    // Same ISO 13616 MOD 97-10 calculation as the account creation use case.
    const rearranged = v.slice(4) + '2927' + v.slice(2, 4);
    let remainder = 0;
    for (const digit of rearranged) {
        remainder = (remainder * 10 + Number(digit)) % 97;
    }
    return remainder === 1;
}

function formatIbanDisplay(iban) {
    if (!iban) return '';
    return iban.replace(/(.{4})/g, '$1 ').trim();
}

function parseApiTimestamp(value) {
    if (value === null || value === undefined || value === '') return null;
    const text = String(value);
    // The API serializes LocalDateTime in UTC without an offset. ECMAScript
    // treats an offset-less date-time as LOCAL time, shifting every rendered
    // instant by the browser's zone. Append Z unless the payload already
    // carries an explicit zone.
    const hasZone = /(?:Z|[+-]\d{2}:?\d{2})$/i.test(text);
    const d = new Date(hasZone ? text : `${text}Z`);
    return Number.isNaN(d.getTime()) ? null : d;
}

function formatDate(dateString) {
    const d = parseApiTimestamp(dateString);
    if (!d) return '—';
    return d.toLocaleString(locale(), {
        day: '2-digit',
        month: '2-digit',
        year: 'numeric',
        hour: '2-digit',
        minute: '2-digit'
    });
}

function formatDateInput(date) {
    // Report bounds are UTC calendar days: the backend stores and compares
    // UTC LocalDateTime values, with no browser-zone conversion.
    const pad = (n) => n.toString().padStart(2, '0');
    return `${date.getUTCFullYear()}-${pad(date.getUTCMonth() + 1)}-${pad(date.getUTCDate())}`;
}

function buildReportDateRange(startDay, endDay) {
    if (!/^\d{4}-\d{2}-\d{2}$/.test(startDay) || !/^\d{4}-\d{2}-\d{2}$/.test(endDay)) {
        throw new Error(__('report.select_dates'));
    }
    if (startDay > endDay) {
        throw new Error(__('report.invalid_date_range'));
    }
    // Backend rule (GenerateTransferReportQueryImpl): start.plusMonths(12)
    // must not be before end. End-of-day is 23:59:59.999999, so endDay must
    // be strictly before start-plus-12-months (ISO strings compare
    // chronologically). Mirrors LocalDate.plusMonths incl. month-end clamp.
    if (endDay >= plusMonths12(startDay)) {
        throw new Error(__('report.range_too_long'));
    }
    // PostgreSQL stores transfer time as TIMESTAMP(6); BETWEEN is inclusive.
    return { startDate: `${startDay}T00:00:00`, endDate: `${endDay}T23:59:59.999999` };
}

function plusMonths12(day) {
    const [y, m, d] = day.split('-').map(Number);
    const target = new Date(y, m - 1 + 12, 1);
    const lastDay = new Date(target.getFullYear(), target.getMonth() + 1, 0).getDate();
    const pad = (n) => String(n).padStart(2, '0');
    return `${target.getFullYear()}-${pad(target.getMonth() + 1)}-${pad(Math.min(d, lastDay))}`;
}

// --- Report keyset pagination (DB-2) ---
// The server serves report page 0 by offset and deeper pages by keyset cursor
// (cursorCreatedAt+cursorId), so deep windows never pay OFFSET and never hit
// the 10k offset guard. cursors[n] holds the cursor that OPENS page n;
// cursors[0] is always null (first page). Forward pushes the nextCursor the
// server just returned; back pops by index — no cursor, no deep page.
function reportCursorForPage(cursors, page) {
    const cursor = Array.isArray(cursors) ? cursors[page] : null;
    return cursor || null;
}

function storeReportCursor(cursors, page, report) {
    const stack = Array.isArray(cursors) ? cursors.slice(0, page + 1) : [null];
    stack[page + 1] = report && report.hasNext === true
        && report.nextCursorCreatedAt && report.nextCursorId != null
        ? { createdAt: report.nextCursorCreatedAt, id: report.nextCursorId }
        : null;
    return stack;
}

function buildReportQuery(criteria, page, size, cursor) {
    const params = {
        accountId: criteria.accountId,
        startDate: criteria.startDate,
        endDate: criteria.endDate,
        page: String(page),
        size: String(size)
    };
    if (cursor) {
        params.cursorCreatedAt = cursor.createdAt;
        params.cursorId = String(cursor.id);
    }
    return new URLSearchParams(params);
}

