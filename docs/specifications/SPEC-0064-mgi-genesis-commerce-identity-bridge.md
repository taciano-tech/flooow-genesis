# SPEC-0064: MGI → Genesis Commerce Identity Bridge

Status: Accepted

## Contract

Every relation is organization-scoped and carries source/target system,
typed identity value, evidence references, provenance, policy version,
assessment time and confirmation state. Evidence values are bounded and never
rendered through `toString`.

The deterministic matcher accepts Mercado Livre transaction evidence and
Omie sales-order evidence already acquired by an authorized read-only seam.
An exact normalized `codigo_pedido_integracao` or `numero_pedido_cliente`
reference equal to the Mercado Livre `order_id` is the only automatic exact
assessment. Multiple exact records are `CONFLICT`.

Candidate generation requires an exact seller SKU plus at least one explicit
additional policy evidence (quantity, exact amount within the versioned
tolerance, same/near date). Value and date alone cannot create a candidate.
Candidates never become confirmed automatically. Product candidates are
separate from transaction assessment.

`CommerceIdentityMatchState` is exactly `EXACT_CONFIRMED`, `CANDIDATE`,
`AMBIGUOUS`, `CONFLICT` or `UNRESOLVED`. Missing values remain missing; zero is
observed only when the source explicitly supplies zero.

## Durability and live bridge decision

No new mapping table or provider route is added in this slice. Persisting a
candidate before governed confirmation would turn a heuristic into identity;
the current Genesis runtime also has no Omie sales-order evidence committer.
The existing read-only provider boundaries remain unchanged. TASK-0162
therefore delivers the deterministic application seam and contract; a future
separately governed slice may add Omie sales-order acquisition and durable
confirmed mappings with organization + source/target identity uniqueness,
revision guards and conflict history.

No request may choose `organizationId` or connection scope. No write-back to
Mercado Livre or Omie exists.

## MGI decision matrix

| MGI capability | Genesis decision | Rationale |
|---|---|---|
| exact external-reference match | ADOPT | strongest transaction evidence |
| SKU + quantity/value/date candidates | ADAPT | typed, versioned, organization-safe; review-only |
| ambiguity/conflict states | ADOPT | never silently choose a winner |
| `/v1/identity/*` API shape | REJECT | Genesis API needs an authorized evidence source first |
| SQLite/local baseline persistence | REJECT | no organization boundary or Genesis durability |
| automatic mapping persistence | REJECT | confirmation is an authority boundary |
| MGI secrets/tokens/provider state | REJECT | credentials remain control-plane scoped |

Commerce identity cannot mutate Economic Truth, financial evidence,
reconciliation cases, systemic signals or recovery hypotheses.
