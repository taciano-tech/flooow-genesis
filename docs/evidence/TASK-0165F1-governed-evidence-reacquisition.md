# TASK-0165F.1 evidence — governed evidence reacquisition

Production baseline discovered after TASK-0165F:

- Mercado Livre: 6 persisted rows, 0 seller-SKU rows; normal source failed
  before committing a page.
- Omie: 132 persisted rows, 117 identity-evaluable, 15 non-evaluable; normal
  refresh returned `alreadyCommittedPages=1` and no records.

The normal connector progress namespace was exhausted/committed, so replaying
normal refresh could not execute a new page commit. This task adds explicit
versioned reacquisition capabilities. They use independent progress rows and
page keys, preserve all existing V021/V026 rows, and allow corrected parser
observations to coexist. Repeated requests in the same generation are
idempotent.

The Mercado Livre source response now exposes only typed safe failure metadata
(`failureCategory` and `retryable`) so future authentication/provider/schema
failures can be diagnosed without payload or credential leakage. The historical
failure did not retain a category beyond `FAILED`; the first reacquisition
response is the authoritative diagnostic.

No provider write, Economic Truth mutation, mapping confirmation or recovery
authority is introduced.
