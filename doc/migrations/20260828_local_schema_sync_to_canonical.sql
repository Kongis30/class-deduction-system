-- =============================================================================
-- 20260828 本地库结构同步至规范（local DB schema sync to canonical）
-- -----------------------------------------------------------------------------
-- 背景：本地开发库（class_deduction_system）结构落后于 doc/init_schema_current.sql，
--       后端启动时报 `Unknown column 'campus_id'`。本脚本为一次性增量同步：
--         ① 补 3 张缺失表：class_group_schedule_slot / course_price_option / enrollment_order_item
--         ② 补 73 个缺失列（NOT NULL 列已给出合理默认值，兼容存量行）
--         ③ 补 11 个索引（含新列的查询/唯一索引）
--         ④ 补 5 个外键
--         ⑤ 替换 teacher_pay_rate 旧唯一键 uk_teacher_product 为 scoped 版本
--   数据保全：存量数据（20 学生/12 课包/28 订单/64 消课/176 课次/17 流水）不受影响。
-- 来源：doc/init_schema_current.sql（按列定义逐条生成，与生产结构一致）。
-- 适用：仅针对旧版结构（列数 661）的本地库执行；已同步库勿重复执行。
-- 验证：执行后重启后端确认启动成功；数据行数不变。
-- =============================================================================
-- 1) 缺失表（course_price_option 先于 enrollment_order_item，因其被后者引用）
CREATE TABLE `class_group_schedule_slot` (
  `id` bigint NOT NULL,
  `class_group_id` bigint NOT NULL,
  `teacher_id` bigint DEFAULT NULL COMMENT 'Optional teacher override for this weekly slot',
  `subject_name` varchar(128) DEFAULT NULL COMMENT 'Subject taught in this weekly class slot',
  `weekday` tinyint NOT NULL,
  `start_time` time NOT NULL,
  `end_time` time NOT NULL,
  `morning_end_time` time DEFAULT NULL COMMENT 'Morning half-day end time',
  `afternoon_start_time` time DEFAULT NULL COMMENT 'Afternoon half-day start time',
  `attendance_options` varchar(64) DEFAULT NULL COMMENT 'Available attendance options',
  `hourly_rate_snapshot` decimal(10,2) DEFAULT NULL COMMENT 'Teacher hourly pay-rate snapshot for this weekly class slot',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `created_by` bigint DEFAULT NULL,
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `updated_by` bigint DEFAULT NULL,
  `is_deleted` tinyint(1) NOT NULL DEFAULT '0',
  `deleted_at` datetime(3) DEFAULT NULL,
  `deleted_by` bigint DEFAULT NULL,
  `version` int NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_class_group_schedule_slot` (`class_group_id`,`weekday`),
  KEY `idx_class_group_schedule_slot_teacher` (`teacher_id`,`weekday`,`start_time`),
  CONSTRAINT `fk_class_group_schedule_slot_group` FOREIGN KEY (`class_group_id`) REFERENCES `class_group` (`id`),
  CONSTRAINT `fk_class_group_schedule_slot_teacher` FOREIGN KEY (`teacher_id`) REFERENCES `teacher` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

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
  `package_unit` varchar(16) DEFAULT NULL COMMENT 'Package unit: MONTH/WEEK/SESSION/HOUR',
  `package_quantity` decimal(8,2) DEFAULT NULL COMMENT 'Package quantity',
  `total_price` decimal(12,2) DEFAULT NULL COMMENT 'Package total price used for billing',
  `derived_unit_price` decimal(12,2) DEFAULT NULL COMMENT 'Derived display-only unit price',
  `full_day_price` decimal(12,2) DEFAULT NULL COMMENT 'Full-day package price',
  `half_day_price` decimal(12,2) DEFAULT NULL COMMENT 'Half-day package price',
  `one_on_one_hours` decimal(6,2) DEFAULT NULL COMMENT 'One-on-one purchased hours, null for group options',
  `group_session_count` int DEFAULT NULL COMMENT 'Purchased sessions for a per-session grade class',
  `group_month_count` int DEFAULT NULL COMMENT 'Purchased months for a monthly grade class',
  `price_amount` decimal(12,2) NOT NULL DEFAULT '0.00' COMMENT 'Default list price for this option',
  `group_total_price` decimal(12,2) DEFAULT NULL COMMENT 'User-entered total group class price',
  `group_session_price` decimal(12,2) DEFAULT NULL COMMENT 'Derived group class price per session',
  `currency_code` varchar(16) NOT NULL DEFAULT 'USD' COMMENT 'Currency code',
  `course_product_id` bigint DEFAULT NULL COMMENT 'Optional linked course product template ID',
  `class_id` bigint DEFAULT NULL COMMENT 'Generated grade class for group price option',
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
  KEY `idx_course_price_option_type` (`course_type`,`status`,`sort_order`),
  KEY `idx_course_price_option_product` (`course_product_id`),
  KEY `idx_course_price_option_class` (`class_id`),
  CONSTRAINT `fk_course_price_option_product` FOREIGN KEY (`course_product_id`) REFERENCES `course_product` (`id`),
  CONSTRAINT `chk_course_price_option_amount` CHECK ((`price_amount` >= 0)),
  CONSTRAINT `chk_course_price_option_hours` CHECK (((`one_on_one_hours` is null) or (`one_on_one_hours` >= 0)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Structured course price options for registration intake';

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
  `start_date` date DEFAULT NULL COMMENT 'Course line enrollment start date',
  `package_unit` varchar(16) DEFAULT NULL COMMENT 'Package unit snapshot',
  `package_quantity` decimal(8,2) DEFAULT NULL COMMENT 'Package quantity snapshot',
  `package_total_price` decimal(12,2) DEFAULT NULL COMMENT 'Package total price snapshot',
  `attendance_option` varchar(32) DEFAULT NULL COMMENT 'Attendance option snapshot',
  `attendance_schedule_json` json DEFAULT NULL COMMENT 'Per-day attendance schedule snapshot',
  `price_amount` decimal(12,2) NOT NULL DEFAULT '0.00' COMMENT 'Final line price amount',
  `original_price_amount` decimal(12,2) DEFAULT NULL COMMENT 'Original configured price before manual override',
  `manual_price_override` tinyint(1) NOT NULL DEFAULT '0' COMMENT 'Manual price override flag: 0=no, 1=yes',
  `one_on_one_hours` decimal(6,2) DEFAULT NULL COMMENT 'One-on-one purchased hours',
  `opening_used_hours` decimal(6,2) DEFAULT NULL COMMENT 'Already used one-on-one hours before system tracking',
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
  UNIQUE KEY `uk_enrollment_order_item_line` (`enrollment_order_id`,`line_no`,`is_deleted`),
  KEY `idx_enrollment_order_item_type` (`course_type`,`item_status`),
  KEY `idx_enrollment_order_item_product` (`course_product_id`),
  KEY `idx_enrollment_order_item_option` (`course_price_option_id`),
  KEY `idx_enrollment_order_item_class` (`class_id`),
  KEY `idx_enrollment_order_item_teacher` (`teacher_id`),
  CONSTRAINT `fk_enrollment_order_item_class` FOREIGN KEY (`class_id`) REFERENCES `class_group` (`id`),
  CONSTRAINT `fk_enrollment_order_item_option` FOREIGN KEY (`course_price_option_id`) REFERENCES `course_price_option` (`id`),
  CONSTRAINT `fk_enrollment_order_item_order` FOREIGN KEY (`enrollment_order_id`) REFERENCES `enrollment_order` (`id`),
  CONSTRAINT `fk_enrollment_order_item_product` FOREIGN KEY (`course_product_id`) REFERENCES `course_product` (`id`),
  CONSTRAINT `fk_enrollment_order_item_teacher` FOREIGN KEY (`teacher_id`) REFERENCES `teacher` (`id`),
  CONSTRAINT `chk_enrollment_order_item_amount` CHECK (((`price_amount` >= 0) and ((`original_price_amount` is null) or (`original_price_amount` >= 0)))),
  CONSTRAINT `chk_enrollment_order_item_hours` CHECK (((`one_on_one_hours` is null) or (`one_on_one_hours` >= 0))),
  CONSTRAINT `chk_enrollment_order_item_opening_used_hours` CHECK (((`opening_used_hours` is null) or (`opening_used_hours` >= 0))),
  CONSTRAINT `chk_enrollment_order_item_units` CHECK (((`one_on_one_units` is null) or (`one_on_one_units` >= 0)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Enrollment order line items for multi-course registration intake';

-- 2) 缺失列
ALTER TABLE `campus` ADD COLUMN `default_pickup_fee_amount` decimal(12,2) NOT NULL DEFAULT '0.00' COMMENT 'Default pickup service fee for new registrations';
ALTER TABLE `campus` ADD COLUMN `default_dropoff_fee_amount` decimal(12,2) NOT NULL DEFAULT '0.00' COMMENT 'Default drop-off service fee for new registrations';
ALTER TABLE `class_enrollment` ADD COLUMN `enrollment_order_item_id` bigint DEFAULT NULL COMMENT 'Source enrollment order item ID';
ALTER TABLE `class_enrollment` ADD COLUMN `billing_type` varchar(32) DEFAULT NULL COMMENT 'Snapshot of product billing type: PER_SESSION/MONTHLY';
ALTER TABLE `class_enrollment` ADD COLUMN `purchased_quantity` int DEFAULT NULL COMMENT 'Purchased quantity: session count (PER_SESSION) or month count (MONTHLY)';
ALTER TABLE `class_enrollment` ADD COLUMN `attendance_option` varchar(32) NOT NULL DEFAULT 'FULL_DAY' COMMENT 'Registration attendance option: FULL_DAY/MORNING/AFTERNOON/NONE';
ALTER TABLE `class_group` ADD COLUMN `grade_band` varchar(64) DEFAULT NULL COMMENT 'Grade band served by this class';
ALTER TABLE `class_group` ADD COLUMN `billing_type` varchar(32) DEFAULT NULL COMMENT 'PER_SESSION or MONTHLY';
ALTER TABLE `class_group` ADD COLUMN `start_date` date DEFAULT NULL COMMENT 'First date for a per-session grade class';
ALTER TABLE `class_group` ADD COLUMN `end_date` date DEFAULT NULL COMMENT 'Last date for a fixed-date grade class';
ALTER TABLE `class_group` ADD COLUMN `total_sessions` int DEFAULT NULL COMMENT 'Total sessions for a per-session grade class';
ALTER TABLE `class_group` ADD COLUMN `start_month` date DEFAULT NULL COMMENT 'First month for a monthly grade class';
ALTER TABLE `class_group` ADD COLUMN `end_month` date DEFAULT NULL COMMENT 'Last month for a monthly grade class';
ALTER TABLE `course_package` ADD COLUMN `enrollment_order_item_id` bigint DEFAULT NULL COMMENT 'Source enrollment order item ID';
ALTER TABLE `course_product` ADD COLUMN `course_type` varchar(32) DEFAULT NULL COMMENT 'Parent course type: AFTER_SCHOOL/SATURDAY/SUMMER/SPRINT/ONE_ON_ONE';
ALTER TABLE `course_product` ADD COLUMN `register_class` varchar(64) DEFAULT NULL COMMENT 'Register Class label shown on registration';
ALTER TABLE `course_product` ADD COLUMN `sort_order` int NOT NULL DEFAULT '99' COMMENT 'Display order for course list sorting';
ALTER TABLE `course_product` ADD COLUMN `billing_type` varchar(32) DEFAULT NULL COMMENT 'GROUP billing type: PER_SESSION/MONTHLY';
ALTER TABLE `course_product` ADD COLUMN `start_date` date DEFAULT NULL COMMENT 'GROUP PER_SESSION start date';
ALTER TABLE `course_product` ADD COLUMN `total_sessions` int DEFAULT NULL COMMENT 'GROUP PER_SESSION total session count';
ALTER TABLE `course_product` ADD COLUMN `start_month` date DEFAULT NULL COMMENT 'GROUP MONTHLY start month (1st of month)';
ALTER TABLE `course_product` ADD COLUMN `end_month` date DEFAULT NULL COMMENT 'GROUP MONTHLY end month (1st of month)';
ALTER TABLE `enrollment_order` ADD COLUMN `campus_id` bigint DEFAULT NULL COMMENT 'Campus ID selected during registration';
ALTER TABLE `enrollment_order` ADD COLUMN `campus_name_snapshot` varchar(128) DEFAULT NULL COMMENT 'Campus name snapshot';
ALTER TABLE `enrollment_order` ADD COLUMN `signup_date` date DEFAULT NULL COMMENT 'Registration signup date';
ALTER TABLE `enrollment_order` ADD COLUMN `period_start_date` date DEFAULT NULL COMMENT 'Registration period start date';
ALTER TABLE `enrollment_order` ADD COLUMN `period_end_date` date DEFAULT NULL COMMENT 'Registration period end date';
ALTER TABLE `enrollment_order` ADD COLUMN `student_name_snapshot` varchar(64) DEFAULT NULL COMMENT 'Student name snapshot';
ALTER TABLE `enrollment_order` ADD COLUMN `grade_snapshot` varchar(32) DEFAULT NULL COMMENT 'Student grade snapshot';
ALTER TABLE `enrollment_order` ADD COLUMN `school_snapshot` varchar(128) DEFAULT NULL COMMENT 'Student school snapshot';
ALTER TABLE `enrollment_order` ADD COLUMN `parent_phone_snapshot` varchar(32) DEFAULT NULL COMMENT 'Primary guardian phone snapshot';
ALTER TABLE `enrollment_order` ADD COLUMN `parent_phone_2_snapshot` varchar(32) DEFAULT NULL COMMENT 'Secondary guardian phone snapshot';
ALTER TABLE `enrollment_order` ADD COLUMN `pickup_required_snapshot` tinyint(1) NOT NULL DEFAULT '0' COMMENT 'Pickup flag snapshot: 0=no, 1=yes';
ALTER TABLE `enrollment_order` ADD COLUMN `pickup_address_snapshot` varchar(255) DEFAULT NULL COMMENT 'Pickup address snapshot';
ALTER TABLE `enrollment_order` ADD COLUMN `dropoff_required_snapshot` tinyint(1) NOT NULL DEFAULT '0' COMMENT 'Dropoff flag snapshot: 0=no, 1=yes';
ALTER TABLE `enrollment_order` ADD COLUMN `dropoff_address_snapshot` varchar(255) DEFAULT NULL COMMENT 'Dropoff address snapshot';
ALTER TABLE `enrollment_order` ADD COLUMN `tuition_subtotal_amount` decimal(12,2) NOT NULL DEFAULT '0.00' COMMENT 'Subtotal of course line prices';
ALTER TABLE `enrollment_order` ADD COLUMN `pickup_fee_amount` decimal(12,2) NOT NULL DEFAULT '0.00' COMMENT 'Pickup service fee amount';
ALTER TABLE `enrollment_order` ADD COLUMN `dropoff_fee_amount` decimal(12,2) NOT NULL DEFAULT '0.00' COMMENT 'Drop-off service fee amount';
ALTER TABLE `enrollment_order` ADD COLUMN `extra_fee_amount` decimal(12,2) NOT NULL DEFAULT '0.00' COMMENT 'Total extra fee amount';
ALTER TABLE `enrollment_order` ADD COLUMN `extra_fee_detail` json DEFAULT NULL COMMENT 'Extra fee detail snapshot JSON';
ALTER TABLE `enrollment_order` ADD COLUMN `discount_detail` json DEFAULT NULL COMMENT 'Discount detail snapshot JSON';
ALTER TABLE `enrollment_order` ADD COLUMN `amount_due` decimal(12,2) NOT NULL DEFAULT '0.00' COMMENT 'Amount due after fees and discounts';
ALTER TABLE `enrollment_order` ADD COLUMN `balance_amount` decimal(12,2) NOT NULL DEFAULT '0.00' COMMENT 'Outstanding balance snapshot';
ALTER TABLE `lesson_occurrence` ADD COLUMN `hourly_rate_snapshot` decimal(10,2) DEFAULT NULL COMMENT 'Teacher hourly pay-rate snapshot copied from schedule/allocation';
ALTER TABLE `lesson_record` ADD COLUMN `planned_start_at` datetime(3) DEFAULT NULL COMMENT 'Planned start time snapshot';
ALTER TABLE `lesson_record` ADD COLUMN `planned_end_at` datetime(3) DEFAULT NULL COMMENT 'Planned end time snapshot';
ALTER TABLE `lesson_record` ADD COLUMN `planned_units` int DEFAULT NULL COMMENT 'Planned units snapshot';
ALTER TABLE `lesson_record` ADD COLUMN `confirm_stage` varchar(32) NOT NULL DEFAULT 'PENDING_ADMIN' COMMENT 'Confirm stage: PENDING_ADMIN/CONFIRMED/REJECTED';
ALTER TABLE `lesson_record` ADD COLUMN `teacher_confirmed_at` datetime(3) DEFAULT NULL COMMENT 'Teacher initial confirmed at';
ALTER TABLE `lesson_record` ADD COLUMN `teacher_confirmed_by_user_id` bigint DEFAULT NULL COMMENT 'Teacher initial confirmed by user ID';
ALTER TABLE `lesson_record` ADD COLUMN `admin_confirmed_at` datetime(3) DEFAULT NULL COMMENT 'Admin final confirmed at';
ALTER TABLE `lesson_record` ADD COLUMN `admin_confirmed_by_user_id` bigint DEFAULT NULL COMMENT 'Admin final confirmed by user ID';
ALTER TABLE `lesson_record` ADD COLUMN `reject_reason` varchar(500) DEFAULT NULL COMMENT 'Admin reject reason';
ALTER TABLE `lesson_record` ADD COLUMN `rejected_at` datetime(3) DEFAULT NULL COMMENT 'Rejected at';
ALTER TABLE `lesson_record` ADD COLUMN `rejected_by_user_id` bigint DEFAULT NULL COMMENT 'Rejected by user ID';
ALTER TABLE `package_teacher_assignment` ADD COLUMN `allocated_units` int DEFAULT NULL COMMENT 'Allocated teaching units; 1 unit = 0.5 hour';
ALTER TABLE `package_teacher_assignment` ADD COLUMN `subject_name` varchar(128) DEFAULT NULL COMMENT 'Subject assigned to this teacher allocation';
ALTER TABLE `package_teacher_assignment` ADD COLUMN `hourly_rate_snapshot` decimal(10,2) DEFAULT NULL COMMENT 'Teacher hourly pay-rate snapshot for this allocation';
ALTER TABLE `payment_record` ADD COLUMN `payment_type` varchar(16) NOT NULL DEFAULT 'PAYMENT' COMMENT 'Record type: PAYMENT/REFUND';
ALTER TABLE `recurring_schedule_slot` ADD COLUMN `teacher_id` bigint DEFAULT NULL COMMENT 'Teacher assigned to this recurring weekly slot';
ALTER TABLE `recurring_schedule_slot` ADD COLUMN `subject_name` varchar(128) DEFAULT NULL COMMENT 'Course/subject name taught in this recurring weekly slot';
ALTER TABLE `recurring_schedule_slot` ADD COLUMN `allocated_units` int DEFAULT NULL COMMENT 'Units (1 unit = 0.5h) allocated to this slot; existing schedules must be completed before further generation';
ALTER TABLE `student` ADD COLUMN `grade` varchar(32) DEFAULT NULL COMMENT 'Current student grade';
ALTER TABLE `student` ADD COLUMN `school` varchar(128) DEFAULT NULL COMMENT 'Current school name';
ALTER TABLE `student` ADD COLUMN `guardian_phone_2` varchar(32) DEFAULT NULL COMMENT 'Secondary guardian phone';
ALTER TABLE `student` ADD COLUMN `pickup_required` tinyint(1) NOT NULL DEFAULT '0' COMMENT 'Default pickup flag: 0=no, 1=yes';
ALTER TABLE `student` ADD COLUMN `pickup_address` varchar(255) DEFAULT NULL COMMENT 'Default pickup address';
ALTER TABLE `student` ADD COLUMN `dropoff_required` tinyint(1) NOT NULL DEFAULT '0' COMMENT 'Default dropoff flag: 0=no, 1=yes';
ALTER TABLE `student` ADD COLUMN `dropoff_address` varchar(255) DEFAULT NULL COMMENT 'Default dropoff address';
ALTER TABLE `teacher_pay_rate` ADD COLUMN `student_id` bigint DEFAULT NULL COMMENT 'Optional student-specific pay-rate override';
ALTER TABLE `teacher_pay_rate` ADD COLUMN `grade_band` varchar(64) DEFAULT NULL COMMENT 'Optional grade/level scope for this teacher pay rate';
ALTER TABLE `teacher_pay_rate` ADD COLUMN `delivery_mode` varchar(16) DEFAULT NULL COMMENT 'Optional delivery scope: ONLINE, OFFLINE, or NULL for any';

-- 3) 新增索引
ALTER TABLE `class_enrollment` ADD KEY `idx_enrollment_order_item` (`enrollment_order_item_id`);
ALTER TABLE `class_group` ADD KEY `idx_class_group_product_grade` (`product_id`,`grade_band`);
ALTER TABLE `course_package` ADD KEY `idx_package_order_item` (`enrollment_order_item_id`);
ALTER TABLE `course_product` ADD KEY `idx_course_product_type` (`course_type`,`status`);
ALTER TABLE `course_product` ADD KEY `idx_course_product_sort_order` (`sort_order`,`created_at`);
ALTER TABLE `enrollment_order` ADD KEY `idx_enrollment_campus_signup` (`campus_id`,`signup_date`);
ALTER TABLE `enrollment_order` ADD KEY `idx_enrollment_period_end` (`period_end_date`);
ALTER TABLE `recurring_schedule_slot` ADD KEY `idx_schedule_slot_teacher` (`teacher_id`,`weekday`,`start_time`);
ALTER TABLE `student` ADD KEY `idx_student_name_phone` (`student_name`,`guardian_phone`,`is_deleted`);
ALTER TABLE `teacher_pay_rate` ADD UNIQUE KEY `uk_teacher_product_student_scope` (`teacher_id`,`course_product_id`,`student_id`,`grade_band`,`delivery_mode`,`is_deleted`);
ALTER TABLE `teacher_pay_rate` ADD KEY `idx_pay_rate_student` (`student_id`);

-- 4) 新增外键
ALTER TABLE `class_enrollment` ADD CONSTRAINT `fk_class_enrollment_order_item` FOREIGN KEY (`enrollment_order_item_id`) REFERENCES `enrollment_order_item` (`id`);
ALTER TABLE `course_package` ADD CONSTRAINT `fk_package_order_item` FOREIGN KEY (`enrollment_order_item_id`) REFERENCES `enrollment_order_item` (`id`);
ALTER TABLE `enrollment_order` ADD CONSTRAINT `fk_enrollment_campus` FOREIGN KEY (`campus_id`) REFERENCES `campus` (`id`);
ALTER TABLE `recurring_schedule_slot` ADD CONSTRAINT `fk_schedule_slot_teacher` FOREIGN KEY (`teacher_id`) REFERENCES `teacher` (`id`);
ALTER TABLE `teacher_pay_rate` ADD CONSTRAINT `fk_pay_rate_student` FOREIGN KEY (`student_id`) REFERENCES `student` (`id`);

-- 5) 替换旧唯一键：新 scoped 唯一键已建，删除被规范移除的旧键
ALTER TABLE `teacher_pay_rate` DROP INDEX `uk_teacher_product`;
