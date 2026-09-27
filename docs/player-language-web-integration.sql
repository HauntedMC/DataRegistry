-- Apply to the DataRegistry player schema before deploying readers or writers that use
-- versioned language preferences. Existing rows start at version zero.
ALTER TABLE player_language ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

CREATE TABLE player_language_mutation (
    request_id VARCHAR(36) PRIMARY KEY,
    player_id BIGINT NOT NULL,
    player_uuid VARCHAR(36) NOT NULL,
    preference VARCHAR(16) NOT NULL,
    expected_version BIGINT NOT NULL,
    result_version BIGINT NOT NULL,
    created_at BIGINT NOT NULL,
    INDEX idx_player_language_mutation_created (created_at)
);

CREATE TABLE player_data_change_outbox (
    event_id VARCHAR(36) PRIMARY KEY,
    capability VARCHAR(64) NOT NULL,
    player_id BIGINT NOT NULL,
    player_uuid VARCHAR(36) NOT NULL,
    revision BIGINT NOT NULL,
    created_at BIGINT NOT NULL,
    published_at BIGINT NULL,
    INDEX idx_player_data_change_unpublished (published_at, created_at)
);
