ALTER TABLE lesson_record
    ADD COLUMN planned_start_at datetime(3) NULL COMMENT 'Planned start time snapshot' AFTER actual_classroom_id,
    ADD COLUMN planned_end_at datetime(3) NULL COMMENT 'Planned end time snapshot' AFTER planned_start_at,
    ADD COLUMN planned_units int NULL COMMENT 'Planned units snapshot' AFTER planned_end_at;

UPDATE lesson_record lr
JOIN lesson_occurrence lo ON lr.occurrence_id = lo.id
SET lr.planned_start_at = COALESCE(lr.planned_start_at, lo.planned_start_at),
    lr.planned_end_at = COALESCE(lr.planned_end_at, lo.planned_end_at),
    lr.planned_units = COALESCE(lr.planned_units, lo.planned_units)
WHERE lr.is_deleted = 0;
