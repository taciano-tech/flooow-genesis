# ADR-0001: Marketplace Operations Application Boundary

Status: Accepted

Date: 2026-07-27

## Context

Flooow Genesis exists to build an organizational computing platform capable
of representing, reasoning about, and orchestrating complex organizational
systems.

The first business validation context is a multi-marketplace operation that
will initially commercialize motorcycle transmission kits through channels
such as Mercado Livre, Amazon, Shopee, and additional marketplaces.

The initial operation must manage two imported containers of transmission
kits while progressively supporting more than fifty sales channels.

The business objective is not limited to reporting operational metrics.

Flooow must be capable of:

1. understanding the goals of the operation;
2. observing the current operational state;
3. identifying deviations from planned goals;
4. explaining the probable causes of those deviations;
5. anticipating future consequences;
6. evaluating possible interventions;
7. recommending the action with the best expected outcome;
8. monitoring execution;
9. verifying whether the operation returned to its planned trajectory.

The existing foundation Kernel provides universal reasoning concepts and
contracts.

It must remain independent from marketplace-specific concepts such as:

- SKU;
- listing;
- sales channel;
- inventory position;
- marketplace order;
- fulfillment center;
- advertising campaign;
- Buy Box;
- sales target;
- replenishment shipment;
- container;
- motorcycle compatibility.

A real application consumer is now required to validate the Kernel against
the business domain without introducing marketplace concepts into the
foundation.

## Decision

A new application boundary named `marketplace-operations` will be introduced
under the `applications` area of the repository.

The dependency direction will be:

    applications:marketplace-operations
                    |
                    v
        platform:foundation:kernel

The Kernel must never depend on the marketplace operations application.

The marketplace operations application will contain business capabilities
related to operating products across multiple sales channels.

Its initial vertical slice will evaluate the risk that inventory conditions
will prevent a SKU from reaching a planned commercial goal.

The first vertical slice must represent the following reasoning cycle:

    Goal
      |
      v
    Current State
      |
      v
    Deviation
      |
      v
    Probable Causes
      |
      v
    Forecast
      |
      v
    Alternatives
      |
      v
    Recommendation
      |
      v
    Expected Impact

The initial implementation will use deterministic input supplied by tests.

It will not include:

- marketplace API integrations;
- persistence;
- HTTP APIs;
- graphical interfaces;
- dashboards;
- machine learning;
- autonomous execution;
- modifications to the Kernel.

## Product Principle

Flooow must not report a relevant business deviation without also working
toward an explanation of:

1. what happened;
2. why it happened;
3. what is likely to happen if nothing changes;
4. which alternatives are available;
5. which intervention has the best expected result;
6. how that intervention contributes to returning the operation to its goal.

Early implementations may provide only part of this cycle, but their contracts
must not reduce the problem to a simple alert or isolated metric.

## Initial Business Scenario

The first scenario will represent a Red Moto transmission-kit SKU.

Example operational context:

- a commercial sales target exists for a defined period;
- units have already been sold;
- inventory is available;
- a daily sales velocity is known;
- replenishment is expected on a future date;
- current inventory may end before replenishment arrives;
- an inventory gap may compromise the planned sales target.

The application must determine, using explicit calculations:

- current stock coverage;
- projected stockout date;
- expected replenishment date;
- projected stockout duration;
- quantity potentially unavailable for sale;
- relationship between the projected shortage and the commercial goal.

The result must be explainable and must not be reduced to labels such as
"high risk" or "sales decreased".

## Architectural Rules

1. Universal concepts remain in the Kernel.
2. Marketplace business concepts remain in the application.
3. Infrastructure integrations remain outside the business domain.
4. Dependencies always point inward.
5. No Kernel abstraction will be added until a real application limitation is
   demonstrated by a failing or insufficient business use case.
6. Every recommendation must be traceable to explicit inputs and calculations.
7. Forecasts must distinguish observed facts from projections.
8. Recommendations must distinguish expected impact from guaranteed outcome.
9. The first implementation must remain deterministic and reproducible.
10. The business language must remain independent of any single marketplace.

## Alternatives Considered

### Add marketplace concepts directly to the Kernel

Rejected because SKU, inventory, listings, and sales channels are not universal
foundation concepts.

### Rename the Kernel as a Business Decision Engine

Rejected because the current Kernel represents general reasoning contracts and
must remain independent from a specific commercial application.

### Continue expanding the reasoning architecture first

Rejected because builders, composite strategies, resolution policies, runtime
modules, and plugin systems currently have no real application consumer.

### Build marketplace integrations first

Rejected because connecting APIs before defining the application model would
couple the business architecture to external platforms.

### Build a dashboard-first product

Rejected because Flooow is intended to improve decision quality rather than
only display historical metrics.

## Consequences

### Positive

- The Kernel will be validated by a real business consumer.
- The business domain will evolve independently from infrastructure.
- Architectural changes will be driven by observed limitations.
- The first use case will remain directly connected to the Red Moto operation.
- Future marketplace connectors can share the same application capabilities.
- Explanations, forecasts, and recommendations can evolve incrementally.

### Negative

- The initial slice will not connect to live marketplace data.
- Some concepts may need refinement after real operational data is introduced.
- Autonomous execution will remain outside the initial scope.
- The first implementation will expose limitations instead of hiding them
  behind premature abstractions.

## Validation

This decision will be validated by the next Pull Request, which must implement
a deterministic vertical slice capable of explaining how a projected inventory
shortage can compromise a SKU sales goal.

The vertical slice must consume the existing Kernel without modifying it.

Any proposed Kernel change must identify:

1. the concrete application requirement;
2. the current Kernel limitation;
3. the failing or insufficient test;
4. the smallest universal change capable of resolving the limitation.
