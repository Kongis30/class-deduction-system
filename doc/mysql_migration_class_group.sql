-- ============================================================
-- Migration: Class Group & Class Enrollment tables
-- Target: Existing database that needs class group support
-- ============================================================

-- 1. Add class_group_id to lesson_occurrence (nullable)
ALTER TABLE `lesson_occurrence`
  ADD COLUMN `class_group_id` BIGINT DEFAULT NULL COMMENT 'Class group ID (null for 1v1 occurrences)' AFTER `student_id`,
  ADD KEY `idx_occurrence_class_group` (`class_group_id`),
  ADD CONSTRAINT `fk_occurrence_class_group` FOREIGN KEY (`class_group_id`) REFERENCES `class_group` (`id`);

-- 2. Make package_id and student_id nullable (class group occurrences don't have these)
ALTER TABLE `lesson_occurrence`
  MODIFY COLUMN `package_id` BIGINT DEFAULT NULL COMMENT 'Course package ID (null for class group occurrences)',
  MODIFY COLUMN `student_id` BIGINT DEFAULT NULL COMMENT 'Student ID (null for class group occurrences)';

-- 3. Create class_group table
CREATE TABLE IF NOT EXISTS `class_group` (
  `id` BIGINT NOT NULL COMMENT 'Primary key ID',
  `class_name` VARCHAR(128) NOT NULL COMMENT 'Class name',
  `description` VARCHAR(500) DEFAULT NULL COMMENT 'Class description',
  `teacher_id` BIGINT DEFAULT NULL COMMENT 'Assigned teacher ID',
  `classroom_id` BIGINT DEFAULT NULL COMMENT 'Default classroom ID',
  `schedule_desc` VARCHAR(255) DEFAULT NULL COMMENT 'Schedule description (e.g. Mon/Wed 9:00-10:30)',
  `status` VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' COMMENT 'Class status: ACTIVE/DISABLED',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT 'Created at',
  `created_by` BIGINT DEFAULT NULL COMMENT 'Created by user ID',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT 'Updated at',
  `updated_by` BIGINT DEFAULT NULL COMMENT 'Updated by user ID',
  `is_deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT 'Soft delete flag: 0=no, 1=yes',
  `deleted_at` DATETIME(3) DEFAULT NULL COMMENT 'Deleted at',
  `deleted_by` BIGINT DEFAULT NULL COMMENT 'Deleted by user ID',
  `version` INT NOT NULL DEFAULT 0 COMMENT 'Optimistic lock version',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_class_group_name` (`class_name`),
  KEY `idx_class_group_status` (`status`),
  KEY `idx_class_group_teacher` (`teacher_id`),
  CONSTRAINT `fk_class_group_teacher` FOREIGN KEY (`teacher_id`) REFERENCES `teacher` (`id`),
  CONSTRAINT `fk_class_group_classroom` FOREIGN KEY (`classroom_id`) REFERENCES `classroom` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Class groups for batch/group lesson management';

-- 4. Create class_enrollment table
CREATE TABLE IF NOT EXISTS `class_enrollment` (
  `id` BIGINT NOT NULL COMMENT 'Primary key ID',
  `class_id` BIGINT NOT NULL COMMENT 'Class group ID',
  `student_id` BIGINT NOT NULL COMMENT 'Student ID',
  `enrollment_date` DATE NOT NULL COMMENT 'Enrollment date',
  `expiry_date` DATE NOT NULL COMMENT 'Enrollment expiry date',
  `status` VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' COMMENT 'Enrollment status: ACTIVE/INACTIVE',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT 'Created at',
  `created_by` BIGINT DEFAULT NULL COMMENT 'Created by user ID',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT 'Updated at',
  `updated_by` BIGINT DEFAULT NULL COMMENT 'Updated by user ID',
  `is_deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT 'Soft delete flag: 0=no, 1=yes',
  `deleted_at` DATETIME(3) DEFAULT NULL COMMENT 'Deleted at',
  `deleted_by` BIGINT DEFAULT NULL COMMENT 'Deleted by user ID',
  `version` INT NOT NULL DEFAULT 0 COMMENT 'Optimistic lock version',
  PRIMARY KEY (`id`),
  KEY `idx_enrollment_class` (`class_id`),
  KEY `idx_enrollment_student` (`student_id`),
  KEY `idx_enrollment_status` (`status`),
  KEY `idx_enrollment_expiry` (`expiry_date`),
  CONSTRAINT `fk_enrollment_class` FOREIGN KEY (`class_id`) REFERENCES `class_group` (`id`),
  CONSTRAINT `fk_enrollment_student_user` FOREIGN KEY (`student_id`) REFERENCES `student` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Class enrollment records linking students to class groups';

-- 5. Seed a demo class (optional, for testing)
-- INSERT IGNORE INTO `class_group` (`id`, `class_name`, `description`, `status`)
-- VALUES (1001, 'Demo Class', 'Demo class for testing', 'ACTIVE');
