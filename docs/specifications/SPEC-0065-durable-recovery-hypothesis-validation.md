# SPEC-0065: Durable Recovery Hypothesis and Recoverability Validation MVP

Status: Accepted

`RecoveryHypothesis` is derived from valid systemic signals and retains signal,
case and evidence lineage, policy version, category/stage, currency, observed
amount, optional explicit potential amount, identity state, validation and
revision. Its deterministic identity is organization + policy + sorted signal
IDs. Replay is unchanged; material lineage/amount/identity/policy changes revise.

`RecoverabilityValidator` is deterministic and versioned. It checks identity,
evidence completeness, policy applicability, eligibility window, duplicate risk,
currency and stage consistency. Missing rule or amount produces `INSUFFICIENT`
or `BLOCKED`, never an inferred value. Validated amount is constructed only
from the explicit policy amount and is not authorization.

States are `OPEN`, `BLOCKED_IDENTITY`, `READY_FOR_VALIDATION`, `VALIDATED` and
`REJECTED`; validation is `INSUFFICIENT`, `BLOCKED`, `VALIDATED` or `REJECTED`.
The contract stops before proposal, authority or execution.

No persistence/API/UI/provider adapter is added in this MVP because the
authorized Omie transaction evidence source and the read-only recovery
visibility composition are the next bounded seams; adding storage before that
source would create an ungrounded recovery record. The domain seam is ready for
the next integration slice.
