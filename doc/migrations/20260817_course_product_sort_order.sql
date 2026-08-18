ALTER TABLE course_product
    ADD COLUMN sort_order INT NOT NULL DEFAULT 99 COMMENT 'Display order for course list sorting'
        AFTER teaching_mode;

CREATE INDEX idx_course_product_sort_order
    ON course_product (sort_order, created_at);
