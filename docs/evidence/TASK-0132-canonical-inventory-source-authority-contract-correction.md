# TASK-0132: Canonical Inventory Source Authority Contract Correction

Status: Contract corrected; no production implementation

Date: 2026-08-20

## Validation finding

The first TASK-0132 compile attempt used the dependency boundary originally
written in SPEC-0038:

```text
inventory-source-authority
  -> inventory-measure-selection only
```

Compilation failed before any test ran. `SelectedCanonicalInventoryMeasure`
publicly exposes `OrganizationId`, `IntegrationConnectionId`, and
`InventoryMappingTarget`, while `inventory-measure-selection` correctly keeps
their owning modules as Gradle `implementation` dependencies. Those types are
therefore not exported to a consumer compile classpath.

This is an accepted module-encapsulation rule, not a reason to change existing
modules to `api`, duplicate identities, use reflection, or erase domain types.

## Correction

SPEC-0038 now authorizes the exact direct production dependencies needed by
the accepted candidate API:

```text
organization-context
integration-control-plane
inventory-identity-mapping
inventory-measure-selection
```

Focused fixtures may additionally use canonical-observation and
source-acceptance because constructing the accepted candidate requires their
typed identifiers.

The new module must enforce this allow-list. Marketplace Operations,
persistence, API, connector runtime, and Kernel dependencies remain forbidden.

## Scope preservation

No source-authority semantic changed. The correction adds no freshness,
health, priority, current-state, availability, confidence, ATP, persistence,
runtime, action, or Kernel behavior.

The aborted implementation files and module registration were removed. This
task contains only the contract correction and evidence.

## Authorization outcome

TASK-0133 is now authorized to implement ADR-0038 / corrected SPEC-0038 under
the explicit dependency allow-list. No broader Trust capability is authorized.
