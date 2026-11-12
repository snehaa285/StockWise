CREATE TABLE product_category (
    id BINARY(16) NOT NULL,
    business_id BINARY(16) NOT NULL,
    name VARCHAR(120) NOT NULL,
    normalized_name VARCHAR(120) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
    archived_at DATETIME(6) NULL,
    active_name VARCHAR(120) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin
        GENERATED ALWAYS AS (
            CASE WHEN archived_at IS NULL THEN normalized_name ELSE NULL END
        ) STORED,
    description VARCHAR(500) NULL,
    batch_tracking_default BOOLEAN NOT NULL,
    expiry_tracking_default BOOLEAN NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT pk_product_category PRIMARY KEY (id),
    CONSTRAINT fk_product_category_business
        FOREIGN KEY (business_id) REFERENCES business (id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT uk_product_category_business_id UNIQUE (business_id, id),
    CONSTRAINT uk_product_category_active_name UNIQUE (business_id, active_name),
    INDEX ix_product_category_business_archive (business_id, archived_at)
) ENGINE=InnoDB
  DEFAULT CHARACTER SET=utf8mb4
  COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE product (
    id BINARY(16) NOT NULL,
    business_id BINARY(16) NOT NULL,
    category_id BINARY(16) NOT NULL,
    name VARCHAR(160) NOT NULL,
    normalized_name VARCHAR(160) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
    sku VARCHAR(100) NULL,
    normalized_sku VARCHAR(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NULL,
    archived_at DATETIME(6) NULL,
    active_sku VARCHAR(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin
        GENERATED ALWAYS AS (
            CASE WHEN archived_at IS NULL THEN normalized_sku ELSE NULL END
        ) STORED,
    unit_of_measure VARCHAR(40) NOT NULL,
    description VARCHAR(1000) NULL,
    batch_tracking_override BOOLEAN NULL,
    expiry_tracking_override BOOLEAN NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT pk_product PRIMARY KEY (id),
    CONSTRAINT fk_product_business
        FOREIGN KEY (business_id) REFERENCES business (id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_product_category
        FOREIGN KEY (business_id, category_id)
        REFERENCES product_category (business_id, id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT uk_product_business_category_id
        UNIQUE (business_id, category_id, id),
    CONSTRAINT uk_product_business_sku UNIQUE (business_id, active_sku),
    INDEX ix_product_business_category_archive (business_id, category_id, archived_at),
    INDEX ix_product_business_name (business_id, normalized_name)
) ENGINE=InnoDB
  DEFAULT CHARACTER SET=utf8mb4
  COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE category_attribute_definition (
    id BINARY(16) NOT NULL,
    business_id BINARY(16) NOT NULL,
    category_id BINARY(16) NOT NULL,
    name VARCHAR(120) NOT NULL,
    normalized_name VARCHAR(120) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
    archived_at DATETIME(6) NULL,
    active_name VARCHAR(120) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin
        GENERATED ALWAYS AS (
            CASE WHEN archived_at IS NULL THEN normalized_name ELSE NULL END
        ) STORED,
    attribute_type VARCHAR(20) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    is_required BOOLEAN NOT NULL,
    description VARCHAR(500) NULL,
    min_text_length INT NULL,
    max_text_length INT NULL,
    min_integer DECIMAL(65, 0) NULL,
    max_integer DECIMAL(65, 0) NULL,
    min_decimal DECIMAL(18, 6) NULL,
    max_decimal DECIMAL(18, 6) NULL,
    min_date DATE NULL,
    max_date DATE NULL,
    min_selections INT NULL,
    max_selections INT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT pk_category_attribute_definition PRIMARY KEY (id),
    CONSTRAINT fk_attribute_definition_category
        FOREIGN KEY (business_id, category_id)
        REFERENCES product_category (business_id, id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT uk_attribute_definition_business_category_id
        UNIQUE (business_id, category_id, id),
    CONSTRAINT uk_attribute_definition_active_name
        UNIQUE (category_id, active_name),
    CONSTRAINT chk_attribute_definition_type
        CHECK (attribute_type IN (
            'TEXT', 'INTEGER', 'DECIMAL', 'BOOLEAN', 'DATE', 'SINGLE_SELECT', 'MULTI_SELECT'
        )),
    CONSTRAINT chk_attribute_text_lengths
        CHECK (
            (min_text_length IS NULL OR min_text_length BETWEEN 0 AND 1000)
            AND (max_text_length IS NULL OR max_text_length BETWEEN 0 AND 1000)
            AND (min_text_length IS NULL OR max_text_length IS NULL
                 OR min_text_length <= max_text_length)
        ),
    CONSTRAINT chk_attribute_integer_range
        CHECK (min_integer IS NULL OR max_integer IS NULL OR min_integer <= max_integer),
    CONSTRAINT chk_attribute_decimal_range
        CHECK (min_decimal IS NULL OR max_decimal IS NULL OR min_decimal <= max_decimal),
    CONSTRAINT chk_attribute_date_range
        CHECK (min_date IS NULL OR max_date IS NULL OR min_date <= max_date),
    CONSTRAINT chk_attribute_selection_range
        CHECK (
            (min_selections IS NULL OR min_selections >= 0)
            AND (max_selections IS NULL OR max_selections >= 0)
            AND (min_selections IS NULL OR max_selections IS NULL
                 OR min_selections <= max_selections)
        ),
    CONSTRAINT chk_attribute_type_constraints
        CHECK (
            (attribute_type = 'TEXT'
                OR (min_text_length IS NULL AND max_text_length IS NULL))
            AND (attribute_type = 'INTEGER'
                OR (min_integer IS NULL AND max_integer IS NULL))
            AND (attribute_type = 'DECIMAL'
                OR (min_decimal IS NULL AND max_decimal IS NULL))
            AND (attribute_type = 'DATE'
                OR (min_date IS NULL AND max_date IS NULL))
            AND (attribute_type = 'MULTI_SELECT'
                OR (min_selections IS NULL AND max_selections IS NULL))
        ),
    INDEX ix_attribute_definition_category_archive (business_id, category_id, archived_at)
) ENGINE=InnoDB
  DEFAULT CHARACTER SET=utf8mb4
  COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE category_attribute_option (
    id BINARY(16) NOT NULL,
    business_id BINARY(16) NOT NULL,
    category_id BINARY(16) NOT NULL,
    definition_id BINARY(16) NOT NULL,
    label VARCHAR(120) NOT NULL,
    normalized_label VARCHAR(120) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
    is_active BOOLEAN NOT NULL,
    position INT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT pk_category_attribute_option PRIMARY KEY (id),
    CONSTRAINT fk_attribute_option_definition
        FOREIGN KEY (business_id, category_id, definition_id)
        REFERENCES category_attribute_definition (business_id, category_id, id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT uk_attribute_option_scope_id
        UNIQUE (business_id, category_id, definition_id, id),
    CONSTRAINT uk_attribute_option_label UNIQUE (definition_id, normalized_label),
    CONSTRAINT chk_attribute_option_position CHECK (position >= 0),
    INDEX ix_attribute_option_active_order (definition_id, is_active, position)
) ENGINE=InnoDB
  DEFAULT CHARACTER SET=utf8mb4
  COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE product_attribute_value (
    id BINARY(16) NOT NULL,
    business_id BINARY(16) NOT NULL,
    category_id BINARY(16) NOT NULL,
    product_id BINARY(16) NOT NULL,
    definition_id BINARY(16) NOT NULL,
    value_type VARCHAR(20) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    text_value VARCHAR(1000) NULL,
    integer_value DECIMAL(65, 0) NULL,
    decimal_value DECIMAL(18, 6) NULL,
    boolean_value BOOLEAN NULL,
    date_value DATE NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT pk_product_attribute_value PRIMARY KEY (id),
    CONSTRAINT uk_product_attribute_value_product_definition
        UNIQUE (product_id, definition_id),
    CONSTRAINT uk_product_attribute_value_scope
        UNIQUE (business_id, category_id, definition_id, id),
    CONSTRAINT fk_product_attribute_value_product
        FOREIGN KEY (business_id, category_id, product_id)
        REFERENCES product (business_id, category_id, id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_product_attribute_value_definition
        FOREIGN KEY (business_id, category_id, definition_id)
        REFERENCES category_attribute_definition (business_id, category_id, id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT chk_product_attribute_value_type
        CHECK (value_type IN (
            'TEXT', 'INTEGER', 'DECIMAL', 'BOOLEAN', 'DATE', 'SINGLE_SELECT', 'MULTI_SELECT'
        )),
    CONSTRAINT chk_product_attribute_value_shape
        CHECK (
            (value_type = 'TEXT' AND text_value IS NOT NULL
                AND integer_value IS NULL AND decimal_value IS NULL
                AND boolean_value IS NULL AND date_value IS NULL)
            OR (value_type = 'INTEGER' AND text_value IS NULL
                AND integer_value IS NOT NULL AND decimal_value IS NULL
                AND boolean_value IS NULL AND date_value IS NULL)
            OR (value_type = 'DECIMAL' AND text_value IS NULL
                AND integer_value IS NULL AND decimal_value IS NOT NULL
                AND boolean_value IS NULL AND date_value IS NULL)
            OR (value_type = 'BOOLEAN' AND text_value IS NULL
                AND integer_value IS NULL AND decimal_value IS NULL
                AND boolean_value IS NOT NULL AND date_value IS NULL)
            OR (value_type = 'DATE' AND text_value IS NULL
                AND integer_value IS NULL AND decimal_value IS NULL
                AND boolean_value IS NULL AND date_value IS NOT NULL)
            OR (value_type IN ('SINGLE_SELECT', 'MULTI_SELECT')
                AND text_value IS NULL AND integer_value IS NULL AND decimal_value IS NULL
                AND boolean_value IS NULL AND date_value IS NULL)
        ),
    INDEX ix_product_attribute_value_definition (definition_id, product_id)
) ENGINE=InnoDB
  DEFAULT CHARACTER SET=utf8mb4
  COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE product_attribute_selection (
    id BINARY(16) NOT NULL,
    business_id BINARY(16) NOT NULL,
    category_id BINARY(16) NOT NULL,
    definition_id BINARY(16) NOT NULL,
    value_id BINARY(16) NOT NULL,
    option_id BINARY(16) NOT NULL,
    CONSTRAINT pk_product_attribute_selection PRIMARY KEY (id),
    CONSTRAINT uk_product_attribute_selection_value_option UNIQUE (value_id, option_id),
    CONSTRAINT fk_product_attribute_selection_value
        FOREIGN KEY (business_id, category_id, definition_id, value_id)
        REFERENCES product_attribute_value (business_id, category_id, definition_id, id)
        ON UPDATE RESTRICT ON DELETE CASCADE,
    CONSTRAINT fk_product_attribute_selection_option
        FOREIGN KEY (business_id, category_id, definition_id, option_id)
        REFERENCES category_attribute_option (business_id, category_id, definition_id, id)
        ON UPDATE RESTRICT ON DELETE RESTRICT
) ENGINE=InnoDB
  DEFAULT CHARACTER SET=utf8mb4
  COLLATE=utf8mb4_0900_ai_ci;
