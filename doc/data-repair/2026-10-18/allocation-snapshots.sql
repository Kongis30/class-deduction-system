DROP PROCEDURE IF EXISTS repair_one_on_one_alloc_20261018;
DELIMITER //
CREATE PROCEDURE repair_one_on_one_alloc_20261018()
BEGIN
  DECLARE changed_count INT DEFAULT 0;
  DECLARE EXIT HANDLER FOR SQLEXCEPTION
  BEGIN
    ROLLBACK;
    RESIGNAL;
  END;
  START TRANSACTION;
  UPDATE recurring_schedule_slot SET allocated_units=8,version=version+1 WHERE id=2105452959699095554 AND allocated_units=16 AND version=0 AND is_deleted=0 AND slot_status='ACTIVE';
  SET changed_count=changed_count+ROW_COUNT();
  UPDATE recurring_schedule_slot SET allocated_units=100,version=version+1 WHERE id=2109293271370518530 AND allocated_units=112 AND version=0 AND is_deleted=0 AND slot_status='ACTIVE';
  SET changed_count=changed_count+ROW_COUNT();
  UPDATE recurring_schedule_slot SET allocated_units=100,version=version+1 WHERE id=2109293271387295746 AND allocated_units=108 AND version=0 AND is_deleted=0 AND slot_status='ACTIVE';
  SET changed_count=changed_count+ROW_COUNT();
  UPDATE recurring_schedule_slot SET allocated_units=72,version=version+1 WHERE id=2109314833729540098 AND allocated_units=80 AND version=0 AND is_deleted=0 AND slot_status='ACTIVE';
  SET changed_count=changed_count+ROW_COUNT();
  UPDATE recurring_schedule_slot SET allocated_units=24,version=version+1 WHERE id=2107571685491748865 AND allocated_units=28 AND version=0 AND is_deleted=0 AND slot_status='ACTIVE';
  SET changed_count=changed_count+ROW_COUNT();
  UPDATE recurring_schedule_slot SET allocated_units=24,version=version+1 WHERE id=2109314934220869633 AND allocated_units=32 AND version=0 AND is_deleted=0 AND slot_status='ACTIVE';
  SET changed_count=changed_count+ROW_COUNT();
  UPDATE recurring_schedule_slot SET allocated_units=68,version=version+1 WHERE id=2109362533124648961 AND allocated_units=80 AND version=0 AND is_deleted=0 AND slot_status='ACTIVE';
  SET changed_count=changed_count+ROW_COUNT();
  UPDATE recurring_schedule_slot SET allocated_units=20,version=version+1 WHERE id=2103859929829462017 AND allocated_units=24 AND version=0 AND is_deleted=0 AND slot_status='ACTIVE';
  SET changed_count=changed_count+ROW_COUNT();
  UPDATE recurring_schedule_slot SET allocated_units=84,version=version+1 WHERE id=2110567404498587649 AND allocated_units=92 AND version=0 AND is_deleted=0 AND slot_status='ACTIVE';
  SET changed_count=changed_count+ROW_COUNT();
  UPDATE recurring_schedule_slot SET allocated_units=209,version=version+1 WHERE id=2110567404502781954 AND allocated_units=224 AND version=0 AND is_deleted=0 AND slot_status='ACTIVE';
  SET changed_count=changed_count+ROW_COUNT();
  UPDATE recurring_schedule_slot SET allocated_units=86,version=version+1 WHERE id=2110567404456644610 AND allocated_units=94 AND version=0 AND is_deleted=0 AND slot_status='ACTIVE';
  SET changed_count=changed_count+ROW_COUNT();
  UPDATE recurring_schedule_slot SET allocated_units=68,version=version+1 WHERE id=2104302629464682497 AND allocated_units=72 AND version=0 AND is_deleted=0 AND slot_status='ACTIVE';
  SET changed_count=changed_count+ROW_COUNT();
  UPDATE recurring_schedule_slot SET allocated_units=32,version=version+1 WHERE id=2104050533041459202 AND allocated_units=36 AND version=0 AND is_deleted=0 AND slot_status='ACTIVE';
  SET changed_count=changed_count+ROW_COUNT();
  IF changed_count <> 13 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Allocation repair precondition failed; rolled back';
  END IF;
  COMMIT;
  SELECT changed_count AS changed_slots;
END//
DELIMITER ;
CALL repair_one_on_one_alloc_20261018();
DROP PROCEDURE repair_one_on_one_alloc_20261018;
