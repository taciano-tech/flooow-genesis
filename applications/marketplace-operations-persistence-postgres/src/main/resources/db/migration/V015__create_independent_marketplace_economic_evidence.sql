CREATE SEQUENCE marketplace_economic_evidence_change_sequence
    AS bigint
    MINVALUE 1
    NO MAXVALUE
    START WITH 1
    INCREMENT BY 1
    NO CYCLE;

CREATE TABLE marketplace_economic_evidence_subject (
    organization_id uuid NOT NULL REFERENCES integration_organization (organization_id),
    marketplace_order_id uuid NOT NULL,
    marketplace_key text NOT NULL CHECK (
        marketplace_key ~ '^[a-z0-9][a-z0-9.-]{0,99}$'
    ),
    external_order_id text NOT NULL CHECK (
        octet_length(external_order_id) BETWEEN 1 AND 256 AND
        external_order_id = btrim(external_order_id) AND
        external_order_id !~ '[[:cntrl:]]'
    ),
    currency char(3) NOT NULL CHECK (currency ~ '^[A-Z]{3}$'),
    current_version bigint NOT NULL DEFAULT 0 CHECK (current_version >= 0),
    PRIMARY KEY (organization_id, marketplace_order_id),
    UNIQUE (organization_id, marketplace_order_id, currency)
);

CREATE TABLE marketplace_economic_evidence_update (
    organization_id uuid NOT NULL,
    marketplace_order_id uuid NOT NULL,
    evidence_version bigint NOT NULL CHECK (evidence_version > 0),
    update_id uuid NOT NULL,
    change_kind text NOT NULL CHECK (change_kind IN ('FACT', 'ATTEMPT', 'CORRECTION')),
    change_sequence bigint NOT NULL DEFAULT nextval('marketplace_economic_evidence_change_sequence')
        CHECK (change_sequence > 0),
    committed_at timestamptz(6) NOT NULL DEFAULT transaction_timestamp(),
    PRIMARY KEY (organization_id, marketplace_order_id, evidence_version),
    UNIQUE (organization_id, marketplace_order_id, update_id),
    UNIQUE (organization_id, change_sequence),
    FOREIGN KEY (organization_id, marketplace_order_id)
        REFERENCES marketplace_economic_evidence_subject (organization_id, marketplace_order_id)
);

CREATE TABLE marketplace_economic_evidence_identifier (
    organization_id uuid NOT NULL,
    marketplace_order_id uuid NOT NULL,
    observation_id uuid NOT NULL,
    evidence_version bigint NOT NULL,
    identifier_kind text NOT NULL CHECK (identifier_kind IN ('FACT', 'ATTEMPT', 'CORRECTION')),
    PRIMARY KEY (organization_id, marketplace_order_id, observation_id),
    UNIQUE (
        organization_id,
        marketplace_order_id,
        observation_id,
        evidence_version,
        identifier_kind
    ),
    FOREIGN KEY (organization_id, marketplace_order_id, evidence_version)
        REFERENCES marketplace_economic_evidence_update (
            organization_id,
            marketplace_order_id,
            evidence_version
        )
);

CREATE TABLE marketplace_economic_evidence_fact (
    organization_id uuid NOT NULL,
    marketplace_order_id uuid NOT NULL,
    fact_id uuid NOT NULL,
    evidence_version bigint NOT NULL,
    identifier_kind text NOT NULL DEFAULT 'FACT' CHECK (identifier_kind = 'FACT'),
    fact_kind text NOT NULL CHECK (fact_kind IN ('COMPONENT', 'EXTERNAL_IDENTITY')),
    family text NOT NULL CHECK (family IN (
        'MARKETPLACE_ORDER',
        'MARKETPLACE_PAYMENT',
        'MARKETPLACE_SHIPPING',
        'PRODUCT_COST',
        'FISCAL_INVOICE',
        'FISCAL_TAX',
        'ADS_IDENTITY',
        'ADS_ALLOCATION'
    )),
    observed_at timestamptz(6) NOT NULL,
    PRIMARY KEY (organization_id, marketplace_order_id, fact_id),
    UNIQUE (organization_id, marketplace_order_id, fact_id, evidence_version),
    UNIQUE (
        organization_id,
        marketplace_order_id,
        fact_id,
        evidence_version,
        fact_kind,
        family
    ),
    FOREIGN KEY (
        organization_id,
        marketplace_order_id,
        fact_id,
        evidence_version,
        identifier_kind
    ) REFERENCES marketplace_economic_evidence_identifier (
        organization_id,
        marketplace_order_id,
        observation_id,
        evidence_version,
        identifier_kind
    )
);

CREATE TABLE marketplace_economic_evidence_component_fact (
    organization_id uuid NOT NULL,
    marketplace_order_id uuid NOT NULL,
    fact_id uuid NOT NULL,
    evidence_version bigint NOT NULL,
    fact_kind text NOT NULL DEFAULT 'COMPONENT' CHECK (fact_kind = 'COMPONENT'),
    family text NOT NULL,
    component_id uuid NOT NULL,
    component_type text NOT NULL CHECK (component_type IN (
        'REVENUE',
        'MARKETPLACE_COMMISSION',
        'MARKETPLACE_FEE',
        'SHIPPING',
        'ADVERTISING',
        'TAX',
        'PRODUCT_COST',
        'FINANCIAL_COST',
        'OTHER_ADJUSTMENT'
    )),
    direction text NOT NULL CHECK (direction IN ('ADDITION', 'DEDUCTION')),
    magnitude numeric(24,6) NOT NULL CHECK (
        magnitude >= 0 AND magnitude < 1000000000000000000
    ),
    currency char(3) NOT NULL CHECK (currency ~ '^[A-Z]{3}$'),
    source_kind text NOT NULL CHECK (source_kind IN ('MARKETPLACE', 'ERP', 'MANUAL', 'CALCULATED')),
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
    occurred_at timestamptz(6) NOT NULL,
    quality text NOT NULL CHECK (quality IN ('CONFIRMED', 'ESTIMATED')),
    coverage text NOT NULL CHECK (coverage IN ('COMPLETE', 'PARTIAL')),
    PRIMARY KEY (organization_id, marketplace_order_id, fact_id),
    UNIQUE (organization_id, marketplace_order_id, component_id),
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
    FOREIGN KEY (organization_id, marketplace_order_id, currency)
        REFERENCES marketplace_economic_evidence_subject (
            organization_id,
            marketplace_order_id,
            currency
        ),
    CONSTRAINT marketplace_economic_component_source_shape CHECK (
        (
            source_kind IN ('MARKETPLACE', 'ERP') AND
            source_external_reference IS NOT NULL AND
            source_external_reference_absence_reason IS NULL
        ) OR (
            source_kind IN ('MANUAL', 'CALCULATED') AND (
                (source_external_reference IS NOT NULL AND
                    source_external_reference_absence_reason IS NULL) OR
                (source_external_reference IS NULL AND
                    source_external_reference_absence_reason = 'INTERNAL_ORIGIN')
            )
        )
    ),
    CONSTRAINT marketplace_economic_component_family_type CHECK (
        (family = 'MARKETPLACE_ORDER' AND component_type IN (
            'REVENUE', 'MARKETPLACE_COMMISSION', 'MARKETPLACE_FEE'
        )) OR
        (family = 'MARKETPLACE_SHIPPING' AND component_type = 'SHIPPING') OR
        (family = 'PRODUCT_COST' AND component_type = 'PRODUCT_COST') OR
        (family = 'FISCAL_TAX' AND component_type = 'TAX') OR
        (family = 'ADS_ALLOCATION' AND component_type = 'ADVERTISING')
    )
);

CREATE TABLE marketplace_economic_evidence_external_identity_fact (
    organization_id uuid NOT NULL,
    marketplace_order_id uuid NOT NULL,
    fact_id uuid NOT NULL,
    evidence_version bigint NOT NULL,
    fact_kind text NOT NULL DEFAULT 'EXTERNAL_IDENTITY' CHECK (fact_kind = 'EXTERNAL_IDENTITY'),
    family text NOT NULL,
    identity_kind text NOT NULL CHECK (identity_kind IN (
        'MARKETPLACE_PAYMENT',
        'ERP_ORDER',
        'FISCAL_INVOICE',
        'MARKETPLACE_ITEM_TO_AD_GROUP'
    )),
    anchor_reference text NOT NULL CHECK (
        octet_length(anchor_reference) BETWEEN 1 AND 256 AND
        anchor_reference = btrim(anchor_reference) AND
        anchor_reference !~ '[[:cntrl:]]'
    ),
    linked_system_key text NOT NULL CHECK (
        linked_system_key ~ '^[a-z0-9][a-z0-9.-]{0,99}$'
    ),
    linked_reference text NOT NULL CHECK (
        octet_length(linked_reference) BETWEEN 1 AND 256 AND
        linked_reference = btrim(linked_reference) AND
        linked_reference !~ '[[:cntrl:]]'
    ),
    source_kind text NOT NULL CHECK (source_kind IN ('MARKETPLACE', 'ERP', 'MANUAL', 'CALCULATED')),
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
    occurred_at timestamptz(6) NOT NULL,
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
    CONSTRAINT marketplace_economic_identity_source_shape CHECK (
        (
            source_kind IN ('MARKETPLACE', 'ERP') AND
            source_external_reference IS NOT NULL AND
            source_external_reference_absence_reason IS NULL
        ) OR (
            source_kind IN ('MANUAL', 'CALCULATED') AND (
                (source_external_reference IS NOT NULL AND
                    source_external_reference_absence_reason IS NULL) OR
                (source_external_reference IS NULL AND
                    source_external_reference_absence_reason = 'INTERNAL_ORIGIN')
            )
        )
    ),
    CONSTRAINT marketplace_economic_identity_family_kind CHECK (
        (family = 'MARKETPLACE_PAYMENT' AND identity_kind = 'MARKETPLACE_PAYMENT') OR
        (family = 'FISCAL_INVOICE' AND identity_kind = 'FISCAL_INVOICE') OR
        (family = 'ADS_IDENTITY' AND identity_kind = 'MARKETPLACE_ITEM_TO_AD_GROUP') OR
        (family = 'MARKETPLACE_ORDER' AND identity_kind = 'ERP_ORDER')
    )
);

CREATE TABLE marketplace_economic_evidence_collection_attempt (
    organization_id uuid NOT NULL,
    marketplace_order_id uuid NOT NULL,
    attempt_id uuid NOT NULL,
    evidence_version bigint NOT NULL,
    identifier_kind text NOT NULL DEFAULT 'ATTEMPT' CHECK (identifier_kind = 'ATTEMPT'),
    family text NOT NULL CHECK (family IN (
        'MARKETPLACE_ORDER',
        'MARKETPLACE_PAYMENT',
        'MARKETPLACE_SHIPPING',
        'PRODUCT_COST',
        'FISCAL_INVOICE',
        'FISCAL_TAX',
        'ADS_IDENTITY',
        'ADS_ALLOCATION'
    )),
    source_system_key text NOT NULL CHECK (
        source_system_key ~ '^[a-z0-9][a-z0-9.-]{0,99}$'
    ),
    outcome text NOT NULL CHECK (outcome IN ('NO_EVIDENCE', 'AMBIGUOUS', 'TEMPORARY_FAILURE')),
    attempted_at timestamptz(6) NOT NULL,
    PRIMARY KEY (organization_id, marketplace_order_id, attempt_id),
    FOREIGN KEY (
        organization_id,
        marketplace_order_id,
        attempt_id,
        evidence_version,
        identifier_kind
    ) REFERENCES marketplace_economic_evidence_identifier (
        organization_id,
        marketplace_order_id,
        observation_id,
        evidence_version,
        identifier_kind
    )
);

CREATE TABLE marketplace_economic_evidence_correction (
    organization_id uuid NOT NULL,
    marketplace_order_id uuid NOT NULL,
    correction_id uuid NOT NULL,
    evidence_version bigint NOT NULL,
    correction_identifier_kind text NOT NULL DEFAULT 'CORRECTION'
        CHECK (correction_identifier_kind = 'CORRECTION'),
    superseded_fact_id uuid NOT NULL,
    replacement_fact_id uuid NOT NULL,
    replacement_identifier_kind text NOT NULL DEFAULT 'FACT'
        CHECK (replacement_identifier_kind = 'FACT'),
    reason text NOT NULL CHECK (reason IN (
        'SOURCE_CORRECTION',
        'MAPPING_CORRECTION',
        'VERIFIED_MANUAL_CORRECTION'
    )),
    observed_at timestamptz(6) NOT NULL,
    PRIMARY KEY (organization_id, marketplace_order_id, correction_id),
    UNIQUE (organization_id, marketplace_order_id, superseded_fact_id),
    FOREIGN KEY (
        organization_id,
        marketplace_order_id,
        correction_id,
        evidence_version,
        correction_identifier_kind
    ) REFERENCES marketplace_economic_evidence_identifier (
        organization_id,
        marketplace_order_id,
        observation_id,
        evidence_version,
        identifier_kind
    ),
    FOREIGN KEY (
        organization_id,
        marketplace_order_id,
        replacement_fact_id,
        evidence_version
    ) REFERENCES marketplace_economic_evidence_fact (
        organization_id,
        marketplace_order_id,
        fact_id,
        evidence_version
    ),
    FOREIGN KEY (organization_id, marketplace_order_id, superseded_fact_id)
        REFERENCES marketplace_economic_evidence_fact (
            organization_id,
            marketplace_order_id,
            fact_id
        ),
    CONSTRAINT marketplace_economic_correction_distinct_ids CHECK (
        correction_id <> superseded_fact_id AND
        replacement_fact_id <> superseded_fact_id AND
        correction_id <> replacement_fact_id
    )
);

CREATE FUNCTION validate_marketplace_economic_evidence_subject_insert()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.current_version <> 0 THEN
        RAISE EXCEPTION 'marketplace economic evidence subject must start at version zero';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER validate_marketplace_economic_evidence_subject_before_insert
    BEFORE INSERT ON marketplace_economic_evidence_subject
    FOR EACH ROW EXECUTE FUNCTION validate_marketplace_economic_evidence_subject_insert();

CREATE FUNCTION validate_marketplace_economic_evidence_subject_update()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.organization_id IS DISTINCT FROM OLD.organization_id OR
       NEW.marketplace_order_id IS DISTINCT FROM OLD.marketplace_order_id OR
       NEW.marketplace_key IS DISTINCT FROM OLD.marketplace_key OR
       NEW.external_order_id IS DISTINCT FROM OLD.external_order_id OR
       NEW.currency IS DISTINCT FROM OLD.currency THEN
        RAISE EXCEPTION 'marketplace economic evidence subject identity is immutable';
    END IF;
    IF NEW.current_version <> OLD.current_version + 1 THEN
        RAISE EXCEPTION 'marketplace economic evidence version must advance by one';
    END IF;
    IF NOT EXISTS (
        SELECT 1
        FROM marketplace_economic_evidence_update
        WHERE organization_id = NEW.organization_id
          AND marketplace_order_id = NEW.marketplace_order_id
          AND evidence_version = NEW.current_version
    ) THEN
        RAISE EXCEPTION 'marketplace economic evidence version requires journal history';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER validate_marketplace_economic_evidence_subject_before_update
    BEFORE UPDATE ON marketplace_economic_evidence_subject
    FOR EACH ROW EXECUTE FUNCTION validate_marketplace_economic_evidence_subject_update();

CREATE FUNCTION stamp_marketplace_economic_evidence_update_insert()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    NEW.committed_at := transaction_timestamp();
    RETURN NEW;
END;
$$;

CREATE TRIGGER stamp_marketplace_economic_evidence_update_before_insert
    BEFORE INSERT ON marketplace_economic_evidence_update
    FOR EACH ROW EXECUTE FUNCTION stamp_marketplace_economic_evidence_update_insert();

CREATE FUNCTION validate_marketplace_economic_evidence_update()
RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE
    expected_identifier_kind text;
    root_version bigint;
BEGIN
    expected_identifier_kind := CASE NEW.change_kind
        WHEN 'FACT' THEN 'FACT'
        WHEN 'ATTEMPT' THEN 'ATTEMPT'
        WHEN 'CORRECTION' THEN 'CORRECTION'
    END;

    IF NOT EXISTS (
        SELECT 1
        FROM marketplace_economic_evidence_identifier
        WHERE organization_id = NEW.organization_id
          AND marketplace_order_id = NEW.marketplace_order_id
          AND observation_id = NEW.update_id
          AND evidence_version = NEW.evidence_version
          AND identifier_kind = expected_identifier_kind
    ) THEN
        RAISE EXCEPTION 'marketplace economic evidence update identifier is inconsistent';
    END IF;

    SELECT current_version INTO root_version
    FROM marketplace_economic_evidence_subject
    WHERE organization_id = NEW.organization_id
      AND marketplace_order_id = NEW.marketplace_order_id;

    IF root_version IS NULL OR root_version < NEW.evidence_version THEN
        RAISE EXCEPTION 'marketplace economic evidence root version is inconsistent';
    END IF;
    RETURN NEW;
END;
$$;

CREATE CONSTRAINT TRIGGER validate_marketplace_economic_evidence_update_at_commit
    AFTER INSERT ON marketplace_economic_evidence_update
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION validate_marketplace_economic_evidence_update();

CREATE FUNCTION validate_marketplace_economic_evidence_identifier()
RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE
    subtype_count integer;
BEGIN
    subtype_count := CASE NEW.identifier_kind
        WHEN 'FACT' THEN (
            SELECT count(*)
            FROM marketplace_economic_evidence_fact
            WHERE organization_id = NEW.organization_id
              AND marketplace_order_id = NEW.marketplace_order_id
              AND fact_id = NEW.observation_id
              AND evidence_version = NEW.evidence_version
        )
        WHEN 'ATTEMPT' THEN (
            SELECT count(*)
            FROM marketplace_economic_evidence_collection_attempt
            WHERE organization_id = NEW.organization_id
              AND marketplace_order_id = NEW.marketplace_order_id
              AND attempt_id = NEW.observation_id
              AND evidence_version = NEW.evidence_version
        )
        WHEN 'CORRECTION' THEN (
            SELECT count(*)
            FROM marketplace_economic_evidence_correction
            WHERE organization_id = NEW.organization_id
              AND marketplace_order_id = NEW.marketplace_order_id
              AND correction_id = NEW.observation_id
              AND evidence_version = NEW.evidence_version
        )
    END;

    IF subtype_count <> 1 THEN
        RAISE EXCEPTION 'marketplace economic evidence identifier subtype is inconsistent';
    END IF;
    RETURN NEW;
END;
$$;

CREATE CONSTRAINT TRIGGER validate_marketplace_economic_evidence_identifier_at_commit
    AFTER INSERT ON marketplace_economic_evidence_identifier
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION validate_marketplace_economic_evidence_identifier();

CREATE FUNCTION validate_marketplace_economic_evidence_fact()
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
    );

    IF subtype_count <> 1 THEN
        RAISE EXCEPTION 'marketplace economic evidence fact subtype is inconsistent';
    END IF;
    RETURN NEW;
END;
$$;

CREATE CONSTRAINT TRIGGER validate_marketplace_economic_evidence_fact_at_commit
    AFTER INSERT ON marketplace_economic_evidence_fact
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION validate_marketplace_economic_evidence_fact();

CREATE FUNCTION reject_marketplace_economic_evidence_mutation()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'marketplace economic evidence history is append-only';
END;
$$;

CREATE FUNCTION reject_marketplace_economic_evidence_subject_delete()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'marketplace economic evidence subject cannot be deleted';
END;
$$;

CREATE TRIGGER protect_marketplace_economic_evidence_subject_delete
    BEFORE DELETE ON marketplace_economic_evidence_subject
    FOR EACH ROW EXECUTE FUNCTION reject_marketplace_economic_evidence_subject_delete();

CREATE TRIGGER protect_marketplace_economic_evidence_update_mutation
    BEFORE UPDATE OR DELETE ON marketplace_economic_evidence_update
    FOR EACH ROW EXECUTE FUNCTION reject_marketplace_economic_evidence_mutation();

CREATE TRIGGER protect_marketplace_economic_evidence_identifier_mutation
    BEFORE UPDATE OR DELETE ON marketplace_economic_evidence_identifier
    FOR EACH ROW EXECUTE FUNCTION reject_marketplace_economic_evidence_mutation();

CREATE TRIGGER protect_marketplace_economic_evidence_fact_mutation
    BEFORE UPDATE OR DELETE ON marketplace_economic_evidence_fact
    FOR EACH ROW EXECUTE FUNCTION reject_marketplace_economic_evidence_mutation();

CREATE TRIGGER protect_marketplace_economic_component_fact_mutation
    BEFORE UPDATE OR DELETE ON marketplace_economic_evidence_component_fact
    FOR EACH ROW EXECUTE FUNCTION reject_marketplace_economic_evidence_mutation();

CREATE TRIGGER protect_marketplace_economic_identity_fact_mutation
    BEFORE UPDATE OR DELETE ON marketplace_economic_evidence_external_identity_fact
    FOR EACH ROW EXECUTE FUNCTION reject_marketplace_economic_evidence_mutation();

CREATE TRIGGER protect_marketplace_economic_attempt_mutation
    BEFORE UPDATE OR DELETE ON marketplace_economic_evidence_collection_attempt
    FOR EACH ROW EXECUTE FUNCTION reject_marketplace_economic_evidence_mutation();

CREATE TRIGGER protect_marketplace_economic_correction_mutation
    BEFORE UPDATE OR DELETE ON marketplace_economic_evidence_correction
    FOR EACH ROW EXECUTE FUNCTION reject_marketplace_economic_evidence_mutation();
