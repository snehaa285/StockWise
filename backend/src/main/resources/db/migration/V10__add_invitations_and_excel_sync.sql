CREATE TABLE business_invitation (
    id BINARY(16) NOT NULL,
    business_id BINARY(16) NOT NULL,
    email_normalized VARCHAR(254) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
    role VARCHAR(16) NOT NULL,
    token_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    status VARCHAR(16) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    created_by_user_id BINARY(16) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    responded_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_business_invitation_token_hash UNIQUE (token_hash),
    CONSTRAINT chk_business_invitation_role CHECK (role IN ('ADMIN', 'MEMBER')),
    CONSTRAINT chk_business_invitation_status
        CHECK (status IN ('PENDING', 'ACCEPTED', 'DECLINED', 'REVOKED', 'EXPIRED')),
    CONSTRAINT fk_business_invitation_business
        FOREIGN KEY (business_id) REFERENCES business (id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_business_invitation_creator
        FOREIGN KEY (created_by_user_id) REFERENCES app_user (id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    INDEX ix_business_invitation_business_status (business_id, status, created_at),
    INDEX ix_business_invitation_email_status (email_normalized, status, expires_at)
) ENGINE=InnoDB
  DEFAULT CHARACTER SET=utf8mb4
  COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE microsoft_onedrive_connection (
    business_id BINARY(16) NOT NULL,
    connected_by_user_id BINARY(16) NOT NULL,
    encrypted_access_token TEXT NOT NULL,
    encrypted_refresh_token TEXT NOT NULL,
    access_token_expires_at DATETIME(6) NOT NULL,
    drive_item_id VARCHAR(512) NULL,
    authorization_status VARCHAR(24) NOT NULL,
    connected_at DATETIME(6) NOT NULL,
    last_synced_at DATETIME(6) NULL,
    PRIMARY KEY (business_id),
    CONSTRAINT chk_microsoft_connection_status
        CHECK (authorization_status IN ('CONNECTED', 'REAUTH_REQUIRED')),
    CONSTRAINT fk_microsoft_connection_business
        FOREIGN KEY (business_id) REFERENCES business (id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_microsoft_connection_user
        FOREIGN KEY (connected_by_user_id) REFERENCES app_user (id)
        ON UPDATE RESTRICT ON DELETE RESTRICT
) ENGINE=InnoDB
  DEFAULT CHARACTER SET=utf8mb4
  COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE microsoft_oauth_state (
    id BINARY(16) NOT NULL,
    business_id BINARY(16) NOT NULL,
    user_id BINARY(16) NOT NULL,
    state_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    encrypted_code_verifier TEXT NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_microsoft_oauth_state_hash UNIQUE (state_hash),
    CONSTRAINT fk_microsoft_oauth_state_business
        FOREIGN KEY (business_id) REFERENCES business (id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_microsoft_oauth_state_user
        FOREIGN KEY (user_id) REFERENCES app_user (id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    INDEX ix_microsoft_oauth_state_expiry (expires_at)
) ENGINE=InnoDB
  DEFAULT CHARACTER SET=utf8mb4
  COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE excel_sync_outbox (
    id BINARY(16) NOT NULL,
    business_id BINARY(16) NOT NULL,
    event_key VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    status VARCHAR(16) NOT NULL,
    attempt_count INT UNSIGNED NOT NULL DEFAULT 0,
    next_attempt_at DATETIME(6) NOT NULL,
    last_error VARCHAR(512) NULL,
    created_at DATETIME(6) NOT NULL,
    locked_at DATETIME(6) NULL,
    completed_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_excel_sync_outbox_business_event UNIQUE (business_id, event_key),
    CONSTRAINT chk_excel_sync_outbox_status
        CHECK (status IN ('PENDING', 'PROCESSING', 'SUCCEEDED', 'FAILED')),
    CONSTRAINT fk_excel_sync_outbox_business
        FOREIGN KEY (business_id) REFERENCES business (id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    INDEX ix_excel_sync_outbox_work (status, next_attempt_at, created_at),
    INDEX ix_excel_sync_outbox_business_status (business_id, status, created_at)
) ENGINE=InnoDB
  DEFAULT CHARACTER SET=utf8mb4
  COLLATE=utf8mb4_0900_ai_ci;
