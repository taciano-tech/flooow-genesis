# SPEC-0012: Inventory Source Ledger

**Status:** Proposed

**Date:** 2026-08-12

**Source decision:** ADR-0012

## Objective

Persist deterministic fake pages for the first typed inventory source capability
and advance sealed connector progress in the same organization-scoped PostgreSQL
transaction, without mapping a provider item to Genesis business inventory.

## Authorized next implementation

Acceptance authorizes TASK-0073 only:

1. add a pure `applications:inventory-source-ingestion` Kotlin module depending
   only on `applications:connector-runtime`;
2. define `inventory.source-balance.read` and its typed source-balance record;
3. add a transport-neutral authenticated progress-protector port and redacted
   sealed-envelope value to the connector runtime;
4. add additive PostgreSQL migration `V006` for connector progress, page commits,
   and immutable inventory source balances;
5. implement the capability-owned PostgreSQL committer in the existing schema-
   owning persistence module;
6. atomically append typed records and compare-and-set sealed progress;
7. prove organization isolation, lifecycle gating, encryption context, rollback,
   idempotency, concurrency, terminal exhaustion, and privacy;
8. use only deterministic connector and protector fakes in tests;
9. leave production startup, HTTP routes, existing business persistence, and
   external traffic unchanged.

No Mercado Livre or Omie class, credential, endpoint, SDK, OAuth exchange, HTTP
client, webhook, scheduler, production protector, key, automatic retry, mapping,
Genesis product/SKU, canonical unit, inventory mutation, risk assessment, event,
outbox delivery, or public ingestion API is authorized.

## Module and capability

The pure module exports:

```text
InventorySourceBalanceCapability.KEY = inventory.source-balance.read
InventorySourceBalanceRecord : ConnectorRecord
SourceItemReference
SourceLocationReference
SourceSku
SourceUnitCode
SourceQuantity
SourceVersion
```

It has no Kernel, Marketplace Operations, HTTP, database, serialization,
framework, provider, or cryptography dependency.

The record contains:

```text
sourceItemReference            required
sourceLocationReference        optional
sourceSku                      optional
sourceUnitCode                 optional
sourceUpdatedAt                optional
sourceVersion                  optional
availableToSell                optional
onHand                         optional
reserved                       optional
pendingInbound                 optional
pendingOutbound                optional
```

At least one quantity is present. There is no generic map, raw JSON, provider
payload, provider URL, Genesis SKU, organization ID, connection ID, or provider
key inside the record; those scopes come from the connector invocation.

## Source text values

References and source metadata are normalized to Unicode NFC, must already be
trimmed, contain no ISO control character, and have these UTF-8 limits:

```text
SourceItemReference:       1..256 bytes
SourceLocationReference:   1..256 bytes
SourceSku:                 1..256 bytes
SourceUnitCode:            1..32 bytes
SourceVersion:             1..128 bytes
```

Values are case-sensitive and otherwise opaque. They are never parsed as URLs,
UUIDs, numbers, marketplace IDs, warehouse IDs, or Genesis identifiers. Empty
optional values are invalid rather than normalized to null.

These values may be stored but are forbidden in logs, metrics, spans, exception
messages, connector outcomes, page-commit metadata, and audit details.

## Decimal quantities

`SourceQuantity` accepts a signed fixed decimal with precision at most 24 and
scale at most 6. Scientific notation, noncanonical leading plus, surrounding
whitespace, NaN, and infinity are impossible or rejected. Equivalent zero and
trailing-zero forms normalize to one canonical plain-decimal representation.

The ledger preserves negative balances. It does not clamp, take absolute values,
round, convert units, or infer one measure from another. A negative reservation
or pending quantity remains source data; later validation may classify it but
ingestion does not rewrite it.

Every populated quantity in one record shares the optional source unit code. If
the provider does not declare a unit, the adapter leaves it null; it does not
invent `EA`, `UN`, or another unit.

## Measure semantics

- `availableToSell`: explicitly documented source quantity currently sellable;
- `onHand`: explicitly documented physical or book quantity on hand;
- `reserved`: explicitly documented quantity reserved from other use;
- `pendingInbound`: explicitly documented expected incoming quantity;
- `pendingOutbound`: explicitly documented expected outgoing quantity.

An adapter specification must name the exact provider field and endpoint used
for each populated measure. Similar names are insufficient evidence. The generic
committer validates shapes only and never derives measures.

## Progress protection

The connector-runtime module adds:

```text
ConnectorProgressProtectionContext(
  organizationId,
  connectionId,
  capability,
  progressVersion
)

ConnectorProgressProtector.seal(context, plaintextBytes)
  -> SealedConnectorProgress

ConnectorProgressProtector.open(context, sealedEnvelope)
  -> plaintextBytes
```

Rules:

- plaintext input and output are owned mutable byte arrays and zeroed by their
  caller in `finally`;
- the protector must not retain plaintext or envelope callback arrays;
- `SealedConnectorProgress` is opaque, defensively copied, redacted by
  `toString`, and at most 16,384 bytes;
- an envelope is cryptographically bound to every context field;
- wrong organization, connection, capability, version, corrupted envelope, or
  wrong key fails closed without a diagnostic containing sensitive bytes;
- a production adapter must use authenticated encryption with a fresh nonce and
  externally managed, rotatable keys;
- TASK-0073 supplies a deterministic reversible fake under test source only and
  production startup supplies no protector.

The runtime's existing 4,096-byte plaintext progress limit remains unchanged.

## PostgreSQL migration V006

`integration_connector_progress` contains:

```text
organization_id uuid
connection_id uuid
capability text
progress_version bigint
progress_envelope bytea nullable
exhausted boolean
last_observed_at timestamptz nullable
updated_at timestamptz
```

Its primary key is `(organization_id, connection_id, capability)`. A composite
foreign key binds it to the same-organization integration connection. Checks
enforce the capability grammar, non-negative version, envelope maximum, and:

```text
initial stream:  exhausted=false, progress_version=0,
                 progress_envelope=null
active stream:   exhausted=false, progress_version>0,
                 progress_envelope non-null
terminal stream: exhausted=true, progress_envelope=null,
                 last_observed_at non-null
```

`integration_connector_page_commit` contains organization, connection,
capability, input progress version, 32-byte page commit key, record count,
exhausted flag, page observed time, and commit time. It is unique by page key and
by input progress version within the scoped stream.

`integration_inventory_source_balance` contains the page scope, zero-based
record ordinal, source values, five nullable `numeric(24,6)` measures, and source
updated time. Its primary key is page scope plus ordinal. A foreign key requires
the owning page commit. Checks mirror all record invariants and require at least
one measure.

No JSON, raw response, credential, secret reference, plaintext progress,
request URL, response body, Genesis SKU, assessment ID, event ID, or destination
is stored.

## Load behavior

The PostgreSQL committer is bound to exactly
`inventory.source-balance.read` and `InventorySourceBalanceRecord`.

`load` requires organization, connection, and exact capability. It verifies an
active organization, an active same-organization connection, and a current
credential binding. An absent state is lazily represented as version zero,
unexhausted, and no progress; it is not inserted until the first commit.

When a row contains an envelope, `load` opens it using its exact stored scope and
version, converts the owned plaintext to `ConnectorProgress`, and zeroes the
protector output even when conversion fails. A protector error returns one
controlled runtime `INTERNAL` result and never resets progress.

Terminal state loads with no protector call. TASK-0073 provides no reset method.

## Atomic page commit

Before starting the transaction, the committer validates exact record runtime
types and record shapes and seals next progress for version `expected + 1` when
the page is not exhausted.

Inside one transaction it:

1. locks or creates the scoped progress row without crossing organizations;
2. revalidates active organization, connection, and credential binding;
3. checks whether the 32-byte page key already committed;
4. rejects a different current version as stale;
5. inserts the page commit and ordered immutable records;
6. compare-and-set advances version and envelope or marks terminal exhaustion;
7. commits.

All envelope callback copies are zeroed after database binding. A rollback leaves
no page, record, version, or terminal change. Plaintext progress never becomes a
SQL parameter.

Repeated page key returns `ALREADY_COMMITTED` only when its stored scope,
version, count, exhausted flag, and observed time agree. Any disagreement is an
`INTERNAL` integrity failure, not a successful duplicate.

## Ordering and snapshots

Record ordinal is the adapter-provided page list order and must be stable for a
repeated read of one progress version. The ledger is append-only. It does not
update a prior balance, choose a latest value, aggregate locations, or delete old
snapshots.

TASK-0073 supports one initial stream ending in a terminal state. Scheduled runs,
lookback windows, stream generation IDs, reset, pruning, retention, and replay
are separate contracts.

## Privacy and observability

Allowed low-cardinality attributes remain provider key, capability, controlled
outcome, and exhausted flag. Counts and durations are allowed.

Source item, location, SKU, unit, version, quantities, source timestamps,
plaintext progress, sealed envelopes, commit keys, organization, connection, and
record ordinals are forbidden metric labels. Existing rules for trace IDs apply,
but source record data and progress are forbidden even in traces.

Database errors, constraint names, protector errors, and record values are
translated to controlled outcomes without raw messages.

## Test plan

1. the pure ingestion module has only the connector-runtime dependency;
2. the exact capability and record type are registered once;
3. source text normalization, controls, whitespace, and UTF-8 limits are enforced;
4. decimal precision, scale, signed values, and canonical representation reproduce;
5. at least one measure is required and missing remains distinct from zero;
6. source values never become Genesis `SkuRef` or `InventorySnapshot`;
7. sealed envelopes copy defensively, redact text, enforce size, and zero scoped arrays;
8. fake protection binds all organization, connection, capability, and version fields;
9. corruption or wrong protection context fails without plaintext disclosure;
10. V006 applies after V001 through V005 and all composite isolation constraints exist;
11. load of an absent stream returns version zero without inserting a row;
12. load rejects foreign, suspended, revoked, draft, or unbound connections uniformly;
13. successful commit inserts page and ordered records and advances progress atomically;
14. PostgreSQL contains a sealed envelope but none of the plaintext progress markers;
15. terminal commit stores no envelope and later execution makes no protector or adapter call;
16. repeated identical page key returns already committed without duplicate records;
17. duplicate-key metadata disagreement fails closed;
18. two concurrent commits for one version accept exactly one page;
19. a stale different page cannot append records or overwrite progress;
20. validation, sealing, SQL, constraint, and commit failures roll back every database effect;
21. negative and decimal source quantities round-trip exactly without mapping or clamping;
22. two organizations and two connections may reuse source identifiers without leakage;
23. lifecycle suspension between read and commit prevents commit;
24. source values, quantities, envelopes, progress, commit keys, and injected error markers
    appear in no outcomes, telemetry observations, or assertion diagnostics;
25. existing API, OpenAPI, assessments, events, delivery, research, and Kernel remain unchanged;
26. production startup has no committer, protector, worker, provider, or external call;
27. the complete repository build remains green.

## Provider research constraints

- Mercado Livre User Products can expose multiple stock-location types with
  different ownership and editability; location identity must be retained;
- Mercado Livre returns an inventory version independently from business SKU and
  may manage fulfillment inventory itself;
- Omie exposes physical, reserved, pending, balance, product, and stock-location
  fields separately;
- Omie operational stock may be negative and must not be rewritten during
  ingestion.

## References

- Mercado Livre distributed inventory:
  https://developers.mercadolivre.com.br/pt_br/lojas-oficiais/estoque-distribuido
- Mercado Livre multi-origin inventory and User Products:
  https://developers.mercadolivre.com.br/pt_br/autenticacao-e-autorizacao/gestao-de-estoque-multiorigem-user-products
- Omie inventory API:
  https://app.omie.com.br/api/v1/estoque/consulta/
- Omie physical and reserved stock semantics:
  https://ajuda.omie.com.br/pt-BR/articles/1592576-analisando-o-estoque-dos-produtos
- Omie negative-stock behavior:
  https://ajuda.omie.com.br/pt-BR/articles/12540308-entendendo-o-estoque-negativo-e-seus-impactos-nos-registros-contabeis

## Remaining boundary

A production progress protector, key lifecycle, provider adapter, credential
onboarding, sync-run reset, scheduling, mapping from source item/location/SKU/unit
to Genesis identity, canonical inventory aggregation, data-quality policy,
retention, reconciliation, webhooks, assessment triggering, and outbound stock
writes require later accepted specifications.

## Acceptance

Merging ADR-0012 and SPEC-0012 authorizes TASK-0073 only. It does not authorize a
real provider, plaintext progress, production encryption, source-to-product
mapping, inventory mutation, assessment, event, worker, or external request.
