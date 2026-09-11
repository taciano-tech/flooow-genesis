# TASK-0165E Evidence — Durable Commerce Identity Recompute

TASK-0165E closes the durable-read gap after the first real Omie ingestion.
Organization-scoped PostgreSQL readers reconstruct Mercado Livre V021 and
Omie V026 source evidence without raw payload exposure or mutation. The
authenticated bodyless recompute endpoint evaluates those records through the
existing CommerceIdentityHealthEvaluator and makes its derived result
available to the existing health and relation reads.

Identity states remain EXACT_CONFIRMED, CANDIDATE, AMBIGUOUS, CONFLICT, and
UNRESOLVED. Missing values remain missing; item IDs are not inferred as SKUs;
no mapping confirmation, Economic Truth write, provider call, or recovery
authority is introduced. Empty organizations produce explicit zero counts
without fabricating coverage.
