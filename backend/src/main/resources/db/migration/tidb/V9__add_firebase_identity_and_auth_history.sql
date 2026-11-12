ALTER TABLE auth_session
    ADD CONSTRAINT uk_auth_session_user_id_id UNIQUE (user_id, id);

ALTER TABLE app_user
    MODIFY COLUMN password_hash VARCHAR(255) NULL;

ALTER TABLE auth_session
    ADD COLUMN authentication_method VARCHAR(24) NOT NULL DEFAULT 'EMAIL_PASSWORD',
    ADD COLUMN user_agent VARCHAR(512) NULL;

CREATE TABLE auth_firebase_identity (
    id BINARY(16) NOT NULL,
    user_id BINARY(16) NOT NULL,
    firebase_uid VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT pk_auth_firebase_identity PRIMARY KEY (id),
    CONSTRAINT uk_auth_firebase_identity_user UNIQUE (user_id),
    CONSTRAINT uk_auth_firebase_identity_uid UNIQUE (firebase_uid),
    CONSTRAINT fk_auth_firebase_identity_user
        FOREIGN KEY (user_id) REFERENCES app_user (id)
        ON UPDATE RESTRICT
        ON DELETE RESTRICT
) ENGINE=InnoDB
  DEFAULT CHARACTER SET=utf8mb4
  COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE auth_login_event (
    id BINARY(16) NOT NULL,
    user_id BINARY(16) NULL,
    session_id BINARY(16) NULL,
    event_type VARCHAR(24) NOT NULL,
    authentication_method VARCHAR(24) NOT NULL,
    occurred_at DATETIME(6) NOT NULL,
    user_agent VARCHAR(512) NULL,
    CONSTRAINT pk_auth_login_event PRIMARY KEY (id),
    CONSTRAINT fk_auth_login_event_user
        FOREIGN KEY (user_id) REFERENCES app_user (id)
        ON UPDATE RESTRICT
        ON DELETE RESTRICT,
    CONSTRAINT fk_auth_login_event_session
        FOREIGN KEY (session_id) REFERENCES auth_session (id)
        ON UPDATE RESTRICT
        ON DELETE RESTRICT,
    CONSTRAINT fk_auth_login_event_session_owner
        FOREIGN KEY (user_id, session_id) REFERENCES auth_session (user_id, id)
        ON UPDATE RESTRICT
        ON DELETE RESTRICT,
    CONSTRAINT chk_auth_login_event_type
        CHECK (event_type IN ('LOGIN_SUCCESS', 'LOGIN_FAILURE', 'LOGOUT', 'SESSION_REVOKED')),
    CONSTRAINT chk_auth_login_event_method
        CHECK (authentication_method IN ('EMAIL_PASSWORD', 'GOOGLE')),
    INDEX ix_auth_login_event_user_time (user_id, occurred_at),
    INDEX ix_auth_login_event_user_session (user_id, session_id),
    INDEX ix_auth_login_event_session (session_id),
    INDEX ix_auth_login_event_retention (occurred_at)
) ENGINE=InnoDB
  DEFAULT CHARACTER SET=utf8mb4
  COLLATE=utf8mb4_0900_ai_ci;
