# TASK-0090 - Marketplace Economic Truth

## Implemented boundary

- Added the pure `io.flooow.marketplace.operations.economics` domain inside the
  existing `applications:marketplace-operations` module.
- Added organization-owned order and component identities, open bounded
  marketplace/source identities, exact currency-aware money, source
  provenance, explicit economic direction, evidence quality, and coverage.
- Added duplicate source-fact protection, canonical immutable component order,
  and strict ownership/currency/coverage validation.
- Added a deterministic calculator with complete/incomplete results, exact
  breakdown, contribution, typed contribution margin, policy version,
  provenance, and truth quality.

## Safety properties

- The economics package contains no Kernel reference; a bytecode boundary test
  enforces this property.
- Missing and partial facts cannot produce authoritative totals or margin.
- Not-applicable facts produce exact zero without converting absence to zero.
- Only the calculator can construct complete or incomplete calculation results.
- Components use non-negative magnitudes and a separate addition/deduction
  direction, preserving reversals without mixed sign conventions.
- Exact decimal input accepts no binary floating-point API, exponent, implicit
  rounding, FX conversion, clock, random ID, connector, persistence, or AI.
- Domain aggregate rendering redacts organizational, commercial, monetary, and
  provenance values.
- No file under `platform/foundation/kernel` changed.

## Acceptance result

The confirmed Mercado Livre fixture reconstructs:

```text
gross revenue          299.90 BRL
marketplace fees        41.99 BRL
shipping                18.40 BRL
advertising              7.20 BRL
taxes                   24.30 BRL
product cost           143.20 BRL
contribution            64.81 BRL
contribution margin      0.21610537
truth quality           CONFIRMED
```

## Verification

- `:applications:marketplace-operations:test` - passed locally, including 17
  new economic truth tests and 23 existing module tests.
- `build -x :applications:marketplace-operations-persistence-postgres:test` -
  passed locally for every module not requiring Docker.
- The complete build reached the existing PostgreSQL Testcontainers suite; its
  40 tests could not start because Docker is unavailable in the desktop
  environment. GitHub CI remains the required full validation.
- `git diff --check` and static boundary scans - passed locally.

The implementation is production-inactive and introduces no live marketplace
data, persistence, API, recommendation, action, or Kernel integration.
