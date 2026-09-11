# TASK-0165I.1 — Typed Omie Product Identifier Evidence

## Decision

Omie transaction product references are preserved as typed source evidence:

| Provider field | Preserved kind |
|---|---|
| `codigo_produto` | `INTERNAL_PRODUCT_ID` |
| `codigo_produto_integracao` | `INTEGRATION_PRODUCT_CODE` |
| `codigo` | `DISPLAY_PRODUCT_CODE` |
| `cCodInt` | `INTEGRATION_PRODUCT_CODE` |
| `cCodigo` | `DISPLAY_PRODUCT_CODE` |

The compact aliases follow the same meanings already used by the durable Omie
product-cost connector: `cCodInt` is the integration reference and `cCodigo`
is the displayed product code. Multiple identifiers present on one transaction
line are retained; textual similarity never collapses their kinds.

New `product_refs` values use
`{"kind":"...","value":"...","quantity":"..."}`. Existing
`{"code":"...","quantity":"..."}` values remain immutable and readable as
`UNKNOWN_LEGACY`; they cannot produce exact within-provider identity. No schema
migration or historical rewrite is required because `product_refs` is JSONB.

When the same source order and fingerprint has both legacy and typed durable
revisions, selection deterministically prefers the semantically richer typed
revision, then the latest observation and durable progress coordinates. Source
fingerprints and old rows are unchanged.

## Two distinct identity graphs

### Graph A — cross-system candidate graph

```text
ML seller_sku
    |
    | exact text equality evidence only
    v
Omie transaction typed product identifier
    |
    | cross-system status remains CANDIDATE / SUGGESTED
    v
explicit cross-system confirmation required
```

The evidence name is
`EXACT_SELLER_SKU_TO_OMIE_PRODUCT_REFERENCE_TEXT`. It describes equality, not
seller-SKU semantics on the Omie side and not product identity.

### Graph B — within-Omie provider graph

```text
Omie transaction typed identifier
    |
    | same-kind provider-semantic resolution
    v
Omie catalog/cost typed identifier
    |
    v
Omie provider product identity
```

Graph B may resolve exactly within Omie only for a unique same-kind match.
Cross-kind equality and `UNKNOWN_LEGACY` remain unresolved. Duplicate provider
products are ambiguous; contradictory non-null identities on repeated
stock/date observations are retained and produce conflict. An exact Graph B
result never upgrades Graph A.

## Catalog boundary and isolation

The catalog reader is read-only and queries
`integration_omie_product_cost_source_observation` by both organization and a
bound Omie connection. It aggregates stock-location/date repetitions by
`source_product_ref` while preserving separately:

- `source_product_ref` as internal product identity;
- `source_integration_ref` as integration product code;
- `source_product_code` as displayed product code.

The transaction and catalog scopes must match exactly. Organization mismatch,
connection mismatch, or mixed Omie transaction connections fail closed.

## Diagnostic metrics

The recompute result now reports transaction reference totals and counts for
all four kinds, catalog product/identifier coverage when the catalog reader is
available, within-Omie exact/ambiguous/conflict/unresolved counts when catalog
evaluation is available, and the ML seller-SKU ↔ Omie reference candidate
count. Catalog and resolver metrics are omitted when that evidence dependency
is unavailable; missing is not reported as zero. Existing TASK-0165H
transaction health counts are unchanged.

## Runtime provenance incident

An apparent 0% TASK-0165H identity result was caused by ambiguity between the
source revision and the artifact/image actually running, not by defective
identity policy. A clean immutable diagnostic image proved the reader/resolver
path. A clean immutable main image then produced:

- `mlTransactionsInspected = 33`
- `omieTransactionsInspected = 133`
- `exactConfirmed = 4`
- `unresolved = 29`
- `coveragePercentage = 12.12`

Future runtime evidence must preserve this chain:

```text
source commit
    -> build artifact
    -> image digest
    -> runtime instance
    -> evaluation
    -> decision
```

That provenance capability is future governance work and does not widen the
current product-identity scope.

## TASK-0165I.2 real-data runtime proof

The 2026-09-11 reboot left Docker Desktop running but removed the Docker CLI
from `PATH` and cleared the process-scoped configuration. The stopped known-good
main API retained all required database, service-principal, connection, vault,
and key variables, while its bind-mounted vault and PostgreSQL named volume
remained present. That context was reused internally without logging values.
Before this proof, no PR #221 artifact was deployed: the retained main and
diagnostic images came from other commits, and both API containers were stopped.

### Immutable provenance and defect found

The clean exact checkout at
`b77b6f418fa8a059a993c5dd0f75a1abfad2d964` produced image
`flooow-api-local:task-0165i2-b77b6f4-exact` with image ID/digest
`sha256:8b66d046bbaa55ab36382ed2285991216575909c7adea653ff7127a0c5db7015`.
Container
`1fdf3f7de59e309d712cef00ea34248b6632fdb2b8d884fd293d45086a399a43`
started at `2026-09-11T12:05:27.716042203Z` on a non-conflicting local port.
Its pre-acquisition recompute reproduced TASK-0165H exactly: 33 Mercado Livre
transactions, 133 Omie transactions, 4 exact confirmations, 29 unresolved, and
12.12% coverage.

That runtime exposed a real implementation defect. The endpoint was fixed to
the already exhausted `reacquisition-v1` progress namespace and returned one
already-committed page with zero records, so a parser upgrade could not create
new revisions. The attempted run changed nothing: 265 historical rows retained
digest `4f3f7fb11db88f723bbaebe474fd98db`.

Commit `b8daa9799fab22b786602be4dd522b5ed5a59ba7` advances only the Omie typed
reacquisition namespace to v2 while retaining v1 connector and reader support.
The clean exact checkout produced image
`flooow-api-local:task-0165i2-b8daa97-exact` with image ID/digest
`sha256:ee929579a13f8a301b551ad5cce5a67e871efb59013f8a8c3d067d714bfe73f6`.
Container
`16eae0dc245cba35df4995a1a4949e84ed465fb7bb22950e09d329c117ab5dca`
started at `2026-09-11T12:21:30.181246964Z`; the final evaluation timestamp was
`2026-09-11T12:22:48.240172Z`.

### Reacquisition and immutability

The corrected endpoint completed two provider-read invocations, committed two
new pages and 135 records, and reported zero already-committed pages under
`marketplace-economic.omie-transaction-evidence.reacquisition-v2`. The live
provider set had grown from 133 to 135 orders. The 265 v0/v1 rows remained
present with the same digest. A v2 replay then reported two already-committed
pages and left its 135 rows and digest unchanged. No row was updated or deleted,
no other organization/connection received a v2 row, and the only provider
operation was the read-only `ListarPedidos` call.

### Product evidence result

The selected 135 Omie transaction revisions contained 340 typed identifier
occurrences:

| Kind | Occurrences | Distinct values |
|---|---:|---:|
| `INTERNAL_PRODUCT_ID` | 170 | 37 |
| `INTEGRATION_PRODUCT_CODE` | 0 | 0 |
| `DISPLAY_PRODUCT_CODE` | 170 | 38 |
| `UNKNOWN_LEGACY` | 0 | 0 |

The governed catalog/cost reader was available, but the durable catalog table
contained zero rows: zero provider products and zero internal, integration, or
display identifiers. Consequently the 340 transaction identifier occurrences
resolved within Omie as 0 exact, 0 ambiguous, 0 conflict, and 340 unresolved.
This is an observed empty durable source, not a substituted value for a missing
dependency.

All 37 distinct historical transaction reference values map uniquely to
`INTERNAL_PRODUCT_ID`; none map to integration, display, unknown, or multiple
kinds. With no catalog evidence, their bounded same-kind result is 37 evaluated,
0 exact, 0 ambiguous, 0 conflict, and 37 unresolved. No cross-kind match was
attempted.

The requested golden SKU matched 53 typed Omie transaction identifiers, all
`DISPLAY_PRODUCT_CODE`. It was also present on 18 Mercado
Livre orders, producing 954 exact-text candidate pairs. It did not resolve to a
catalog identity because catalog evidence was empty, and no explicit
cross-system confirmation evidence or confirmation mechanism exists. Across
all SKUs, recompute reported 1,352 candidate pairs; every pair remained a
candidate and none became a confirmed product relation.

### Transaction identity regression result

The exact corrected runtime reproduced the historical 4/29/12.12 result before
reacquisition. After the live provider set changed, recompute inspected 135 Omie
transactions and reported 5 exact, 10 candidate, 2 ambiguous, 0 conflict, 16
unresolved, and 15.15% coverage. The change is attributable to newly observed
provider evidence: the evaluator/policy version remained
`MGI_GENESIS_IDENTITY_V1`, the historical rows remained immutable, and product
candidate evidence did not promote transaction or product identity.

## Safety boundary

This change is read-only. It introduces no provider write, Omie write-back,
Mercado Livre mutation, Economic Truth change, recovery execution, authority
escalation, fuzzy matching, causal assumption, or transitive confidence.
