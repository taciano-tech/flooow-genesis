# EXP-0008 — Intelligence Boundary Harness

Purpose: prove a Flooow-owned intelligence boundary before any production integration.

## Invariants

- Agent/model output is never canonical Economic Truth.
- Model output has no autonomous execution authority.
- The model receives governed read-only context, not repositories or persistence internals.
- Organization mismatch is rejected before tool/model access.
- Governed read tool is invoked exactly once by the orchestrator.
- Provenance/freshness travels with the observation.
- CI does not require Ollama.

## CI-safe test

```powershell
.\gradlew.bat -p research\experiments\exp-0008-intelligence-boundary-harness test
```

## Local Ollama test

```powershell
$env:FLOOOW_OLLAMA_TEST="true"
.\gradlew.bat -p research\experiments\exp-0008-intelligence-boundary-harness test --tests "*LangChain4jOllamaManualTest*"
Remove-Item Env:FLOOOW_OLLAMA_TEST
```

This experiment is intentionally isolated. Do not wire it into production modules until an ADR/specification accepts the boundary.
