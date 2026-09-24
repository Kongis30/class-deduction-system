-- 将 1-on-1 报名前已使用课时单独保存到课时包，供排课页维护。
SELECT COUNT(*) INTO @done FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'course_package' AND COLUMN_NAME = 'opening_used_units';
SET @sql = IF(@done = 0,
  'ALTER TABLE `course_package` ADD COLUMN `opening_used_units` int NOT NULL DEFAULT 0 COMMENT ''Hours consumed before lesson tracking, in half-hour units'' AFTER `consumed_units`',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 仅首次迁移时回填历史报名值；后续调整保存在 course_package，不被收据快照覆盖。
SET @should_backfill = @done = 0;
SET @sql = IF(@should_backfill,
  'UPDATE course_package cp INNER JOIN enrollment_order_item item ON item.id = cp.enrollment_order_item_id SET cp.opening_used_units = LEAST(GREATEST(ROUND(COALESCE(item.opening_used_hours, 0) * 2), 0), GREATEST(COALESCE(cp.consumed_units, 0), 0)) WHERE cp.teaching_mode = ''ONE_ON_ONE'' AND item.opening_used_hours > 0',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
