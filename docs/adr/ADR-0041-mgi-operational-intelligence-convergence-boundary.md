# ADR-0041: MGI Operational Intelligence Convergence Boundary

Status: Accepted

Date: 2026-08-27

## Context

Marketplace Growth Intelligence (MGI) v0.7.6 is a working local Python
application that has validated useful operational behavior against Mercado
Livre and Omie. Its strongest recent capability is parallel evidence
resolution: marketplace shipment cost, product COGS, fiscal evidence, and Ads
identity may progress independently from ERP sales-order identity.

Flooow Genesis already owns stronger reusable foundations:

- organization isolation;
- exact decimal money and canonical currency;
- explicit complete, partial, missing, and not-applicable coverage;
- economic source provenance and external references;
- append-only financial trace and expected-versus-actual reconciliation;
- provider-neutral connector execution contracts;
- Postgres persistence and outbox boundaries;
- canonical inventory evidence and source-authority work;
- Kernel isolation from marketplace vocabulary.

MGI also contains capabilities that Genesis does not yet expose operationally:

- concrete Mercado Livre and Omie read-only integrations;
- fast Sales Intelligence list and detail projections;
- background evidence enrichment;
- operational integration-gap diagnostics;
- visible evidence progression for shipment, COGS, fiscal, and Ads identity.

Copying MGI into Genesis would duplicate economic truth, ledger, identity,
connector, persistence, and audit concepts. Rewriting MGI as one large Genesis
feature would preserve its current coupling and delay operational learning.

## Decision

MGI v0.7.6 is accepted as a validated behavioral baseline and transitional
operational reference. It is not accepted as a second canonical architecture,
a Kernel dependency, or a package to transplant wholesale.

Convergence will preserve MGI behavior through small, provider-neutral Genesis
contracts and executable acceptance scenarios. Production authority remains in
Genesis.

The boundary is:

```text
Mercado Livre / Omie provider facts
  -> provider adapters
  -> organization-scoped evidence observations
  -> durable append-only ingestion
  -> canonical economic and financial domains
  -> fast operational read projection
  -> Sales Intelligence API / UI
```

Heavy provider work remains outside the read path. List and detail requests
read a local projection and never trigger broad marketplace or ERP scans.

## Canonical ownership

| Concern | Canonical owner | MGI v0.7.6 role |
| --- | --- | --- |
| Organization context | Genesis | Missing in local prototype |
| Money and currency | Genesis `MarketplaceMoney` | Behavioral input only; Python floats are not canonical |
| Economic components and coverage | Genesis Economic Truth | Reference scenarios |
| Expected/actual financial trace | Genesis financial ledger | Reference terminology and provider evidence |
| Reconciliation | Genesis reconciliation | Reference use cases |
| Connector execution | Genesis connector runtime | Provider behavior reference |
| Durable ingestion/outbox | Genesis persistence | Not supplied by the local SQLite design |
| Sales read projection | New Genesis vertical capability | MGI UX and fast-read behavior are the baseline |
| Mercado Livre / Omie mapping | New provider adapters outside Kernel | MGI mappings are evidence, not shared domain |
| Ads item/ad-group identity | New non-financial observation contract | MGI invariant must be preserved |
| Autonomous action | Future policy/authority capability | Explicitly disabled in v0.7.6 |

## Required invariants

1. Unknown financial values remain absent or explicitly missing; they never
   become zero.
2. An authoritative zero is a known observation with provenance.
3. Independent evidence families may progress independently.
4. A later missing, failed, or empty provider response must not silently erase
   previously accepted evidence.
5. Ads identity is not Ads financial allocation.
6. No campaign or Ad Group spend is distributed to an order without an
   explicit, versioned allocation policy.
7. Economic Truth is complete only when all required component coverage is
   complete or not applicable.
8. Revenue, known operational net, contribution, and profit remain distinct.
9. Source occurrence time, Genesis observation time, commit time, and
   projection time remain distinct.
10. Projection and append-only history must be transactionally consistent or
    recoverable by deterministic replay.
11. Every durable fact and projection is organization-scoped.
12. Provider enrichment is bounded, observable, retryable, and outside the
    synchronous read path.
13. Marketplace, SKU, Ads, invoice, fee, or provider vocabulary does not enter
    the Kernel.

## Transitional use of MGI v0.7.6

The downloaded archive may be used as a read-only controlled prototype while
convergence proceeds, subject to all of these limits:

- no ERP writes;
- no Ads mutation;
- no price, inventory, order, fiscal, or financial execution;
- no claim that its SQLite projection is the Genesis system of record;
- no silent allocation of Ads spend;
- no production decision based on a component still missing;
- no import of local credentials or `.mgi` runtime data into the repository.

The archive itself is immutable audit input. Corrections must be made in a
maintained source branch or in the canonical Genesis implementation, never by
silently changing the downloaded baseline.

## Consequences

### Positive

- operational learning from MGI is retained;
- Genesis avoids a second economic truth or ledger;
- exact money, organization isolation, durability, and auditability are
  available before live automation;
- fast reads remain an architectural requirement rather than a UI accident;
- provider-specific work can advance without contaminating the Kernel.

### Cost

- provider adapters and a durable sales projection still need to be built;
- MGI behavior must be expressed as acceptance fixtures before replacement;
- the local prototype remains transitional until the canonical vertical slice
  is operational.

## Rejected alternatives

### Copy the Python application into the Genesis repository as production code

Rejected because it would create parallel economic, ledger, connector,
persistence, and identity authorities.

### Rewrite all MGI capabilities in one epic

Rejected because it would combine provider integration, ingestion, economic
truth, read projection, UI, and automation without independently testable
boundaries.

### Put marketplace evidence primitives in the Kernel

Rejected because they are vertical concepts. The Kernel remains responsible
for universal organizational computing primitives only.

### Start with dashboards or autonomous actions

Rejected because a dashboard does not establish trustworthy evidence, and
execution is unsafe before provenance, coverage, durability, reconciliation,
policy, and authority are proven.
