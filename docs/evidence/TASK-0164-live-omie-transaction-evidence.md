# TASK-0164 Evidence

MGI archaeology confirmed `POST https://app.omie.com.br/api/v1/produtos/pedido/`, method `ListarPedidos`, numbered `pagina`/`registros_por_pagina` pagination, and stable fields `codigo_pedido`, `codigo_pedido_integracao`, `numero_pedido_cliente`, product codes, quantities, dates, status and totals. Genesis adopts the endpoint and read-only pagination; adapts normalization and durable page replay to Genesis contracts; rejects MGI auto-persistence of identity suggestions and all provider writes.

The implementation registers a typed Omie transaction connector and additive V026 PostgreSQL evidence table. Real-data evaluation is deferred to TASK-0165 when secure Omie and Mercado Livre evidence can be evaluated together.
