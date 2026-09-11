# EXP-0011 — Multi-hop Support Graph Findings

## Hypothesis
Useful intelligence requires support chains, not only single-hop checks.

## Gate
Flooow validates the entire proof path.

## Success criteria
- direct evidence seeds the graph;
- deterministic registered rules derive intermediate propositions;
- downstream derivation succeeds only when all premises are supported;
- missing premise, unknown rule, cycle, or unsupported intermediate node prevents downstream support;
- graph edges remain auditable;
- no accepted claim becomes canonical truth or executable authority.
