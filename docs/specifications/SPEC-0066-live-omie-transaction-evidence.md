# SPEC-0066 — Live Omie Transaction Evidence

The Omie adapter calls only `https://app.omie.com.br/api/v1/produtos/pedido/` with `ListarPedidos`, using secure runtime credentials. Pages are numbered and committed through the existing connector runtime and PostgreSQL progress protocol. Normalized records preserve missing values as missing and retain deterministic source fingerprints. Replays validate the existing page and never silently overwrite it.

The output is `OmieTransactionEvidenceRecord`; it is source evidence, not truth. No provider mutation, marketplace mutation, recovery, claim, refund, dispute, authority or autonomous action is authorized. Commerce identity assessment remains governed by TASK-0162 and is not auto-confirmed by ingestion.
