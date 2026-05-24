-- JIN BEST TUTORING class deduction and scheduling system
-- MySQL 8.4 schema draft
-- Notes:
-- 1. All primary keys use BIGINT. Generate them with an application-side snowflake strategy or a similar ID allocator.
-- 2. The minimum lesson unit is 0.5 hours and is stored through *_units fields.
-- 3. 1 unit = 0.5h
-- 4. All tables and fields include English comments for development and maintenance.
-- 5. This script includes seed data for dictionaries, default roles, menu permissions, and lesson status rules.

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

CREATE DATABASE IF NOT EXISTS `class_deduction_system`
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_0900_ai_ci;

USE `class_deduction_system`;

-- ----------------------------
-- 1. Authentication and authorization tables
-- ----------------------------

CREATE TABLE IF NOT EXISTS `sys_role` (
  `id` BIGINT NOT NULL COMMENT 'Primary key ID',
  `role_code` VARCHAR(64) NOT NULL COMMENT 'Role code',
  `role_name` VARCHAR(64) NOT NULL COMMENT 'Role name',
  `status` VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' COMMENT 'Status: ACTIVE/DISABLED',
  `remark` VARCHAR(500) DEFAULT NULL COMMENT 'Notes',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT 'Created at',
  `created_by` BIGINT DEFAULT NULL COMMENT 'Created by user ID',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT 'Updated at',
  `updated_by` BIGINT DEFAULT NULL COMMENT 'Updated by user ID',
  `is_deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT 'Soft delete flag: 0=no, 1=yes',
  `deleted_at` DATETIME(3) DEFAULT NULL COMMENT 'Deleted at',
  `deleted_by` BIGINT DEFAULT NULL COMMENT 'Deleted by user ID',
  `version` INT NOT NULL DEFAULT 0 COMMENT 'Optimistic lock version',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_role_code` (`role_code`),
  KEY `idx_sys_role_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='System roles';

CREATE TABLE IF NOT EXISTS `sys_permission` (
  `id` BIGINT NOT NULL COMMENT 'Primary key ID',
  `permission_code` VARCHAR(128) NOT NULL COMMENT 'Permission code',
  `permission_name` VARCHAR(128) NOT NULL COMMENT 'Permission name',
  `permission_type` VARCHAR(32) NOT NULL COMMENT 'Permission type: MENU/ACTION/API',
  `parent_id` BIGINT DEFAULT NULL COMMENT 'Parent permission ID',
  `path` VARCHAR(255) DEFAULT NULL COMMENT 'Frontend route or API path',
  `method` VARCHAR(16) DEFAULT NULL COMMENT 'HTTP method or action type',
  `status` VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' COMMENT 'Status: ACTIVE/DISABLED',
  `remark` VARCHAR(500) DEFAULT NULL COMMENT 'Notes',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT 'Created at',
  `created_by` BIGINT DEFAULT NULL COMMENT 'Created by user ID',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT 'Updated at',
  `updated_by` BIGINT DEFAULT NULL COMMENT 'Updated by user ID',
  `is_deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT 'Soft delete flag: 0=no, 1=yes',
  `deleted_at` DATETIME(3) DEFAULT NULL COMMENT 'Deleted at',
  `deleted_by` BIGINT DEFAULT NULL COMMENT 'Deleted by user ID',
  `version` INT NOT NULL DEFAULT 0 COMMENT 'Optimistic lock version',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_permission_code` (`permission_code`),
  KEY `idx_sys_permission_parent` (`parent_id`),
  KEY `idx_sys_permission_type_status` (`permission_type`, `status`),
  CONSTRAINT `fk_sys_permission_parent` FOREIGN KEY (`parent_id`) REFERENCES `sys_permission` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='System permissions';

CREATE TABLE IF NOT EXISTS `teacher` (
  `id` BIGINT NOT NULL COMMENT 'Primary key ID',
  `teacher_no` VARCHAR(32) NOT NULL COMMENT 'Teacher code',
  `teacher_name` VARCHAR(64) NOT NULL COMMENT 'Teacher name',
  `email` VARCHAR(128) DEFAULT NULL COMMENT 'Email',
  `mobile` VARCHAR(32) DEFAULT NULL COMMENT 'Mobile',
  `subject_tags` JSON DEFAULT NULL COMMENT 'Subject tags JSON',
  `status` VARCHAR(32) NOT NULL DEFAULT 'ACTIVE/INACTIVE/DISABLED' COMMENT 'Status',
  `campus_id` BIGINT DEFAULT NULL COMMENT 'Campus ID',
  `hire_date` DATE DEFAULT NULL COMMENT 'Hire date',
  `notes` VARCHAR(500) DEFAULT NULL COMMENT 'Notes',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT 'Created at',
  `created_by` BIGINT DEFAULT NULL COMMENT 'Created by user ID',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT 'Updated at',
  `updated_by` BIGINT DEFAULT NULL COMMENT 'Updated by user ID',
  `is_deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT 'Soft delete flag: 0=no, 1=yes',
  `deleted_at` DATETIME(3) DEFAULT NULL COMMENT 'Deleted at',
  `deleted_by` BIGINT DEFAULT NULL COMMENT 'Deleted by user ID',
  `version` INT NOT NULL DEFAULT 0 COMMENT 'Optimistic lock version',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_teacher_no` (`teacher_no`),
  KEY `idx_teacher_name` (`teacher_name`),
  KEY `idx_teacher_status` (`status`),
  KEY `idx_teacher_campus` (`campus_id`),
  CONSTRAINT `fk_teacher_campus` FOREIGN KEY (`campus_id`) REFERENCES `campus` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Teacher master';

CREATE TABLE IF NOT EXISTS `sys_user` (
  `id` BIGINT NOT NULL COMMENT 'Primary key ID',
  `username` VARCHAR(64) NOT NULL COMMENT 'Username',
  `password_hash` VARCHAR(255) NOT NULL COMMENT 'Password hash',
  `user_type` VARCHAR(32) NOT NULL COMMENT 'User type: ADMIN/STAFF/TEACHER/STUDENT',
  `display_name` VARCHAR(64) NOT NULL COMMENT 'Display name',
  `mobile` VARCHAR(32) DEFAULT NULL COMMENT 'Mobile',
  `email` VARCHAR(128) DEFAULT NULL COMMENT 'Email',
  `teacher_id` BIGINT DEFAULT NULL COMMENT 'Linked teacher ID, used by teacher accounts only',
  `student_id` BIGINT DEFAULT NULL COMMENT 'Linked student ID, used by student accounts only',
  `status` VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' COMMENT 'Status: ACTIVE/DISABLED/LOCKED',
  `last_login_at` DATETIME(3) DEFAULT NULL COMMENT 'Last login time',
  `remark` VARCHAR(500) DEFAULT NULL COMMENT 'Notes',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT 'Created at',
  `created_by` BIGINT DEFAULT NULL COMMENT 'Created by user ID',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT 'Updated at',
  `updated_by` BIGINT DEFAULT NULL COMMENT 'Updated by user ID',
  `is_deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT 'Soft delete flag: 0=no, 1=yes',
  `deleted_at` DATETIME(3) DEFAULT NULL COMMENT 'Deleted at',
  `deleted_by` BIGINT DEFAULT NULL COMMENT 'Deleted by user ID',
  `version` INT NOT NULL DEFAULT 0 COMMENT 'Optimistic lock version',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_user_username` (`username`),
  UNIQUE KEY `uk_sys_user_teacher_id` (`teacher_id`),
  UNIQUE KEY `uk_sys_user_student_id` (`student_id`),
  KEY `idx_sys_user_teacher_id` (`teacher_id`),
  KEY `idx_sys_user_type_status` (`user_type`, `status`),
  CONSTRAINT `fk_sys_user_teacher` FOREIGN KEY (`teacher_id`) REFERENCES `teacher` (`id`),
  CONSTRAINT `fk_sys_user_student` FOREIGN KEY (`student_id`) REFERENCES `student` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='System users';

CREATE TABLE IF NOT EXISTS `sys_user_role` (
  `id` BIGINT NOT NULL COMMENT 'Primary key ID',
  `user_id` BIGINT NOT NULL COMMENT 'User ID',
  `role_id` BIGINT NOT NULL COMMENT 'Role ID',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT 'Created at',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_user_role` (`user_id`, `role_id`),
  KEY `idx_sys_user_role_role` (`role_id`),
  CONSTRAINT `fk_sys_user_role_user` FOREIGN KEY (`user_id`) REFERENCES `sys_user` (`id`),
  CONSTRAINT `fk_sys_user_role_role` FOREIGN KEY (`role_id`) REFERENCES `sys_role` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='User-role mapping';

CREATE TABLE IF NOT EXISTS `sys_role_permission` (
  `id` BIGINT NOT NULL COMMENT 'Primary key ID',
  `role_id` BIGINT NOT NULL COMMENT 'Role ID',
  `permission_id` BIGINT NOT NULL COMMENT 'Permission ID',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT 'Created at',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_role_permission` (`role_id`, `permission_id`),
  KEY `idx_sys_role_permission_permission` (`permission_id`),
  CONSTRAINT `fk_sys_role_permission_role` FOREIGN KEY (`role_id`) REFERENCES `sys_role` (`id`),
  CONSTRAINT `fk_sys_role_permission_permission` FOREIGN KEY (`permission_id`) REFERENCES `sys_permission` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Role-permission mapping';

CREATE TABLE IF NOT EXISTS `sys_dict_type` (
  `id` BIGINT NOT NULL COMMENT 'Primary key ID',
  `dict_code` VARCHAR(64) NOT NULL COMMENT 'Dictionary type code',
  `dict_name` VARCHAR(128) NOT NULL COMMENT 'Dictionary type name',
  `status` VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' COMMENT 'Status: ACTIVE/DISABLED',
  `remark` VARCHAR(500) DEFAULT NULL COMMENT 'Notes',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT 'Created at',
  `created_by` BIGINT DEFAULT NULL COMMENT 'Created by user ID',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT 'Updated at',
  `updated_by` BIGINT DEFAULT NULL COMMENT 'Updated by user ID',
  `is_deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT 'Soft delete flag: 0=no, 1=yes',
  `deleted_at` DATETIME(3) DEFAULT NULL COMMENT 'Deleted at',
  `deleted_by` BIGINT DEFAULT NULL COMMENT 'Deleted by user ID',
  `version` INT NOT NULL DEFAULT 0 COMMENT 'Optimistic lock version',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_dict_type_code` (`dict_code`),
  KEY `idx_sys_dict_type_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Dictionary types';

CREATE TABLE IF NOT EXISTS `sys_dict_item` (
  `id` BIGINT NOT NULL COMMENT 'Primary key ID',
  `dict_type_id` BIGINT NOT NULL COMMENT 'Dictionary type ID',
  `item_code` VARCHAR(64) NOT NULL COMMENT 'Dictionary item code',
  `item_name` VARCHAR(128) NOT NULL COMMENT 'Dictionary item name',
  `item_value` VARCHAR(128) NOT NULL COMMENT 'Dictionary item value',
  `sort_order` INT NOT NULL DEFAULT 0 COMMENT 'Sort order',
  `is_default` TINYINT(1) NOT NULL DEFAULT 0 COMMENT 'Default flag: 0=no, 1=yes',
  `status` VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' COMMENT 'Status: ACTIVE/DISABLED',
  `remark` VARCHAR(500) DEFAULT NULL COMMENT 'Notes',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT 'Created at',
  `created_by` BIGINT DEFAULT NULL COMMENT 'Created by user ID',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT 'Updated at',
  `updated_by` BIGINT DEFAULT NULL COMMENT 'Updated by user ID',
  `is_deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT 'Soft delete flag: 0=no, 1=yes',
  `deleted_at` DATETIME(3) DEFAULT NULL COMMENT 'Deleted at',
  `deleted_by` BIGINT DEFAULT NULL COMMENT 'Deleted by user ID',
  `version` INT NOT NULL DEFAULT 0 COMMENT 'Optimistic lock version',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_dict_item_code` (`dict_type_id`, `item_code`),
  KEY `idx_sys_dict_item_value` (`dict_type_id`, `item_value`),
  KEY `idx_sys_dict_item_status` (`status`),
  CONSTRAINT `fk_sys_dict_item_type` FOREIGN KEY (`dict_type_id`) REFERENCES `sys_dict_type` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Dictionary items';

CREATE TABLE IF NOT EXISTS `sys_login_log` (
  `id` BIGINT NOT NULL COMMENT 'Primary key ID',
  `user_id` BIGINT DEFAULT NULL COMMENT 'User ID',
  `username_snapshot` VARCHAR(64) NOT NULL COMMENT 'Username snapshot',
  `login_result` VARCHAR(32) NOT NULL COMMENT 'Login result: SUCCESS/FAILED',
  `ip` VARCHAR(64) DEFAULT NULL COMMENT 'Login IP',
  `user_agent` VARCHAR(500) DEFAULT NULL COMMENT 'User agent',
  `login_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT 'Login time',
  PRIMARY KEY (`id`),
  KEY `idx_sys_login_log_user_time` (`user_id`, `login_at` DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Login logs';

CREATE TABLE IF NOT EXISTS `sys_operation_log` (
  `id` BIGINT NOT NULL COMMENT 'Primary key ID',
  `operator_user_id` BIGINT DEFAULT NULL COMMENT 'Operator user ID',
  `operator_name_snapshot` VARCHAR(64) NOT NULL COMMENT 'Operator name snapshot',
  `biz_type` VARCHAR(64) NOT NULL COMMENT 'Business type',
  `biz_id` BIGINT DEFAULT NULL COMMENT 'Business record ID',
  `operation_code` VARCHAR(64) NOT NULL COMMENT 'Operation code',
  `operation_desc` VARCHAR(255) NOT NULL COMMENT 'Operation description',
  `request_snapshot` JSON DEFAULT NULL COMMENT 'Request snapshot',
  `response_snapshot` JSON DEFAULT NULL COMMENT 'Response snapshot',
  `operate_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT 'Operated at',
  PRIMARY KEY (`id`),
  KEY `idx_sys_operation_log_biz` (`biz_type`, `biz_id`, `operate_at` DESC),
  KEY `idx_sys_operation_log_operator` (`operator_user_id`, `operate_at` DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Operation audit logs';

-- ----------------------------
-- 2. Base data tables
-- ----------------------------

CREATE TABLE IF NOT EXISTS `student` (
  `id` BIGINT NOT NULL COMMENT 'Primary key ID',
  `student_no` VARCHAR(32) NOT NULL COMMENT 'Student code',
  `student_name` VARCHAR(64) NOT NULL COMMENT 'Student name',
  `english_name` VARCHAR(64) DEFAULT NULL COMMENT 'English name',
  `gender` VARCHAR(16) DEFAULT NULL COMMENT 'Gender',
  `birth_date` DATE DEFAULT NULL COMMENT 'Birth date',
  `source_channel` VARCHAR(64) DEFAULT NULL COMMENT 'Lead source',
  `status` VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' COMMENT 'Status: ACTIVE/INACTIVE',
  `campus_id` BIGINT DEFAULT NULL COMMENT 'Campus ID',
  `notes` VARCHAR(500) DEFAULT NULL COMMENT 'Notes',
  `guardian_name` VARCHAR(64) DEFAULT NULL COMMENT 'Primary guardian name',
  `guardian_phone` VARCHAR(32) DEFAULT NULL COMMENT 'Primary guardian phone',
  `guardian_email` VARCHAR(128) DEFAULT NULL COMMENT 'Primary guardian email',
  `guardian_relation_type` VARCHAR(32) DEFAULT NULL COMMENT 'Primary guardian relation type',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT 'Created at',
  `created_by` BIGINT DEFAULT NULL COMMENT 'Created by user ID',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT 'Updated at',
  `updated_by` BIGINT DEFAULT NULL COMMENT 'Updated by user ID',
  `is_deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT 'Soft delete flag: 0=no, 1=yes',
  `deleted_at` DATETIME(3) DEFAULT NULL COMMENT 'Deleted at',
  `deleted_by` BIGINT DEFAULT NULL COMMENT 'Deleted by user ID',
  `version` INT NOT NULL DEFAULT 0 COMMENT 'Optimistic lock version',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_student_no` (`student_no`),
  KEY `idx_student_name` (`student_name`),
  KEY `idx_student_status` (`status`),
  KEY `idx_student_campus` (`campus_id`),
  CONSTRAINT `fk_student_campus` FOREIGN KEY (`campus_id`) REFERENCES `campus` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Student master';

-- Campus table for managing building/campus locations
CREATE TABLE IF NOT EXISTS `campus` (
  `id` BIGINT NOT NULL COMMENT 'Primary key ID',
  `campus_name` VARCHAR(64) NOT NULL COMMENT 'Campus name',
  `display_order` INT NOT NULL DEFAULT 99 COMMENT 'Display order for sorting',
  `status` VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' COMMENT 'Status: ACTIVE/INACTIVE',
  `notes` VARCHAR(500) DEFAULT NULL COMMENT 'Notes',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT 'Created at',
  `created_by` BIGINT DEFAULT NULL COMMENT 'Created by user ID',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT 'Updated at',
  `updated_by` BIGINT DEFAULT NULL COMMENT 'Updated by user ID',
  `is_deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT 'Soft delete flag: 0=no, 1=yes',
  `deleted_at` DATETIME(3) DEFAULT NULL COMMENT 'Deleted at',
  `deleted_by` BIGINT DEFAULT NULL COMMENT 'Deleted by user ID',
  `version` INT NOT NULL DEFAULT 0 COMMENT 'Optimistic lock version',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_campus_name` (`campus_name`),
  KEY `idx_campus_status` (`status`),
  KEY `idx_campus_display_order` (`display_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Campus master';

CREATE TABLE IF NOT EXISTS `classroom` (
  `id` BIGINT NOT NULL COMMENT 'Primary key ID',
  `room_code` VARCHAR(32) NOT NULL COMMENT 'Room code',
  `room_name` VARCHAR(64) NOT NULL COMMENT 'Room name',
  `campus` VARCHAR(64) DEFAULT NULL COMMENT 'Campus name (deprecated, use campus_id)',
  `campus_id` BIGINT DEFAULT NULL COMMENT 'Campus ID',
  `capacity` INT DEFAULT NULL COMMENT 'Capacity',
  `floor` VARCHAR(16) DEFAULT NULL COMMENT 'Floor number or designation',
  `status` VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' COMMENT 'Status: ACTIVE/DISABLED',
  `notes` VARCHAR(500) DEFAULT NULL COMMENT 'Notes',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT 'Created at',
  `created_by` BIGINT DEFAULT NULL COMMENT 'Created by user ID',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT 'Updated at',
  `updated_by` BIGINT DEFAULT NULL COMMENT 'Updated by user ID',
  `is_deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT 'Soft delete flag: 0=no, 1=yes',
  `deleted_at` DATETIME(3) DEFAULT NULL COMMENT 'Deleted at',
  `deleted_by` BIGINT DEFAULT NULL COMMENT 'Deleted by user ID',
  `version` INT NOT NULL DEFAULT 0 COMMENT 'Optimistic lock version',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_room_code` (`room_code`),
  KEY `idx_room_name` (`room_name`),
  KEY `idx_classroom_campus` (`campus`),
  KEY `idx_classroom_campus_id` (`campus_id`),
  KEY `idx_classroom_status` (`status`),
  CONSTRAINT `fk_classroom_campus` FOREIGN KEY (`campus_id`) REFERENCES `campus` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Classroom master';

-- Subject table for managing course subjects (Math, English, Science, SAT, etc.)
CREATE TABLE IF NOT EXISTS `subject` (
  `id` BIGINT NOT NULL COMMENT 'Primary key ID',
  `subject_name` VARCHAR(64) NOT NULL COMMENT 'Subject name',
  `display_order` INT NOT NULL DEFAULT 99 COMMENT 'Display order for sorting',
  `status` VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' COMMENT 'Status: ACTIVE/INACTIVE',
  `notes` VARCHAR(500) DEFAULT NULL COMMENT 'Notes',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT 'Created at',
  `created_by` BIGINT DEFAULT NULL COMMENT 'Created by user ID',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT 'Updated at',
  `updated_by` BIGINT DEFAULT NULL COMMENT 'Updated by user ID',
  `is_deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT 'Soft delete flag: 0=no, 1=yes',
  `deleted_at` DATETIME(3) DEFAULT NULL COMMENT 'Deleted at',
  `deleted_by` BIGINT DEFAULT NULL COMMENT 'Deleted by user ID',
  `version` INT NOT NULL DEFAULT 0 COMMENT 'Optimistic lock version',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_subject_name` (`subject_name`),
  KEY `idx_subject_status` (`status`),
  KEY `idx_subject_display_order` (`display_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Course subjects (Math, English, Science, SAT, etc.)';

CREATE TABLE IF NOT EXISTS `course_product` (
  `id` BIGINT NOT NULL COMMENT 'Primary key ID',
  `product_code` VARCHAR(32) NOT NULL COMMENT 'Course product code',
  `product_name` VARCHAR(128) NOT NULL COMMENT 'Course product name',
  `subject_name` VARCHAR(64) DEFAULT NULL COMMENT 'Subject name',
  `teaching_mode` VARCHAR(32) NOT NULL COMMENT 'Teaching mode: ONE_ON_ONE/GROUP',
  `default_total_units` INT DEFAULT NULL COMMENT 'Default total units, null for class group products',
  `default_lesson_units` INT DEFAULT NULL COMMENT 'Default lesson units, null for class group products',
  `expiry_duration_days` INT DEFAULT NULL COMMENT 'Default enrollment expiry duration in days, for class group products',
  `default_classroom_id` BIGINT DEFAULT NULL COMMENT 'Default classroom ID (for class group products)',
  `default_teacher_id` BIGINT DEFAULT NULL COMMENT 'Default teacher ID (for class group products)',
  `list_price` DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT 'List price',
  `status` VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' COMMENT 'Status: ACTIVE/INACTIVE',
  `notes` VARCHAR(500) DEFAULT NULL COMMENT 'Notes',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT 'Created at',
  `created_by` BIGINT DEFAULT NULL COMMENT 'Created by user ID',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT 'Updated at',
  `updated_by` BIGINT DEFAULT NULL COMMENT 'Updated by user ID',
  `is_deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT 'Soft delete flag: 0=no, 1=yes',
  `deleted_at` DATETIME(3) DEFAULT NULL COMMENT 'Deleted at',
  `deleted_by` BIGINT DEFAULT NULL COMMENT 'Deleted by user ID',
  `version` INT NOT NULL DEFAULT 0 COMMENT 'Optimistic lock version',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_course_product_code` (`product_code`),
  KEY `idx_course_product_name` (`product_name`),
  KEY `idx_course_product_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Course product templates';

-- 班课产品排课时段
CREATE TABLE IF NOT EXISTS `course_product_schedule_slot` (
  `id` BIGINT NOT NULL COMMENT 'Primary key ID',
  `product_id` BIGINT NOT NULL COMMENT 'Course product ID',
  `weekday` TINYINT NOT NULL COMMENT 'Weekday: 1=Mon..7=Sun',
  `start_time` TIME NOT NULL COMMENT 'Start time, e.g. 09:00',
  `end_time` TIME NOT NULL COMMENT 'End time, e.g. 10:30',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `created_by` BIGINT DEFAULT NULL COMMENT 'Created by user ID',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `updated_by` BIGINT DEFAULT NULL COMMENT 'Updated by user ID',
  `is_deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT 'Soft delete flag: 0=no, 1=yes',
  `deleted_at` DATETIME(3) DEFAULT NULL COMMENT 'Deleted at',
  `deleted_by` BIGINT DEFAULT NULL COMMENT 'Deleted by user ID',
  `version` INT NOT NULL DEFAULT 0 COMMENT 'Optimistic lock version',
  PRIMARY KEY (`id`),
  KEY `idx_cpss_product` (`product_id`),
  CONSTRAINT `fk_cpss_product` FOREIGN KEY (`product_id`) REFERENCES `course_product` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Course product schedule slots for class group products';

-- ----------------------------
-- 3. Enrollment and course package tables
-- ----------------------------

CREATE TABLE IF NOT EXISTS `enrollment_order` (
  `id` BIGINT NOT NULL COMMENT 'Primary key ID',
  `enrollment_no` VARCHAR(32) NOT NULL COMMENT 'Enrollment order number',
  `student_id` BIGINT NOT NULL COMMENT 'Student ID',
  `order_status` VARCHAR(32) NOT NULL DEFAULT 'DRAFT' COMMENT 'Order status: DRAFT/CONFIRMED/CANCELLED',
  `cashier_user_id` BIGINT DEFAULT NULL COMMENT 'Cashier user ID',
  `handled_by_name` VARCHAR(64) DEFAULT NULL COMMENT 'Handler name snapshot',
  `currency_code` VARCHAR(16) NOT NULL DEFAULT 'USD' COMMENT 'Currency code',
  `original_amount` DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT 'Original amount',
  `discount_amount` DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT 'Discount amount',
  `received_amount` DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT 'Received amount',
  `payment_status` VARCHAR(32) NOT NULL DEFAULT 'UNPAID' COMMENT 'Payment status: UNPAID/PARTIAL/PAID',
  `remark` VARCHAR(500) DEFAULT NULL COMMENT 'Notes',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT 'Created at',
  `created_by` BIGINT DEFAULT NULL COMMENT 'Created by user ID',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT 'Updated at',
  `updated_by` BIGINT DEFAULT NULL COMMENT 'Updated by user ID',
  `is_deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT 'Soft delete flag: 0=no, 1=yes',
  `deleted_at` DATETIME(3) DEFAULT NULL COMMENT 'Deleted at',
  `deleted_by` BIGINT DEFAULT NULL COMMENT 'Deleted by user ID',
  `version` INT NOT NULL DEFAULT 0 COMMENT 'Optimistic lock version',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_enrollment_no` (`enrollment_no`),
  KEY `idx_enrollment_student` (`student_id`, `created_at` DESC),
  KEY `idx_enrollment_status` (`order_status`, `payment_status`),
  KEY `idx_enrollment_cashier` (`cashier_user_id`),
  CONSTRAINT `fk_enrollment_student` FOREIGN KEY (`student_id`) REFERENCES `student` (`id`),
  CONSTRAINT `fk_enrollment_cashier` FOREIGN KEY (`cashier_user_id`) REFERENCES `sys_user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Enrollment orders';

CREATE TABLE IF NOT EXISTS `course_package` (
  `id` BIGINT NOT NULL COMMENT 'Primary key ID',
  `package_no` VARCHAR(32) NOT NULL COMMENT 'Course package number',
  `enrollment_order_id` BIGINT NOT NULL COMMENT 'Source enrollment order ID',
  `student_id` BIGINT NOT NULL COMMENT 'Student ID',
  `course_product_id` BIGINT DEFAULT NULL COMMENT 'Source course product ID',
  `package_name` VARCHAR(128) NOT NULL COMMENT 'Course package name snapshot',
  `subject_name` VARCHAR(64) DEFAULT NULL COMMENT 'Subject name snapshot',
  `teaching_mode` VARCHAR(32) NOT NULL COMMENT 'Teaching mode snapshot',
  `total_units` INT NOT NULL COMMENT 'Total units, 1=0.5 hours',
  `default_lesson_units` INT NOT NULL COMMENT 'Default lesson units',
  `consumed_units` INT NOT NULL DEFAULT 0 COMMENT 'Consumed units',
  `remaining_units` INT NOT NULL DEFAULT 0 COMMENT 'Remaining units',
  `original_amount` DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT 'Original amount snapshot',
  `discount_amount` DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT 'Discount amount',
  `received_amount` DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT 'Received amount',
  `package_status` VARCHAR(32) NOT NULL DEFAULT 'DRAFT' COMMENT 'Course package status: DRAFT/ACTIVE/COMPLETED/CLOSED',
  `start_date` DATE DEFAULT NULL COMMENT 'Start date',
  `estimated_end_date` DATE DEFAULT NULL COMMENT 'Estimated end date',
  `completed_at` DATETIME(3) DEFAULT NULL COMMENT 'Completed at',
  `remark` VARCHAR(500) DEFAULT NULL COMMENT 'Notes',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT 'Created at',
  `created_by` BIGINT DEFAULT NULL COMMENT 'Created by user ID',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT 'Updated at',
  `updated_by` BIGINT DEFAULT NULL COMMENT 'Updated by user ID',
  `is_deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT 'Soft delete flag: 0=no, 1=yes',
  `deleted_at` DATETIME(3) DEFAULT NULL COMMENT 'Deleted at',
  `deleted_by` BIGINT DEFAULT NULL COMMENT 'Deleted by user ID',
  `version` INT NOT NULL DEFAULT 0 COMMENT 'Optimistic lock version',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_course_package_no` (`package_no`),
  KEY `idx_package_student` (`student_id`, `package_status`),
  KEY `idx_package_estimated_end` (`estimated_end_date`),
  KEY `idx_package_product` (`course_product_id`),
  KEY `idx_package_enrollment` (`enrollment_order_id`),
  CONSTRAINT `fk_package_enrollment` FOREIGN KEY (`enrollment_order_id`) REFERENCES `enrollment_order` (`id`),
  CONSTRAINT `fk_package_student` FOREIGN KEY (`student_id`) REFERENCES `student` (`id`),
  CONSTRAINT `fk_package_product` FOREIGN KEY (`course_product_id`) REFERENCES `course_product` (`id`),
  CONSTRAINT `chk_course_package_units` CHECK (`total_units` >= 0 AND `default_lesson_units` > 0 AND `consumed_units` >= 0 AND `remaining_units` >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Course package table created after enrollment for a student''s independent lesson balance account';

CREATE TABLE IF NOT EXISTS `package_teacher_assignment` (
  `id` BIGINT NOT NULL COMMENT 'Primary key ID',
  `package_id` BIGINT NOT NULL COMMENT 'Course package ID',
  `teacher_id` BIGINT NOT NULL COMMENT 'Teacher ID',
  `is_primary` TINYINT(1) NOT NULL DEFAULT 1 COMMENT 'Primary teacher flag: 0=no, 1=yes',
  `effective_from` DATETIME(3) NOT NULL COMMENT 'Effective from',
  `effective_to` DATETIME(3) DEFAULT NULL COMMENT 'Effective to',
  `assignment_status` VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' COMMENT 'Assignment status: ACTIVE/ENDED',
  `change_reason` VARCHAR(255) DEFAULT NULL COMMENT 'Change reason',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT 'Created at',
  `created_by` BIGINT DEFAULT NULL COMMENT 'Created by user ID',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT 'Updated at',
  `updated_by` BIGINT DEFAULT NULL COMMENT 'Updated by user ID',
  `is_deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT 'Soft delete flag: 0=no, 1=yes',
  `deleted_at` DATETIME(3) DEFAULT NULL COMMENT 'Deleted at',
  `deleted_by` BIGINT DEFAULT NULL COMMENT 'Deleted by user ID',
  `version` INT NOT NULL DEFAULT 0 COMMENT 'Optimistic lock version',
  PRIMARY KEY (`id`),
  KEY `idx_assignment_package` (`package_id`, `effective_from` DESC),
  KEY `idx_assignment_teacher` (`teacher_id`, `assignment_status`, `effective_from` DESC),
  CONSTRAINT `fk_assignment_package` FOREIGN KEY (`package_id`) REFERENCES `course_package` (`id`),
  CONSTRAINT `fk_assignment_teacher` FOREIGN KEY (`teacher_id`) REFERENCES `teacher` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Package teacher assignment history';

CREATE TABLE IF NOT EXISTS `payment_record` (
  `id` BIGINT NOT NULL COMMENT 'Primary key ID',
  `enrollment_order_id` BIGINT NOT NULL COMMENT 'Enrollment order ID',
  `payment_no` VARCHAR(32) NOT NULL COMMENT 'Payment record number',
  `payment_method` VARCHAR(32) NOT NULL DEFAULT 'CASH' COMMENT 'Payment method, fixed as cash in phase one',
  `payment_status` VARCHAR(32) NOT NULL DEFAULT 'PAID' COMMENT 'Payment status',
  `amount` DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT 'Payment amount',
  `paid_at` DATETIME(3) DEFAULT NULL COMMENT 'Paid at',
  `handled_by_user_id` BIGINT DEFAULT NULL COMMENT 'Handler user ID',
  `handled_by_name` VARCHAR(64) DEFAULT NULL COMMENT 'Handler name snapshot',
  `remark` VARCHAR(500) DEFAULT NULL COMMENT 'Notes',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT 'Created at',
  `created_by` BIGINT DEFAULT NULL COMMENT 'Created by user ID',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT 'Updated at',
  `updated_by` BIGINT DEFAULT NULL COMMENT 'Updated by user ID',
  `is_deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT 'Soft delete flag: 0=no, 1=yes',
  `deleted_at` DATETIME(3) DEFAULT NULL COMMENT 'Deleted at',
  `deleted_by` BIGINT DEFAULT NULL COMMENT 'Deleted by user ID',
  `version` INT NOT NULL DEFAULT 0 COMMENT 'Optimistic lock version',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_payment_no` (`payment_no`),
  KEY `idx_payment_order` (`enrollment_order_id`, `paid_at` DESC),
  KEY `idx_payment_handler` (`handled_by_user_id`),
  CONSTRAINT `fk_payment_order` FOREIGN KEY (`enrollment_order_id`) REFERENCES `enrollment_order` (`id`),
  CONSTRAINT `fk_payment_handler` FOREIGN KEY (`handled_by_user_id`) REFERENCES `sys_user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Enrollment payment records';

CREATE TABLE IF NOT EXISTS `receipt_record` (
  `id` BIGINT NOT NULL COMMENT 'Primary key ID',
  `receipt_no` VARCHAR(32) NOT NULL COMMENT 'Receipt number',
  `enrollment_order_id` BIGINT NOT NULL COMMENT 'Enrollment order ID',
  `student_id` BIGINT NOT NULL COMMENT 'Student ID',
  `template_code` VARCHAR(64) NOT NULL COMMENT 'Template code',
  `receipt_payload` JSON DEFAULT NULL COMMENT 'Receipt snapshot JSON',
  `issued_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT 'Issued at',
  `issued_by` BIGINT DEFAULT NULL COMMENT 'Issued by user ID',
  `file_url` VARCHAR(500) DEFAULT NULL COMMENT 'Receipt file URL',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT 'Created at',
  `created_by` BIGINT DEFAULT NULL COMMENT 'Created by user ID',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT 'Updated at',
  `updated_by` BIGINT DEFAULT NULL COMMENT 'Updated by user ID',
  `is_deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT 'Soft delete flag: 0=no, 1=yes',
  `deleted_at` DATETIME(3) DEFAULT NULL COMMENT 'Deleted at',
  `deleted_by` BIGINT DEFAULT NULL COMMENT 'Deleted by user ID',
  `version` INT NOT NULL DEFAULT 0 COMMENT 'Optimistic lock version',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_receipt_no` (`receipt_no`),
  KEY `idx_receipt_order` (`enrollment_order_id`, `issued_at` DESC),
  KEY `idx_receipt_student` (`student_id`),
  CONSTRAINT `fk_receipt_order` FOREIGN KEY (`enrollment_order_id`) REFERENCES `enrollment_order` (`id`),
  CONSTRAINT `fk_receipt_student` FOREIGN KEY (`student_id`) REFERENCES `student` (`id`),
  CONSTRAINT `fk_receipt_issued_by` FOREIGN KEY (`issued_by`) REFERENCES `sys_user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Receipt records';

-- ----------------------------
-- 4. Scheduling tables
-- ----------------------------

CREATE TABLE IF NOT EXISTS `recurring_schedule_rule` (
  `id` BIGINT NOT NULL COMMENT 'Primary key ID',
  `package_id` BIGINT NOT NULL COMMENT 'Course package ID',
  `primary_teacher_id` BIGINT DEFAULT NULL COMMENT 'Default primary teacher ID',
  `default_classroom_id` BIGINT DEFAULT NULL COMMENT 'Default classroom ID',
  `rule_status` VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' COMMENT 'Rule status: ACTIVE/PAUSED/ENDED',
  `start_date` DATE NOT NULL COMMENT 'Rule start date',
  `end_date` DATE DEFAULT NULL COMMENT 'Rule end date',
  `generation_horizon_days` INT NOT NULL DEFAULT 90 COMMENT 'Occurrence generation horizon days',
  `notes` VARCHAR(500) DEFAULT NULL COMMENT 'Notes',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT 'Created at',
  `created_by` BIGINT DEFAULT NULL COMMENT 'Created by user ID',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT 'Updated at',
  `updated_by` BIGINT DEFAULT NULL COMMENT 'Updated by user ID',
  `is_deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT 'Soft delete flag: 0=no, 1=yes',
  `deleted_at` DATETIME(3) DEFAULT NULL COMMENT 'Deleted at',
  `deleted_by` BIGINT DEFAULT NULL COMMENT 'Deleted by user ID',
  `version` INT NOT NULL DEFAULT 0 COMMENT 'Optimistic lock version',
  PRIMARY KEY (`id`),
  KEY `idx_schedule_rule_package` (`package_id`, `rule_status`),
  KEY `idx_schedule_rule_teacher` (`primary_teacher_id`, `rule_status`),
  KEY `idx_schedule_rule_classroom` (`default_classroom_id`),
  CONSTRAINT `fk_schedule_rule_package` FOREIGN KEY (`package_id`) REFERENCES `course_package` (`id`),
  CONSTRAINT `fk_schedule_rule_teacher` FOREIGN KEY (`primary_teacher_id`) REFERENCES `teacher` (`id`),
  CONSTRAINT `fk_schedule_rule_classroom` FOREIGN KEY (`default_classroom_id`) REFERENCES `classroom` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Recurring schedule rules';

CREATE TABLE IF NOT EXISTS `recurring_schedule_slot` (
  `id` BIGINT NOT NULL COMMENT 'Primary key ID',
  `rule_id` BIGINT NOT NULL COMMENT 'Rule ID',
  `weekday` TINYINT NOT NULL COMMENT 'Weekday: 1-7',
  `start_time` TIME NOT NULL COMMENT 'Start time',
  `end_time` TIME NOT NULL COMMENT 'End time',
  `planned_units` INT NOT NULL COMMENT 'Planned units',
  `slot_status` VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' COMMENT 'Slot status: ACTIVE/INACTIVE',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT 'Created at',
  `created_by` BIGINT DEFAULT NULL COMMENT 'Created by user ID',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT 'Updated at',
  `updated_by` BIGINT DEFAULT NULL COMMENT 'Updated by user ID',
  `is_deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT 'Soft delete flag: 0=no, 1=yes',
  `deleted_at` DATETIME(3) DEFAULT NULL COMMENT 'Deleted at',
  `deleted_by` BIGINT DEFAULT NULL COMMENT 'Deleted by user ID',
  `version` INT NOT NULL DEFAULT 0 COMMENT 'Optimistic lock version',
  PRIMARY KEY (`id`),
  KEY `idx_schedule_slot_rule` (`rule_id`, `weekday`, `start_time`),
  CONSTRAINT `fk_schedule_slot_rule` FOREIGN KEY (`rule_id`) REFERENCES `recurring_schedule_rule` (`id`),
  CONSTRAINT `chk_schedule_slot_weekday` CHECK (`weekday` BETWEEN 1 AND 7),
  CONSTRAINT `chk_schedule_slot_units` CHECK (`planned_units` > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Recurring schedule slots';

CREATE TABLE IF NOT EXISTS `lesson_occurrence` (
  `id` BIGINT NOT NULL COMMENT 'Primary key ID',
  `lesson_no` VARCHAR(32) NOT NULL COMMENT 'Lesson number',
  `package_id` BIGINT DEFAULT NULL COMMENT 'Course package ID (null for class group occurrences)',
  `student_id` BIGINT DEFAULT NULL COMMENT 'Student ID (null for class group occurrences)',
  `class_group_id` BIGINT DEFAULT NULL COMMENT 'Class group ID (null for 1v1 occurrences)',
  `schedule_rule_id` BIGINT DEFAULT NULL COMMENT 'Source rule ID',
  `schedule_slot_id` BIGINT DEFAULT NULL COMMENT 'Source slot ID',
  `occurrence_type` VARCHAR(32) NOT NULL COMMENT 'Occurrence type: REGULAR/MAKEUP/MANUAL',
  `planned_teacher_id` BIGINT DEFAULT NULL COMMENT 'Planned teacher ID',
  `planned_classroom_id` BIGINT DEFAULT NULL COMMENT 'Planned classroom ID',
  `planned_start_at` DATETIME(3) NOT NULL COMMENT 'Planned start time',
  `planned_end_at` DATETIME(3) NOT NULL COMMENT 'Planned end time',
  `planned_units` INT NOT NULL COMMENT 'Planned units',
  `planning_status` VARCHAR(32) NOT NULL DEFAULT 'SCHEDULED' COMMENT 'Planning status: SCHEDULED/ADJUSTED/CANCELLED/MERGED',
  `source_occurrence_id` BIGINT DEFAULT NULL COMMENT 'Source occurrence ID used for makeup lessons or adjustments',
  `conflict_checked` TINYINT(1) NOT NULL DEFAULT 0 COMMENT 'Conflict check completed: 0=no, 1=yes',
  `notes` VARCHAR(500) DEFAULT NULL COMMENT 'Notes',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT 'Created at',
  `created_by` BIGINT DEFAULT NULL COMMENT 'Created by user ID',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT 'Updated at',
  `updated_by` BIGINT DEFAULT NULL COMMENT 'Updated by user ID',
  `is_deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT 'Soft delete flag: 0=no, 1=yes',
  `deleted_at` DATETIME(3) DEFAULT NULL COMMENT 'Deleted at',
  `deleted_by` BIGINT DEFAULT NULL COMMENT 'Deleted by user ID',
  `version` INT NOT NULL DEFAULT 0 COMMENT 'Optimistic lock version',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_lesson_no` (`lesson_no`),
  KEY `idx_occurrence_package_time` (`package_id`, `planned_start_at`),
  KEY `idx_occurrence_student_time` (`student_id`, `planned_start_at`, `planned_end_at`),
  KEY `idx_occurrence_teacher_time` (`planned_teacher_id`, `planned_start_at`, `planned_end_at`),
  KEY `idx_occurrence_room_time` (`planned_classroom_id`, `planned_start_at`, `planned_end_at`),
  KEY `idx_occurrence_rule` (`schedule_rule_id`, `planned_start_at`),
  KEY `idx_occurrence_slot` (`schedule_slot_id`),
  KEY `idx_occurrence_source` (`source_occurrence_id`),
  KEY `idx_occurrence_class_group` (`class_group_id`),
  CONSTRAINT `fk_occurrence_package` FOREIGN KEY (`package_id`) REFERENCES `course_package` (`id`),
  CONSTRAINT `fk_occurrence_student` FOREIGN KEY (`student_id`) REFERENCES `student` (`id`),
  CONSTRAINT `fk_occurrence_rule` FOREIGN KEY (`schedule_rule_id`) REFERENCES `recurring_schedule_rule` (`id`),
  CONSTRAINT `fk_occurrence_slot` FOREIGN KEY (`schedule_slot_id`) REFERENCES `recurring_schedule_slot` (`id`),
  CONSTRAINT `fk_occurrence_teacher` FOREIGN KEY (`planned_teacher_id`) REFERENCES `teacher` (`id`),
  CONSTRAINT `fk_occurrence_classroom` FOREIGN KEY (`planned_classroom_id`) REFERENCES `classroom` (`id`),
  CONSTRAINT `fk_occurrence_source` FOREIGN KEY (`source_occurrence_id`) REFERENCES `lesson_occurrence` (`id`),
  CONSTRAINT `fk_occurrence_class_group` FOREIGN KEY (`class_group_id`) REFERENCES `class_group` (`id`),
  CONSTRAINT `chk_occurrence_units` CHECK (`planned_units` > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Lesson occurrences';

CREATE TABLE IF NOT EXISTS `lesson_adjustment_log` (
  `id` BIGINT NOT NULL COMMENT 'Primary key ID',
  `occurrence_id` BIGINT NOT NULL COMMENT 'Occurrence ID',
  `adjustment_type` VARCHAR(32) NOT NULL COMMENT 'Adjustment type: RESCHEDULE/CHANGE_ROOM/CHANGE_TEACHER/MAKEUP_CREATE',
  `before_snapshot` JSON DEFAULT NULL COMMENT 'Before snapshot',
  `after_snapshot` JSON DEFAULT NULL COMMENT 'After snapshot',
  `reason` VARCHAR(255) DEFAULT NULL COMMENT 'Adjustment reason',
  `adjusted_by` BIGINT DEFAULT NULL COMMENT 'Adjusted by user ID',
  `adjusted_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT 'Adjusted at',
  PRIMARY KEY (`id`),
  KEY `idx_adjustment_occurrence` (`occurrence_id`, `adjusted_at` DESC),
  KEY `idx_adjustment_user` (`adjusted_by`),
  CONSTRAINT `fk_adjustment_occurrence` FOREIGN KEY (`occurrence_id`) REFERENCES `lesson_occurrence` (`id`),
  CONSTRAINT `fk_adjustment_user` FOREIGN KEY (`adjusted_by`) REFERENCES `sys_user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Lesson adjustment logs';

-- ----------------------------
-- 5. Lesson execution and ledger tables
-- ----------------------------

CREATE TABLE IF NOT EXISTS `lesson_record` (
  `id` BIGINT NOT NULL COMMENT 'Primary key ID',
  `occurrence_id` BIGINT NOT NULL COMMENT 'Planned occurrence ID',
  `package_id` BIGINT NOT NULL COMMENT 'Course package ID',
  `student_id` BIGINT NOT NULL COMMENT 'Student ID',
  `planned_teacher_id` BIGINT DEFAULT NULL COMMENT 'Planned teacher ID snapshot',
  `actual_teacher_id` BIGINT DEFAULT NULL COMMENT 'Actual teacher ID',
  `planned_classroom_id` BIGINT DEFAULT NULL COMMENT 'Planned classroom ID snapshot',
  `actual_classroom_id` BIGINT DEFAULT NULL COMMENT 'Actual classroom ID',
  `result_status` VARCHAR(32) NOT NULL COMMENT 'Result status: COMPLETED/FORMAL_LEAVE/TEMP_CANCEL/NO_SHOW/TEACHER_CANCEL/MAKEUP_COMPLETED/SUBSTITUTED',
  `actual_start_at` DATETIME(3) DEFAULT NULL COMMENT 'Actual start time',
  `actual_end_at` DATETIME(3) DEFAULT NULL COMMENT 'Actual end time',
  `actual_units` INT NOT NULL DEFAULT 0 COMMENT 'Actual units',
  `should_deduct` TINYINT(1) NOT NULL DEFAULT 0 COMMENT 'Deduct flag: 0=no, 1=yes',
  `deducted_units` INT NOT NULL DEFAULT 0 COMMENT 'Deducted units',
  `should_extend_end_date` TINYINT(1) NOT NULL DEFAULT 0 COMMENT 'Extend end date flag: 0=no, 1=yes',
  `confirmed_by_user_id` BIGINT DEFAULT NULL COMMENT 'Confirmed by user ID',
  `confirmed_at` DATETIME(3) DEFAULT NULL COMMENT 'Confirmed at',
  `record_source` VARCHAR(32) NOT NULL DEFAULT 'STAFF' COMMENT 'Record source: STAFF/TEACHER/SYSTEM',
  `notes` VARCHAR(500) DEFAULT NULL COMMENT 'Notes',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT 'Created at',
  `created_by` BIGINT DEFAULT NULL COMMENT 'Created by user ID',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT 'Updated at',
  `updated_by` BIGINT DEFAULT NULL COMMENT 'Updated by user ID',
  `is_deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT 'Soft delete flag: 0=no, 1=yes',
  `deleted_at` DATETIME(3) DEFAULT NULL COMMENT 'Deleted at',
  `deleted_by` BIGINT DEFAULT NULL COMMENT 'Deleted by user ID',
  `version` INT NOT NULL DEFAULT 0 COMMENT 'Optimistic lock version',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_lesson_record_occurrence_student` (`occurrence_id`, `student_id`),
  KEY `idx_lesson_record_package` (`package_id`, `confirmed_at` DESC),
  KEY `idx_lesson_record_student` (`student_id`, `confirmed_at` DESC),
  KEY `idx_lesson_record_actual_teacher` (`actual_teacher_id`, `confirmed_at` DESC),
  KEY `idx_lesson_record_status` (`result_status`, `confirmed_at` DESC),
  KEY `idx_lesson_record_confirm_user` (`confirmed_by_user_id`),
  CONSTRAINT `fk_lesson_record_occurrence` FOREIGN KEY (`occurrence_id`) REFERENCES `lesson_occurrence` (`id`),
  CONSTRAINT `fk_lesson_record_package` FOREIGN KEY (`package_id`) REFERENCES `course_package` (`id`),
  CONSTRAINT `fk_lesson_record_student` FOREIGN KEY (`student_id`) REFERENCES `student` (`id`),
  CONSTRAINT `fk_lesson_record_planned_teacher` FOREIGN KEY (`planned_teacher_id`) REFERENCES `teacher` (`id`),
  CONSTRAINT `fk_lesson_record_actual_teacher` FOREIGN KEY (`actual_teacher_id`) REFERENCES `teacher` (`id`),
  CONSTRAINT `fk_lesson_record_planned_classroom` FOREIGN KEY (`planned_classroom_id`) REFERENCES `classroom` (`id`),
  CONSTRAINT `fk_lesson_record_actual_classroom` FOREIGN KEY (`actual_classroom_id`) REFERENCES `classroom` (`id`),
  CONSTRAINT `fk_lesson_record_confirm_user` FOREIGN KEY (`confirmed_by_user_id`) REFERENCES `sys_user` (`id`),
  CONSTRAINT `chk_lesson_record_units` CHECK (`actual_units` >= 0 AND `deducted_units` >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Lesson records';

CREATE TABLE IF NOT EXISTS `hour_ledger` (
  `id` BIGINT NOT NULL COMMENT 'Primary key ID',
  `package_id` BIGINT NOT NULL COMMENT 'Course package ID',
  `lesson_record_id` BIGINT DEFAULT NULL COMMENT 'Source lesson record ID',
  `occurrence_id` BIGINT DEFAULT NULL COMMENT 'Source planned occurrence ID',
  `ledger_type` VARCHAR(32) NOT NULL COMMENT 'Ledger type: INITIAL_ALLOCATE/DEDUCT/RESTORE/MANUAL_ADJUST/NO_DEDUCT_AUDIT',
  `change_units` INT NOT NULL COMMENT 'Unit change amount, positive adds and negative deducts',
  `balance_after_units` INT NOT NULL COMMENT 'Balance after change',
  `business_date` DATE NOT NULL COMMENT 'Business date',
  `reason_code` VARCHAR(64) DEFAULT NULL COMMENT 'Reason code',
  `reason_desc` VARCHAR(255) DEFAULT NULL COMMENT 'Reason description',
  `operated_by` BIGINT DEFAULT NULL COMMENT 'Operator user ID',
  `operated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT 'Operated at',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT 'Created at',
  `created_by` BIGINT DEFAULT NULL COMMENT 'Created by user ID',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT 'Updated at',
  `updated_by` BIGINT DEFAULT NULL COMMENT 'Updated by user ID',
  `is_deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT 'Soft delete flag: 0=no, 1=yes',
  `deleted_at` DATETIME(3) DEFAULT NULL COMMENT 'Deleted at',
  `deleted_by` BIGINT DEFAULT NULL COMMENT 'Deleted by user ID',
  `version` INT NOT NULL DEFAULT 0 COMMENT 'Optimistic lock version',
  PRIMARY KEY (`id`),
  KEY `idx_hour_ledger_package_time` (`package_id`, `operated_at` DESC, `id` DESC),
  KEY `idx_hour_ledger_record` (`lesson_record_id`),
  KEY `idx_hour_ledger_business` (`package_id`, `business_date`),
  KEY `idx_hour_ledger_occurrence` (`occurrence_id`),
  KEY `idx_hour_ledger_operator` (`operated_by`),
  CONSTRAINT `fk_hour_ledger_package` FOREIGN KEY (`package_id`) REFERENCES `course_package` (`id`),
  CONSTRAINT `fk_hour_ledger_record` FOREIGN KEY (`lesson_record_id`) REFERENCES `lesson_record` (`id`),
  CONSTRAINT `fk_hour_ledger_occurrence` FOREIGN KEY (`occurrence_id`) REFERENCES `lesson_occurrence` (`id`),
  CONSTRAINT `fk_hour_ledger_operator` FOREIGN KEY (`operated_by`) REFERENCES `sys_user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Hour ledger';

CREATE TABLE IF NOT EXISTS `package_balance_snapshot` (
  `package_id` BIGINT NOT NULL COMMENT 'Course package ID',
  `total_units` INT NOT NULL COMMENT 'Total units',
  `consumed_units` INT NOT NULL DEFAULT 0 COMMENT 'Consumed units',
  `remaining_units` INT NOT NULL DEFAULT 0 COMMENT 'Remaining units',
  `last_ledger_id` BIGINT DEFAULT NULL COMMENT 'Last ledger ID',
  `snapshot_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT 'Snapshot at',
  PRIMARY KEY (`package_id`),
  KEY `idx_balance_snapshot_ledger` (`last_ledger_id`),
  CONSTRAINT `fk_balance_snapshot_package` FOREIGN KEY (`package_id`) REFERENCES `course_package` (`id`),
  CONSTRAINT `fk_balance_snapshot_ledger` FOREIGN KEY (`last_ledger_id`) REFERENCES `hour_ledger` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Course package balance snapshot';

-- ----------------------------
-- 6. Notification and configuration tables
-- ----------------------------

CREATE TABLE IF NOT EXISTS `notification_template` (
  `id` BIGINT NOT NULL COMMENT 'Primary key ID',
  `template_code` VARCHAR(64) NOT NULL COMMENT 'Template code',
  `template_name` VARCHAR(128) NOT NULL COMMENT 'Template name',
  `channel_type` VARCHAR(32) NOT NULL DEFAULT 'EMAIL' COMMENT 'Channel type, EMAIL in phase one',
  `subject_template` VARCHAR(255) NOT NULL COMMENT 'Subject template',
  `body_template` LONGTEXT NOT NULL COMMENT 'Body template',
  `status` VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' COMMENT 'Status: ACTIVE/INACTIVE',
  `remark` VARCHAR(500) DEFAULT NULL COMMENT 'Notes',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT 'Created at',
  `created_by` BIGINT DEFAULT NULL COMMENT 'Created by user ID',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT 'Updated at',
  `updated_by` BIGINT DEFAULT NULL COMMENT 'Updated by user ID',
  `is_deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT 'Soft delete flag: 0=no, 1=yes',
  `deleted_at` DATETIME(3) DEFAULT NULL COMMENT 'Deleted at',
  `deleted_by` BIGINT DEFAULT NULL COMMENT 'Deleted by user ID',
  `version` INT NOT NULL DEFAULT 0 COMMENT 'Optimistic lock version',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_notification_template_code` (`template_code`),
  KEY `idx_notification_template_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Notification templates';

CREATE TABLE IF NOT EXISTS `notification_log` (
  `id` BIGINT NOT NULL COMMENT 'Primary key ID',
  `channel_type` VARCHAR(32) NOT NULL DEFAULT 'EMAIL' COMMENT 'Channel type',
  `template_id` BIGINT DEFAULT NULL COMMENT 'Template ID',
  `student_id` BIGINT DEFAULT NULL COMMENT 'Student ID',
  `package_id` BIGINT DEFAULT NULL COMMENT 'Course package ID',
  `lesson_record_id` BIGINT DEFAULT NULL COMMENT 'Source lesson record ID',
  `recipient` VARCHAR(255) NOT NULL COMMENT 'Recipient',
  `send_status` VARCHAR(32) NOT NULL DEFAULT 'PENDING' COMMENT 'Send status: PENDING/PROCESSING/SUCCESS/FAILED',
  `request_payload` JSON DEFAULT NULL COMMENT 'Request snapshot',
  `response_payload` JSON DEFAULT NULL COMMENT 'Response snapshot',
  `error_message` VARCHAR(500) DEFAULT NULL COMMENT 'Error message',
  `scheduled_at` DATETIME(3) DEFAULT NULL COMMENT 'Scheduled at',
  `sent_at` DATETIME(3) DEFAULT NULL COMMENT 'Sent at',
  `retry_count` INT NOT NULL DEFAULT 0 COMMENT 'Retry count',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT 'Created at',
  `created_by` BIGINT DEFAULT NULL COMMENT 'Created by user ID',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT 'Updated at',
  `updated_by` BIGINT DEFAULT NULL COMMENT 'Updated by user ID',
  `is_deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT 'Soft delete flag: 0=no, 1=yes',
  `deleted_at` DATETIME(3) DEFAULT NULL COMMENT 'Deleted at',
  `deleted_by` BIGINT DEFAULT NULL COMMENT 'Deleted by user ID',
  `version` INT NOT NULL DEFAULT 0 COMMENT 'Optimistic lock version',
  PRIMARY KEY (`id`),
  KEY `idx_notification_package` (`package_id`, `sent_at` DESC),
  KEY `idx_notification_record` (`lesson_record_id`),
  KEY `idx_notification_status` (`send_status`, `scheduled_at`),
  KEY `idx_notification_template` (`template_id`),
  KEY `idx_notification_student` (`student_id`),
  CONSTRAINT `fk_notification_template` FOREIGN KEY (`template_id`) REFERENCES `notification_template` (`id`),
  CONSTRAINT `fk_notification_student` FOREIGN KEY (`student_id`) REFERENCES `student` (`id`),
  CONSTRAINT `fk_notification_package` FOREIGN KEY (`package_id`) REFERENCES `course_package` (`id`),
  CONSTRAINT `fk_notification_lesson_record` FOREIGN KEY (`lesson_record_id`) REFERENCES `lesson_record` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Notification logs';

CREATE TABLE IF NOT EXISTS `brand_config` (
  `id` BIGINT NOT NULL COMMENT 'Primary key ID',
  `brand_name` VARCHAR(128) NOT NULL COMMENT 'Brand name',
  `logo_url` VARCHAR(500) DEFAULT NULL COMMENT 'Logo URL',
  `theme_color` VARCHAR(32) DEFAULT NULL COMMENT 'Theme color',
  `support_email` VARCHAR(128) DEFAULT NULL COMMENT 'Support email',
  `receipt_footer_text` VARCHAR(500) DEFAULT NULL COMMENT 'Receipt footer text',
  `status` VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' COMMENT 'Status: ACTIVE/INACTIVE',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT 'Created at',
  `created_by` BIGINT DEFAULT NULL COMMENT 'Created by user ID',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT 'Updated at',
  `updated_by` BIGINT DEFAULT NULL COMMENT 'Updated by user ID',
  `is_deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT 'Soft delete flag: 0=no, 1=yes',
  `deleted_at` DATETIME(3) DEFAULT NULL COMMENT 'Deleted at',
  `deleted_by` BIGINT DEFAULT NULL COMMENT 'Deleted by user ID',
  `version` INT NOT NULL DEFAULT 0 COMMENT 'Optimistic lock version',
  PRIMARY KEY (`id`),
  KEY `idx_brand_config_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Brand config';

CREATE TABLE IF NOT EXISTS `lesson_status_rule` (
  `id` BIGINT NOT NULL COMMENT 'Primary key ID',
  `status_code` VARCHAR(32) NOT NULL COMMENT 'Lesson status code',
  `status_name` VARCHAR(64) NOT NULL COMMENT 'Lesson status name',
  `default_should_deduct` TINYINT(1) NOT NULL DEFAULT 0 COMMENT 'Default deduct flag: 0=no, 1=yes',
  `deduct_mode` VARCHAR(32) NOT NULL DEFAULT 'NONE' COMMENT 'Deduct mode: NONE/ACTUAL_UNITS/PLANNED_UNITS/FIXED_UNITS',
  `fixed_units` INT DEFAULT NULL COMMENT 'Fixed deduct units',
  `default_should_extend` TINYINT(1) NOT NULL DEFAULT 0 COMMENT 'Default extend flag: 0=no, 1=yes',
  `allow_manual_override` TINYINT(1) NOT NULL DEFAULT 1 COMMENT 'Manual override flag: 0=no, 1=yes',
  `status` VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' COMMENT 'Rule status: ACTIVE/DISABLED',
  `remark` VARCHAR(500) DEFAULT NULL COMMENT 'Notes',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT 'Created at',
  `created_by` BIGINT DEFAULT NULL COMMENT 'Created by user ID',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT 'Updated at',
  `updated_by` BIGINT DEFAULT NULL COMMENT 'Updated by user ID',
  `is_deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT 'Soft delete flag: 0=no, 1=yes',
  `deleted_at` DATETIME(3) DEFAULT NULL COMMENT 'Deleted at',
  `deleted_by` BIGINT DEFAULT NULL COMMENT 'Deleted by user ID',
  `version` INT NOT NULL DEFAULT 0 COMMENT 'Optimistic lock version',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_lesson_status_rule_code` (`status_code`),
  KEY `idx_lesson_status_rule_status` (`status`),
  CONSTRAINT `chk_lesson_status_rule_fixed_units` CHECK (`fixed_units` IS NULL OR `fixed_units` >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Lesson status rules';

-- ----------------------------
-- 1.9 Class group (batch/group class management)
-- ----------------------------
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

-- ----------------------------
-- 1.10 Session persistence (MySQL fallback when Redis is unavailable)
-- ----------------------------
CREATE TABLE IF NOT EXISTS `auth_session` (
  `id` BIGINT NOT NULL COMMENT 'Primary key ID',
  `token` VARCHAR(64) NOT NULL COMMENT 'Session token (UUID without dash)',
  `user_id` BIGINT NOT NULL COMMENT 'User ID',
  `session_data` TEXT NOT NULL COMMENT 'JSON-serialized session payload',
  `expires_at` DATETIME(3) NOT NULL COMMENT 'Session expiry time',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT 'Created at',
  `created_by` BIGINT DEFAULT NULL COMMENT 'Created by user ID',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT 'Updated at',
  `updated_by` BIGINT DEFAULT NULL COMMENT 'Updated by user ID',
  `is_deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT 'Soft delete flag: 0=no, 1=yes',
  `deleted_at` DATETIME(3) DEFAULT NULL COMMENT 'Deleted at',
  `deleted_by` BIGINT DEFAULT NULL COMMENT 'Deleted by user ID',
  `version` INT NOT NULL DEFAULT 0 COMMENT 'Optimistic lock version',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_auth_session_token` (`token`),
  KEY `idx_auth_session_user` (`user_id`),
  KEY `idx_auth_session_expires` (`expires_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Session persistence (MySQL fallback for Redis failures)';

-- ----------------------------
-- 7. Seed data
-- ----------------------------

-- 7.1 Seed roles
INSERT INTO `sys_role` (`id`, `role_code`, `role_name`, `status`, `remark`)
VALUES
  (100, 'ADMIN', 'Administrator', 'ACTIVE', 'System administrator with full permissions'),
  (110, 'STAFF', 'Staff', 'ACTIVE', 'Internal staff responsible for day-to-day operations'),
  (120, 'TEACHER', 'Teacher', 'ACTIVE', 'Future role limited to viewing a personal timetable and confirming personal lessons'),
  (130, 'STUDENT', 'Student', 'ACTIVE', 'Student who can access their own learning portal')
ON DUPLICATE KEY UPDATE
  `role_name` = VALUES(`role_name`),
  `status` = VALUES(`status`),
  `remark` = VALUES(`remark`),
  `updated_at` = CURRENT_TIMESTAMP(3);

-- 7.1.1 Seed admin user account (password: admin123, BCrypt encoded with {bcrypt} prefix)
INSERT IGNORE INTO `sys_user` (`id`, `username`, `password_hash`, `user_type`, `display_name`, `status`, `is_deleted`, `version`, `created_at`, `updated_at`)
VALUES
  (1, 'admin', '{bcrypt}$2b$12$UWD.L4nVrcRuZ5npNfQZHO0l7TMTR/8DipVo2dNxMXDb/KqT/Y6xO', 'ADMIN', 'System Admin', 'ACTIVE', 0, 0, NOW(), NOW());

-- 7.1.2 Seed admin role assignment
INSERT IGNORE INTO `sys_user_role` (`id`, `user_id`, `role_id`, `created_at`)
VALUES (1, 1, 100, NOW());

-- 7.2 Seed dictionary types
INSERT INTO `sys_dict_type` (`id`, `dict_code`, `dict_name`, `status`, `remark`)
VALUES
  (1000, 'USER_TYPE', 'User type', 'ACTIVE', 'System account user type'),
  (1010, 'USER_STATUS', 'User status', 'ACTIVE', 'System account status'),
  (1020, 'STUDENT_STATUS', 'Student status', 'ACTIVE', 'Student status'),
  (1030, 'TEACHER_STATUS', 'Teacher status', 'ACTIVE', 'Teacher status'),
  (1040, 'CLASSROOM_STATUS', 'Classroom status', 'ACTIVE', 'Classroom status'),
  (1050, 'COURSE_PACKAGE_STATUS', 'Course package status', 'ACTIVE', 'Course package lifecycle status'),
  (1060, 'PAYMENT_STATUS', 'Payment status', 'ACTIVE', 'Payment status'),
  (1070, 'LESSON_PLANNING_STATUS', 'Lesson planning status', 'ACTIVE', 'Scheduling status'),
  (1080, 'LESSON_RESULT_STATUS', 'Lesson result status', 'ACTIVE', 'Lesson result status'),
  (1090, 'LESSON_LEDGER_TYPE', 'Unit ledger type', 'ACTIVE', 'Unit ledger type'),
  (1100, 'TEACHING_MODE', 'Teaching mode', 'ACTIVE', 'Course delivery mode')
ON DUPLICATE KEY UPDATE
  `dict_name` = VALUES(`dict_name`),
  `status` = VALUES(`status`),
  `remark` = VALUES(`remark`),
  `updated_at` = CURRENT_TIMESTAMP(3);

-- 7.3 Seed dictionary items
INSERT INTO `sys_dict_item` (`id`, `dict_type_id`, `item_code`, `item_name`, `item_value`, `sort_order`, `is_default`, `status`, `remark`)
VALUES
  (10001, 1000, 'ADMIN', 'Administrator', 'ADMIN', 10, 0, 'ACTIVE', 'Administrator account'),
  (10002, 1000, 'STAFF', 'Staff', 'STAFF', 20, 1, 'ACTIVE', 'Staff account'),
  (10003, 1000, 'TEACHER', 'Teacher', 'TEACHER', 30, 0, 'ACTIVE', 'Teacher account'),

  (10101, 1010, 'ACTIVE', 'Active', 'ACTIVE', 10, 1, 'ACTIVE', 'Can log in normally'),
  (10102, 1010, 'DISABLED', 'Disabled', 'DISABLED', 20, 0, 'ACTIVE', 'Account is disabled'),
  (10103, 1010, 'LOCKED', 'Locked', 'LOCKED', 30, 0, 'ACTIVE', 'Account is locked'),

  (10201, 1020, 'ACTIVE', 'Enrolled', 'ACTIVE', 10, 1, 'ACTIVE', 'Student is actively enrolled'),
  (10202, 1020, 'INACTIVE', 'Inactive', 'INACTIVE', 20, 0, 'ACTIVE', 'Student is no longer active'),

  (10301, 1030, 'ACTIVE', 'Active', 'ACTIVE', 10, 1, 'ACTIVE', 'Teacher is available for scheduling'),
  (10302, 1030, 'INACTIVE', 'Inactive', 'INACTIVE', 20, 0, 'ACTIVE', 'Teacher is temporarily unavailable for scheduling'),
  (10303, 1030, 'DISABLED', 'Disabled', 'DISABLED', 30, 0, 'ACTIVE', 'Account or profile is disabled'),

  (10401, 1040, 'ACTIVE', 'Active', 'ACTIVE', 10, 1, 'ACTIVE', 'Classroom is available for scheduling'),
  (10402, 1040, 'DISABLED', 'Inactive', 'DISABLED', 20, 0, 'ACTIVE', 'Classroom is inactive'),

  (10501, 1050, 'DRAFT', 'Draft', 'DRAFT', 10, 0, 'ACTIVE', 'Draft package'),
  (10502, 1050, 'ACTIVE', 'Active', 'ACTIVE', 20, 1, 'ACTIVE', 'Package is active'),
  (10503, 1050, 'COMPLETED', 'Completed', 'COMPLETED', 30, 0, 'ACTIVE', 'Package units are fully consumed'),
  (10504, 1050, 'CLOSED', 'Closed', 'CLOSED', 40, 0, 'ACTIVE', 'Closed manually'),

  (10601, 1060, 'UNPAID', 'Unpaid', 'UNPAID', 10, 1, 'ACTIVE', 'Payment not received yet'),
  (10602, 1060, 'PARTIAL', 'Partially paid', 'PARTIAL', 20, 0, 'ACTIVE', 'Payment partially collected'),
  (10603, 1060, 'PAID', 'Paid', 'PAID', 30, 0, 'ACTIVE', 'Payment collected'),

  (10701, 1070, 'SCHEDULED', 'Scheduled', 'SCHEDULED', 10, 1, 'ACTIVE', 'Regular planned lesson'),
  (10702, 1070, 'ADJUSTED', 'Adjusted', 'ADJUSTED', 20, 0, 'ACTIVE', 'Adjusted planned lesson'),
  (10703, 1070, 'CANCELLED', 'Cancelled', 'CANCELLED', 30, 0, 'ACTIVE', 'Cancelled planned lesson'),
  (10704, 1070, 'MERGED', 'Merged', 'MERGED', 40, 0, 'ACTIVE', 'Merged lesson'),

  (10801, 1080, 'COMPLETED', 'Completed', 'COMPLETED', 10, 1, 'ACTIVE', 'Lesson completed normally'),
  (10802, 1080, 'FORMAL_LEAVE', 'Formal leave', 'FORMAL_LEAVE', 20, 0, 'ACTIVE', 'Formal leave does not deduct units'),
  (10803, 1080, 'TEMP_CANCEL', 'Temporary cancellation', 'TEMP_CANCEL', 30, 0, 'ACTIVE', 'Temporary cancellation with configurable deduction behavior'),
  (10804, 1080, 'NO_SHOW', 'No-show', 'NO_SHOW', 40, 0, 'ACTIVE', 'Student no-show deducts units by default'),
  (10805, 1080, 'TEACHER_CANCEL', 'Teacher cancellation', 'TEACHER_CANCEL', 50, 0, 'ACTIVE', 'Teacher cancellation does not deduct units by default'),
  (10806, 1080, 'MAKEUP_COMPLETED', 'Makeup completed', 'MAKEUP_COMPLETED', 60, 0, 'ACTIVE', 'Makeup completed'),
  (10807, 1080, 'SUBSTITUTED', 'Substitution completed', 'SUBSTITUTED', 70, 0, 'ACTIVE', 'Substitution completed'),

  (10901, 1090, 'INITIAL_ALLOCATE', 'Initial allocation', 'INITIAL_ALLOCATE', 10, 1, 'ACTIVE', 'Initial course package allocation'),
  (10902, 1090, 'DEDUCT', 'Deduction', 'DEDUCT', 20, 0, 'ACTIVE', 'Standard unit deduction'),
  (10903, 1090, 'RESTORE', 'Restore', 'RESTORE', 30, 0, 'ACTIVE', 'Restore units'),
  (10904, 1090, 'MANUAL_ADJUST', 'Manual adjustment', 'MANUAL_ADJUST', 40, 0, 'ACTIVE', 'Manual unit adjustment'),
  (10905, 1090, 'NO_DEDUCT_AUDIT', 'No-deduct audit', 'NO_DEDUCT_AUDIT', 50, 0, 'ACTIVE', 'No deduction but keep the audit record'),

  (11001, 1100, 'ONE_ON_ONE', 'One-on-one', 'ONE_ON_ONE', 10, 1, 'ACTIVE', 'One-on-one course'),
  (11002, 1100, 'GROUP', 'Group', 'GROUP', 20, 0, 'ACTIVE', 'Group course')
ON DUPLICATE KEY UPDATE
  `item_name` = VALUES(`item_name`),
  `item_value` = VALUES(`item_value`),
  `sort_order` = VALUES(`sort_order`),
  `is_default` = VALUES(`is_default`),
  `status` = VALUES(`status`),
  `remark` = VALUES(`remark`),
  `updated_at` = CURRENT_TIMESTAMP(3);

-- 7.4 Seed menus and base permissions
INSERT INTO `sys_permission` (`id`, `permission_code`, `permission_name`, `permission_type`, `parent_id`, `path`, `method`, `status`, `remark`)
VALUES
  (10000, 'dashboard', 'Dashboard', 'MENU', NULL, '/dashboard', NULL, 'ACTIVE', 'Top-level menu: Dashboard'),

  (11000, 'base_data', 'Base data', 'MENU', NULL, '/base-data', NULL, 'ACTIVE', 'Top-level menu: Base data'),
  (11010, 'student', 'Student profiles', 'MENU', 11000, '/base-data/students', NULL, 'ACTIVE', 'Second-level menu: Student profiles'),
  (11020, 'teacher', 'Teacher profiles', 'MENU', 11000, '/base-data/teachers', NULL, 'ACTIVE', 'Second-level menu: Teacher profiles'),
  (11030, 'classroom', 'Classroom management', 'MENU', 11000, '/base-data/classrooms', NULL, 'ACTIVE', 'Second-level menu: Classroom management'),
  (11040, 'course_product', 'Course products', 'MENU', 11000, '/base-data/course-products', NULL, 'ACTIVE', 'Second-level menu: Course products'),

  (12000, 'enrollment_package', 'Enrollment and packages', 'MENU', NULL, '/enrollment', NULL, 'ACTIVE', 'Top-level menu: Enrollment and packages'),
  (12010, 'enrollment_order', 'Enrollment orders', 'MENU', 12000, '/enrollment/orders', NULL, 'ACTIVE', 'Second-level menu: Enrollment orders'),
  (12020, 'course_package', 'Course package management', 'MENU', 12000, '/enrollment/packages', NULL, 'ACTIVE', 'Second-level menu: Course package management'),
  (12030, 'receipt_record', 'Receipt management', 'MENU', 12000, '/enrollment/receipts', NULL, 'ACTIVE', 'Second-level menu: Receipt management'),

  (13000, 'schedule_center', 'Scheduling', 'MENU', NULL, '/schedule', NULL, 'ACTIVE', 'Top-level menu: Scheduling'),
  (13010, 'recurring_schedule', 'Recurring scheduling', 'MENU', 13000, '/schedule/recurring', NULL, 'ACTIVE', 'Second-level menu: Recurring scheduling'),
  (13020, 'adjustment', 'Reschedules and makeup', 'MENU', 13000, '/schedule/adjustments', NULL, 'ACTIVE', 'Second-level menu: Reschedules and makeup'),
  (13030, 'teacher_schedule', 'Teacher timetable', 'MENU', 13000, '/schedule/teacher', NULL, 'ACTIVE', 'Second-level menu: Teacher timetable'),
  (13040, 'classroom_occupancy', 'Classroom occupancy', 'MENU', 13000, '/schedule/classrooms', NULL, 'ACTIVE', 'Second-level menu: Classroom occupancy'),

  (14000, 'lesson_deduction', 'Lessons and deduction', 'MENU', NULL, '/lessons', NULL, 'ACTIVE', 'Top-level menu: Lessons and deduction'),
  (14010, 'lesson_record', 'Lesson records', 'MENU', 14000, '/lessons/records', NULL, 'ACTIVE', 'Second-level menu: Lesson records'),
  (14020, 'lesson_exception', 'Exception records', 'MENU', 14000, '/lessons/exceptions', NULL, 'ACTIVE', 'Second-level menu: Exception records'),
  (14030, 'hour_ledger', 'Deduction ledger', 'MENU', 14000, '/lessons/ledger', NULL, 'ACTIVE', 'Second-level menu: Deduction ledger'),
  (14040, 'package_balance', 'Remaining units', 'MENU', 14000, '/lessons/balance', NULL, 'ACTIVE', 'Second-level menu: Remaining units'),

  (15000, 'system_setting', 'System settings', 'MENU', NULL, '/system', NULL, 'ACTIVE', 'Top-level menu: System settings'),
  (15010, 'lesson_status_rule', 'Lesson status rules', 'MENU', 15000, '/system/rules', NULL, 'ACTIVE', 'Second-level menu: Lesson status rules'),
  (15020, 'notification_template', 'Notification templates', 'MENU', 15000, '/system/notifications', NULL, 'ACTIVE', 'Second-level menu: Notification templates'),
  (15030, 'brand_config', 'Brand settings', 'MENU', 15000, '/system/brand', NULL, 'ACTIVE', 'Second-level menu: Brand settings'),
  (15040, 'account_permission', 'Accounts and permissions', 'MENU', 15000, '/system/accounts', NULL, 'ACTIVE', 'Second-level menu: Accounts and permissions'),

  (16010, 'teacher:self:schedule:view', 'Teacher view own timetable', 'ACTION', NULL, NULL, NULL, 'ACTIVE', 'Teacher-side extension permission'),
  (16020, 'teacher:self:lesson:confirm', 'Teacher confirm own lessons', 'ACTION', NULL, NULL, NULL, 'ACTIVE', 'Teacher-side extension permission')
ON DUPLICATE KEY UPDATE
  `permission_name` = VALUES(`permission_name`),
  `permission_type` = VALUES(`permission_type`),
  `parent_id` = VALUES(`parent_id`),
  `path` = VALUES(`path`),
  `method` = VALUES(`method`),
  `status` = VALUES(`status`),
  `remark` = VALUES(`remark`),
  `updated_at` = CURRENT_TIMESTAMP(3);

-- 7.5 Seed role permissions
INSERT IGNORE INTO `sys_role_permission` (`id`, `role_id`, `permission_id`, `created_at`)
VALUES
  (20001, 100, 10000, CURRENT_TIMESTAMP(3)),
  (20002, 100, 11000, CURRENT_TIMESTAMP(3)),
  (20003, 100, 11010, CURRENT_TIMESTAMP(3)),
  (20004, 100, 11020, CURRENT_TIMESTAMP(3)),
  (20005, 100, 11030, CURRENT_TIMESTAMP(3)),
  (20006, 100, 11040, CURRENT_TIMESTAMP(3)),
  (20007, 100, 12000, CURRENT_TIMESTAMP(3)),
  (20008, 100, 12010, CURRENT_TIMESTAMP(3)),
  (20009, 100, 12020, CURRENT_TIMESTAMP(3)),
  (20010, 100, 12030, CURRENT_TIMESTAMP(3)),
  (20011, 100, 13000, CURRENT_TIMESTAMP(3)),
  (20012, 100, 13010, CURRENT_TIMESTAMP(3)),
  (20013, 100, 13020, CURRENT_TIMESTAMP(3)),
  (20014, 100, 13030, CURRENT_TIMESTAMP(3)),
  (20015, 100, 13040, CURRENT_TIMESTAMP(3)),
  (20016, 100, 14000, CURRENT_TIMESTAMP(3)),
  (20017, 100, 14010, CURRENT_TIMESTAMP(3)),
  (20018, 100, 14020, CURRENT_TIMESTAMP(3)),
  (20019, 100, 14030, CURRENT_TIMESTAMP(3)),
  (20020, 100, 14040, CURRENT_TIMESTAMP(3)),
  (20021, 100, 15000, CURRENT_TIMESTAMP(3)),
  (20022, 100, 15010, CURRENT_TIMESTAMP(3)),
  (20023, 100, 15020, CURRENT_TIMESTAMP(3)),
  (20024, 100, 15030, CURRENT_TIMESTAMP(3)),
  (20025, 100, 15040, CURRENT_TIMESTAMP(3)),
  (20026, 100, 16010, CURRENT_TIMESTAMP(3)),
  (20027, 100, 16020, CURRENT_TIMESTAMP(3)),

  (20101, 110, 10000, CURRENT_TIMESTAMP(3)),
  (20102, 110, 11000, CURRENT_TIMESTAMP(3)),
  (20103, 110, 11010, CURRENT_TIMESTAMP(3)),
  (20104, 110, 11020, CURRENT_TIMESTAMP(3)),
  (20105, 110, 11030, CURRENT_TIMESTAMP(3)),
  (20106, 110, 11040, CURRENT_TIMESTAMP(3)),
  (20107, 110, 12000, CURRENT_TIMESTAMP(3)),
  (20108, 110, 12010, CURRENT_TIMESTAMP(3)),
  (20109, 110, 12020, CURRENT_TIMESTAMP(3)),
  (20110, 110, 12030, CURRENT_TIMESTAMP(3)),
  (20111, 110, 13000, CURRENT_TIMESTAMP(3)),
  (20112, 110, 13010, CURRENT_TIMESTAMP(3)),
  (20113, 110, 13020, CURRENT_TIMESTAMP(3)),
  (20114, 110, 13030, CURRENT_TIMESTAMP(3)),
  (20115, 110, 13040, CURRENT_TIMESTAMP(3)),
  (20116, 110, 14000, CURRENT_TIMESTAMP(3)),
  (20117, 110, 14010, CURRENT_TIMESTAMP(3)),
  (20118, 110, 14020, CURRENT_TIMESTAMP(3)),
  (20119, 110, 14030, CURRENT_TIMESTAMP(3)),
  (20120, 110, 14040, CURRENT_TIMESTAMP(3)),

  (20201, 120, 16010, CURRENT_TIMESTAMP(3)),
  (20202, 120, 16020, CURRENT_TIMESTAMP(3));

-- 7.6 Seed lesson status rules
INSERT INTO `lesson_status_rule` (
  `id`, `status_code`, `status_name`, `default_should_deduct`, `deduct_mode`, `fixed_units`,
  `default_should_extend`, `allow_manual_override`, `status`, `remark`
)
VALUES
  (30001, 'COMPLETED', 'Completed', 1, 'ACTUAL_UNITS', NULL, 0, 1, 'ACTIVE', 'Completed lesson; deduct actual units'),
  (30002, 'FORMAL_LEAVE', 'Formal leave', 0, 'NONE', NULL, 1, 1, 'ACTIVE', 'Formal leave does not deduct units and extends the end date'),
  (30003, 'TEMP_CANCEL', 'Temporary cancellation', 0, 'NONE', NULL, 1, 1, 'ACTIVE', 'Temporary cancellation does not deduct units by default and can be overridden'),
  (30004, 'NO_SHOW', 'No-show', 1, 'PLANNED_UNITS', NULL, 0, 1, 'ACTIVE', 'No-show deducts planned units by default'),
  (30005, 'TEACHER_CANCEL', 'Teacher cancellation', 0, 'NONE', NULL, 1, 1, 'ACTIVE', 'Teacher cancellation does not deduct units and extends the end date'),
  (30006, 'MAKEUP_COMPLETED', 'Makeup completed', 1, 'ACTUAL_UNITS', NULL, 0, 1, 'ACTIVE', 'Makeup completed; deduct actual units'),
  (30007, 'SUBSTITUTED', 'Substitution completed', 1, 'ACTUAL_UNITS', NULL, 0, 1, 'ACTIVE', 'Substitution completed; deduct actual units')
ON DUPLICATE KEY UPDATE
  `status_name` = VALUES(`status_name`),
  `default_should_deduct` = VALUES(`default_should_deduct`),
  `deduct_mode` = VALUES(`deduct_mode`),
  `fixed_units` = VALUES(`fixed_units`),
  `default_should_extend` = VALUES(`default_should_extend`),
  `allow_manual_override` = VALUES(`allow_manual_override`),
  `status` = VALUES(`status`),
  `remark` = VALUES(`remark`),
  `updated_at` = CURRENT_TIMESTAMP(3);

-- 7.7 Seed brand configuration
INSERT INTO `brand_config` (
  `id`, `brand_name`, `logo_url`, `theme_color`, `support_email`, `receipt_footer_text`, `status`
)
VALUES
  (40001, 'JIN BEST TUTORING', NULL, '#18b58a', 'support@example.com', 'Thank you for choosing JIN BEST TUTORING.', 'ACTIVE')
ON DUPLICATE KEY UPDATE
  `brand_name` = VALUES(`brand_name`),
  `logo_url` = VALUES(`logo_url`),
  `theme_color` = VALUES(`theme_color`),
  `support_email` = VALUES(`support_email`),
  `receipt_footer_text` = VALUES(`receipt_footer_text`),
  `status` = VALUES(`status`),
  `updated_at` = CURRENT_TIMESTAMP(3);

-- 7.8 Seed notification templates
INSERT INTO `notification_template` (
  `id`, `template_code`, `template_name`, `channel_type`, `subject_template`, `body_template`, `status`, `remark`
)
VALUES
  (
    50001,
    'LESSON_BALANCE_EMAIL',
    'Post-lesson remaining units notice',
    'EMAIL',
    '[${brandName}] ${studentName} lesson summary and remaining units',
    'Dear parent,\n\nStudent: ${studentName}\nCourse package: ${packageName}\nLesson status: ${lessonStatus}\nUnits deducted this time: ${deductHours}\nRemaining units: ${remainingHours}\nEstimated end date: ${estimatedEndDate}\n\nIf anything looks incorrect, please contact the front desk team.\n\n${brandName}',
    'ACTIVE',
    'Remaining-unit email template sent to guardians after lesson confirmation'
  )
ON DUPLICATE KEY UPDATE
  `template_name` = VALUES(`template_name`),
  `channel_type` = VALUES(`channel_type`),
  `subject_template` = VALUES(`subject_template`),
  `body_template` = VALUES(`body_template`),
  `status` = VALUES(`status`),
  `remark` = VALUES(`remark`),
  `updated_at` = CURRENT_TIMESTAMP(3);

-- ----------------------------
-- Initial subjects data
-- ----------------------------
INSERT INTO `subject` (`id`, `subject_name`, `display_order`, `status`, `notes`) VALUES
(1, 'Math', 1, 'ACTIVE', 'Mathematics courses including algebra, geometry, calculus, SHSAT math, etc.'),
(2, 'English', 2, 'ACTIVE', 'English language arts, reading, writing, ELA test prep.'),
(3, 'Science', 3, 'ACTIVE', 'Science courses including physics, chemistry, biology.'),
(4, 'SAT', 4, 'ACTIVE', 'SAT preparation courses.'),
(5, 'Music', 5, 'ACTIVE', 'Music lessons including piano, guitar, violin, etc.'),
(6, 'Reading', 6, 'ACTIVE', 'Reading comprehension and literacy skills.')
ON DUPLICATE KEY UPDATE
  `subject_name` = VALUES(`subject_name`),
  `display_order` = VALUES(`display_order`),
  `status` = VALUES(`status`),
  `notes` = VALUES(`notes`);

SET FOREIGN_KEY_CHECKS = 1;

-- ----------------------------
-- 7.9 Notification Config (system config, single row id=1)
-- ----------------------------
CREATE TABLE IF NOT EXISTS `notification_config` (
  `id` BIGINT NOT NULL COMMENT 'Primary key ID',
  `enabled` TINYINT(1) NOT NULL DEFAULT 0 COMMENT 'Email enabled flag: 0=disabled, 1=enabled',
  `from_address` VARCHAR(255) NOT NULL DEFAULT '' COMMENT 'SMTP sender email address',
  `from_name` VARCHAR(128) NOT NULL DEFAULT 'JIN BEST TUTORING' COMMENT 'SMTP sender display name',
  `auth_code` VARCHAR(255) NOT NULL DEFAULT '' COMMENT 'SMTP authorization code (encrypted in production)',
  `strict_allowlist` TINYINT(1) NOT NULL DEFAULT 1 COMMENT 'Allowlist mode: 0=disabled (allow all), 1=enabled (allow only allowed_recipients)',
  `allowed_recipients` TEXT DEFAULT NULL COMMENT 'JSON array of allowed recipient domains or emails',
  `low_balance_threshold` INT NOT NULL DEFAULT 2 COMMENT 'Low balance warning threshold in 0.5h units (e.g. 2 = 1 hour)',
  `low_balance_recipients` TEXT DEFAULT NULL COMMENT 'JSON array of email addresses to notify on low balance',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT 'Created at',
  `created_by` BIGINT DEFAULT NULL COMMENT 'Created by user ID',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT 'Updated at',
  `updated_by` BIGINT DEFAULT NULL COMMENT 'Updated by user ID',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Email sending configuration';

-- ----------------------------
-- 7.10 Notification Trigger Rules
-- ----------------------------
CREATE TABLE IF NOT EXISTS `notification_trigger_rule` (
  `id` BIGINT NOT NULL COMMENT 'Primary key ID',
  `trigger_type` VARCHAR(64) NOT NULL COMMENT 'Trigger type: LESSON_CONFIRMED / LOW_BALANCE',
  `template_id` BIGINT DEFAULT NULL COMMENT 'Associated notification_template.id',
  `enabled` TINYINT(1) NOT NULL DEFAULT 0 COMMENT 'Trigger enabled flag',
  `recipient_type` VARCHAR(32) NOT NULL DEFAULT 'GUARDIAN_EMAIL' COMMENT 'Recipient type: GUARDIAN_EMAIL / CUSTOM_EMAIL',
  `custom_recipients` TEXT DEFAULT NULL COMMENT 'JSON array of email addresses when recipient_type=CUSTOM_EMAIL',
  `threshold_value` INT DEFAULT NULL COMMENT 'Threshold value for LOW_BALANCE trigger (in 0.5h units)',
  `remark` VARCHAR(500) DEFAULT NULL COMMENT 'Notes',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT 'Created at',
  `created_by` BIGINT DEFAULT NULL COMMENT 'Created by user ID',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT 'Updated at',
  `updated_by` BIGINT DEFAULT NULL COMMENT 'Updated by user ID',
  `is_deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT 'Soft delete flag: 0=no, 1=yes',
  `deleted_at` DATETIME(3) DEFAULT NULL COMMENT 'Deleted at',
  `deleted_by` BIGINT DEFAULT NULL COMMENT 'Deleted by user ID',
  `version` INT NOT NULL DEFAULT 0 COMMENT 'Optimistic lock version',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_trigger_type` (`trigger_type`, `is_deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Notification trigger rules: controls when and to whom emails are sent';

-- Seed notification config
INSERT INTO `notification_config` (`id`, `enabled`, `from_address`, `from_name`, `auth_code`, `strict_allowlist`, `allowed_recipients`, `low_balance_threshold`, `low_balance_recipients`, `created_at`, `updated_at`)
VALUES (1, 0, '', 'JIN BEST TUTORING', '', 1, '[]', 2, '[]', NOW(), NOW())
ON DUPLICATE KEY UPDATE `id`=`id`;

-- Seed trigger rules
INSERT INTO `notification_trigger_rule` (`id`, `trigger_type`, `template_id`, `enabled`, `recipient_type`, `threshold_value`, `remark`, `created_at`, `updated_at`, `is_deleted`, `version`)
VALUES
  (1, 'LESSON_CONFIRMED', 50001, 0, 'GUARDIAN_EMAIL', NULL, 'Send email to guardian after lesson confirmation', NOW(), NOW(), 0, 0),
  (2, 'LOW_BALANCE', 50001, 0, 'CUSTOM_EMAIL', 4, 'Send email when remaining hours drops below threshold (4 units = 2h)', NOW(), NOW(), 0, 0)
ON DUPLICATE KEY UPDATE
  `template_id` = VALUES(`template_id`),
  `enabled` = VALUES(`enabled`),
  `recipient_type` = VALUES(`recipient_type`),
  `threshold_value` = VALUES(`threshold_value`);

-- ----------------------------
-- 10. Teacher payment tables
-- ----------------------------

CREATE TABLE IF NOT EXISTS `teacher_pay_rate` (
  `id` BIGINT NOT NULL COMMENT 'Primary key ID',
  `teacher_id` BIGINT NOT NULL COMMENT 'Teacher ID',
  `course_product_id` BIGINT NOT NULL COMMENT 'Course product ID',
  `hourly_rate` DECIMAL(10,2) NOT NULL COMMENT 'Per-hour rate in dollars',
  `notes` VARCHAR(500) DEFAULT NULL COMMENT 'Notes',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT 'Created at',
  `created_by` BIGINT DEFAULT NULL COMMENT 'Created by user ID',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT 'Updated at',
  `updated_by` BIGINT DEFAULT NULL COMMENT 'Updated by user ID',
  `is_deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT 'Soft delete flag: 0=no, 1=yes',
  `deleted_at` DATETIME(3) DEFAULT NULL COMMENT 'Deleted at',
  `deleted_by` BIGINT DEFAULT NULL COMMENT 'Deleted by user ID',
  `version` INT NOT NULL DEFAULT 0 COMMENT 'Optimistic lock version',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_teacher_product` (`teacher_id`, `course_product_id`),
  KEY `idx_pay_rate_teacher` (`teacher_id`),
  KEY `idx_pay_rate_product` (`course_product_id`),
  CONSTRAINT `fk_pay_rate_teacher` FOREIGN KEY (`teacher_id`) REFERENCES `teacher` (`id`),
  CONSTRAINT `fk_pay_rate_product` FOREIGN KEY (`course_product_id`) REFERENCES `course_product` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Teacher hourly pay rate configuration';

CREATE TABLE IF NOT EXISTS `teacher_payment` (
  `id` BIGINT NOT NULL COMMENT 'Primary key ID',
  `payment_no` VARCHAR(32) NOT NULL COMMENT 'Payment number',
  `teacher_id` BIGINT NOT NULL COMMENT 'Teacher ID',
  `period_start` DATE NOT NULL COMMENT 'Settlement period start',
  `period_end` DATE NOT NULL COMMENT 'Settlement period end',
  `total_units` INT NOT NULL COMMENT 'Total half-hour units',
  `total_hours` DECIMAL(8,1) NOT NULL COMMENT 'Total hours (units/2)',
  `total_amount` DECIMAL(10,2) NOT NULL COMMENT 'Total payment amount',
  `status` VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT 'Status: PENDING/PAID/CANCELLED',
  `paid_at` DATETIME(3) DEFAULT NULL COMMENT 'Paid at timestamp',
  `paid_by` BIGINT DEFAULT NULL COMMENT 'Paid by user ID',
  `notes` VARCHAR(500) DEFAULT NULL COMMENT 'Notes',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT 'Created at',
  `created_by` BIGINT DEFAULT NULL COMMENT 'Created by user ID',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT 'Updated at',
  `updated_by` BIGINT DEFAULT NULL COMMENT 'Updated by user ID',
  `is_deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT 'Soft delete flag: 0=no, 1=yes',
  `deleted_at` DATETIME(3) DEFAULT NULL COMMENT 'Deleted at',
  `deleted_by` BIGINT DEFAULT NULL COMMENT 'Deleted by user ID',
  `version` INT NOT NULL DEFAULT 0 COMMENT 'Optimistic lock version',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_payment_no` (`payment_no`),
  KEY `idx_payment_teacher` (`teacher_id`),
  KEY `idx_payment_status` (`status`),
  CONSTRAINT `fk_payment_teacher` FOREIGN KEY (`teacher_id`) REFERENCES `teacher` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Teacher payment records';

CREATE TABLE IF NOT EXISTS `teacher_payment_item` (
  `id` BIGINT NOT NULL COMMENT 'Primary key ID',
  `payment_id` BIGINT NOT NULL COMMENT 'Payment ID',
  `lesson_record_id` BIGINT NOT NULL COMMENT 'Lesson record ID',
  `package_id` BIGINT DEFAULT NULL COMMENT 'Course package ID',
  `course_product_id` BIGINT DEFAULT NULL COMMENT 'Course product ID',
  `actual_units` INT NOT NULL COMMENT 'Actual half-hour units',
  `hourly_rate` DECIMAL(10,2) NOT NULL COMMENT 'Hourly rate snapshot at payment time',
  `amount` DECIMAL(10,2) NOT NULL COMMENT 'Calculated amount (hours * rate)',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT 'Created at',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_payment_item_lesson_record` (`lesson_record_id`),
  KEY `idx_payment_item_payment` (`payment_id`),
  CONSTRAINT `fk_payment_item_payment` FOREIGN KEY (`payment_id`) REFERENCES `teacher_payment` (`id`),
  CONSTRAINT `fk_payment_item_lesson_record` FOREIGN KEY (`lesson_record_id`) REFERENCES `lesson_record` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Teacher payment item details';

