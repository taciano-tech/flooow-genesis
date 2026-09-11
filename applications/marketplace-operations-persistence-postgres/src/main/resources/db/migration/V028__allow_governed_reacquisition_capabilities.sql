DO $$
DECLARE c_name text; c_table text;
BEGIN
    FOR c_name, c_table IN
        SELECT conname, relname
        FROM pg_constraint JOIN pg_class ON pg_class.oid = conrelid
        WHERE relname IN ('integration_mercado_livre_order_source_observation',
                          'integration_mercado_livre_order_item_source_observation',
                          'integration_mercado_livre_payment_source_observation')
          AND contype = 'c'
          AND pg_get_constraintdef(pg_constraint.oid) LIKE '%marketplace-economic.order-source%'
    LOOP
        EXECUTE format('ALTER TABLE %I DROP CONSTRAINT %I', c_table, c_name);
    END LOOP;
END $$;

ALTER TABLE integration_mercado_livre_order_source_observation
    ADD CONSTRAINT integration_mercado_livre_order_source_reacq_capability_check
    CHECK (capability IN ('marketplace-economic.order-source', 'marketplace-economic.order-source.reacquisition-v1'));
ALTER TABLE integration_mercado_livre_order_item_source_observation
    ADD CONSTRAINT integration_mercado_livre_order_item_source_reacq_capability_check
    CHECK (capability IN ('marketplace-economic.order-source', 'marketplace-economic.order-source.reacquisition-v1'));
ALTER TABLE integration_mercado_livre_payment_source_observation
    ADD CONSTRAINT integration_mercado_livre_payment_source_reacq_capability_check
    CHECK (capability IN ('marketplace-economic.order-source', 'marketplace-economic.order-source.reacquisition-v1'));
