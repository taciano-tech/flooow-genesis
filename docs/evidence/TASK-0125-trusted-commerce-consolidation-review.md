# TASK-0125: Trusted Commerce Consolidation Review

Status: Target direction consolidated; no production implementation authorized

Date: 2026-08-20

## Repository audit

Inspected canonical `main` at `54d1545`, recent merged PRs through #119, open
PR state, the protected Trust and Policy/Listing roadmaps, the Marketplace
Intelligence Operating Model, and the accepted Marketplace Economic Truth,
Financial Trace Ledger, Financial Reconciliation, inventory, and pricing
contracts. No open PR existed at the audit checkpoint.

The active local TASK-0123 implementation was kept separate from this
strategic consolidation. No Kernel, production code, API, schema, provider,
fiscal rule, automation, or dependency changed.

## Reuse decision

Most proposed capabilities were already protected by TASK-0124: Operational
Truth, Inventory Confidence, Safe ATP, graceful degradation, Supplier
Reliability, Seller Reputation Protection, Flooow-owned Fiscal Orchestration,
specialist-reviewed consignation, controlled sandbox execution, and future OFI
integration.

The proposed Settlement Ledger is not a new foundation. The accepted Financial
Trace Ledger already retains immutable expected and actual stages through
settlement, payment account, and bank; Financial Reconciliation already
calculates pending, partial, divergent, and fully reconciled states. Future
participant payable and settlement-grouping semantics must extend those
contracts rather than duplicate them.

## Material refinements accepted

- separate Fiscal Eligibility (what policy permits) from Fiscal Truth (what
  documents and events occurred);
- keep Economic, Operational, Marketplace, Fiscal, and Financial evidence
  boundaries distinct without creating generic Kernel `Truth` primitives;
- record that financial flow never defines commercial, operational, or fiscal
  reality;
- prefer atomic title, custody, inventory-recognition, risk-bearing,
  sale-right, reservation-holder, and fiscal-seller facts over one
  `economicOwner` assertion;
- sequence Seller Entitlement after Inventory Confidence, Safe ATP, and an
  explicit allocation policy;
- retain future Product Fiscal Profile and Fiscal Policy Registry direction
  with evidence, applicability, effective dates, policy versions, reasons, and
  replaceable provider references;
- keep shared cross-dock and dedicated fulfillment as separately eligible
  workflows;
- constrain the first specialist-approved pilot to one importer, 3PL, seller,
  SKU, marketplace, operating mode, route, and fiscal policy;
- require both happy-path and material exception evidence before pilot scale;
- retain detailed order and fulfillment timestamps as future Supplier
  Reliability and Seller Reputation inputs.

## Explicit non-decisions

This review does not approve a CFOP, CST, NCM, tax treatment, venda-a-ordem
classification, consignation model, generic tax engine, fiscal provider,
interstate expansion, Full/FBA/Shopee Fulfillment workflow, escrow, split,
participant payment, autonomous inventory publication, or irreversible fiscal
execution.

## Sequence outcome

The accepted repository sequence remains authoritative. The consolidation does
not interrupt deterministic Marketplace Pricing. When inventory work resumes,
the smallest visible dependency remains source authority/health/freshness,
followed by current-state selection, operational reservations and unconfirmed
demand, Inventory Confidence, and Safe ATP. Later fiscal, entitlement,
allocation, sandbox, fulfillment, OFI, and automation work must be derived from
the then-current dependency graph rather than this target document alone.
