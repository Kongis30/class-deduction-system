-- Group attendance segments, teacher pay-rate snapshots, and 1-on-1 pause support.

ALTER TABLE class_enrollment
    ADD COLUMN attendance_option VARCHAR(32) NOT NULL DEFAULT 'FULL_DAY'
        COMMENT 'Registration attendance option: FULL_DAY/MORNING/AFTERNOON/NONE'
        AFTER purchased_quantity;

ALTER TABLE class_group_schedule_slot
    ADD COLUMN subject_name VARCHAR(128) NULL
        COMMENT 'Subject taught in this weekly class slot'
        AFTER teacher_id,
    ADD COLUMN hourly_rate_snapshot DECIMAL(10,2) NULL
        COMMENT 'Teacher hourly pay-rate snapshot for this weekly class slot'
        AFTER attendance_options;

ALTER TABLE lesson_occurrence
    ADD COLUMN hourly_rate_snapshot DECIMAL(10,2) NULL
        COMMENT 'Teacher hourly pay-rate snapshot copied from schedule/allocation'
        AFTER planned_units;

ALTER TABLE package_teacher_assignment
    ADD COLUMN subject_name VARCHAR(128) NULL
        COMMENT 'Subject assigned to this teacher allocation'
        AFTER allocated_units,
    ADD COLUMN hourly_rate_snapshot DECIMAL(10,2) NULL
        COMMENT 'Teacher hourly pay-rate snapshot for this allocation'
        AFTER subject_name;
