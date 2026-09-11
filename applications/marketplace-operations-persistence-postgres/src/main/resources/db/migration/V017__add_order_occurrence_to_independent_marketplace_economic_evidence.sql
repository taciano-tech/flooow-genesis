-- TASK-0148
-- Durable OrderOccurrence persistence for independent marketplace economic evidence.
-- Additive migration only: V015/V016 remain immutable and no backfill is performed.

ALTER TABLE marketplace_economic_evidence_fact
    DROP CONSTRAINT marketplace_economic_evidence_fact_fact_kind_check;

ALTER TABLE marketplace_economic_evidence_fact
    ADD CONSTRAINT marketplace_economic_evidence_fact_fact_kind_check
    CHECK (fact_kind IN ('COMPONENT', 'EXTERNAL_IDENTITY', 'ORDER_OCCURRENCE'));

CREATE TABLE marketplace_economic_evidence_order_occurrence_fact (
    organization_id uuid NOT NULL,
    marketplace_order_id uuid NOT NULL,
    fact_id uuid NOT NULL,
    evidence_version bigint NOT NULL,
    fact_kind text NOT NULL DEFAULT 'ORDER_OCCURRENCE'
        CHECK (fact_kind = 'ORDER_OCCURRENCE'),
    family text NOT NULL DEFAULT 'MARKETPLACE_ORDER'
        CHECK (family = 'MARKETPLACE_ORDER'),
    occurred_at timestamptz(6) NOT NULL,
    source_kind text NOT NULL
        CHECK (source_kind IN ('MARKETPLACE', 'ERP', 'MANUAL', 'CALCULATED')),
    source_system_key text NOT NULL CHECK (
        source_system_key ~ '^[a-z0-9][a-z0-9.-]{0,99}$'
    ),
    source_external_reference text NULL CHECK (
        source_external_reference IS NULL OR (
            octet_length(source_external_reference) BETWEEN 1 AND 256 AND
            source_external_reference = btrim(source_external_reference) AND
            source_external_reference !~ '[[:cntrl:]]'
        )
    ),
    source_external_reference_absence_reason text NULL CHECK (
        source_external_reference_absence_reason IS NULL OR
        source_external_reference_absence_reason = 'INTERNAL_ORIGIN'
    ),
    PRIMARY KEY (organization_id, marketplace_order_id, fact_id),
    FOREIGN KEY (
        organization_id,
        marketplace_order_id,
        fact_id,
        evidence_version,
        fact_kind,
        family
    ) REFERENCES marketplace_economic_evidence_fact (
        organization_id,
        marketplace_order_id,
        fact_id,
        evidence_version,
        fact_kind,
        family
    ),
    CONSTRAINT marketplace_economic_order_occurrence_source_shape CHECK (
        (
            source_kind IN ('MARKETPLACE', 'ERP') AND
            source_external_reference IS NOT NULL AND
            source_external_reference_absence_reason IS NULL
        ) OR (
            source_kind IN ('MANUAL', 'CALCULATED') AND (
                (
                    source_external_reference IS NOT NULL AND
                    source_external_reference_absence_reason IS NULL
                ) OR (
                    source_external_reference IS NULL AND
                    source_external_reference_absence_reason = 'INTERNAL_ORIGIN'
                )
            )
        )
    )
);

CREATE OR REPLACE FUNCTION validate_marketplace_economic_evidence_fact()
RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE
    subtype_count integer;
BEGIN
    subtype_count := (
        SELECT count(*)
        FROM marketplace_economic_evidence_component_fact
        WHERE organization_id = NEW.organization_id
          AND marketplace_order_id = NEW.marketplace_order_id
          AND fact_id = NEW.fact_id
    ) + (
        SELECT count(*)
        FROM marketplace_economic_evidence_external_identity_fact
        WHERE organization_id = NEW.organization_id
          AND marketplace_order_id = NEW.marketplace_order_id
          AND fact_id = NEW.fact_id
    ) + (
        SELECT count(*)
        FROM marketplace_economic_evidence_order_occurrence_fact
        WHERE organization_id = NEW.organization_id
          AND marketplace_order_id = NEW.marketplace_order_id
          AND fact_id = NEW.fact_id
    );

    IF subtype_count <> 1 THEN
        RAISE EXCEPTION 'marketplace economic evidence fact subtype is inconsistent';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER protect_marketplace_economic_order_occurrence_fact_mutation
    BEFORE UPDATE OR DELETE ON marketplace_economic_evidence_order_occurrence_fact
    FOR EACH ROW EXECUTE FUNCTION reject_marketplace_economic_evidence_mutation();
