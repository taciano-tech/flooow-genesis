CREATE TABLE marketplace_systemic_divergence_signal (
    organization_id UUID NOT NULL,
    signal_id UUID NOT NULL,
    stage TEXT NOT NULL,
    currency TEXT NOT NULL,
    policy_version TEXT NOT NULL,
    window_millis BIGINT NOT NULL,
    first_seen_at TIMESTAMPTZ NOT NULL,
    last_seen_at TIMESTAMPTZ NOT NULL,
    occurrence_count INTEGER NOT NULL,
    absolute_difference NUMERIC(38,6) NOT NULL,
    status TEXT NOT NULL,
    revision BIGINT NOT NULL,
    case_ids JSONB NOT NULL,
    PRIMARY KEY (organization_id, signal_id),
    UNIQUE (organization_id, stage, currency, policy_version),
    CHECK (occurrence_count >= 2),
    CHECK (revision > 0)
);
CREATE INDEX marketplace_systemic_divergence_signal_page_idx ON marketplace_systemic_divergence_signal (organization_id, last_seen_at DESC, signal_id DESC);
