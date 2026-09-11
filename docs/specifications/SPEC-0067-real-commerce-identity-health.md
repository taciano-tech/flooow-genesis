# SPEC-0067 — Real Commerce Identity Health

Input is bounded, read-only ML and Omie transaction evidence. Output is a relation assessment and aggregate health: inspected counts, exact/candidate/ambiguous/conflict/unresolved counts, exact coverage, policy version, evaluation window, timestamp and explainable evidence/reason buckets. Organization is taken from the authenticated principal for API reads; browser requests never provide organizationId.

The flow has no confirmation, mapping-write, provider mutation, recovery or authority route. Missing is distinct from zero. The health API returns `available:false` when no real evaluation has been performed.
