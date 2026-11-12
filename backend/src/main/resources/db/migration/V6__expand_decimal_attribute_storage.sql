ALTER TABLE category_attribute_definition
    MODIFY COLUMN min_decimal DECIMAL(24, 6) NULL,
    MODIFY COLUMN max_decimal DECIMAL(24, 6) NULL;

ALTER TABLE product_attribute_value
    MODIFY COLUMN decimal_value DECIMAL(24, 6) NULL;
