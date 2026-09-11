-- TASK-0158: durable derived reconciliation cases. Economic Truth and ledger evidence remain immutable.
CREATE TABLE marketplace_reconciliation_case (
    organization_id uuid NOT NULL,
    case_id uuid NOT NULL,
    marketplace_order_id uuid NOT NULL,
    financial_trace_id uuid NOT NULL,
    policy_version text NOT NULL CHECK (policy_version ~ '^[a-z0-9][a-z0-9./-]{0,99}$'),
    currency char(3) NOT NULL CHECK (currency ~ '^[A-Z]{3}$'),
    status text NOT NULL CHECK (status IN ('OPEN','ACKNOWLEDGED','RESOLVED')),
    opened_at timestamptz(6) NOT NULL,
    last_observed_at timestamptz(6) NOT NULL,
    resolved_at timestamptz(6),
    revision bigint NOT NULL CHECK (revision > 0),
    absolute_difference_summary numeric(24,6) NOT NULL CHECK (absolute_difference_summary >= 0),
    stage_details jsonb NOT NULL CHECK (jsonb_typeof(stage_details) = 'array'),
    evidence_entry_ids jsonb NOT NULL CHECK (jsonb_typeof(evidence_entry_ids) = 'array'),
    PRIMARY KEY (organization_id, case_id),
    FOREIGN KEY (organization_id, financial_trace_id)
        REFERENCES marketplace_financial_trace (organization_id, trace_id)
);

CREATE UNIQUE INDEX marketplace_reconciliation_case_identity_idx
    ON marketplace_reconciliation_case (organization_id, financial_trace_id, policy_version);

CREATE INDEX marketplace_reconciliation_case_org_page_idx
    ON marketplace_reconciliation_case (organization_id, last_observed_at DESC, case_id DESC);

CREATE INDEX marketplace_reconciliation_case_org_status_idx
    ON marketplace_reconciliation_case (organization_id, status, last_observed_at DESC);

CREATE OR REPLACE FUNCTION reject_marketplace_reconciliation_case_mutation()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'marketplace reconciliation cases cannot be deleted';
    END IF;
    RETURN NEW;
END $$;

CREATE TRIGGER marketplace_reconciliation_case_no_delete
    BEFORE DELETE ON marketplace_reconciliation_case
    FOR EACH ROW EXECUTE FUNCTION reject_marketplace_reconciliation_case_mutation();
