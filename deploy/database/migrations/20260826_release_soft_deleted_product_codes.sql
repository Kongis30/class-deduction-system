-- Migration: release product_code held by soft-deleted course_product rows.
--
-- Context: the unique key `uk_course_product_code` spans all rows, including
-- soft-deleted ones (is_deleted=1), while the application-level uniqueness check only
-- looks at active rows and generateProductCode() picked the max PRD-#### across all
-- rows too. A soft-deleted row keeping its original code therefore caused
-- DuplicateKeyException 500s when an admin created a new course after deleting one
-- (the regenerated code collided with the tombstone row).
--
-- From now on deleteCourseProduct() tombstones the code on soft delete. This migration
-- fixes rows soft-deleted before that change by appending a "_DEL_<id>" suffix (kept
-- within the 64-char column limit), which is exactly the format the application uses.
--
-- Idempotent: only touches rows that are soft-deleted and not already tombstoned.

UPDATE `course_product`
SET `product_code` = CONCAT(
        LEFT(`product_code`, 32 - CHAR_LENGTH(CONCAT('_DEL_', `id`))),
        '_DEL_', `id`
    )
WHERE `is_deleted` = 1
  AND `product_code` NOT LIKE CONCAT('%\_DEL\_', `id`);
