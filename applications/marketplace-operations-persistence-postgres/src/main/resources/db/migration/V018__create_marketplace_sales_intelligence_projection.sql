-- TASK-0146
-- Durable current-state Sales Intelligence projection.
-- Derivative mutable state only; canonical economic evidence remains authoritative.

CREATE TABLE marketplace_sales_intelligence_projection (
    organization_id uuid NOT NULL,
    marketplace_order_id uuid NOT NULL,
    source_evidence_version bigint NOT NULL CHECK (source_evidence_version >= 0),
    state_kind text NOT NULL CHECK (state_kind IN ('UNRESOLVED', 'CALCULATED')),
    assembly_policy_version text NOT NULL CHECK (
        assembly_policy_version ~ '^[a-z0-9-]+/[1-9][0-9]*$'
    ),
    calculation_policy_version text NULL CHECK (
        calculation_policy_version IS NULL OR
        calculation_policy_version ~ '^[a-z0-9][a-z0-9./-]{0,63}$'
    ),
    calculation_kind text NULL CHECK (
        calculation_kind IS NULL OR calculation_kind IN ('COMPLETE', 'INCOMPLETE')
    ),
    state_payload jsonb NOT NULL CHECK (jsonb_typeof(state_payload) = 'object'),
    last_applied_change_sequence bigint NOT NULL CHECK (last_applied_change_sequence > 0),
    projected_at timestamptz(6) NOT NULL,
    PRIMARY KEY (organization_id, marketplace_order_id),
    FOREIGN KEY (organization_id, marketplace_order_id)
        REFERENCES marketplace_economic_evidence_subject (
            organization_id,
            marketplace_order_id
        ),
    CONSTRAINT marketplace_sales_intelligence_state_shape CHECK (
        (
            state_kind = 'UNRESOLVED' AND
            calculation_policy_version IS NULL AND
            calculation_kind IS NULL
        ) OR (
            state_kind = 'CALCULATED' AND
            calculation_policy_version IS NOT NULL AND
            calculation_kind IS NOT NULL
        )
    )
);

CREATE INDEX marketplace_sales_intelligence_org_page_idx
    ON marketplace_sales_intelligence_projection (
        organization_id,
        projected_at DESC,
        marketplace_order_id DESC
    );
