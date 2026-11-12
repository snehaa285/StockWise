CREATE TABLE business_inventory_settings (
    business_id BINARY(16) NOT NULL,
    near_expiry_days SMALLINT UNSIGNED NOT NULL DEFAULT 30,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (business_id),
    CONSTRAINT fk_business_inventory_settings_business
        FOREIGN KEY (business_id) REFERENCES business (id),
    CONSTRAINT chk_business_inventory_settings_near_expiry_days
        CHECK (near_expiry_days BETWEEN 1 AND 3650)
) ENGINE=InnoDB
  DEFAULT CHARACTER SET=utf8mb4
  COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE inventory_reorder_level (
    id BINARY(16) NOT NULL,
    business_id BINARY(16) NOT NULL,
    product_id BINARY(16) NOT NULL,
    location_id BINARY(16) NOT NULL,
    reorder_level DECIMAL(18,3) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_inventory_reorder_level_scope
        UNIQUE (business_id, product_id, location_id),
    CONSTRAINT ck_inventory_reorder_level_nonnegative CHECK (reorder_level >= 0),
    CONSTRAINT fk_inventory_reorder_level_business
        FOREIGN KEY (business_id) REFERENCES business (id),
    CONSTRAINT fk_inventory_reorder_level_product
        FOREIGN KEY (business_id, product_id) REFERENCES product (business_id, id),
    CONSTRAINT fk_inventory_reorder_level_location
        FOREIGN KEY (business_id, location_id)
        REFERENCES inventory_location (business_id, id),
    INDEX ix_inventory_reorder_level_location (business_id, location_id)
) ENGINE=InnoDB
  DEFAULT CHARACTER SET=utf8mb4
  COLLATE=utf8mb4_0900_ai_ci;
