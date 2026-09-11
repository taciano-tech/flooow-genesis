# Marketplace Operations

Experimental application boundary used to validate the Flooow Kernel against
real marketplace operations without introducing business concepts into the
foundation.

The first vertical slice evaluates whether an inventory shortage can prevent a
SKU from reaching a commercial sales goal. Inputs are deterministic and no
external integrations, persistence, or autonomous execution are included.

The EXP-0001 Track B workflow experiment adds domain-owned immutable inventory
snapshots, commands, occurrences, and typed transition results. It consumes
existing Kernel language and `Action`/`Outcome` contracts without introducing
universal `Entity`, `State`, `Event`, or `Process` abstractions. Rejected
transitions preserve the input snapshot, and all executions remain local and
deterministic.
