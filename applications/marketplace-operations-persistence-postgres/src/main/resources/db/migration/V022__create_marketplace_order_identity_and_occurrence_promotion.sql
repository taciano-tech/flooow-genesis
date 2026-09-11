CREATE TABLE marketplace_order_identity_registry (
    organization_id uuid NOT NULL REFERENCES integration_organization (organization_id),
    marketplace_key text NOT NULL CHECK (
        marketplace_key ~ '^[a-z0-9][a-z0-9.-]{0,99}$'
    ),
    external_order_id text NOT NULL CHECK (
        octet_length(external_order_id) BETWEEN 1 AND 256 AND
        external_order_id = btrim(external_order_id) AND
        external_order_id !~ '[[:cntrl:]]'
    ),
    marketplace_order_id uuid NOT NULL,
    currency char(3) NOT NULL CHECK (currency ~ '^[A-Z]{3}$'),
    allocated_at timestamptz(6) NOT NULL,
    first_source_connection_id uuid NOT NULL,
    first_source_capability text NOT NULL CHECK (
        first_source_capability = 'marketplace-economic.order-source'
    ),
    first_source_input_progress_version bigint NOT NULL CHECK (
        first_source_input_progress_version >= 0
    ),
    first_source_record_ordinal integer NOT NULL CHECK (
        first_source_record_ordinal BETWEEN 0 AND 999
    ),
    PRIMARY KEY (organization_id, marketplace_order_id),
    UNIQUE (organization_id, marketplace_key, external_order_id),
    FOREIGN KEY (
        organization_id,
        first_source_connection_id,
        first_source_capability,
        first_source_input_progress_version,
        first_source_record_ordinal
    ) REFERENCES integration_mercado_livre_order_source_observation (
        organization_id,
        connection_id,
        capability,
        input_progress_version,
        record_ordinal
    )
);

CREATE TABLE marketplace_order_occurrence_source_promotion (
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

CREATE FUNCTION validate_marketplace_order_identity_registry_source()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    IF NEW.marketplace_key <> 'mercado-livre' THEN
        RAISE EXCEPTION 'marketplace order identity source mismatch';
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM integration_mercado_livre_order_source_observation source
        WHERE source.organization_id = NEW.organization_id
          AND source.connection_id = NEW.first_source_connection_id
          AND source.capability = NEW.first_source_capability
          AND source.input_progress_version = NEW.first_source_input_progress_version
          AND source.record_ordinal = NEW.first_source_record_ordinal
          AND source.external_order_ref = NEW.external_order_id
          AND source.currency = NEW.currency
    ) THEN
        RAISE EXCEPTION 'marketplace order identity source mismatch';
    END IF;

    RETURN NEW;
END;
$$;

CREATE TRIGGER validate_marketplace_order_identity_registry_source_insert
BEFORE INSERT ON marketplace_order_identity_registry
FOR EACH ROW
EXECUTE FUNCTION validate_marketplace_order_identity_registry_source();

CREATE FUNCTION validate_marketplace_order_occurrence_source_promotion()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
    source_currency char(3);
    source_external_order_id text;
    identity_currency char(3);
    identity_external_order_id text;
    identity_marketplace_key text;
BEGIN
    SELECT source.currency, source.external_order_ref
      INTO source_currency, source_external_order_id
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
        RAISE EXCEPTION 'marketplace order promotion identity mismatch';
    END IF;

    IF NEW.outcome = 'IDENTITY_CONFLICT' THEN
        IF source_currency = identity_currency THEN
            RAISE EXCEPTION 'marketplace order identity conflict is not proven';
        END IF;
    ELSIF source_currency <> identity_currency THEN
        RAISE EXCEPTION 'marketplace order promotion currency mismatch';
    END IF;

    RETURN NEW;
END;
$$;

CREATE TRIGGER validate_marketplace_order_occurrence_source_promotion_insert
BEFORE INSERT ON marketplace_order_occurrence_source_promotion
FOR EACH ROW
EXECUTE FUNCTION validate_marketplace_order_occurrence_source_promotion();

CREATE FUNCTION reject_marketplace_order_promotion_mutation()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION 'marketplace order promotion state is append-only';
END;
$$;

CREATE TRIGGER protect_marketplace_order_identity_registry_mutation
BEFORE UPDATE OR DELETE ON marketplace_order_identity_registry
FOR EACH ROW
EXECUTE FUNCTION reject_marketplace_order_promotion_mutation();

CREATE TRIGGER protect_marketplace_order_occurrence_source_promotion_mutation
BEFORE UPDATE OR DELETE ON marketplace_order_occurrence_source_promotion
FOR EACH ROW
EXECUTE FUNCTION reject_marketplace_order_promotion_mutation();

CREATE INDEX marketplace_order_occurrence_source_promotion_order_idx
    ON marketplace_order_occurrence_source_promotion (
        organization_id,
        marketplace_order_id,
        promoted_at
    );