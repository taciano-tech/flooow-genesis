# EXP-0003 Fixture Freeze

**Freeze version:** 1.0

**Date:** 2026-08-04

**Protocol source commit:** `2adf94cdb0e9120e259378cd41283e2877839b14`

## Purpose

Freeze implementation-independent inputs and semantic expectations for
EXP-0003 before an executable harness is written.

## Frozen Artifacts

- `evidence-relationship-input.properties`: identifiers, canonical evidence,
  relationships, scenarios, policies, domains, and controlled metadata;
- `evidence-relationship-expected.snapshot`: expected semantic directions,
  reasons, totals, validation results, invariants, reduction oracle, and
  ablation oracles.

These files are expected evidence. They are not observed output and must never
be overwritten by a test or snapshot-update mode.

Future observed output must be written to a separate artifact and compared
against the expected snapshot. Any difference is experimental evidence and
must be preserved before a fixture change is proposed.

## Canonicalization Rules

1. Scenario, evidence, relationship, and trace entries are ordered by their
   declared identifiers.
2. P2 converts each `Confidence.value` with `BigDecimal.valueOf`, sums in
   `evidenceId` order, and compares exact decimal totals without tolerance.
3. Decimal output uses plain normalized notation with at least one fractional
   digit, such as `0.0`, `0.8`, and `1.0`.
4. Sets are serialized in canonical identifier order.
5. The fixed evaluation instant is `2026-08-04T18:45:00Z`.
6. UTF-8 and LF are the canonical encoding and line ending.
7. Expected and observed snapshots are compared after line-ending
   normalization only; semantic values are not normalized after execution.

## Canonical Identifier Grammar

- evidence: `<domain>-E-<evidence-alias>`;
- observation: `<domain>-O-<evidence-alias>`;
- relationship: `<domain>-R-<scenario>-<two-digit-ordinal>`;
- primary hypothesis: `M1` or `S1` as declared in the input;
- alternative hypothesis: `M2` or `S2` as declared in the input.

`domain` is exactly `M` or `S`. Ordinals follow the relationship order frozen
for each scenario. Identifiers are case-sensitive and hyphen-delimited.

Example: the first relationship in marketplace scenario C1 is
`M-R-C1-01`, references evidence `M-E-SUPPORT_080`, and that evidence references
observation `M-O-SUPPORT_080`.

## Canonical Successful-Trace Schema

Every successful core execution is serialized as one property:

```text
core.<domain>.<scenario>.<policy>=<hypothesisId>|<direction>|<reason>|<supportTotal>|<contradictTotal>|<relationships>|<instant>
```

Each retained relationship is serialized as:

```text
<relationshipId>><evidenceId>><hypothesisId>><relationshipDirection>><confidence>
```

Multiple relationships are joined with commas in relationship-ID order. P1
totals are `NOT_APPLICABLE`; P2 totals use canonical decimal notation. No field
may be omitted. The expected snapshot expands all 24 domain/scenario/policy
core traces rather than relying on a parity assertion.

## Validation Error Vocabulary

The fixture freeze declares these experimental validation outcomes:

- `NO_RELATIONSHIPS`;
- `HYPOTHESIS_MISMATCH`;
- `EVIDENCE_NOT_FOUND`;
- `CONTRADICTORY_DUPLICATE_RELATIONSHIP`;
- `IDENTICAL_DUPLICATE_RELATIONSHIP`.

Validation errors produce no partial judgment.

## Change Control

After merge, changes require a new freeze version and an explicit rationale.
No fixture may be weakened to make a prototype pass. Production Kernel source,
test source, and executable experiment code are outside this freeze.
