CREATE TABLE business (
    id BINARY(16) NOT NULL,
    name VARCHAR(120) NOT NULL,
    created_by BINARY(16) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT pk_business PRIMARY KEY (id),
    CONSTRAINT fk_business_creator
        FOREIGN KEY (created_by) REFERENCES app_user (id)
        ON UPDATE RESTRICT
        ON DELETE RESTRICT,
    INDEX ix_business_created_by (created_by)
) ENGINE=InnoDB
  DEFAULT CHARACTER SET=utf8mb4
  COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE business_membership (
    id BINARY(16) NOT NULL,
    business_id BINARY(16) NOT NULL,
    user_id BINARY(16) NOT NULL,
    member_role VARCHAR(10) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    created_at DATETIME(6) NOT NULL,
    removed_at DATETIME(6) NULL,
    active_owner_business_id BINARY(16)
        GENERATED ALWAYS AS (
            CASE
                WHEN member_role = 'OWNER' AND removed_at IS NULL THEN business_id
                ELSE NULL
            END
        ) STORED,
    active_membership_business_id BINARY(16)
        GENERATED ALWAYS AS (
            CASE WHEN removed_at IS NULL THEN business_id ELSE NULL END
        ) STORED,
    active_membership_user_id BINARY(16)
        GENERATED ALWAYS AS (
            CASE WHEN removed_at IS NULL THEN user_id ELSE NULL END
        ) STORED,
    CONSTRAINT pk_business_membership PRIMARY KEY (id),
    CONSTRAINT uk_business_membership_active_owner
        UNIQUE (active_owner_business_id),
    CONSTRAINT fk_business_membership_business
        FOREIGN KEY (business_id) REFERENCES business (id)
        ON UPDATE RESTRICT
        ON DELETE RESTRICT,
    CONSTRAINT fk_business_membership_user
        FOREIGN KEY (user_id) REFERENCES app_user (id)
        ON UPDATE RESTRICT
        ON DELETE RESTRICT,
    CONSTRAINT chk_business_membership_role
        CHECK (member_role IN ('OWNER', 'ADMIN', 'MEMBER')),
    CONSTRAINT uk_business_membership_active_user
        UNIQUE (active_membership_business_id, active_membership_user_id),
    INDEX ix_business_membership_business_removed (business_id, removed_at),
    INDEX ix_business_membership_user_removed (user_id, removed_at)
) ENGINE=InnoDB
  DEFAULT CHARACTER SET=utf8mb4
  COLLATE=utf8mb4_0900_ai_ci;
