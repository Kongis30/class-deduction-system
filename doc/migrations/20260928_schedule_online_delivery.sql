-- Add explicit per-slot online delivery while keeping classrooms for physical-room reservations.
ALTER TABLE `class_group_schedule_slot`
  ADD COLUMN `delivery_mode` varchar(16) DEFAULT NULL COMMENT 'IN_PERSON or ONLINE' AFTER `teacher_id`;

ALTER TABLE `recurring_schedule_slot`
  ADD COLUMN `delivery_mode` varchar(16) DEFAULT NULL COMMENT 'IN_PERSON or ONLINE' AFTER `teacher_id`,
  ADD COLUMN `classroom_id` bigint DEFAULT NULL COMMENT 'Classroom assigned to this weekly slot' AFTER `delivery_mode`;

ALTER TABLE `lesson_occurrence`
  ADD COLUMN `delivery_mode` varchar(16) DEFAULT NULL COMMENT 'Delivery mode snapshot: IN_PERSON or ONLINE' AFTER `planned_teacher_id`;

-- A non-null room is unambiguously an in-person lesson. Keep roomless legacy rows unknown.
UPDATE `class_group_schedule_slot`
SET `delivery_mode` = 'IN_PERSON'
WHERE `delivery_mode` IS NULL AND `classroom_id` IS NOT NULL;

UPDATE `recurring_schedule_slot`
SET `delivery_mode` = 'IN_PERSON'
WHERE `delivery_mode` IS NULL AND `classroom_id` IS NOT NULL;

UPDATE `lesson_occurrence`
SET `delivery_mode` = 'IN_PERSON'
WHERE `delivery_mode` IS NULL AND `planned_classroom_id` IS NOT NULL;
