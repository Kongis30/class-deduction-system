-- 每个班课时段可单独配置两组老师/科目，并保留课次生成时的科目快照。
ALTER TABLE class_group_schedule_slot
    ADD COLUMN alternate_teacher_id BIGINT NULL COMMENT 'Teacher for the alternating lesson in this weekly slot' AFTER teacher_id,
    ADD COLUMN alternate_subject_name VARCHAR(128) NULL COMMENT 'Subject for the alternating lesson in this weekly slot' AFTER subject_name,
    ADD COLUMN alternate_delivery_mode VARCHAR(16) NULL COMMENT 'Delivery mode for the alternating lesson' AFTER alternate_subject_name,
    ADD COLUMN alternate_classroom_id BIGINT NULL COMMENT 'Classroom for the alternating lesson' AFTER alternate_delivery_mode,
    ADD COLUMN rotation_anchor_date DATE NULL COMMENT 'First scheduled date assigned to the primary teacher and subject' AFTER alternate_classroom_id;

ALTER TABLE lesson_occurrence
    ADD COLUMN subject_name VARCHAR(128) NULL COMMENT 'Subject snapshot copied from the weekly class slot' AFTER planned_teacher_id;
