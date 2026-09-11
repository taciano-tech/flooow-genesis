# TASK-0165H evidence — explicit Mercado Livre order reference recognition

Production discovery found four exact intersections between observed Mercado
Livre order IDs and Omie provider-native references. Previously the Omie
projection intentionally supplied no declared marketplace IDs, so the existing
bridge could not confirm those relations.

This task adds a pure representation-only resolver. A provider reference is
declared as a Mercado Livre order ID only when its trimmed value, with at most
one leading `#` removed, belongs to the observed Mercado Livre order set for
the same organization/evaluation. Embedded, prefixed, suffixed, guessed,
amount-based, date-based and SKU-based values are rejected.

Historical/reacquired Omie revisions are aggregated by stable provider order
identity plus integration/customer reference before evaluation. Distinct Omie
orders that declare the same marketplace order remain separate and therefore
retain the existing conflict semantics. No V026/V028 row is changed, no
mapping is persisted, and CommerceIdentityBridge policy is unchanged.
