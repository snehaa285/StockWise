ALTER TABLE microsoft_onedrive_connection
    ADD CONSTRAINT chk_microsoft_connection_status_tidb
        CHECK (authorization_status IN ('CONNECTED', 'REAUTH_REQUIRED', 'DISCONNECTED'));

ALTER TABLE microsoft_onedrive_connection
    MODIFY COLUMN encrypted_access_token TEXT NULL;

ALTER TABLE microsoft_onedrive_connection
    MODIFY COLUMN encrypted_refresh_token TEXT NULL;

ALTER TABLE microsoft_onedrive_connection
    MODIFY COLUMN access_token_expires_at DATETIME(6) NULL;

ALTER TABLE microsoft_onedrive_connection
    ADD COLUMN microsoft_account_id VARCHAR(128) NULL AFTER connected_by_user_id;

ALTER TABLE microsoft_onedrive_connection
    ADD COLUMN microsoft_account_email VARCHAR(254) NULL AFTER microsoft_account_id;

ALTER TABLE microsoft_onedrive_connection
    DROP CHECK chk_microsoft_connection_status;
