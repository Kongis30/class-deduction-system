DELIMITER $$
CREATE PROCEDURE repair_terry_makeup()
BEGIN
  DECLARE matched_count INT DEFAULT 0;
  DECLARE changed_count INT DEFAULT 0;
  DECLARE source_count INT DEFAULT 0;
  DECLARE EXIT HANDLER FOR SQLEXCEPTION
  BEGIN
    ROLLBACK;
    RESIGNAL;
  END;
  START TRANSACTION;
  SELECT COUNT(*) INTO source_count
  FROM lesson_occurrence source
  JOIN lesson_record lr ON lr.occurrence_id=source.id
  WHERE source.id=2104087386511712258
    AND source.package_id=2103129684612534273
    AND source.schedule_rule_id=2103130135932227586
    AND source.schedule_slot_id=2104087386482352130
    AND source.planning_status='MERGED' AND source.is_deleted=0
    AND lr.confirm_stage='CONFIRMED' AND lr.result_status='FORMAL_LEAVE'
    AND lr.should_deduct=0 AND lr.deducted_units=0 AND lr.is_deleted=0;
  IF source_count<>1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Approved source leave changed; no correction applied';
  END IF;
  SELECT COUNT(*) INTO matched_count
  FROM lesson_occurrence o
  WHERE o.id=2108683905713954817
    AND o.package_id=2103129684612534273
    AND o.schedule_rule_id=2103130135932227586
    AND o.schedule_slot_id=2104087386478157826
    AND o.planned_teacher_id=2102797594360643586
    AND o.planned_start_at='2027-03-23 18:00:00.000'
    AND o.planned_units=4 AND o.occurrence_type='MAKEUP'
    AND o.source_occurrence_id=2104087386511712258
    AND o.leave_carryover_units=0
    AND o.planning_status='SCHEDULED' AND o.version=0 AND o.is_deleted=0
    AND NOT EXISTS (SELECT 1 FROM lesson_record lr WHERE lr.occurrence_id=o.id);
  IF matched_count<>1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Wrong-slot makeup changed; no correction applied';
  END IF;
  SELECT COUNT(*) INTO matched_count
  FROM lesson_occurrence o
  JOIN recurring_schedule_slot sl ON sl.id=o.schedule_slot_id
  WHERE o.id=2109459296003149826
    AND o.package_id=2103129684612534273
    AND o.schedule_rule_id=2103130135932227586
    AND o.schedule_slot_id=2104087386482352130
    AND o.planned_teacher_id=2102805280682770434
    AND o.planned_start_at='2027-02-26 18:00:00.000'
    AND o.planned_end_at='2027-02-26 20:00:00.000'
    AND o.planned_units=4 AND o.occurrence_type='REGULAR'
    AND o.source_occurrence_id IS NULL AND o.leave_carryover_units=0
    AND o.planning_status='SCHEDULED' AND o.version=0 AND o.is_deleted=0
    AND sl.rule_id=o.schedule_rule_id AND sl.teacher_id=o.planned_teacher_id
    AND sl.weekday=5 AND sl.slot_status='ACTIVE' AND sl.is_deleted=0
    AND NOT EXISTS (SELECT 1 FROM lesson_record lr WHERE lr.occurrence_id=o.id);
  IF matched_count<>1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Friday candidate changed; no correction applied';
  END IF;
  SELECT COUNT(*) INTO matched_count
  FROM lesson_occurrence o
  WHERE o.source_occurrence_id=2104087386511712258
    AND o.occurrence_type='MAKEUP' AND o.is_deleted=0
    AND o.planning_status NOT IN ('CANCELLED','MERGED');
  IF matched_count<>1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Source has unexpected active makeups';
  END IF;
  UPDATE lesson_occurrence
  SET occurrence_type='MAKEUP', source_occurrence_id=2104087386511712258,
      version=version+1, updated_at=NOW(3)
  WHERE id=2109459296003149826 AND version=0 AND occurrence_type='REGULAR'
    AND planning_status='SCHEDULED' AND is_deleted=0;
  SET changed_count=ROW_COUNT();
  IF changed_count<>1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Friday makeup conversion failed';
  END IF;
  UPDATE lesson_occurrence
  SET planning_status='CANCELLED', version=version+1, updated_at=NOW(3)
  WHERE id=2108683905713954817 AND version=0 AND occurrence_type='MAKEUP'
    AND planning_status='SCHEDULED' AND is_deleted=0;
  SET changed_count=ROW_COUNT();
  IF changed_count<>1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Wrong-slot makeup cancellation failed';
  END IF;
  COMMIT;
  SELECT 1 AS converted_friday_lesson, changed_count AS technical_cancellations;
END$$
DELIMITER ;
CALL repair_terry_makeup();
DROP PROCEDURE repair_terry_makeup;
