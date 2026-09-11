# ADR-0020: Marketplace Economic Truth Boundary

Status: Accepted

Date: 2026-08-13

## Context

Flooow Genesis is an Organizational Computing platform. Marketplace
Intelligence is one business vertical that consumes Genesis capabilities; it is
not a Kernel vocabulary.

The existing `applications:marketplace-operations` boundary already validates
the Kernel with marketplace inventory scenarios. The next product thesis needs
a different foundation: given one normalized marketplace order, reconstruct its
economic result and explain every component used.

The unsafe shortcuts would be to:

- put marketplace, order, fee, or SKU concepts in the Kernel;
- calculate with binary floating point;
- treat an absent component as confirmed zero;
- count the same external economic fact twice under different internal IDs;
- encode costs as sometimes negative and sometimes positive;
- identify marketplaces with an enum that must change for every connector;
- call an incomplete calculation economic truth;
- couple the domain to Mercado Livre payloads, databases, APIs, or AI.

## Decision

Introduce a production-inactive Marketplace Economic Truth domain inside the
existing `applications:marketplace-operations` module, under the
`io.flooow.marketplace.operations.economics` package.

It is a pure, deterministic domain model over already normalized inputs. It
opens no clock, network, database, file, credential, random source, connector,
or model. It imports no Kernel type.

No new Gradle module is needed. The existing marketplace application boundary
is the correct ownership boundary.

## Organization ownership

Every order and component is owned by one trusted `OrganizationId`. A component
may participate only in an order with the same organization and order ID.

Organization authority is input to the pure domain. It is not read from a
header, environment, global context, or external payload.

## Open marketplace identity

Marketplace identity is a bounded canonical key such as:

```text
mercado-livre
amazon-br
shopee-br
```

It is not a closed enum. Adding one of the expected marketplace connectors must
not require changing the economic calculator.

The key names the normalized commercial channel, not a credential, provider
adapter, endpoint, or API version.

## Monetary representation

Marketplace money uses an immutable exact `BigDecimal` plus a canonical
three-letter currency code intended for ISO 4217 values. Input is canonical
decimal text, never `Double` or `Float`. Values are bounded and normalized so
equality is numeric and deterministic. Registry validation remains an input
normalization responsibility in this slice.

An input component carries a non-negative magnitude. Economic direction is a
separate controlled value:

```text
ADDITION
DEDUCTION
```

This avoids mixed sign conventions while allowing later credits, refunds, fee
reversals, and adjustments to retain their economic type. Result totals may be
negative.

No FX conversion exists. One order and all its components must have one exact
currency or construction fails.

## Economic component

The controlled component types are:

```text
REVENUE
MARKETPLACE_COMMISSION
MARKETPLACE_FEE
SHIPPING
ADVERTISING
TAX
PRODUCT_COST
FINANCIAL_COST
OTHER_ADJUSTMENT
```

Each component contains exact organization/order ownership, internal component
ID, type, direction, magnitude, source, external reference state, occurrence
time, and evidence quality.

Evidence quality is only:

```text
CONFIRMED
ESTIMATED
```

It is not a confidence score. Missing is not a synthetic zero-valued component.

## Provenance and duplicate protection

Source provenance contains a controlled source kind and an open bounded system
key:

```text
MARKETPLACE
ERP
MANUAL
CALCULATED
```

An external reference is either a bounded present value or an explicit absence
reason. Marketplace and ERP facts require a present reference. Manual and
calculated facts may explicitly declare internal origin.

Within one order:

- internal component IDs are unique;
- present source fact keys are unique by source kind, system key, external
  reference, and component type;
- every component must cite the same organization and order.

This is domain duplicate protection, not persistence idempotency.

## Coverage is not zero

For every controlled component type the normalized order declares exactly one
coverage state:

```text
COMPLETE
NOT_APPLICABLE
PARTIAL
MISSING
```

`COMPLETE` requires at least one component and declares that the supplied set is
complete. `PARTIAL` requires at least one component and declares known missing
facts. `NOT_APPLICABLE` and `MISSING` require none. An explicit zero amount is a
complete present fact; absence never becomes zero implicitly.

Any `PARTIAL` or `MISSING` type produces a controlled incomplete result.
Incomplete output may explain the unavailable coverage and preserve supplied
component provenance, but it is not an `EconomicResult` and exposes no
authoritative contribution or margin.

## Deterministic calculation

A complete calculation:

1. validates ownership, currency, coverage, provenance, and duplicates;
2. orders provenance canonically by component ID;
3. nets additions and deductions by economic type;
4. calculates the accepted breakdown;
5. derives contribution;
6. derives margin only when gross revenue is positive;
7. records an explicit calculation policy version.

The result exposes:

```text
grossRevenue
totalMarketplaceFees
totalShipping
totalAdvertising
totalTaxes
totalProductCost
totalFinancialCost
totalOtherAdjustments
contribution
contributionMargin
components
calculationPolicyVersion
truthQuality
```

Gross revenue is revenue additions minus revenue deductions. Each cost total is
deductions minus additions. Contribution is gross revenue minus every net cost
total.

Margin is contribution divided by gross revenue at scale 8 with `HALF_EVEN`.
Non-positive gross revenue returns a typed undefined margin; it does not divide
by zero or manufacture zero percent.

Truth quality is `CONFIRMED` when all present components are confirmed and
`ESTIMATED` when at least one is estimated. It is independent from future model
or decision confidence.

## Privacy and rendering

IDs, marketplace keys, order references, system keys, external references,
money, component values, provenance, organization, and timestamps are not
rendered by domain `toString` methods. Canonical wrappers are bounded, NFC
normalized where textual, and render redacted or internal placeholders.

The domain exposes structured values deliberately to authorized in-process
callers; safe rendering does not replace caller authorization.

## No Genesis integration yet

The economic domain does not manufacture `Observation`, `Evidence`,
`Hypothesis`, `Evaluation`, `Judgment`, or `Decision` objects merely to show a
Kernel integration. A later accepted experiment may translate stable economic
truth into public Genesis contracts.

## No infrastructure or intelligence

This boundary includes no:

- marketplace payload or connector;
- persistence, repository, migration, ledger, or event;
- API, JSON, controller, dashboard, or UI formatting;
- commission, tax, freight, advertising, or FX rule engine;
- pricing, promotion, forecast, recommendation, simulation, or optimization;
- LLM, ML, expert, agent, action, reconciliation, outcome, or learning;
- Kernel modification.

## Consequences

### Positive

- the first Marketplace Intelligence epic starts from deterministic truth;
- incomplete evidence cannot masquerade as a zero cost;
- every result is organization-scoped, currency-safe, and traceable;
- the model supports many marketplaces without a growing enum;
- reversals and credits remain explicit without mixed amount signs;
- later economic rules can cite a calculation version;
- Marketplace vocabulary remains outside the Kernel.

### Negative

- normalized callers must declare coverage explicitly;
- a complete result needs more than a list of amounts;
- persistence and live Mercado Livre data remain unavailable;
- rule history is identified but not persisted;
- source normalization remains a later responsibility.

## Alternatives considered

### Closed marketplace enum

Rejected because connector growth would change the calculator's domain code.

### Missing component means zero

Rejected because unavailable data and non-applicability have different economic
meaning.

### Signed input amounts

Rejected because sign meaning would be duplicated between type and value and is
easy to mix across providers.

### Reuse inventory quantity as money

Rejected because quantity has no currency and a different bound and meaning.

### Add money to the Kernel

Rejected because this slice demonstrates only a marketplace-domain need, not a
validated universal organizational primitive.

### Create a new economics Gradle module

Rejected because the accepted marketplace application boundary already owns
the domain and no technical isolation constraint requires another module.

### Implement persistence first

Rejected because stable domain invariants must exist before storage design.

## Authorization

This ADR alone authorizes no implementation. SPEC-0020 may authorize only the
pure Marketplace Economic Truth domain and tests in the existing marketplace
application. It authorizes no Kernel, integration, persistence, API, runtime,
event, recommendation, AI, or external action.
