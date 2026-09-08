/**
 * Published language of the Account bounded context (Open Host Service).
 *
 * <p>This package is the only stable contract downstream contexts (e.g. transfer)
 * may depend on. It contains no business logic and no framework dependencies —
 * only immutable DTOs, the {@link com.bank.app.accountapi.AccountApi} port, and
 * the {@link com.bank.app.accountapi.AccountSnapshotCache} caching contract
 * (with its framework-free {@link com.bank.app.accountapi.AbstractAccountSnapshotCache}
 * base). The {@code account} module implements the port; infrastructure provides
 * the cache backend; consumers must never depend on
 * {@code com.bank.app.account..} directly (enforced by ArchUnit).
 */
package com.bank.app.accountapi;
