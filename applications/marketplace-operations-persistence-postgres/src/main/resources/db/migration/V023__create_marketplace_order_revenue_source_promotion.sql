CREATE TABLE marketplace_order_revenue_source_promotion (
    organization_id uuid NOT NULL,
    source_connection_id uuid NOT NULL,
    source_capability text NOT NULL CHECK (
        source_capability = 'marketplace-economic.order-source'
    ),
    source_input_progress_version bigint NOT NULL CHECK (
        source_input_progress_version >= 0
    ),
    source_record_ordinal integer NOT NULL CHECK (
        source_record_ordinal BETWEEN 0 AND 999
    ),
    marketplace_order_id uuid NOT NULL,
    outcome text NOT NULL CHECK (
        outcome IN ('PROMOTED', 'DUPLICATE', 'IDENTITY_CONFLICT', 'EVIDENCE_CONFLICT')
    ),
    promoted_at timestamptz(6) NOT NULL,
    PRIMARY KEY (
        organization_id,
        source_connection_id,
        source_capability,
        source_input_progress_version,
        source_record_ordinal
    ),
    FOREIGN KEY (
        organization_id,
        source_connection_id,
        source_capability,
        source_input_progress_version,
        source_record_ordinal
    ) REFERENCES integration_mercado_livre_order_source_observation (
        organization_id,
        connection_id,
        capability,
        input_progress_version,
        record_ordinal
    ),
    FOREIGN KEY (organization_id, marketplace_order_id)
        REFERENCES marketplace_order_identity_registry (
            organization_id,
            marketplace_order_id
        )
);

CREATE FUNCTION validate_marketplace_order_revenue_source_promotion()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
    source_currency char(3);
    source_external_order_id text;
    source_date_closed timestamptz;
    identity_currency char(3);
    identity_external_order_id text;
    identity_marketplace_key text;
BEGIN
    SELECT source.currency, source.external_order_ref, source.date_closed
      INTO source_currency, source_external_order_id, source_date_closed
      FROM integration_mercado_livre_order_source_observation source
     WHERE source.organization_id = NEW.organization_id
       AND source.connection_id = NEW.source_connection_id
       AND source.capability = NEW.source_capability
       AND source.input_progress_version = NEW.source_input_progress_version
       AND source.record_ordinal = NEW.source_record_ordinal;

    SELECT identity.currency, identity.external_order_id, identity.marketplace_key
      INTO identity_currency, identity_external_order_id, identity_marketplace_key
      FROM marketplace_order_identity_registry identity
     WHERE identity.organization_id = NEW.organization_id
       AND identity.marketplace_order_id = NEW.marketplace_order_id;

    IF source_external_order_id IS NULL OR identity_external_order_id IS NULL OR
       identity_marketplace_key <> 'mercado-livre' OR
       source_external_order_id <> identity_external_order_id THEN
        RAISE EXCEPTION 'marketplace order revenue promotion identity mismatch';
    END IF;

    IF source_date_closed IS NULL THEN
        RAISE EXCEPTION 'marketplace order revenue source is not closed';
    END IF;

    IF NEW.outcome = 'IDENTITY_CONFLICT' THEN
        IF source_currency = identity_currency THEN
            RAISE EXCEPTION 'marketplace order revenue identity conflict is not proven';
        END IF;
    ELSIF source_currency <> identity_currency THEN
        RAISE EXCEPTION 'marketplace order revenue promotion currency mismatch';
    END IF;

    RETURN NEW;
END;
$$;

CREATE TRIGGER validate_marketplace_order_revenue_source_promotion_insert
BEFORE INSERT ON marketplace_order_revenue_source_promotion
FOR EACH ROW
EXECUTE FUNCTION validate_marketplace_order_revenue_source_promotion();

CREATE TRIGGER protect_revenue_source_promotion_mutation
BEFORE UPDATE OR DELETE ON marketplace_order_revenue_source_promotion
FOR EACH ROW
EXECUTE FUNCTION reject_marketplace_order_promotion_mutation();

CREATE INDEX marketplace_order_revenue_source_promotion_order_idx
    ON marketplace_order_revenue_source_promotion (
        organization_id,
        marketplace_order_id,
        promoted_at
    );