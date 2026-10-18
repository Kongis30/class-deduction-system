CREATE TEMPORARY TABLE end_date_target (
 id BIGINT PRIMARY KEY,
 old_end DATE NOT NULL,
 new_end DATE NOT NULL,
 old_version INT NOT NULL
);
INSERT INTO end_date_target VALUES
(2103120080113553410,'2026-12-24','2026-12-17',3),
(2104073185609195521,'2026-11-12','2026-11-19',5),
(2104073998469505026,'2026-11-29','2026-11-28',3);
DELIMITER $$
CREATE PROCEDURE repair_one_on_one_estimated_end()
BEGIN
 DECLARE matches INT DEFAULT 0;
 DECLARE changed INT DEFAULT 0;
 DECLARE EXIT HANDLER FOR SQLEXCEPTION
 BEGIN
  ROLLBACK;
  RESIGNAL;
 END;
 START TRANSACTION;
 SELECT COUNT(*) INTO matches
 FROM end_date_target t JOIN course_package p ON p.id=t.id
 WHERE p.estimated_end_date=t.old_end AND p.version=t.old_version AND p.is_deleted=0
  AND p.package_status='ACTIVE'
  AND t.new_end=(SELECT MAX(DATE(o.planned_end_at)) FROM lesson_occurrence o
      WHERE o.package_id=p.id AND o.is_deleted=0 AND o.planning_status NOT IN ('CANCELLED','MERGED'));
 IF matches<>3 THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Schedule tail or package changed; no estimated dates updated';
 END IF;
 UPDATE course_package p JOIN end_date_target t ON t.id=p.id
 SET p.estimated_end_date=t.new_end,p.version=p.version+1,p.updated_at=NOW(3)
 WHERE p.estimated_end_date=t.old_end AND p.version=t.old_version;
 SET changed=ROW_COUNT();
 IF changed<>3 THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Partial date update blocked and rolled back';
 END IF;
 COMMIT;
 SELECT changed AS corrected_estimated_end_dates;
END$$
DELIMITER ;
CALL repair_one_on_one_estimated_end();
DROP PROCEDURE repair_one_on_one_estimated_end;
DROP TEMPORARY TABLE end_date_target;
