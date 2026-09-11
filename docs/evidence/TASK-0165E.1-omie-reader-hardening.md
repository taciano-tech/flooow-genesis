# TASK-0165E.1 Evidence — Real Omie Identity Reader Hardening

The first production Omie pull contained 132 durable V026 rows. 117 rows had
an identity-bearing integration reference; 15 had no integration reference,
customer order reference, or product reference. The reader preserves all
persisted evidence and projects only rows satisfying the existing
`OmieSalesOrderEvidence` invariant. Non-evaluable rows are counted as skipped
projection rows and are never fabricated, deleted, or mutated.

The recompute response reports persisted, identity-evaluable, and non-evaluable
row counts without exposing provider payloads. With six Mercado Livre records
and no cross-system order/product references, six unresolved relationships are
expected. Missing values remain missing and no economic or identity authority
is introduced.
