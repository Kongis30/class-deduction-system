DELIMITER $$
CREATE PROCEDURE repair_terry_estimated_end()
BEGIN
 DECLARE changed_count INT DEFAULT 0;
 DECLARE EXIT HANDLER FOR SQLEXCEPTION
 BEGIN
  ROLLBACK;
  RESIGNAL;
 END;
 START TRANSACTION;
 UPDATE course_package p
 SET p.estimated_end_date='2027-03-18',p.version=p.version+1,p.updated_at=NOW(3)
 WHERE p.id=2103129684612534273 AND p.package_status='ACTIVE' AND p.is_deleted=0
   AND p.estimated_end_date='2027-02-02' AND p.version=7 AND p.remaining_units=280
   AND (SELECT MAX(DATE(o.planned_end_at)) FROM lesson_occurrence o
        WHERE o.package_id=p.id AND o.schedule_rule_id=2103130135932227586
          AND o.is_deleted=0 AND o.planning_status NOT IN ('CANCELLED','MERGED'))='2027-03-18';
 SET changed_count=ROW_COUNT();
 IF changed_count<>1 THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Package or planned tail changed; end date correction aborted';
 END IF;
 COMMIT;
 SELECT changed_count AS updated_estimated_end_dates;
END$$
DELIMITER ;
CALL repair_terry_estimated_end();
DROP PROCEDURE repair_terry_estimated_end;
