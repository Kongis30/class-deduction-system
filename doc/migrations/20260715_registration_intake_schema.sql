-- ============================================================================
-- Migration: Registration intake schema support
-- Date: 2026-07-15
-- Target tables:
--   student, enrollment_order, enrollment_order_item, course_price_option,
--   course_package, class_enrollment
--
-- Notes:
--   This project does not use Flyway or Liquibase. Back up the database before
--   running this script and keep doc/mysql_schema_from_db.sql in sync.
--   The changes below are additive and keep existing enrollment data intact.
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 1. student: add current profile fields needed by the registration intake form.
-- ---------------------------------------------------------------------------
ALTER TABLE `student`
  ADD COLUMN `grade` varchar(32) DEFAULT NULL COMMENT 'Current student grade' AFTER `birth_date`,
  ADD COLUMN `school` varchar(128) DEFAULT NULL COMMENT 'Current school name' AFTER `grade`,
  ADD COLUMN `guardian_phone_2` varchar(32) DEFAULT NULL COMMENT 'Secondary guardian phone' AFTER `guardian_phone`,
  ADD COLUMN `pickup_required` tinyint(1) NOT NULL DEFAULT '0' COMMENT 'Default pickup flag: 0=no, 1=yes' AFTER `guardian_relation_type`,
  ADD COLUMN `pickup_address` varchar(255) DEFAULT NULL COMMENT 'Default pickup address' AFTER `pickup_required`,
  ADD COLUMN `dropoff_required` tinyint(1) NOT NULL DEFAULT '0' COMMENT 'Default dropoff flag: 0=no, 1=yes' AFTER `pickup_address`,
  ADD COLUMN `dropoff_address` varchar(255) DEFAULT NULL COMMENT 'Default dropoff address' AFTER `dropoff_required`,
  ADD KEY `idx_student_name_phone` (`student_name`, `guardian_phone`, `is_deleted`);

-- ---------------------------------------------------------------------------
-- 2. enrollment_order: add stable registration and fee snapshot fields.
-- ---------------------------------------------------------------------------
ALTER TABLE `enrollment_order`
  ADD COLUMN `campus_id` bigint DEFAULT NULL COMMENT 'Campus ID selected during registration' AFTER `class_id`,
  ADD COLUMN `campus_name_snapshot` varchar(128) DEFAULT NULL COMMENT 'Campus name snapshot' AFTER `campus_id`,
  ADD COLUMN `signup_date` date DEFAULT NULL COMMENT 'Registration signup date' AFTER `handled_by_name`,
  ADD COLUMN `period_start_date` date DEFAULT NULL COMMENT 'Registration period start date' AFTER `signup_date`,
  ADD COLUMN `period_end_date` date DEFAULT NULL COMMENT 'Registration period end date' AFTER `period_start_date`,
  ADD COLUMN `student_name_snapshot` varchar(64) DEFAULT NULL COMMENT 'Student name snapshot' AFTER `period_end_date`,
  ADD COLUMN `grade_snapshot` varchar(32) DEFAULT NULL COMMENT 'Student grade snapshot' AFTER `student_name_snapshot`,
  ADD COLUMN `school_snapshot` varchar(128) DEFAULT NULL COMMENT 'Student school snapshot' AFTER `grade_snapshot`,
  ADD COLUMN `parent_phone_snapshot` varchar(32) DEFAULT NULL COMMENT 'Primary guardian phone snapshot' AFTER `school_snapshot`,
  ADD COLUMN `parent_phone_2_snapshot` varchar(32) DEFAULT NULL COMMENT 'Secondary guardian phone snapshot' AFTER `parent_phone_snapshot`,
  ADD COLUMN `pickup_required_snapshot` tinyint(1) NOT NULL DEFAULT '0' COMMENT 'Pickup flag snapshot: 0=no, 1=yes' AFTER `parent_phone_2_snapshot`,
  ADD COLUMN `pickup_address_snapshot` varchar(255) DEFAULT NULL COMMENT 'Pickup address snapshot' AFTER `pickup_required_snapshot`,
  ADD COLUMN `dropoff_required_snapshot` tinyint(1) NOT NULL DEFAULT '0' COMMENT 'Dropoff flag snapshot: 0=no, 1=yes' AFTER `pickup_address_snapshot`,
  ADD COLUMN `dropoff_address_snapshot` varchar(255) DEFAULT NULL COMMENT 'Dropoff address snapshot' AFTER `dropoff_required_snapshot`,
  ADD COLUMN `tuition_subtotal_amount` decimal(12,2) NOT NULL DEFAULT '0.00' COMMENT 'Subtotal of course line prices' AFTER `currency_code`,
  ADD COLUMN `pickup_fee_amount` decimal(12,2) NOT NULL DEFAULT '0.00' COMMENT 'Pickup and dropoff fee amount' AFTER `tuition_subtotal_amount`,
  ADD COLUMN `extra_fee_amount` decimal(12,2) NOT NULL DEFAULT '0.00' COMMENT 'Total extra fee amount' AFTER `pickup_fee_amount`,
  ADD COLUMN `extra_fee_detail` json DEFAULT NULL COMMENT 'Extra fee detail snapshot JSON' AFTER `extra_fee_amount`,
  ADD COLUMN `discount_detail` json DEFAULT NULL COMMENT 'Discount detail snapshot JSON' AFTER `discount_amount`,
  ADD COLUMN `amount_due` decimal(12,2) NOT NULL DEFAULT '0.00' COMMENT 'Amount due after fees and discounts' AFTER `received_amount`,
  ADD COLUMN `balance_amount` decimal(12,2) NOT NULL DEFAULT '0.00' COMMENT 'Outstanding balance snapshot' AFTER `amount_due`,
  ADD KEY `idx_enrollment_campus_signup` (`campus_id`, `signup_date`),
  ADD KEY `idx_enrollment_period_end` (`period_end_date`),
  ADD CONSTRAINT `fk_enrollment_campus` FOREIGN KEY (`campus_id`) REFERENCES `campus` (`id`);

-- ---------------------------------------------------------------------------
-- 3. course_price_option: structured registration price options.
-- ---------------------------------------------------------------------------
ALTER TABLE `course_product`
  ADD COLUMN `course_type` varchar(32) DEFAULT NULL COMMENT 'Parent course type: AFTER_SCHOOL/SATURDAY/SUMMER/SPRINT/ONE_ON_ONE' AFTER `product_name`,
  ADD COLUMN `register_class` varchar(64) DEFAULT NULL COMMENT 'Register Class label shown on registration' AFTER `course_type`,
  ADD KEY `idx_course_product_type` (`course_type`, `status`);

CREATE TABLE `course_price_option` (
  `id` bigint NOT NULL COMMENT 'Primary key ID',
  `option_code` varchar(64) NOT NULL COMMENT 'Unique price option code',
  `course_type` varchar(32) NOT NULL COMMENT 'Course type: AFTER_SCHOOL/SATURDAY/SUMMER/SPRINT/ONE_ON_ONE',
  `register_class` varchar(64) NOT NULL COMMENT 'Register Class display label',
  `grade_band` varchar(64) DEFAULT NULL COMMENT 'Grade or level option label',
  `period_label` varchar(128) DEFAULT NULL COMMENT 'Period or package option label',
  `duration_label` varchar(128) DEFAULT NULL COMMENT 'Duration option label',
  `time_slot_label` varchar(128) DEFAULT NULL COMMENT 'Time slot option label',
  `detail_text` varchar(500) NOT NULL COMMENT 'Generated detail text used on registration and receipt',
  `one_on_one_hours` decimal(6,2) DEFAULT NULL COMMENT 'One-on-one purchased hours, null for group options',
  `price_amount` decimal(12,2) NOT NULL DEFAULT '0.00' COMMENT 'Configured sale price for this matrix row',
  `currency_code` varchar(16) NOT NULL DEFAULT 'USD' COMMENT 'Currency code',
  `course_product_id` bigint NOT NULL COMMENT 'Linked parent course product ID',
  `sort_order` int NOT NULL DEFAULT '0' COMMENT 'Sort order',
  `status` varchar(32) NOT NULL DEFAULT 'ACTIVE' COMMENT 'Status: ACTIVE/INACTIVE',
  `notes` varchar(500) DEFAULT NULL COMMENT 'Internal notes',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT 'Created at',
  `created_by` bigint DEFAULT NULL COMMENT 'Created by user ID',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT 'Updated at',
  `updated_by` bigint DEFAULT NULL COMMENT 'Updated by user ID',
  `is_deleted` tinyint(1) NOT NULL DEFAULT '0' COMMENT 'Soft delete flag: 0=no, 1=yes',
  `deleted_at` datetime(3) DEFAULT NULL COMMENT 'Deleted at',
  `deleted_by` bigint DEFAULT NULL COMMENT 'Deleted by user ID',
  `version` int NOT NULL DEFAULT '0' COMMENT 'Optimistic lock version',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_course_price_option_code` (`option_code`),
  KEY `idx_course_price_option_type` (`course_type`, `status`, `sort_order`),
  KEY `idx_course_price_option_product` (`course_product_id`),
  CONSTRAINT `fk_course_price_option_product` FOREIGN KEY (`course_product_id`) REFERENCES `course_product` (`id`),
  CONSTRAINT `chk_course_price_option_active_product` CHECK (((`status` <> 'ACTIVE') OR (`course_product_id` IS NOT NULL))),
  CONSTRAINT `chk_course_price_option_amount` CHECK (`price_amount` >= 0),
  CONSTRAINT `chk_course_price_option_hours` CHECK (((`one_on_one_hours` IS NULL) OR (`one_on_one_hours` >= 0)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Structured course price options for registration intake';

-- ---------------------------------------------------------------------------
-- 4. enrollment_order_item: preserve multi-course registration line snapshots.
-- ---------------------------------------------------------------------------
CREATE TABLE `enrollment_order_item` (
  `id` bigint NOT NULL COMMENT 'Primary key ID',
  `enrollment_order_id` bigint NOT NULL COMMENT 'Enrollment order ID',
  `line_no` int NOT NULL DEFAULT '1' COMMENT 'Line number inside the enrollment order',
  `register_class` varchar(64) NOT NULL COMMENT 'Register Class label snapshot',
  `course_type` varchar(32) DEFAULT NULL COMMENT 'Structured course type snapshot',
  `course_product_id` bigint DEFAULT NULL COMMENT 'Selected course product ID',
  `course_price_option_id` bigint DEFAULT NULL COMMENT 'Selected price option ID',
  `class_id` bigint DEFAULT NULL COMMENT 'Generated or selected class group ID',
  `detail` varchar(500) NOT NULL COMMENT 'Course detail snapshot for receipt printing',
  `grade_band` varchar(64) DEFAULT NULL COMMENT 'Grade or level option snapshot',
  `period_label` varchar(128) DEFAULT NULL COMMENT 'Period or package option snapshot',
  `time_slot_label` varchar(128) DEFAULT NULL COMMENT 'Time slot option snapshot',
  `price_amount` decimal(12,2) NOT NULL DEFAULT '0.00' COMMENT 'Final line price amount',
  `original_price_amount` decimal(12,2) DEFAULT NULL COMMENT 'Original configured price before manual override',
  `manual_price_override` tinyint(1) NOT NULL DEFAULT '0' COMMENT 'Manual price override flag: 0=no, 1=yes',
  `one_on_one_hours` decimal(6,2) DEFAULT NULL COMMENT 'One-on-one purchased hours',
  `one_on_one_units` int DEFAULT NULL COMMENT 'One-on-one units snapshot, 1 unit = 0.5 hour',
  `initial_lesson_start_at` datetime(3) DEFAULT NULL COMMENT 'Structured first one-on-one lesson start time',
  `initial_lesson_end_at` datetime(3) DEFAULT NULL COMMENT 'Structured first one-on-one lesson end time',
  `teacher_id` bigint DEFAULT NULL COMMENT 'Required one-on-one teacher ID',
  `item_status` varchar(32) NOT NULL DEFAULT 'ACTIVE' COMMENT 'Item status: ACTIVE/CANCELLED',
  `remark` varchar(500) DEFAULT NULL COMMENT 'Notes',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT 'Created at',
  `created_by` bigint DEFAULT NULL COMMENT 'Created by user ID',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT 'Updated at',
  `updated_by` bigint DEFAULT NULL COMMENT 'Updated by user ID',
  `is_deleted` tinyint(1) NOT NULL DEFAULT '0' COMMENT 'Soft delete flag: 0=no, 1=yes',
  `deleted_at` datetime(3) DEFAULT NULL COMMENT 'Deleted at',
  `deleted_by` bigint DEFAULT NULL COMMENT 'Deleted by user ID',
  `version` int NOT NULL DEFAULT '0' COMMENT 'Optimistic lock version',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_enrollment_order_item_line` (`enrollment_order_id`, `line_no`, `is_deleted`),
  KEY `idx_enrollment_order_item_type` (`course_type`, `item_status`),
  KEY `idx_enrollment_order_item_product` (`course_product_id`),
  KEY `idx_enrollment_order_item_option` (`course_price_option_id`),
  KEY `idx_enrollment_order_item_class` (`class_id`),
  KEY `idx_enrollment_order_item_teacher` (`teacher_id`),
  CONSTRAINT `fk_enrollment_order_item_order` FOREIGN KEY (`enrollment_order_id`) REFERENCES `enrollment_order` (`id`),
  CONSTRAINT `fk_enrollment_order_item_product` FOREIGN KEY (`course_product_id`) REFERENCES `course_product` (`id`),
  CONSTRAINT `fk_enrollment_order_item_option` FOREIGN KEY (`course_price_option_id`) REFERENCES `course_price_option` (`id`),
  CONSTRAINT `fk_enrollment_order_item_teacher` FOREIGN KEY (`teacher_id`) REFERENCES `teacher` (`id`),
  CONSTRAINT `chk_enrollment_order_item_amount` CHECK (((`price_amount` >= 0) AND ((`original_price_amount` IS NULL) OR (`original_price_amount` >= 0)))),
  CONSTRAINT `chk_enrollment_order_item_hours` CHECK (((`one_on_one_hours` IS NULL) OR (`one_on_one_hours` >= 0))),
  CONSTRAINT `chk_enrollment_order_item_units` CHECK (((`one_on_one_units` IS NULL) OR (`one_on_one_units` >= 0)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Enrollment order line items for multi-course registration intake';

-- ---------------------------------------------------------------------------
-- 5. Downstream artifact links: map generated packages/enrollments to items.
-- ---------------------------------------------------------------------------
ALTER TABLE `course_package`
  ADD COLUMN `enrollment_order_item_id` bigint DEFAULT NULL COMMENT 'Source enrollment order item ID' AFTER `enrollment_order_id`,
  ADD KEY `idx_package_order_item` (`enrollment_order_item_id`),
  ADD CONSTRAINT `fk_package_order_item` FOREIGN KEY (`enrollment_order_item_id`) REFERENCES `enrollment_order_item` (`id`);

ALTER TABLE `class_enrollment`
  ADD COLUMN `enrollment_order_item_id` bigint DEFAULT NULL COMMENT 'Source enrollment order item ID' AFTER `student_id`,
  ADD KEY `idx_enrollment_order_item` (`enrollment_order_item_id`),
  ADD CONSTRAINT `fk_class_enrollment_order_item` FOREIGN KEY (`enrollment_order_item_id`) REFERENCES `enrollment_order_item` (`id`);

-- ---------------------------------------------------------------------------
-- 6. Verification queries.
-- ---------------------------------------------------------------------------
SELECT table_name, column_name
FROM information_schema.columns
WHERE table_schema = DATABASE()
  AND table_name IN ('student', 'enrollment_order', 'course_package', 'class_enrollment')
  AND column_name IN (
    'grade',
    'school',
    'guardian_phone_2',
    'pickup_required',
    'dropoff_required',
    'campus_id',
    'signup_date',
    'period_end_date',
    'student_name_snapshot',
    'balance_amount',
    'enrollment_order_item_id'
  )
ORDER BY table_name, ordinal_position;

SELECT table_name
FROM information_schema.tables
WHERE table_schema = DATABASE()
  AND table_name IN ('course_price_option', 'enrollment_order_item')
ORDER BY table_name;

-- ---------------------------------------------------------------------------
-- Manual rollback order:
--   1. Drop fk_class_enrollment_order_item and class_enrollment.enrollment_order_item_id.
--   2. Drop fk_package_order_item and course_package.enrollment_order_item_id.
--   3. Drop enrollment_order_item.
--   4. Drop course_price_option.
--   5. Drop the new enrollment_order snapshot columns and indexes.
--   6. Drop the new student intake columns and idx_student_name_phone.
-- Keep a database backup because rolling back after new registrations may remove
-- business data captured only in the new tables or columns.
-- ---------------------------------------------------------------------------
