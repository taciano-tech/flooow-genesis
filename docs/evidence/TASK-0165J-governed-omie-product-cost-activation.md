# TASK-0165J — Governed Omie Product-Cost Activation + Product Identity Closure

Date: 2026-09-11

## Decision

Activate the existing TASK-0149 read-only Omie product-cost capability in the
production API composition and expose one authenticated, bodyless,
organization-scoped refresh trigger. No ADR or SPEC changes are required: the
implementation realizes the already-authorized TASK-0149 architecture.

## Root-cause trace

The Integration Control Plane already held an active organization-bound Omie
static credential and `FLOOOW_OMIE_CONNECTION_ID` selected that connection.
Connector Runtime and the TASK-0149 adapter/committer were individually
implemented and tested. Production `Application.kt`, however, registered only
the Mercado Livre order and Omie transaction connectors/committers. It did not
register `OmieEconomicEvidenceConnector` or
`PostgresOmieProductCostCommitter`, and no route invoked
`marketplace-economic.product-cost`. The capability was therefore unreachable;
the durable progress and source-observation tables both remained at zero.

Activation exposed two additional bounded composition constraints before the
proof could complete:

1. Connector Runtime accepts one connector object per provider, so separate
   Omie registrations fail closed. `OmieProviderConnector` now presents one
   Omie descriptor and delegates each capability to its existing adapter.
2. The shared Omie refresh trigger retained the transaction-oriented ten-page
   ceiling. The first live product-cost attempt immutably committed ten pages
   and 500 rows, then returned `BUDGET_EXCEEDED` with progress unexhausted. The
   product-cost trigger now receives a bounded 100-page ceiling; transaction
   refresh and reacquisition retain their existing ten-page ceiling.

## Progress and immutability

Before activation, the governed database contained no product-cost progress row
and no product-cost observation. A new versioned namespace is therefore neither
required nor justified. The original
`marketplace-economic.product-cost` namespace remains authoritative. The
partial ten-page proof was resumed from durable progress and completed without
deleting, resetting, updating, or rewriting any historical page or source
observation.

Replay after exhaustion returned one already-committed invocation, zero new
pages, and zero records. All 41 page commits and 2,009 normalized observations
remain immutable.

## Exact runtime provenance

The immutable acquisition spans two explicit provenance chains because the
first one exposed the ten-page trigger ceiling after committing its pages:

```text
source commit: a8ddf9c682776de55f28416cbad89f17b88dcf36
image/tag: flooow-api-local:task-0165j-a8ddf9c-exact
image digest: sha256:847c980f4f968e7a067fdb525c45bf0168de879a82bbafb2f97954307586f368
runtime instance: 2956ad11ffc5bfbec3593ba0b01afe5af8731c28cd5c538f74bf74af0a55f804
runtime started: 2026-09-11T15:38:22.060715581Z
bounded evaluation timestamp: 2026-09-11T15:38:51.431670Z
result: pages 1-10 / 500 observations committed; progress remained open

source commit: 5f7728ceb96388f63c56ca657e02e332af827a09
image/tag: flooow-api-local:task-0165j-5f7728c-exact
image digest: sha256:59a431598ba59e683918956f64ea185c9705e8b26f05f87adcecc0035d2055d8
runtime instance: 5a495c87ce8b54c875967df78f987d191d715e8580754dae6507ffd0d3354500
runtime started: 2026-09-11T15:52:48.783448575Z
final evaluation timestamp: 2026-09-11T15:58:45.519613Z
result: pages 11-41 / 1,509 observations committed; progress exhausted
```

The runtime reused the retained local encrypted vault, active Control Plane
connection, PostgreSQL volume, and isolated local port without logging secret
values. The only provider operation was read-only `ListarPosEstoque`.

## Real observed result

| Measure | Observed |
|---|---:|
| Provider pages requested / committed | 41 / 41 |
| Durable observations | 2,009 |
| Distinct provider products / internal IDs | 287 / 287 |
| Distinct integration codes | 12 |
| Distinct display codes | 287 |
| Missing `nCMC` | 0 |
| Observed zero `nCMC` | 1,793 |
| Non-zero `nCMC` | 216 |
| Catalog scopes (organization + connection) | 1 |

`nCMC` remains exact provider evidence. Observed zero is not treated as missing;
missing would not be treated as zero. No currency was inferred and no value was
promoted into canonical Economic Truth.

The catalog reader consumed all 2,009 durable observations and evaluated 287
provider products. Across the 340 typed transaction identifier occurrences,
same-kind within-Omie resolution produced 337 exact, 0 ambiguous, 0 conflict,
and 3 unresolved. All 37 historical distinct `INTERNAL_PRODUCT_ID` values
resolved exactly and uniquely: 37 evaluated, 37 exact, 0 ambiguous, 0 conflict,
0 unresolved.

Golden SKU `MKP-CONST-ELETR-TM11552-01` resolved by
`DISPLAY_PRODUCT_CODE` to exactly one valid Omie provider product. This closes
only the within-Omie edge. The 1,352 Mercado Livre seller-SKU equality relations
remain candidate-only; no explicit cross-system confirmation exists and no
transitive promotion occurred.

Transaction-identity regression metrics remained 5 exact, 10 candidate, 2
ambiguous, 0 conflict, 16 unresolved, and 15.15% coverage under unchanged policy
`MGI_GENESIS_IDENTITY_V1`.

## Verification

- focused Omie provider-composition and authenticated activation-route tests;
- TASK-0149 adapter parsing, missing/zero `nCMC`, typed identifiers, provider
  invocation, failure, pagination, and credential tests;
- PostgreSQL page/progress durability, replay idempotency, rollback,
  organization/connection isolation, product-cost persistence, and catalog
  reader tests;
- within-Omie exact, ambiguity, contradiction, cross-kind, unknown-legacy, and
  no-transitive-promotion tests;
- TASK-0165H/I API and domain regressions;
- full repository build and `git diff --check`.

## Preserved boundaries

No Omie write, Mercado Livre write, invented value, currency inference,
cross-kind identity inference, cross-system identity confirmation, Economic
Truth promotion, historical rewrite, or authority widening is introduced.
