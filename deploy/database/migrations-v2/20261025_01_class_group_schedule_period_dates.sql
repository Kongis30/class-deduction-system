-- 给可从班级周期明确推导的旧班课时段回填日期，不猜测缺少周期的排课。
-- 仅处理未删除的班级和时段，且只处理起止日期都为空的旧记录。
DELIMITER $$

DROP PROCEDURE IF EXISTS add_class_group_schedule_period_dates_20261025$$
CREATE PROCEDURE add_class_group_schedule_period_dates_20261025()
BEGIN
    DECLARE active_database VARCHAR(64);
    DECLARE start_column_exists INT DEFAULT 0;
    DECLARE end_column_exists INT DEFAULT 0;

    SELECT DATABASE() INTO active_database;
    IF active_database <> 'class_deduction_system_v2' THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Run this migration only against class_deduction_system_v2.';
    END IF;

    SELECT COUNT(*) INTO start_column_exists
      FROM information_schema.columns
     WHERE table_schema = active_database
       AND table_name = 'class_group_schedule_slot'
       AND column_name = 'effective_start_date';
    IF start_column_exists = 0 THEN
        ALTER TABLE class_group_schedule_slot
            ADD COLUMN effective_start_date DATE DEFAULT NULL
                COMMENT 'First date of this class schedule period';
    END IF;

    SELECT COUNT(*) INTO end_column_exists
      FROM information_schema.columns
     WHERE table_schema = active_database
       AND table_name = 'class_group_schedule_slot'
       AND column_name = 'effective_end_date';
    IF end_column_exists = 0 THEN
        ALTER TABLE class_group_schedule_slot
            ADD COLUMN effective_end_date DATE DEFAULT NULL
                COMMENT 'Last date of this class schedule period';
    END IF;
END$$

CALL add_class_group_schedule_period_dates_20261025()$$
DROP PROCEDURE add_class_group_schedule_period_dates_20261025$$

DELIMITER ;

-- Existing UI already uses these class-level dates as the fallback for old slots,
-- so this only makes that known period explicit on each slot.
START TRANSACTION;

UPDATE class_group_schedule_slot slot
JOIN class_group class_row ON class_row.id = slot.class_group_id
SET slot.effective_start_date = class_row.start_date,
    slot.effective_end_date = COALESCE(class_row.schedule_base_end_date, class_row.end_date)
WHERE slot.is_deleted = 0
  AND class_row.is_deleted = 0
  AND slot.effective_start_date IS NULL
  AND slot.effective_end_date IS NULL
  AND class_row.start_date IS NOT NULL
  AND COALESCE(class_row.schedule_base_end_date, class_row.end_date) IS NOT NULL
  AND COALESCE(class_row.schedule_base_end_date, class_row.end_date) >= class_row.start_date;

COMMIT;

SELECT COUNT(*) AS remaining_slots_without_inferable_dates
FROM class_group_schedule_slot slot
JOIN class_group class_row ON class_row.id = slot.class_group_id
WHERE slot.is_deleted = 0
  AND class_row.is_deleted = 0
  AND slot.effective_start_date IS NULL
  AND slot.effective_end_date IS NULL;
