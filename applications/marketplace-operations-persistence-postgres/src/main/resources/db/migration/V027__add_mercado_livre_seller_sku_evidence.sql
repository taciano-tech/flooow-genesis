ALTER TABLE integration_mercado_livre_order_item_source_observation
    ADD COLUMN seller_sku text NULL;

ALTER TABLE integration_mercado_livre_order_item_source_observation
    ADD CONSTRAINT integration_mercado_livre_order_item_seller_sku_check CHECK (
        seller_sku IS NULL OR (
            octet_length(seller_sku) BETWEEN 1 AND 128 AND
            seller_sku = btrim(seller_sku) AND seller_sku !~ '[[:cntrl:]]'
        )
    );
