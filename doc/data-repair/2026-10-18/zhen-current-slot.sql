-- Historical repair record. Already applied online on 2026-10-18.
-- 2026-09-26 09:00 completed lesson predates this active slot's creation
-- at 2026-09-26 23:31:37, so it belongs to the preceding schedule period.
DELIMITER $$
CREATE PROCEDURE repair_zhen_current_slot()
BEGIN
  DECLARE changed_count INT DEFAULT 0;
  DECLARE EXIT HANDLER FOR SQLEXCEPTION
  BEGIN
    ROLLBACK;
    RESIGNAL;
  END;
  START TRANSACTION;
  UPDATE recurring_schedule_slot
  SET allocated_units=24,version=version+1,updated_at=NOW(3)
  WHERE id=2104051305011499009
    AND rule_id=2104050839548612610
    AND teacher_id=2086093048800407554
    AND allocated_units=28 AND version=0
    AND slot_status='ACTIVE' AND is_deleted=0;
  SET changed_count=ROW_COUNT();
  IF changed_count<>1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Zhen slot changed; no correction applied';
  END IF;
  COMMIT;
  SELECT changed_count AS corrected_slots;
END$$
DELIMITER ;
CALL repair_zhen_current_slot();
DROP PROCEDURE repair_zhen_current_slot;
