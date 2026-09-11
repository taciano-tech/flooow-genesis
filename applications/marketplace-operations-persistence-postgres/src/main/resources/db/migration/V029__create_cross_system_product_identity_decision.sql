CREATE TABLE integration_cross_system_product_identity_decision (
    organization_id uuid NOT NULL,
    decision_id uuid NOT NULL,
    mercado_livre_connection_id uuid NOT NULL,
    mercado_livre_item_id text NOT NULL,
    mercado_livre_seller_sku text NOT NULL,
    omie_connection_id uuid NOT NULL,
    omie_provider_product_id text NOT NULL,
    decision_kind text NOT NULL,
    revision integer NOT NULL,
    supersedes_decision_id uuid NULL,
    principal_ref text NOT NULL,
    reason text NOT NULL,
    provenance text NOT NULL,
    correlation_id uuid NOT NULL,
    decided_at timestamptz NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (organization_id, decision_id),
    FOREIGN KEY (organization_id, mercado_livre_connection_id)
        REFERENCES integration_connection (organization_id, connection_id),
    FOREIGN KEY (organization_id, omie_connection_id)
        REFERENCES integration_connection (organization_id, connection_id),
    FOREIGN KEY (organization_id, supersedes_decision_id)
        REFERENCES integration_cross_system_product_identity_decision (organization_id, decision_id),
    CHECK (mercado_livre_connection_id <> omie_connection_id),
    CHECK (decision_kind IN ('CONFIRMED', 'REJECTED')),
    CHECK (reason IN ('EXPLICIT_CONFIRMATION', 'EXPLICIT_REJECTION', 'CORRECTION')),
    CHECK (
        (supersedes_decision_id IS NOT NULL AND reason = 'CORRECTION') OR
        (supersedes_decision_id IS NULL AND decision_kind = 'CONFIRMED' AND
            reason = 'EXPLICIT_CONFIRMATION') OR
        (supersedes_decision_id IS NULL AND decision_kind = 'REJECTED' AND
            reason = 'EXPLICIT_REJECTION')
    ),
    CHECK (revision > 0),
    CHECK ((revision = 1) = (supersedes_decision_id IS NULL)),
    CHECK (octet_length(mercado_livre_item_id) BETWEEN 1 AND 128),
    CHECK (mercado_livre_item_id = btrim(mercado_livre_item_id)),
    CHECK (mercado_livre_item_id !~ '[[:cntrl:]]'),
    CHECK (octet_length(mercado_livre_seller_sku) BETWEEN 1 AND 128),
    CHECK (mercado_livre_seller_sku = btrim(mercado_livre_seller_sku)),
    CHECK (mercado_livre_seller_sku !~ '[[:cntrl:]]'),
    CHECK (octet_length(omie_provider_product_id) BETWEEN 1 AND 128),
    CHECK (omie_provider_product_id = btrim(omie_provider_product_id)),
    CHECK (omie_provider_product_id !~ '[[:cntrl:]]'),
    CHECK (octet_length(principal_ref) BETWEEN 1 AND 128),
    CHECK (principal_ref = btrim(principal_ref)),
    CHECK (principal_ref !~ '[[:cntrl:]]'),
    CHECK (octet_length(provenance) BETWEEN 1 AND 256),
    CHECK (provenance = btrim(provenance)),
    CHECK (provenance !~ '[[:cntrl:]]')
);

CREATE UNIQUE INDEX integration_cross_system_product_identity_supersession_idx
    ON integration_cross_system_product_identity_decision (
        organization_id,
        supersedes_decision_id
    )
    WHERE supersedes_decision_id IS NOT NULL;

CREATE UNIQUE INDEX integration_cross_system_product_identity_revision_idx
    ON integration_cross_system_product_identity_decision (
        organization_id,
        mercado_livre_connection_id,
        mercado_livre_item_id,
        mercado_livre_seller_sku,
        omie_connection_id,
        omie_provider_product_id,
        revision
    );

CREATE INDEX integration_cross_system_product_identity_current_idx
    ON integration_cross_system_product_identity_decision (
        organization_id,
        mercado_livre_connection_id,
        mercado_livre_item_id,
        mercado_livre_seller_sku,
        omie_connection_id,
        omie_provider_product_id
    );

CREATE FUNCTION validate_cross_system_product_identity_supersession()
RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE
    previous integration_cross_system_product_identity_decision%ROWTYPE;
BEGIN
    IF NEW.supersedes_decision_id IS NULL THEN
        RETURN NEW;
    END IF;
    SELECT * INTO previous
      FROM integration_cross_system_product_identity_decision
     WHERE organization_id = NEW.organization_id
       AND decision_id = NEW.supersedes_decision_id;
    IF NOT FOUND OR previous.mercado_livre_connection_id <> NEW.mercado_livre_connection_id OR
       previous.mercado_livre_item_id <> NEW.mercado_livre_item_id OR
       previous.mercado_livre_seller_sku <> NEW.mercado_livre_seller_sku OR
       previous.omie_connection_id <> NEW.omie_connection_id OR
       previous.omie_provider_product_id <> NEW.omie_provider_product_id OR
       NEW.revision <> previous.revision + 1 THEN
        RAISE EXCEPTION 'invalid cross-system product identity supersession';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER validate_cross_system_product_identity_supersession_insert
BEFORE INSERT ON integration_cross_system_product_identity_decision
FOR EACH ROW EXECUTE FUNCTION validate_cross_system_product_identity_supersession();

CREATE FUNCTION reject_cross_system_product_identity_decision_mutation()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'cross-system product identity decisions are append-only';
END;
$$;

CREATE TRIGGER protect_cross_system_product_identity_decision_mutation
BEFORE UPDATE OR DELETE ON integration_cross_system_product_identity_decision
FOR EACH ROW EXECUTE FUNCTION reject_cross_system_product_identity_decision_mutation();
