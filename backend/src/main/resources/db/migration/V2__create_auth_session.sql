CREATE TABLE auth_session (
    id BINARY(16) NOT NULL,
    user_id BINARY(16) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    last_rotated_at DATETIME(6) NULL,
    expires_at DATETIME(6) NOT NULL,
    revoked_at DATETIME(6) NULL,
    revocation_reason VARCHAR(32) NULL,
    CONSTRAINT pk_auth_session PRIMARY KEY (id),
    CONSTRAINT fk_auth_session_user
        FOREIGN KEY (user_id) REFERENCES app_user (id)
        ON UPDATE RESTRICT
        ON DELETE RESTRICT,
    CONSTRAINT chk_auth_session_expiry CHECK (expires_at > created_at),
    INDEX ix_auth_session_user (user_id),
    INDEX ix_auth_session_expiry (expires_at)
) ENGINE=InnoDB
  DEFAULT CHARACTER SET=utf8mb4
  COLLATE=utf8mb4_0900_ai_ci;
