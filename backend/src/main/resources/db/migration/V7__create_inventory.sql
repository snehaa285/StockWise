ALTER TABLE product
    ADD CONSTRAINT uk_product_business_id UNIQUE (business_id, id);

CREATE TABLE inventory_location (
    id BINARY(16) NOT NULL,
    business_id BINARY(16) NOT NULL,
    name VARCHAR(160) NOT NULL,
    normalized_name VARCHAR(160) NOT NULL,
    active_name VARCHAR(160)
        GENERATED ALWAYS AS (
            CASE WHEN archived_at IS NULL THEN normalized_name ELSE NULL END
        ) STORED,
    description VARCHAR(1000) NULL,
    archived_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_inventory_location_business_id UNIQUE (business_id, id),
    CONSTRAINT uk_inventory_location_active_name UNIQUE (business_id, active_name),
    CONSTRAINT fk_inventory_location_business
        FOREIGN KEY (business_id) REFERENCES business (id),
    INDEX ix_inventory_location_business_archive (business_id, archived_at)
);

CREATE TABLE inventory_batch (
    id BINARY(16) NOT NULL,
    business_id BINARY(16) NOT NULL,
    product_id BINARY(16) NOT NULL,
    batch_code VARCHAR(120) NOT NULL,
    normalized_batch_code VARCHAR(120) NOT NULL,
    expiry_date DATE NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_inventory_batch_business_product_id
        UNIQUE (business_id, product_id, id),
    CONSTRAINT uk_inventory_batch_product_code
        UNIQUE (business_id, product_id, normalized_batch_code),
    CONSTRAINT fk_inventory_batch_product
        FOREIGN KEY (business_id, product_id)
        REFERENCES product (business_id, id),
    INDEX ix_inventory_batch_expiry (business_id, expiry_date)
);

CREATE TABLE inventory_operation (
    id BINARY(16) NOT NULL,
    business_id BINARY(16) NOT NULL,
    operation_type VARCHAR(24) NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    request_hash BINARY(32) NOT NULL,
    created_by_user_id BINARY(16) NOT NULL,
    result_json LONGTEXT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_inventory_operation_business_id
        UNIQUE (business_id, id),
    CONSTRAINT uk_inventory_operation_business_key
        UNIQUE (business_id, idempotency_key),
    CONSTRAINT fk_inventory_operation_business
        FOREIGN KEY (business_id) REFERENCES business (id),
    CONSTRAINT fk_inventory_operation_user
        FOREIGN KEY (created_by_user_id) REFERENCES app_user (id),
    INDEX ix_inventory_operation_created (business_id, created_at)
);

CREATE TABLE inventory_stock_balance (
    id BINARY(16) NOT NULL,
    business_id BINARY(16) NOT NULL,
    product_id BINARY(16) NOT NULL,
    location_id BINARY(16) NOT NULL,
    batch_id BINARY(16) NULL,
    batch_key BINARY(16)
        GENERATED ALWAYS AS (
            COALESCE(batch_id, X'00000000000000000000000000000000')
        ) STORED,
    quantity DECIMAL(18,3) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_inventory_stock_bucket
        UNIQUE (business_id, product_id, location_id, batch_key),
    CONSTRAINT ck_inventory_stock_nonnegative CHECK (quantity >= 0),
    CONSTRAINT ck_inventory_stock_batch_key CHECK (
        batch_id IS NULL OR batch_id <> X'00000000000000000000000000000000'
    ),
    CONSTRAINT fk_inventory_stock_business
        FOREIGN KEY (business_id) REFERENCES business (id),
    CONSTRAINT fk_inventory_stock_product
        FOREIGN KEY (business_id, product_id)
        REFERENCES product (business_id, id),
    CONSTRAINT fk_inventory_stock_location
        FOREIGN KEY (business_id, location_id)
        REFERENCES inventory_location (business_id, id),
    CONSTRAINT fk_inventory_stock_batch
        FOREIGN KEY (business_id, product_id, batch_id)
        REFERENCES inventory_batch (business_id, product_id, id),
    INDEX ix_inventory_stock_location (business_id, location_id),
    INDEX ix_inventory_stock_product (business_id, product_id)
);

CREATE TABLE inventory_movement (
    id BINARY(16) NOT NULL,
    business_id BINARY(16) NOT NULL,
    operation_id BINARY(16) NOT NULL,
    product_id BINARY(16) NOT NULL,
    location_id BINARY(16) NOT NULL,
    batch_id BINARY(16) NULL,
    movement_type VARCHAR(24) NOT NULL,
    quantity_delta DECIMAL(18,3) NOT NULL,
    quantity_after DECIMAL(18,3) NOT NULL,
    source_location_id BINARY(16) NULL,
    destination_location_id BINARY(16) NULL,
    reason VARCHAR(500) NULL,
    actor_user_id BINARY(16) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT ck_inventory_movement_nonzero CHECK (quantity_delta <> 0),
    CONSTRAINT ck_inventory_movement_nonnegative_after CHECK (quantity_after >= 0),
    CONSTRAINT fk_inventory_movement_business
        FOREIGN KEY (business_id) REFERENCES business (id),
    CONSTRAINT fk_inventory_movement_operation
        FOREIGN KEY (business_id, operation_id)
        REFERENCES inventory_operation (business_id, id),
    CONSTRAINT fk_inventory_movement_product
        FOREIGN KEY (business_id, product_id)
        REFERENCES product (business_id, id),
    CONSTRAINT fk_inventory_movement_location
        FOREIGN KEY (business_id, location_id)
        REFERENCES inventory_location (business_id, id),
    CONSTRAINT fk_inventory_movement_batch
        FOREIGN KEY (business_id, product_id, batch_id)
        REFERENCES inventory_batch (business_id, product_id, id),
    CONSTRAINT fk_inventory_movement_source_location
        FOREIGN KEY (business_id, source_location_id)
        REFERENCES inventory_location (business_id, id),
    CONSTRAINT fk_inventory_movement_destination_location
        FOREIGN KEY (business_id, destination_location_id)
        REFERENCES inventory_location (business_id, id),
    CONSTRAINT fk_inventory_movement_actor
        FOREIGN KEY (actor_user_id) REFERENCES app_user (id),
    INDEX ix_inventory_movement_history (business_id, created_at, id),
    INDEX ix_inventory_movement_product_history (business_id, product_id, created_at),
    INDEX ix_inventory_movement_location_history (business_id, location_id, created_at)
);
