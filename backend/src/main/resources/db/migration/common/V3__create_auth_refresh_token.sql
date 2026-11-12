CREATE TABLE auth_refresh_token (
    id BINARY(16) NOT NULL,
    session_id BINARY(16) NOT NULL,
    token_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    issued_at DATETIME(6) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    consumed_at DATETIME(6) NULL,
    revoked_at DATETIME(6) NULL,
    CONSTRAINT pk_auth_refresh_token PRIMARY KEY (id),
    CONSTRAINT uk_auth_refresh_token_hash UNIQUE (token_hash),
    CONSTRAINT fk_auth_refresh_token_session
        FOREIGN KEY (session_id) REFERENCES auth_session (id)
        ON UPDATE RESTRICT
        ON DELETE RESTRICT,
    CONSTRAINT chk_auth_refresh_token_expiry CHECK (expires_at > issued_at),
    INDEX ix_auth_refresh_token_session_expiry (session_id, expires_at)
) ENGINE=InnoDB
  DEFAULT CHARACTER SET=utf8mb4
  COLLATE=utf8mb4_0900_ai_ci;
