-- Keep recurring allocation ownership and recurrence identity stable after one-off lesson adjustments.
DELIMITER $$

DROP PROCEDURE IF EXISTS add_one_on_one_adjustment_snapshots$$
CREATE PROCEDURE add_one_on_one_adjustment_snapshots()
BEGIN
    DECLARE owner_column_exists INT DEFAULT 0;
    DECLARE anchor_column_exists INT DEFAULT 0;

    SELECT COUNT(*) INTO owner_column_exists
      FROM information_schema.columns
     WHERE table_schema = DATABASE()
       AND table_name = 'lesson_occurrence'
       AND column_name = 'allocation_owner_teacher_id';
    IF owner_column_exists = 0 THEN
        ALTER TABLE lesson_occurrence
            ADD COLUMN allocation_owner_teacher_id bigint DEFAULT NULL
                COMMENT 'Teacher whose recurring slot owns this occurrence allocation'
                AFTER schedule_slot_id;
    END IF;

    SELECT COUNT(*) INTO anchor_column_exists
      FROM information_schema.columns
     WHERE table_schema = DATABASE()
       AND table_name = 'lesson_occurrence'
       AND column_name = 'allocation_anchor_date';
    IF anchor_column_exists = 0 THEN
        ALTER TABLE lesson_occurrence
            ADD COLUMN allocation_anchor_date date DEFAULT NULL
                COMMENT 'Original recurring date retained when this occurrence is adjusted'
                AFTER allocation_owner_teacher_id;
    END IF;

    UPDATE lesson_occurrence occurrence
    JOIN recurring_schedule_slot slot ON slot.id = occurrence.schedule_slot_id
       SET occurrence.allocation_owner_teacher_id = slot.teacher_id
     WHERE occurrence.is_deleted = 0
       AND occurrence.allocation_owner_teacher_id IS NULL
       AND slot.teacher_id IS NOT NULL;

    UPDATE lesson_occurrence occurrence
       SET occurrence.allocation_anchor_date = COALESCE(
           (
               SELECT DATE(STR_TO_DATE(
                   REPLACE(JSON_UNQUOTE(JSON_EXTRACT(log.before_snapshot, '$.plannedStartAt')), 'T', ' '),
                   '%Y-%m-%d %H:%i:%s'))
                 FROM lesson_adjustment_log log
                WHERE log.occurrence_id = occurrence.id
                  AND log.adjustment_type IN ('RESCHEDULE', 'WEEKLY_TEMP_UPDATE')
                  AND JSON_EXTRACT(log.before_snapshot, '$.plannedStartAt') IS NOT NULL
                ORDER BY log.adjusted_at, log.id
                LIMIT 1
           ), DATE(occurrence.planned_start_at))
     WHERE occurrence.is_deleted = 0
       AND occurrence.schedule_slot_id IS NOT NULL
       AND occurrence.allocation_anchor_date IS NULL;
END$$

CALL add_one_on_one_adjustment_snapshots()$$
DROP PROCEDURE add_one_on_one_adjustment_snapshots$$

DELIMITER ;
