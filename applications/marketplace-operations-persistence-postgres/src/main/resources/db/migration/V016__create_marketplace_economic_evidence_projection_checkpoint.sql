CREATE TABLE marketplace_economic_evidence_projection_checkpoint (
    organization_id uuid NOT NULL,
    projection_name text NOT NULL,
    last_change_sequence bigint NOT NULL,
    updated_at timestamptz(6) NOT NULL,
    PRIMARY KEY (organization_id, projection_name),
    CONSTRAINT marketplace_economic_evidence_projection_checkpoint_organization_fk
        FOREIGN KEY (organization_id)
        REFERENCES integration_organization (organization_id),
    CONSTRAINT marketplace_economic_evidence_projection_checkpoint_change_fk
        FOREIGN KEY (organization_id, last_change_sequence)
        REFERENCES marketplace_economic_evidence_update (
            organization_id,
            change_sequence
        ),
    CONSTRAINT marketplace_economic_evidence_projection_checkpoint_name_check
        CHECK (
            length(projection_name) BETWEEN 1 AND 100
            AND projection_name ~ '^[a-z0-9][a-z0-9-]*$'
        ),
    CONSTRAINT marketplace_economic_evidence_projection_checkpoint_sequence_check
        CHECK (last_change_sequence >= 0)
);

CREATE FUNCTION stamp_marketplace_economic_evidence_projection_checkpoint()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    NEW.updated_at := transaction_timestamp();
    RETURN NEW;
END;
$$;

CREATE TRIGGER stamp_marketplace_economic_evidence_projection_checkpoint_before_write
    BEFORE INSERT OR UPDATE
    ON marketplace_economic_evidence_projection_checkpoint
    FOR EACH ROW
    EXECUTE FUNCTION stamp_marketplace_economic_evidence_projection_checkpoint();
