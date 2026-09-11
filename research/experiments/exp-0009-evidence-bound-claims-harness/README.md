# EXP-0009 — Evidence-Bound Intelligence Claims

Purpose: harden the semantic boundary discovered by EXP-0008.

EXP-0008 proved that governed input does not guarantee semantically grounded model output. The live model invented an industry average and labor-cost context that were not present in governed evidence.

EXP-0009 moves the trust boundary away from free-form prose and into structured claims that are classified and validated deterministically.

## Core rule

A model may draft claims. Flooow decides their epistemic status.

The model does not decide what is Economic Truth.

## Claim kinds

- `OBSERVATION` — must match a governed `factKey`, value and evidence reference.
- `INFERENCE` — must link to known governed evidence, but remains non-canonical.
- `HYPOTHESIS` — may introduce something to investigate, but remains explicitly hypothetical.
- `ASSUMPTION` — explicitly non-evidentiary.

## Deterministic gates

- unknown evidence reference -> fail;
- observation without evidence -> unsupported;
- observation with mismatched fact/value -> unsupported;
- invented industry-average fact -> unsupported;
- invented labor-cost fact -> unsupported;
- hypothesis -> allowed only as hypothesis;
- no validated claim is executable or canonical truth.

## CI-safe test

```powershell
.\gradlew.bat -p research\experiments\exp-0009-evidence-bound-claims-harness test
```

## Optional local Ollama test

```powershell
$env:FLOOOW_OLLAMA_TEST="true"
.\gradlew.bat -p research\experiments\exp-0009-evidence-bound-claims-harness test --tests "*OllamaEvidenceBoundClaimsManualTest*"
Remove-Item Env:FLOOOW_OLLAMA_TEST
```

This remains research-only. No production integration is introduced.
