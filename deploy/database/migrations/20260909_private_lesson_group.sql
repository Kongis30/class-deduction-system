-- 1 对 N 私教课：一张报名单、多名学生、共享一套排课和课次。
-- 本脚本只新增结构与索引；历史 1-on-1 课包的单人成组回填由后续数据迁移窗口执行。

CREATE TABLE IF NOT EXISTS private_lesson_group (
  id bigint NOT NULL,
  group_no varchar(32) NOT NULL,
  enrollment_order_id bigint NOT NULL,
  enrollment_order_item_id bigint DEFAULT NULL,
  course_product_id bigint DEFAULT NULL,
  grade_band varchar(64) DEFAULT NULL,
  participant_count int NOT NULL DEFAULT 1,
  second_participant_multiplier decimal(5,4) NOT NULL DEFAULT 0.5000,
  third_participant_multiplier decimal(5,4) NOT NULL DEFAULT 0.5000,
  group_status varchar(32) NOT NULL DEFAULT 'ACTIVE',
  created_at datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  created_by bigint DEFAULT NULL,
  updated_at datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  updated_by bigint DEFAULT NULL,
  is_deleted tinyint(1) NOT NULL DEFAULT 0,
  deleted_at datetime(3) DEFAULT NULL,
  deleted_by bigint DEFAULT NULL,
  version int NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_private_lesson_group_no (group_no, is_deleted),
  KEY idx_private_lesson_group_order (enrollment_order_id, is_deleted),
  KEY idx_private_lesson_group_product (course_product_id, group_status, is_deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS private_lesson_group_member (
  id bigint NOT NULL,
  private_lesson_group_id bigint NOT NULL,
  student_id bigint NOT NULL,
  package_id bigint NOT NULL,
  participant_index int NOT NULL,
  price_multiplier decimal(5,4) NOT NULL,
  price_amount decimal(12,2) NOT NULL DEFAULT 0.00,
  member_status varchar(32) NOT NULL DEFAULT 'ACTIVE',
  created_at datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  created_by bigint DEFAULT NULL,
  updated_at datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  updated_by bigint DEFAULT NULL,
  is_deleted tinyint(1) NOT NULL DEFAULT 0,
  deleted_at datetime(3) DEFAULT NULL,
  deleted_by bigint DEFAULT NULL,
  version int NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_private_lesson_member_index (private_lesson_group_id, participant_index, is_deleted),
  UNIQUE KEY uk_private_lesson_member_package (private_lesson_group_id, package_id, is_deleted),
  KEY idx_private_lesson_member_student (student_id, member_status, is_deleted),
  KEY idx_private_lesson_member_package (package_id, member_status, is_deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS enrollment_order_participant (
  id bigint NOT NULL,
  enrollment_order_id bigint NOT NULL,
  student_id bigint NOT NULL,
  participant_index int NOT NULL,
  student_name_snapshot varchar(128) NOT NULL,
  student_no_snapshot varchar(32) DEFAULT NULL,
  grade_snapshot varchar(64) DEFAULT NULL,
  school_snapshot varchar(128) DEFAULT NULL,
  student_class_snapshot varchar(64) DEFAULT NULL,
  guardian_name_snapshot varchar(128) DEFAULT NULL,
  guardian_phone_snapshot varchar(64) DEFAULT NULL,
  guardian_phone2_snapshot varchar(64) DEFAULT NULL,
  guardian_email_snapshot varchar(128) DEFAULT NULL,
  pickup_required_snapshot tinyint(1) DEFAULT NULL,
  pickup_address_snapshot varchar(255) DEFAULT NULL,
  dropoff_required_snapshot tinyint(1) DEFAULT NULL,
  dropoff_address_snapshot varchar(255) DEFAULT NULL,
  gross_amount decimal(12,2) DEFAULT NULL,
  discount_amount decimal(12,2) NOT NULL DEFAULT 0.00,
  discount_detail json DEFAULT NULL,
  amount_due decimal(12,2) NOT NULL DEFAULT 0.00,
  paid_amount decimal(12,2) NOT NULL DEFAULT 0.00,
  balance_amount decimal(12,2) NOT NULL DEFAULT 0.00,
  created_at datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  created_by bigint DEFAULT NULL,
  updated_at datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  updated_by bigint DEFAULT NULL,
  is_deleted tinyint(1) NOT NULL DEFAULT 0,
  deleted_at datetime(3) DEFAULT NULL,
  deleted_by bigint DEFAULT NULL,
  version int NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_order_participant_index (enrollment_order_id, participant_index, is_deleted),
  KEY idx_order_participant_student (student_id, is_deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

SET @sql = (SELECT IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='recurring_schedule_rule' AND COLUMN_NAME='private_lesson_group_id') = 0,
  'ALTER TABLE recurring_schedule_rule ADD COLUMN private_lesson_group_id bigint DEFAULT NULL AFTER package_id',
  'SELECT 1'));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = (SELECT IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='payment_record' AND COLUMN_NAME='participant_index') = 0,
  'ALTER TABLE payment_record ADD COLUMN participant_index int DEFAULT NULL AFTER enrollment_order_id',
  'SELECT 1'));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = (SELECT IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='enrollment_order_participant' AND COLUMN_NAME='student_class_snapshot') = 0,
  'ALTER TABLE enrollment_order_participant ADD COLUMN student_class_snapshot varchar(64) DEFAULT NULL AFTER school_snapshot, ADD COLUMN guardian_name_snapshot varchar(128) DEFAULT NULL, ADD COLUMN guardian_phone_snapshot varchar(64) DEFAULT NULL, ADD COLUMN guardian_phone2_snapshot varchar(64) DEFAULT NULL, ADD COLUMN guardian_email_snapshot varchar(128) DEFAULT NULL, ADD COLUMN pickup_required_snapshot tinyint(1) DEFAULT NULL, ADD COLUMN pickup_address_snapshot varchar(255) DEFAULT NULL, ADD COLUMN dropoff_required_snapshot tinyint(1) DEFAULT NULL, ADD COLUMN dropoff_address_snapshot varchar(255) DEFAULT NULL, ADD COLUMN gross_amount decimal(12,2) DEFAULT NULL, ADD COLUMN discount_amount decimal(12,2) NOT NULL DEFAULT 0.00, ADD COLUMN discount_detail json DEFAULT NULL, ADD COLUMN amount_due decimal(12,2) NOT NULL DEFAULT 0.00, ADD COLUMN paid_amount decimal(12,2) NOT NULL DEFAULT 0.00, ADD COLUMN balance_amount decimal(12,2) NOT NULL DEFAULT 0.00',
  'SELECT 1'));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = (SELECT IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='lesson_occurrence' AND COLUMN_NAME='private_lesson_group_id') = 0,
  'ALTER TABLE lesson_occurrence ADD COLUMN private_lesson_group_id bigint DEFAULT NULL AFTER package_id',
  'SELECT 1'));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
