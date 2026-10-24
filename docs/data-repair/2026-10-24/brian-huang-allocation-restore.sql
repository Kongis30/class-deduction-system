-- Restore Brian Huang's current Mr. David slot after validating the current-period lesson timeline.
-- Stop if ROW_COUNT() is not 1; never relax the expected-value/version predicates.
START TRANSACTION;

UPDATE recurring_schedule_slot
SET allocated_units = 80,
    version = version + 1,
    updated_at = CURRENT_TIMESTAMP(3)
WHERE id = 2113984610943234050
  AND rule_id = 2104091773195853825
  AND teacher_id = 2102806110341267458
  AND allocated_units = 72
  AND version = 0
  AND slot_status = 'ACTIVE'
  AND is_deleted = 0;

SELECT ROW_COUNT() AS changed_slots;
SELECT id, rule_id, teacher_id, allocated_units, version, slot_status, is_deleted
FROM recurring_schedule_slot
WHERE id = 2113984610943234050;

COMMIT;
