-- 课包显式累计赠课，避免把历史续费金额误算为 Bonus。
SET @bonus_units_missing = (
    SELECT COUNT(*) = 0
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'course_package'
      AND COLUMN_NAME = 'bonus_units'
);
SET @sql = IF(
    @bonus_units_missing,
    'ALTER TABLE course_package ADD COLUMN bonus_units INT NOT NULL DEFAULT 0 COMMENT ''Explicit bonus hours granted, in half-hour units'' AFTER total_units',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 仅回填可从当前绑定订单行或赠课调整流水明确确认的赠课。
-- 旧续费会覆盖课包的订单行关联，因此不从课包总数与最新订单差额推断历史赠课。
SET @sql = IF(
    @bonus_units_missing,
    'UPDATE course_package cp
     LEFT JOIN enrollment_order_item item ON item.id = cp.enrollment_order_item_id
     LEFT JOIN (
         SELECT package_id, SUM(GREATEST(change_units, 0)) AS bonus_units
         FROM hour_ledger
         WHERE is_deleted = 0 AND reason_code = ''BONUS_HOURS''
         GROUP BY package_id
     ) ledger_bonus ON ledger_bonus.package_id = cp.id
     SET cp.bonus_units = GREATEST(COALESCE(ledger_bonus.bonus_units, 0), 0)
         + CASE
             WHEN cp.teaching_mode = ''ONE_ON_ONE''
               AND item.one_on_one_units IS NOT NULL
               AND item.one_on_one_hours IS NOT NULL
             THEN GREATEST(item.one_on_one_units - ROUND(item.one_on_one_hours * 2), 0)
             ELSE 0
           END
     WHERE cp.teaching_mode = ''ONE_ON_ONE''',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
