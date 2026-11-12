ALTER TABLE microsoft_onedrive_connection
    DROP CHECK chk_microsoft_connection_status,
    MODIFY COLUMN encrypted_access_token TEXT NULL,
    MODIFY COLUMN encrypted_refresh_token TEXT NULL,
    MODIFY COLUMN access_token_expires_at DATETIME(6) NULL,
    ADD COLUMN microsoft_account_id VARCHAR(128) NULL AFTER connected_by_user_id,
    ADD COLUMN microsoft_account_email VARCHAR(254) NULL AFTER microsoft_account_id,
    ADD CONSTRAINT chk_microsoft_connection_status
        CHECK (authorization_status IN ('CONNECTED', 'REAUTH_REQUIRED', 'DISCONNECTED'));
