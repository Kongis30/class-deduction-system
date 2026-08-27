-- Migration: release option_code held by soft-deleted course_price_option rows.
--
-- Context: the unique key `uk_course_price_option_code` spans all rows, including
-- soft-deleted ones (is_deleted=1), while the application-level uniqueness check only
-- looks at active rows. A soft-deleted row keeping its original code therefore caused
-- DuplicateKeyException 500s when an admin recreated the same course price option
-- (e.g. AFTER_SCHOOL_2026_2027_ESL, AFTER_SCHOOL_2026_2027_SATURDAY_CLASS_G3_6_ESL).
--
-- From now on deleteOption() tombstones the code on soft delete. This migration fixes
-- the rows soft-deleted before that change by appending a "_DEL_<id>" suffix (kept
-- within the 64-char column limit), which is exactly the format the application uses.
--
-- Idempotent: only touches rows that are soft-deleted and not already tombstoned.

UPDATE `course_price_option`
SET `option_code` = CONCAT(
        LEFT(`option_code`, 64 - CHAR_LENGTH(CONCAT('_DEL_', `id`))),
        '_DEL_', `id`
    )
WHERE `is_deleted` = 1
  AND `option_code` NOT LIKE CONCAT('%\_DEL\_', `id`);
