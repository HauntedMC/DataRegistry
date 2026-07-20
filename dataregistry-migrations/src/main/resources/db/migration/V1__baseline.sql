-- DataRegistry MySQL 8 baseline. Run this migration before starting either platform with orm.schema-mode=validate.

CREATE TABLE player_entity (
    id BIGINT NOT NULL AUTO_INCREMENT,
    uuid VARCHAR(36) NOT NULL,
    username VARCHAR(32) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_player_entity_uuid UNIQUE (uuid),
    INDEX idx_player_username (username)
);

CREATE TABLE player_lifecycle_outbox (
    id BIGINT NOT NULL AUTO_INCREMENT,
    event_id VARCHAR(96) NOT NULL,
    event_type VARCHAR(32) NOT NULL,
    player_id BIGINT NOT NULL,
    player_uuid VARCHAR(36) NOT NULL,
    username VARCHAR(32) NOT NULL,
    server_name VARCHAR(64),
    occurred_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL,
    published_at TIMESTAMP NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_plo_event_id UNIQUE (event_id),
    INDEX idx_plo_type_created (event_type, created_at),
    INDEX idx_plo_player_created (player_id, created_at),
    INDEX idx_plo_unpublished (published_at, created_at)
);

CREATE TABLE player_activity_summary (
    player_id BIGINT NOT NULL,
    version BIGINT NOT NULL,
    first_seen_at TIMESTAMP NOT NULL,
    last_seen_at TIMESTAMP NOT NULL,
    last_login_at TIMESTAMP NULL,
    last_logout_at TIMESTAMP NULL,
    PRIMARY KEY (player_id),
    CONSTRAINT fk_pas_player FOREIGN KEY (player_id) REFERENCES player_entity (id),
    INDEX idx_pas_last_seen_at (last_seen_at),
    INDEX idx_pas_last_login_at (last_login_at),
    INDEX idx_pas_last_logout_at (last_logout_at)
);

CREATE TABLE player_online_status (
    player_id BIGINT NOT NULL,
    online BOOLEAN NOT NULL,
    current_server VARCHAR(64) NOT NULL,
    previous_server VARCHAR(64) NULL,
    PRIMARY KEY (player_id),
    CONSTRAINT fk_pos_player FOREIGN KEY (player_id) REFERENCES player_entity (id)
);

CREATE TABLE player_connection_info (
    player_id BIGINT NOT NULL,
    version BIGINT NOT NULL,
    ip_address VARCHAR(45) NULL,
    first_connection_at TIMESTAMP NULL,
    last_connection_at TIMESTAMP NULL,
    last_disconnect_at TIMESTAMP NULL,
    virtual_host VARCHAR(255) NULL,
    PRIMARY KEY (player_id),
    CONSTRAINT fk_pci_player FOREIGN KEY (player_id) REFERENCES player_entity (id),
    INDEX idx_pci_ip_address (ip_address),
    INDEX idx_pci_last_conn_at (last_connection_at),
    INDEX idx_pci_last_disc_at (last_disconnect_at)
);

CREATE TABLE player_sessions (
    id BIGINT NOT NULL AUTO_INCREMENT,
    player_id BIGINT NOT NULL,
    ip_address VARCHAR(45) NULL,
    virtual_host VARCHAR(255) NULL,
    started_at TIMESTAMP NOT NULL,
    ended_at TIMESTAMP NULL,
    first_server VARCHAR(64) NULL,
    last_server VARCHAR(64) NULL,
    version BIGINT NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_ps_player FOREIGN KEY (player_id) REFERENCES player_entity (id),
    INDEX idx_psi_player_started (player_id, started_at),
    INDEX idx_psi_player_open (player_id, ended_at),
    INDEX idx_psi_open_started (ended_at, started_at)
);

CREATE TABLE player_session_visits (
    id BIGINT NOT NULL AUTO_INCREMENT,
    player_id BIGINT NOT NULL,
    session_id BIGINT NOT NULL,
    server_name VARCHAR(64) NOT NULL,
    entered_at TIMESTAMP NOT NULL,
    left_at TIMESTAMP NULL,
    version BIGINT NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_psv_player FOREIGN KEY (player_id) REFERENCES player_entity (id),
    CONSTRAINT fk_psv_session FOREIGN KEY (session_id) REFERENCES player_sessions (id),
    INDEX idx_psv_player_entered (player_id, entered_at),
    INDEX idx_psv_player_open (player_id, left_at),
    INDEX idx_psv_session_entered (session_id, entered_at),
    INDEX idx_psv_session_open (session_id, left_at)
);

CREATE TABLE player_playtime (
    id BIGINT NOT NULL AUTO_INCREMENT,
    player_id BIGINT NOT NULL,
    gamemode_key VARCHAR(64) NOT NULL,
    tracked_millis BIGINT NOT NULL,
    segment_count BIGINT NOT NULL,
    first_tracked_at TIMESTAMP NOT NULL,
    last_tracked_at TIMESTAMP NOT NULL,
    version BIGINT NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_ppt_player FOREIGN KEY (player_id) REFERENCES player_entity (id),
    CONSTRAINT uk_player_playtime_player_gamemode UNIQUE (player_id, gamemode_key),
    INDEX idx_ppt_player (player_id),
    INDEX idx_ppt_gamemode_time (gamemode_key, tracked_millis),
    INDEX idx_ppt_last_tracked (last_tracked_at)
);

CREATE TABLE player_playtime_segments (
    id BIGINT NOT NULL AUTO_INCREMENT,
    player_id BIGINT NOT NULL,
    session_id BIGINT NOT NULL,
    gamemode_key VARCHAR(64) NOT NULL,
    entry_server VARCHAR(64) NOT NULL,
    last_server VARCHAR(64) NOT NULL,
    started_at TIMESTAMP NOT NULL,
    last_accrued_at TIMESTAMP NOT NULL,
    ended_at TIMESTAMP NULL,
    close_reason VARCHAR(32) NULL,
    version BIGINT NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_ppts_player FOREIGN KEY (player_id) REFERENCES player_entity (id),
    CONSTRAINT fk_ppts_session FOREIGN KEY (session_id) REFERENCES player_sessions (id),
    INDEX idx_ppts_player_open (player_id, ended_at),
    INDEX idx_ppts_player_started (player_id, started_at),
    INDEX idx_ppts_gamemode_started (gamemode_key, started_at),
    INDEX idx_ppts_session_open (session_id, ended_at),
    INDEX idx_ppts_open_started (ended_at, started_at)
);

CREATE TABLE player_language (
    player_id BIGINT NOT NULL,
    language VARCHAR(16) NOT NULL,
    effective_language VARCHAR(16) NULL,
    PRIMARY KEY (player_id),
    CONSTRAINT fk_pl_player FOREIGN KEY (player_id) REFERENCES player_entity (id)
);

CREATE TABLE player_nicknames (
    player_id BIGINT NOT NULL,
    nickname VARCHAR(255) NOT NULL,
    PRIMARY KEY (player_id),
    CONSTRAINT fk_pn_player FOREIGN KEY (player_id) REFERENCES player_entity (id)
);

CREATE TABLE player_name_history (
    id BIGINT NOT NULL AUTO_INCREMENT,
    player_id BIGINT NOT NULL,
    username VARCHAR(32) NOT NULL,
    last_seen_at TIMESTAMP NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_pnh_player FOREIGN KEY (player_id) REFERENCES player_entity (id),
    INDEX idx_pnh_player_seen (player_id, last_seen_at),
    INDEX idx_pnh_username_seen (username, last_seen_at),
    INDEX idx_pnh_player_username_seen (player_id, username, last_seen_at)
);

CREATE TABLE network_service (
    id BIGINT NOT NULL AUTO_INCREMENT,
    service_kind VARCHAR(16) NOT NULL,
    service_name VARCHAR(96) NOT NULL,
    platform VARCHAR(32) NOT NULL,
    first_seen_at TIMESTAMP NOT NULL,
    last_seen_at TIMESTAMP NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_ns_kind_name UNIQUE (service_kind, service_name),
    INDEX idx_ns_last_seen_at (last_seen_at),
    INDEX idx_ns_service_name (service_name)
);

CREATE TABLE service_instance (
    id BIGINT NOT NULL AUTO_INCREMENT,
    service_id BIGINT NOT NULL,
    instance_id VARCHAR(36) NOT NULL,
    status VARCHAR(16) NOT NULL,
    host VARCHAR(255) NULL,
    port INTEGER NULL,
    started_at TIMESTAMP NOT NULL,
    last_seen_at TIMESTAMP NOT NULL,
    stopped_at TIMESTAMP NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_si_service FOREIGN KEY (service_id) REFERENCES network_service (id),
    CONSTRAINT uk_si_instance_id UNIQUE (instance_id),
    INDEX idx_si_service_last_seen (service_id, last_seen_at),
    INDEX idx_si_status_last_seen (status, last_seen_at),
    INDEX idx_si_status_host_port_seen (status, host, port, last_seen_at)
);

CREATE TABLE service_probe (
    id BIGINT NOT NULL AUTO_INCREMENT,
    service_id BIGINT NOT NULL,
    target_instance_id VARCHAR(36) NULL,
    observer_instance_id VARCHAR(36) NOT NULL,
    status VARCHAR(16) NOT NULL,
    target_host VARCHAR(255) NULL,
    target_port INTEGER NULL,
    latency_millis BIGINT NULL,
    error_code VARCHAR(64) NULL,
    error_detail VARCHAR(255) NULL,
    checked_at TIMESTAMP NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_sp_service FOREIGN KEY (service_id) REFERENCES network_service (id),
    INDEX idx_sp_service_checked (service_id, checked_at),
    INDEX idx_sp_observer_checked (observer_instance_id, checked_at),
    INDEX idx_sp_status_checked (status, checked_at),
    INDEX idx_sp_checked_at (checked_at)
);
