-- Obsolete cleanup:
-- Student purchased duration belongs to the registration form, not Price Row.
-- Some local databases may have applied an older draft that added these columns.
SET @drop_group_session_count = (
  SELECT IF(
    EXISTS (
      SELECT 1
      FROM information_schema.columns
      WHERE table_schema = DATABASE()
        AND table_name = 'course_price_option'
        AND column_name = 'group_session_count'
    ),
    'ALTER TABLE `course_price_option` DROP COLUMN `group_session_count`',
    'SELECT 1'
  )
);
PREPARE stmt FROM @drop_group_session_count;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @drop_group_month_count = (
  SELECT IF(
    EXISTS (
      SELECT 1
      FROM information_schema.columns
      WHERE table_schema = DATABASE()
        AND table_name = 'course_price_option'
        AND column_name = 'group_month_count'
    ),
    'ALTER TABLE `course_price_option` DROP COLUMN `group_month_count`',
    'SELECT 1'
  )
);
PREPARE stmt FROM @drop_group_month_count;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
