CREATE TEMPORARY TABLE repair_target (
  id BIGINT PRIMARY KEY,
  expected_start DATETIME(3) NOT NULL
);
INSERT INTO repair_target (id, expected_start) VALUES
(2109319832643674113, '2026-11-14 16:30:00.000'),
(2109319832664645633, '2026-11-20 18:00:00.000'),
(2104073412810448898, '2027-03-20 16:00:00.000'),
(2109319833193127937, '2027-04-03 16:00:00.000'),
(2106423030546169858, '2026-12-19 15:00:00.000'),
(2103346563637628929, '2026-12-24 18:30:00.000'),
(2106423030567141377, '2026-12-31 18:30:00.000'),
(2106423030940434433, '2026-10-29 07:30:00.000'),
(2106423030944628738, '2026-10-30 07:30:00.000'),
(2106423030948823042, '2026-11-20 07:30:00.000'),
(2104449078420582401, '2026-11-29 17:00:00.000'),
(2104449078118592513, '2026-11-01 11:00:00.000'),
(2106423030869131266, '2026-10-30 20:00:00.000'),
(2106423030881714178, '2026-11-03 07:30:00.000'),
(2106423030885908482, '2026-11-04 07:30:00.000'),
(2104449077946626050, '2026-11-14 15:00:00.000');
DELIMITER $$
CREATE PROCEDURE repair_excess_one_on_one_occurrences()
BEGIN
  DECLARE matched_count INT DEFAULT 0;
  DECLARE changed_count INT DEFAULT 0;
  DECLARE EXIT HANDLER FOR SQLEXCEPTION
  BEGIN
    ROLLBACK;
    RESIGNAL;
  END;
  START TRANSACTION;
  SELECT COUNT(*) INTO matched_count
  FROM repair_target t
  JOIN lesson_occurrence o ON o.id=t.id
  JOIN recurring_schedule_rule r ON r.id=o.schedule_rule_id
  JOIN recurring_schedule_slot sl ON sl.id=o.schedule_slot_id AND sl.rule_id=r.id
  WHERE o.planned_start_at=t.expected_start
    AND o.planned_units=4 AND o.occurrence_type='REGULAR'
    AND o.planning_status='SCHEDULED' AND o.is_deleted=0 AND o.version=0
    AND r.rule_status='ACTIVE' AND r.is_deleted=0
    AND sl.slot_status='ACTIVE' AND sl.is_deleted=0
    AND NOT EXISTS (SELECT 1 FROM lesson_record lr WHERE lr.occurrence_id=o.id);
  IF matched_count<>16 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Candidate occurrence changed; no correction applied';
  END IF;
  UPDATE lesson_occurrence o JOIN repair_target t ON t.id=o.id
  SET o.planning_status='CANCELLED', o.version=o.version+1, o.updated_at=NOW(3)
  WHERE o.planned_start_at=t.expected_start
    AND o.planned_units=4 AND o.occurrence_type='REGULAR'
    AND o.planning_status='SCHEDULED' AND o.is_deleted=0 AND o.version=0;
  SET changed_count=ROW_COUNT();
  IF changed_count<>16 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Partial occurrence correction blocked and rolled back';
  END IF;
  COMMIT;
  SELECT changed_count AS technical_cancellations;
END$$
DELIMITER ;
CALL repair_excess_one_on_one_occurrences();
DROP PROCEDURE repair_excess_one_on_one_occurrences;
DROP TEMPORARY TABLE repair_target;
