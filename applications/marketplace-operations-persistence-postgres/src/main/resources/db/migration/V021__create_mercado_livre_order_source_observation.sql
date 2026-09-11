CREATE TABLE integration_mercado_livre_order_source_observation (
    organization_id uuid NOT NULL,
    connection_id uuid NOT NULL,
    capability text NOT NULL CHECK (capability = 'marketplace-economic.order-source'),
    input_progress_version bigint NOT NULL CHECK (input_progress_version >= 0),
    record_ordinal integer NOT NULL CHECK (record_ordinal BETWEEN 0 AND 999),
    external_order_ref text NOT NULL CHECK (
        octet_length(external_order_ref) BETWEEN 1 AND 64 AND
        external_order_ref = btrim(external_order_ref) AND
        external_order_ref !~ '[[:cntrl:]]'
    ),
    provider_status text NOT NULL CHECK (
        octet_length(provider_status) BETWEEN 1 AND 64 AND
        provider_status = btrim(provider_status) AND
        provider_status !~ '[[:cntrl:]]'
    ),
    date_created timestamptz(6) NOT NULL,
    date_last_updated timestamptz(6) NOT NULL,
    date_closed timestamptz(6) NULL,
    currency char(3) NOT NULL CHECK (currency ~ '^[A-Z]{3}$'),
    total_amount numeric(24,6) NOT NULL CHECK (
        total_amount >= 0 AND total_amount < 1000000000000000000
    ),
    paid_amount numeric(24,6) NULL CHECK (
        paid_amount IS NULL OR
        (paid_amount >= 0 AND paid_amount < 1000000000000000000)
    ),
    pack_ref text NULL CHECK (
        pack_ref IS NULL OR (
            octet_length(pack_ref) BETWEEN 1 AND 64 AND
            pack_ref = btrim(pack_ref) AND
            pack_ref !~ '[[:cntrl:]]'
        )
    ),
    shipping_ref text NULL CHECK (
        shipping_ref IS NULL OR (
            octet_length(shipping_ref) BETWEEN 1 AND 64 AND
            shipping_ref = btrim(shipping_ref) AND
            shipping_ref !~ '[[:cntrl:]]'
        )
    ),
    observed_at timestamptz(6) NOT NULL,
    PRIMARY KEY (
        organization_id,
        connection_id,
        capability,
        input_progress_version,
        record_ordinal
    ),
    FOREIGN KEY (
        organization_id,
        connection_id,
        capability,
        input_progress_version
    ) REFERENCES integration_connector_page_commit (
        organization_id,
        connection_id,
        capability,
        input_progress_version
    )
);

CREATE TABLE integration_mercado_livre_order_item_source_observation (
    organization_id uuid NOT NULL,
    connection_id uuid NOT NULL,
    capability text NOT NULL CHECK (capability = 'marketplace-economic.order-source'),
    input_progress_version bigint NOT NULL CHECK (input_progress_version >= 0),
    record_ordinal integer NOT NULL CHECK (record_ordinal BETWEEN 0 AND 999),
    item_ordinal integer NOT NULL CHECK (item_ordinal BETWEEN 0 AND 99),
    item_ref text NOT NULL CHECK (
        octet_length(item_ref) BETWEEN 1 AND 64 AND
        item_ref = btrim(item_ref) AND item_ref !~ '[[:cntrl:]]'
    ),
    variation_ref text NULL CHECK (
        variation_ref IS NULL OR (
            octet_length(variation_ref) BETWEEN 1 AND 64 AND
            variation_ref = btrim(variation_ref) AND
            variation_ref !~ '[[:cntrl:]]'
        )
    ),
    quantity numeric(24,6) NOT NULL CHECK (
        quantity > 0 AND quantity < 1000000000000000000
    ),
    unit_price numeric(24,6) NOT NULL CHECK (
        unit_price >= 0 AND unit_price < 1000000000000000000
    ),
    currency char(3) NOT NULL CHECK (currency ~ '^[A-Z]{3}$'),
    sale_fee numeric(24,6) NULL CHECK (
        sale_fee IS NULL OR
        (sale_fee >= 0 AND sale_fee < 1000000000000000000)
    ),
    gross_price numeric(24,6) NULL CHECK (
        gross_price IS NULL OR
        (gross_price >= 0 AND gross_price < 1000000000000000000)
    ),
    PRIMARY KEY (
        organization_id,
        connection_id,
        capability,
        input_progress_version,
        record_ordinal,
        item_ordinal
    ),
    FOREIGN KEY (
        organization_id,
        connection_id,
        capability,
        input_progress_version,
        record_ordinal
    ) REFERENCES integration_mercado_livre_order_source_observation (
        organization_id,
        connection_id,
        capability,
        input_progress_version,
        record_ordinal
    )
);

CREATE TABLE integration_mercado_livre_payment_source_observation (
    organization_id uuid NOT NULL,
    connection_id uuid NOT NULL,
    capability text NOT NULL CHECK (capability = 'marketplace-economic.order-source'),
    input_progress_version bigint NOT NULL CHECK (input_progress_version >= 0),
    record_ordinal integer NOT NULL CHECK (record_ordinal BETWEEN 0 AND 999),
    payment_ordinal integer NOT NULL CHECK (payment_ordinal BETWEEN 0 AND 99),
    payment_ref text NOT NULL CHECK (
        octet_length(payment_ref) BETWEEN 1 AND 64 AND
        payment_ref = btrim(payment_ref) AND payment_ref !~ '[[:cntrl:]]'
    ),
    provider_status text NOT NULL CHECK (
        octet_length(provider_status) BETWEEN 1 AND 64 AND
        provider_status = btrim(provider_status) AND
        provider_status !~ '[[:cntrl:]]'
    ),
    transaction_amount numeric(24,6) NOT NULL CHECK (
        transaction_amount >= 0 AND
        transaction_amount < 1000000000000000000
    ),
    currency char(3) NOT NULL CHECK (currency ~ '^[A-Z]{3}$'),
    date_created timestamptz(6) NULL,
    date_last_modified timestamptz(6) NULL,
    PRIMARY KEY (
        organization_id,
        connection_id,
        capability,
        input_progress_version,
        record_ordinal,
        payment_ordinal
    ),
    FOREIGN KEY (
        organization_id,
        connection_id,
        capability,
        input_progress_version,
        record_ordinal
    ) REFERENCES integration_mercado_livre_order_source_observation (
        organization_id,
        connection_id,
        capability,
        input_progress_version,
        record_ordinal
    )
);

CREATE INDEX integration_mercado_livre_order_source_lookup_idx
    ON integration_mercado_livre_order_source_observation (
        organization_id,
        connection_id,
        external_order_ref,
        date_last_updated DESC
    );