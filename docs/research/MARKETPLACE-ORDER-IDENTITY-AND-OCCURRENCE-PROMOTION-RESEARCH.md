# Marketplace Order Identity and OrderOccurrence Promotion Research

Status: Research complete for TASK-0153 governance

Date: 2026-09-06

## Question

After TASK-0152 durably records Mercado Livre seller-order source observations,
what is the narrowest safe stage that may create Genesis' internal
`MarketplaceOrderId` and promote provider order timing into independent economic
evidence without turning source money into canonical economics?

## Baseline

TASK-0152 deliberately ends here:

```text
Mercado Livre /orders/search
  -> typed provider order/item/payment source record
  -> durable normalized V021 source observation
  -> durable connector progress
```

It does not create:

- `MarketplaceOrderId`;
- `MarketplaceEconomicEvidenceSubject`;
- canonical revenue;
- canonical commission;
- canonical payment settlement;
- canonical shipping cost;
- product-cost association.

That boundary remains correct.

## Existing canonical subject contract

Independent economic evidence requires:

```text
organizationId
MarketplaceOrderId
marketplace
externalOrderId
currency
```

`MarketplaceOrderId` is an internal UUID.

The durable evidence subject is immutable once created.

Current persistence does not make `(organization, marketplace, externalOrderId)`
globally unique. Therefore provider promotion needs a separately governed
identity registry rather than constructing a new UUID on every observation.

## Strong provider order identity

Mercado Livre supplies a stable external order `id`.

For this source, exact identity is:

```text
organization
+
marketplace = mercado-livre
+
externalOrderId = provider order id
```

The source connection is provenance, not business identity.

Therefore this is wrong:

```text
organization + connection + externalOrderId -> MarketplaceOrderId
```

A seller connection can be replaced/re-authorized while the marketplace order
remains the same business object.

This is also wrong:

```text
UUID = hash(provider order id)
```

Genesis internal identities remain opaque UUIDs. Provider identifiers do not
become the internal identifier algorithm.

## Governed identity allocation

Create one durable registry:

```text
(organization, marketplace, externalOrderId)
  -> exactly one MarketplaceOrderId
  -> immutable currency
  -> first durable source-observation provenance
```

Allocation may be automatic because the association is exact provider identity,
not fuzzy product/entity matching.

Concurrency rule:

```text
two workers propose two random internal UUIDs
  -> unique external identity constraint chooses one durable winner
  -> both workers resolve the same stored MarketplaceOrderId
```

No last-write-wins identity replacement.

If the same external order later appears with a different source currency, fail
closed as an identity/subject conflict.

## First canonical promotion

The first promoted canonical fact is only:

```text
MarketplaceEconomicOrderOccurrenceObservation
```

Source value:

```text
occurredAt = Mercado Livre order date_created
```

Provenance:

```text
kind = MARKETPLACE
systemKey = br.com.mercadolivre
externalReference = external order id
observedAt = durable TASK-0152 source observedAt
```

This is source-backed OrderOccurrence evidence. It does not claim that provider
clock ordering is stronger than existing evidence rules.

The current evidence aggregate already gives marketplace/ERP OrderOccurrence a
canonical source-fact identity by source kind + system key + external reference.
Equal re-observation is duplicate. Different occurrence time under the same
source identity is conflict, not silent overwrite.

## No money promotion in TASK-0153

TASK-0152 source rows include:

```text
totalAmount
paidAmount
saleFee
payment transactionAmount
```

TASK-0153 does not convert any of them into:

```text
REVENUE
MARKETPLACE_COMMISSION
MARKETPLACE_FEE
SHIPPING
settlement
tax
```

Those require separate semantic research and policy.

Identity creation and OrderOccurrence are prerequisites, not permission to infer
financial truth.

## Re-observation and correction

The same Mercado Livre order may appear in multiple source hours.

If `date_created` and source identity are unchanged:

```text
independent evidence -> Duplicate
```

If the same provider source identity later presents a different `date_created`:

```text
independent evidence -> SourceFactConflict
```

TASK-0153 records that conflict as a durable promotion outcome. It does not
automatically issue an economic-evidence correction.

Source correction/adjudication is separately governed.

## Durable per-source promotion outcome

Each V021 parent order source row needs at most one terminal promotion record for
this task:

```text
PROMOTED
DUPLICATE
IDENTITY_CONFLICT
EVIDENCE_CONFLICT
```

Temporary/infrastructure failures are not terminally marked.

A crash after evidence apply but before promotion marking is safe:

```text
retry
  -> same durable order identity
  -> evidence Duplicate
  -> terminal promotion marker
```

No exactly-once fiction is required across two repositories/transactions.

## Application boundary

Create a new provider-neutral application module:

```text
applications:marketplace-order-source-promotion
```

It owns:

- source-candidate contract;
- exact external-order identity allocation contract;
- bounded promotion orchestration;
- construction of `MarketplaceEconomicEvidenceSubject`;
- construction of marketplace OrderOccurrence evidence;
- evidence result interpretation;
- terminal promotion outcome semantics.

It does not own:

- provider HTTP;
- OAuth;
- Connector Runtime;
- Postgres/JDBC;
- scheduler;
- economic calculation;
- Sales Intelligence.

Postgres owns the V021 reader, identity registry, and terminal promotion ledger.

## Pending-source discovery

Do not add another global checkpoint.

The Postgres source store can select bounded V021 parent order rows for one
organization/connection where no TASK-0153 terminal promotion row exists.

Empty connector pages require no special promotion checkpoint.

This is a durable work-set, not an exactly-once stream.

## Evidence concurrency

Evidence promotion uses the existing
`MarketplaceIndependentEconomicEvidenceRepository`.

The application:

1. resolves/allocates the durable order identity;
2. builds the immutable evidence subject;
3. reads current evidence version;
4. applies one OrderOccurrence fact;
5. handles concurrent stale version with a small bounded re-read/retry;
6. accepts `Applied` or `Duplicate`;
7. records source-fact conflict explicitly;
8. fails closed on integrity/unavailable states.

It does not bypass or duplicate Postgres economic-evidence semantics.

## Conclusion

The safe next sequence is:

```text
TASK-0152 durable ML source observation
  -> TASK-0153 exact external-order identity registry
  -> internal MarketplaceOrderId allocation
  -> MarketplaceEconomicEvidenceSubject
  -> source-backed OrderOccurrence
  -> existing Economic Truth assembly
```

Money remains source-only until separately governed.