# IBAN checksum rollout

New accounts receive server-generated, checksum-valid Turkish-format IBANs using a synthetic bank code for this simulation. The value object still accepts the earlier format-only records on reads: changing an existing account identifier automatically would break transfer references and clients. The dev/demo seeder recognizes its three old example IBANs and will not create a second funded account for the same example BBAN.

Before deploying against a database created by an earlier version, run this **read-only** inventory in an authorized environment. Store the output in an access-controlled location; account identifiers are sensitive.

```sql
SELECT id,
       left(iban, 8) || '*******' || right(iban, 4) AS masked_iban
FROM accounts
WHERE iban !~ '^TR[0-9]{24}$'
   OR (iban ~ '^TR[0-9]{24}$'
       AND (substring(iban FROM 5) || '2927' || substring(iban FROM 3 FOR 2))::numeric % 97 <> 1)
ORDER BY id;
```

Any returned row needs a business decision and a coordinated correction procedure. Do not rewrite IBANs solely by calculating new check digits: even a mathematically valid number does not establish that an institution allocated it or that the current user owns it. Preserve references, client contracts, audit evidence, and rollback/forward-repair steps before a manual change. The application enforces checksum on **new account creation only**; legacy reads remain possible during this migration. Institution allocation and ownership verification remain separate product integrations.

Spendable-transfer creation is gated structurally, not just by the two callsites: `Transfer.create` may only be called by `TransferDomainService.validateAndCreateTransfer`, which enforces MOD 97-10 via `Iban.checked` on both legs before delegating (pinned by `CodingRulesArchitectureTest.onlyTransferDomainServiceMayCreateTransfers`, alongside the per-path checksum rules). Legacy format-only reads never touch `create`, so they are unaffected by the gate.
