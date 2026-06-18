-- MySQL dump 10.13  Distrib 9.6.0, for macos26.4 (arm64)
--
-- Host: 127.0.0.1    Database: class_deduction_system
-- ------------------------------------------------------
-- Server version	9.6.0

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

--
-- Table structure for table `auth_session`
--

DROP TABLE IF EXISTS `auth_session`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `auth_session` (
  `id` bigint NOT NULL,
  `token` varchar(64) NOT NULL,
  `user_id` bigint NOT NULL,
  `session_data` text NOT NULL,
  `expires_at` datetime(3) NOT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `created_by` bigint DEFAULT NULL,
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `updated_by` bigint DEFAULT NULL,
  `is_deleted` tinyint(1) NOT NULL DEFAULT '0',
  `deleted_at` datetime(3) DEFAULT NULL,
  `deleted_by` bigint DEFAULT NULL,
  `version` int NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_auth_session_token` (`token`),
  KEY `idx_auth_session_user` (`user_id`),
  KEY `idx_auth_session_expires` (`expires_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Session persistence (MySQL fallback)';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `brand_config`
--

DROP TABLE IF EXISTS `brand_config`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `brand_config` (
  `id` bigint NOT NULL COMMENT 'Primary key ID',
  `brand_name` varchar(128) NOT NULL COMMENT 'Brand name',
  `logo_url` varchar(500) DEFAULT NULL COMMENT 'Logo URL',
  `theme_color` varchar(32) DEFAULT NULL COMMENT 'Theme color',
  `support_email` varchar(128) DEFAULT NULL COMMENT 'Support email',
  `receipt_footer_text` varchar(500) DEFAULT NULL COMMENT 'Receipt footer text',
  `status` varchar(32) NOT NULL DEFAULT 'ACTIVE' COMMENT 'Status: ACTIVE/INACTIVE',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT 'Created at',
  `created_by` bigint DEFAULT NULL COMMENT 'Created by user ID',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT 'Updated at',
  `updated_by` bigint DEFAULT NULL COMMENT 'Updated by user ID',
  `is_deleted` tinyint(1) NOT NULL DEFAULT '0' COMMENT 'Soft delete flag: 0=no, 1=yes',
  `deleted_at` datetime(3) DEFAULT NULL COMMENT 'Deleted at',
  `deleted_by` bigint DEFAULT NULL COMMENT 'Deleted by user ID',
  `version` int NOT NULL DEFAULT '0' COMMENT 'Optimistic lock version',
  PRIMARY KEY (`id`),
  KEY `idx_brand_config_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Brand config';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `campus`
--

DROP TABLE IF EXISTS `campus`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `campus` (
  `id` bigint NOT NULL COMMENT 'Primary key ID',
  `campus_name` varchar(64) NOT NULL COMMENT 'Campus name',
  `display_order` int NOT NULL DEFAULT '99' COMMENT 'Display order for sorting',
  `status` varchar(32) NOT NULL DEFAULT 'ACTIVE' COMMENT 'Status: ACTIVE/INACTIVE',
  `notes` varchar(500) DEFAULT NULL COMMENT 'Notes',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT 'Created at',
  `created_by` bigint DEFAULT NULL COMMENT 'Created by user ID',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT 'Updated at',
  `updated_by` bigint DEFAULT NULL COMMENT 'Updated by user ID',
  `is_deleted` tinyint(1) NOT NULL DEFAULT '0' COMMENT 'Soft delete flag: 0=no, 1=yes',
  `deleted_at` datetime(3) DEFAULT NULL COMMENT 'Deleted at',
  `deleted_by` bigint DEFAULT NULL COMMENT 'Deleted by user ID',
  `version` int NOT NULL DEFAULT '0' COMMENT 'Optimistic lock version',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_campus_name` (`campus_name`),
  KEY `idx_campus_status` (`status`),
  KEY `idx_campus_display_order` (`display_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Campus master';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `class_enrollment`
--

DROP TABLE IF EXISTS `class_enrollment`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `class_enrollment` (
  `id` bigint NOT NULL COMMENT 'Primary key ID',
  `class_id` bigint NOT NULL COMMENT 'Class group ID',
  `student_id` bigint NOT NULL COMMENT 'Student ID',
  `enrollment_date` date NOT NULL COMMENT 'Enrollment date',
  `expiry_date` date NOT NULL COMMENT 'Expiry date',
  `billing_type` varchar(32) DEFAULT NULL COMMENT 'Snapshot of product billing type: PER_SESSION/MONTHLY',
  `purchased_quantity` int DEFAULT NULL COMMENT 'Purchased quantity: session count (PER_SESSION) or month count (MONTHLY)',
  `status` varchar(32) NOT NULL DEFAULT 'ACTIVE' COMMENT 'Status: ACTIVE/EXPIRED/CANCELLED',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT 'Created at',
  `created_by` bigint DEFAULT NULL COMMENT 'Created by user ID',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT 'Updated at',
  `updated_by` bigint DEFAULT NULL COMMENT 'Updated by user ID',
  `is_deleted` tinyint(1) NOT NULL DEFAULT '0' COMMENT 'Soft delete flag: 0=no, 1=yes',
  `deleted_at` datetime(3) DEFAULT NULL COMMENT 'Deleted at',
  `deleted_by` bigint DEFAULT NULL COMMENT 'Deleted by user ID',
  `version` int NOT NULL DEFAULT '0' COMMENT 'Optimistic lock version',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_enrollment_class_student` (`class_id`,`student_id`,`is_deleted`),
  KEY `idx_enrollment_class` (`class_id`,`status`),
  KEY `idx_enrollment_student` (`student_id`),
  CONSTRAINT `fk_enrollment_class_group` FOREIGN KEY (`class_id`) REFERENCES `class_group` (`id`),
  CONSTRAINT `fk_enrollment_student_user` FOREIGN KEY (`student_id`) REFERENCES `student` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Class enrollment records';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `class_group`
--

DROP TABLE IF EXISTS `class_group`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `class_group` (
  `id` bigint NOT NULL COMMENT 'Primary key ID',
  `class_name` varchar(128) NOT NULL COMMENT 'Class name',
  `description` varchar(500) DEFAULT NULL COMMENT 'Description',
  `teacher_id` bigint DEFAULT NULL COMMENT 'Assigned teacher ID',
  `classroom_id` bigint DEFAULT NULL COMMENT 'Default classroom ID',
  `list_price` decimal(12,2) NOT NULL DEFAULT '0.00' COMMENT 'Original price (sticker price for this class group)',
  `schedule_desc` varchar(255) DEFAULT NULL COMMENT 'Schedule description (e.g. Weekly Sat 10:00-12:00)',
  `status` varchar(32) NOT NULL DEFAULT 'ACTIVE' COMMENT 'Status: ACTIVE/DISABLED',
  `product_id` bigint DEFAULT NULL COMMENT '关联的课程产品 ID',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT 'Created at',
  `created_by` bigint DEFAULT NULL COMMENT 'Created by user ID',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT 'Updated at',
  `updated_by` bigint DEFAULT NULL COMMENT 'Updated by user ID',
  `is_deleted` tinyint(1) NOT NULL DEFAULT '0' COMMENT 'Soft delete flag: 0=no, 1=yes',
  `deleted_at` datetime(3) DEFAULT NULL COMMENT 'Deleted at',
  `deleted_by` bigint DEFAULT NULL COMMENT 'Deleted by user ID',
  `version` int NOT NULL DEFAULT '0' COMMENT 'Optimistic lock version',
  PRIMARY KEY (`id`),
  KEY `idx_class_group_name` (`class_name`),
  KEY `idx_class_group_teacher` (`teacher_id`),
  KEY `idx_class_group_status` (`status`),
  KEY `fk_class_group_classroom` (`classroom_id`),
  CONSTRAINT `fk_class_group_classroom` FOREIGN KEY (`classroom_id`) REFERENCES `classroom` (`id`),
  CONSTRAINT `fk_class_group_teacher` FOREIGN KEY (`teacher_id`) REFERENCES `teacher` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Group class master';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `classroom`
--

DROP TABLE IF EXISTS `classroom`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `classroom` (
  `id` bigint NOT NULL COMMENT 'Primary key ID',
  `room_code` varchar(32) NOT NULL COMMENT 'Room code',
  `room_name` varchar(64) NOT NULL COMMENT 'Room name',
  `campus` varchar(64) DEFAULT NULL COMMENT 'Campus name',
  `campus_id` bigint DEFAULT NULL COMMENT 'Campus ID',
  `capacity` int DEFAULT NULL COMMENT 'Capacity',
  `floor` varchar(16) DEFAULT NULL COMMENT 'Floor number or designation',
  `status` varchar(32) NOT NULL DEFAULT 'ACTIVE' COMMENT 'Status: ACTIVE/DISABLED',
  `notes` varchar(500) DEFAULT NULL COMMENT 'Notes',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT 'Created at',
  `created_by` bigint DEFAULT NULL COMMENT 'Created by user ID',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT 'Updated at',
  `updated_by` bigint DEFAULT NULL COMMENT 'Updated by user ID',
  `is_deleted` tinyint(1) NOT NULL DEFAULT '0' COMMENT 'Soft delete flag: 0=no, 1=yes',
  `deleted_at` datetime(3) DEFAULT NULL COMMENT 'Deleted at',
  `deleted_by` bigint DEFAULT NULL COMMENT 'Deleted by user ID',
  `version` int NOT NULL DEFAULT '0' COMMENT 'Optimistic lock version',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_room_code` (`room_code`),
  KEY `idx_room_name` (`room_name`),
  KEY `idx_classroom_campus` (`campus`),
  KEY `idx_classroom_status` (`status`),
  KEY `idx_classroom_campus_id` (`campus_id`),
  CONSTRAINT `fk_classroom_campus` FOREIGN KEY (`campus_id`) REFERENCES `campus` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Classroom master';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `course_package`
--

DROP TABLE IF EXISTS `course_package`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `course_package` (
  `id` bigint NOT NULL COMMENT 'Primary key ID',
  `package_no` varchar(32) NOT NULL COMMENT 'Course package number',
  `enrollment_order_id` bigint NOT NULL COMMENT 'Source enrollment order ID',
  `student_id` bigint NOT NULL COMMENT 'Student ID',
  `course_product_id` bigint DEFAULT NULL COMMENT 'Source course product ID',
  `package_name` varchar(128) NOT NULL COMMENT 'Course package name snapshot',
  `subject_name` varchar(64) DEFAULT NULL COMMENT 'Subject name snapshot',
  `teaching_mode` varchar(32) NOT NULL COMMENT 'Teaching mode snapshot',
  `total_units` int NOT NULL COMMENT 'Total units, 1=0.5 hours',
  `default_lesson_units` int NOT NULL COMMENT 'Default lesson units',
  `consumed_units` int NOT NULL DEFAULT '0' COMMENT 'Consumed units',
  `remaining_units` int NOT NULL DEFAULT '0' COMMENT 'Remaining units',
  `original_amount` decimal(12,2) NOT NULL DEFAULT '0.00' COMMENT 'Original amount snapshot',
  `discount_amount` decimal(12,2) NOT NULL DEFAULT '0.00' COMMENT 'Discount amount',
  `received_amount` decimal(12,2) NOT NULL DEFAULT '0.00' COMMENT 'Received amount',
  `package_status` varchar(32) NOT NULL DEFAULT 'DRAFT' COMMENT 'Course package status: DRAFT/ACTIVE/COMPLETED/CLOSED',
  `start_date` date DEFAULT NULL COMMENT 'Start date',
  `estimated_end_date` date DEFAULT NULL COMMENT 'Estimated end date',
  `completed_at` datetime(3) DEFAULT NULL COMMENT 'Completed at',
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
  UNIQUE KEY `uk_course_package_no` (`package_no`),
  KEY `idx_package_student` (`student_id`,`package_status`),
  KEY `idx_package_estimated_end` (`estimated_end_date`),
  KEY `idx_package_product` (`course_product_id`),
  KEY `idx_package_enrollment` (`enrollment_order_id`),
  CONSTRAINT `fk_package_enrollment` FOREIGN KEY (`enrollment_order_id`) REFERENCES `enrollment_order` (`id`),
  CONSTRAINT `fk_package_product` FOREIGN KEY (`course_product_id`) REFERENCES `course_product` (`id`),
  CONSTRAINT `fk_package_student` FOREIGN KEY (`student_id`) REFERENCES `student` (`id`),
  CONSTRAINT `chk_course_package_units` CHECK (((`total_units` >= 0) and (`default_lesson_units` > 0) and (`consumed_units` >= 0) and (`remaining_units` >= 0)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Course package table created after enrollment for a student''s independent lesson balance account';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `course_product`
--

DROP TABLE IF EXISTS `course_product`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `course_product` (
  `id` bigint NOT NULL COMMENT 'Primary key ID',
  `product_code` varchar(32) NOT NULL COMMENT 'Course product code',
  `product_name` varchar(128) NOT NULL COMMENT 'Course product name',
  `subject_name` varchar(64) DEFAULT NULL COMMENT 'Subject name',
  `teaching_mode` varchar(32) NOT NULL COMMENT 'Teaching mode: ONE_ON_ONE/GROUP',
  `default_total_units` int DEFAULT NULL COMMENT 'Default total units, null for class group',
  `default_lesson_units` int DEFAULT NULL COMMENT 'Default lesson units, null for class group',
  `billing_type` varchar(32) DEFAULT NULL COMMENT 'GROUP billing type: PER_SESSION/MONTHLY',
  `start_date` date DEFAULT NULL COMMENT 'GROUP PER_SESSION start date',
  `total_sessions` int DEFAULT NULL COMMENT 'GROUP PER_SESSION total session count',
  `start_month` date DEFAULT NULL COMMENT 'GROUP MONTHLY start month (1st of month)',
  `end_month` date DEFAULT NULL COMMENT 'GROUP MONTHLY end month (1st of month)',
  `default_classroom_id` bigint DEFAULT NULL COMMENT 'Default classroom for class group products',
  `default_teacher_id` bigint DEFAULT NULL COMMENT 'Default teacher ID (for class group products)',
  `list_price` decimal(12,2) NOT NULL DEFAULT '0.00' COMMENT 'List price',
  `status` varchar(32) NOT NULL DEFAULT 'ACTIVE' COMMENT 'Status: ACTIVE/INACTIVE',
  `notes` varchar(500) DEFAULT NULL COMMENT 'Notes',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT 'Created at',
  `created_by` bigint DEFAULT NULL COMMENT 'Created by user ID',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT 'Updated at',
  `updated_by` bigint DEFAULT NULL COMMENT 'Updated by user ID',
  `is_deleted` tinyint(1) NOT NULL DEFAULT '0' COMMENT 'Soft delete flag: 0=no, 1=yes',
  `deleted_at` datetime(3) DEFAULT NULL COMMENT 'Deleted at',
  `deleted_by` bigint DEFAULT NULL COMMENT 'Deleted by user ID',
  `version` int NOT NULL DEFAULT '0' COMMENT 'Optimistic lock version',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_course_product_code` (`product_code`),
  KEY `idx_course_product_name` (`product_name`),
  KEY `idx_course_product_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Course product templates';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `course_product_schedule_slot`
--

DROP TABLE IF EXISTS `course_product_schedule_slot`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `course_product_schedule_slot` (
  `id` bigint NOT NULL COMMENT 'Primary key ID',
  `product_id` bigint NOT NULL COMMENT 'Course product ID',
  `weekday` tinyint NOT NULL COMMENT 'Weekday: 1=Mon..7=Sun',
  `start_time` time NOT NULL COMMENT 'Start time, e.g. 09:00',
  `end_time` time NOT NULL COMMENT 'End time, e.g. 10:30',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `created_by` bigint DEFAULT NULL COMMENT 'Created by user ID',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `updated_by` bigint DEFAULT NULL COMMENT 'Updated by user ID',
  `is_deleted` tinyint(1) NOT NULL DEFAULT '0' COMMENT 'Soft delete flag: 0=no, 1=yes',
  `deleted_at` datetime(3) DEFAULT NULL COMMENT 'Deleted at',
  `deleted_by` bigint DEFAULT NULL COMMENT 'Deleted by user ID',
  `version` int NOT NULL DEFAULT '0' COMMENT 'Optimistic lock version',
  PRIMARY KEY (`id`),
  KEY `idx_cpss_product` (`product_id`),
  CONSTRAINT `fk_cpss_product` FOREIGN KEY (`product_id`) REFERENCES `course_product` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Course product schedule slots for class group products';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `discount_preset`
--

DROP TABLE IF EXISTS `discount_preset`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `discount_preset` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `preset_name` varchar(100) NOT NULL COMMENT '折扣名称，如 Sibling discount, Early bird',
  `discount_type` varchar(20) NOT NULL COMMENT 'PERCENTAGE / FIXED',
  `discount_value` decimal(10,2) NOT NULL COMMENT '百分比填10(=10% off), 固定金额填100(=$100 off)',
  `sort_order` int NOT NULL DEFAULT '0' COMMENT '排序号，小的排前面',
  `status` varchar(20) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE / INACTIVE',
  `is_deleted` int NOT NULL DEFAULT '0',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `created_by` bigint DEFAULT NULL,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `updated_by` bigint DEFAULT NULL,
  `deleted_at` datetime DEFAULT NULL,
  `deleted_by` bigint DEFAULT NULL,
  `version` int DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_status` (`status`,`is_deleted`)
) ENGINE=InnoDB AUTO_INCREMENT=2067527779648086018 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Discount presets configuration';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `enrollment_order`
--

DROP TABLE IF EXISTS `enrollment_order`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `enrollment_order` (
  `id` bigint NOT NULL COMMENT 'Primary key ID',
  `enrollment_no` varchar(32) NOT NULL COMMENT 'Enrollment order number',
  `student_id` bigint NOT NULL COMMENT 'Student ID',
  `class_id` bigint DEFAULT NULL COMMENT 'Class group ID (for group class enrollments)',
  `order_status` varchar(32) NOT NULL DEFAULT 'DRAFT' COMMENT 'Order status: DRAFT/CONFIRMED/CANCELLED',
  `cashier_user_id` bigint DEFAULT NULL COMMENT 'Cashier user ID',
  `handled_by_name` varchar(64) DEFAULT NULL COMMENT 'Handler name snapshot',
  `currency_code` varchar(16) NOT NULL DEFAULT 'USD' COMMENT 'Currency code',
  `original_amount` decimal(12,2) NOT NULL DEFAULT '0.00' COMMENT 'Original amount',
  `discount_amount` decimal(12,2) NOT NULL DEFAULT '0.00' COMMENT 'Discount amount',
  `received_amount` decimal(12,2) NOT NULL DEFAULT '0.00' COMMENT 'Received amount',
  `payment_status` varchar(32) NOT NULL DEFAULT 'UNPAID' COMMENT 'Payment status: UNPAID/PARTIAL/PAID/REFUNDED',
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
  UNIQUE KEY `uk_enrollment_no` (`enrollment_no`),
  KEY `idx_enrollment_student` (`student_id`,`created_at` DESC),
  KEY `idx_enrollment_status` (`order_status`,`payment_status`),
  KEY `idx_enrollment_cashier` (`cashier_user_id`),
  KEY `idx_enrollment_order_class_id` (`class_id`),
  CONSTRAINT `fk_enrollment_cashier` FOREIGN KEY (`cashier_user_id`) REFERENCES `sys_user` (`id`),
  CONSTRAINT `fk_enrollment_student` FOREIGN KEY (`student_id`) REFERENCES `student` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Enrollment orders';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `hour_ledger`
--

DROP TABLE IF EXISTS `hour_ledger`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `hour_ledger` (
  `id` bigint NOT NULL COMMENT 'Primary key ID',
  `package_id` bigint NOT NULL COMMENT 'Course package ID',
  `lesson_record_id` bigint DEFAULT NULL COMMENT 'Source lesson record ID',
  `occurrence_id` bigint DEFAULT NULL COMMENT 'Source planned occurrence ID',
  `ledger_type` varchar(32) NOT NULL COMMENT 'Ledger type: INITIAL_ALLOCATE/DEDUCT/RESTORE/MANUAL_ADJUST/NO_DEDUCT_AUDIT',
  `change_units` int NOT NULL COMMENT 'Unit change amount, positive adds and negative deducts',
  `balance_after_units` int NOT NULL COMMENT 'Balance after change',
  `business_date` date NOT NULL COMMENT 'Business date',
  `reason_code` varchar(64) DEFAULT NULL COMMENT 'Reason code',
  `reason_desc` varchar(255) DEFAULT NULL COMMENT 'Reason description',
  `operated_by` bigint DEFAULT NULL COMMENT 'Operator user ID',
  `operated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT 'Operated at',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT 'Created at',
  `created_by` bigint DEFAULT NULL COMMENT 'Created by user ID',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT 'Updated at',
  `updated_by` bigint DEFAULT NULL COMMENT 'Updated by user ID',
  `is_deleted` tinyint(1) NOT NULL DEFAULT '0' COMMENT 'Soft delete flag: 0=no, 1=yes',
  `deleted_at` datetime(3) DEFAULT NULL COMMENT 'Deleted at',
  `deleted_by` bigint DEFAULT NULL COMMENT 'Deleted by user ID',
  `version` int NOT NULL DEFAULT '0' COMMENT 'Optimistic lock version',
  PRIMARY KEY (`id`),
  KEY `idx_hour_ledger_package_time` (`package_id`,`operated_at` DESC,`id` DESC),
  KEY `idx_hour_ledger_record` (`lesson_record_id`),
  KEY `idx_hour_ledger_business` (`package_id`,`business_date`),
  KEY `idx_hour_ledger_occurrence` (`occurrence_id`),
  KEY `idx_hour_ledger_operator` (`operated_by`),
  CONSTRAINT `fk_hour_ledger_occurrence` FOREIGN KEY (`occurrence_id`) REFERENCES `lesson_occurrence` (`id`),
  CONSTRAINT `fk_hour_ledger_operator` FOREIGN KEY (`operated_by`) REFERENCES `sys_user` (`id`),
  CONSTRAINT `fk_hour_ledger_package` FOREIGN KEY (`package_id`) REFERENCES `course_package` (`id`),
  CONSTRAINT `fk_hour_ledger_record` FOREIGN KEY (`lesson_record_id`) REFERENCES `lesson_record` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Hour ledger';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `lesson_adjustment_log`
--

DROP TABLE IF EXISTS `lesson_adjustment_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `lesson_adjustment_log` (
  `id` bigint NOT NULL COMMENT 'Primary key ID',
  `occurrence_id` bigint NOT NULL COMMENT 'Occurrence ID',
  `adjustment_type` varchar(32) NOT NULL COMMENT 'Adjustment type: RESCHEDULE/CHANGE_ROOM/CHANGE_TEACHER/MAKEUP_CREATE',
  `before_snapshot` json DEFAULT NULL COMMENT 'Before snapshot',
  `after_snapshot` json DEFAULT NULL COMMENT 'After snapshot',
  `reason` varchar(255) DEFAULT NULL COMMENT 'Adjustment reason',
  `adjusted_by` bigint DEFAULT NULL COMMENT 'Adjusted by user ID',
  `adjusted_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT 'Adjusted at',
  PRIMARY KEY (`id`),
  KEY `idx_adjustment_occurrence` (`occurrence_id`,`adjusted_at` DESC),
  KEY `idx_adjustment_user` (`adjusted_by`),
  CONSTRAINT `fk_adjustment_occurrence` FOREIGN KEY (`occurrence_id`) REFERENCES `lesson_occurrence` (`id`),
  CONSTRAINT `fk_adjustment_user` FOREIGN KEY (`adjusted_by`) REFERENCES `sys_user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Lesson adjustment logs';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `lesson_occurrence`
--

DROP TABLE IF EXISTS `lesson_occurrence`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `lesson_occurrence` (
  `id` bigint NOT NULL COMMENT 'Primary key ID',
  `lesson_no` varchar(32) NOT NULL COMMENT 'Lesson number',
  `package_id` bigint DEFAULT NULL COMMENT 'Course package ID (null for class group occurrences)',
  `class_group_id` bigint DEFAULT NULL COMMENT 'Class group ID (for group class occurrences)',
  `student_id` bigint DEFAULT NULL COMMENT 'Student ID (null for class group occurrences)',
  `schedule_rule_id` bigint DEFAULT NULL COMMENT 'Source rule ID',
  `schedule_slot_id` bigint DEFAULT NULL COMMENT 'Source slot ID',
  `occurrence_type` varchar(32) NOT NULL COMMENT 'Occurrence type: REGULAR/MAKEUP/MANUAL',
  `planned_teacher_id` bigint DEFAULT NULL COMMENT 'Planned teacher ID',
  `planned_classroom_id` bigint DEFAULT NULL COMMENT 'Planned classroom ID',
  `planned_start_at` datetime(3) NOT NULL COMMENT 'Planned start time',
  `planned_end_at` datetime(3) NOT NULL COMMENT 'Planned end time',
  `planned_units` int NOT NULL COMMENT 'Planned units',
  `planning_status` varchar(32) NOT NULL DEFAULT 'SCHEDULED' COMMENT 'Planning status: SCHEDULED/ADJUSTED/CANCELLED/MERGED',
  `source_occurrence_id` bigint DEFAULT NULL COMMENT 'Source occurrence ID used for makeup lessons or adjustments',
  `conflict_checked` tinyint(1) NOT NULL DEFAULT '0' COMMENT 'Conflict check completed: 0=no, 1=yes',
  `notes` varchar(500) DEFAULT NULL COMMENT 'Notes',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT 'Created at',
  `created_by` bigint DEFAULT NULL COMMENT 'Created by user ID',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT 'Updated at',
  `updated_by` bigint DEFAULT NULL COMMENT 'Updated by user ID',
  `is_deleted` tinyint(1) NOT NULL DEFAULT '0' COMMENT 'Soft delete flag: 0=no, 1=yes',
  `deleted_at` datetime(3) DEFAULT NULL COMMENT 'Deleted at',
  `deleted_by` bigint DEFAULT NULL COMMENT 'Deleted by user ID',
  `version` int NOT NULL DEFAULT '0' COMMENT 'Optimistic lock version',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_lesson_no` (`lesson_no`),
  KEY `idx_occurrence_package_time` (`package_id`,`planned_start_at`),
  KEY `idx_occurrence_student_time` (`student_id`,`planned_start_at`,`planned_end_at`),
  KEY `idx_occurrence_teacher_time` (`planned_teacher_id`,`planned_start_at`,`planned_end_at`),
  KEY `idx_occurrence_room_time` (`planned_classroom_id`,`planned_start_at`,`planned_end_at`),
  KEY `idx_occurrence_rule` (`schedule_rule_id`,`planned_start_at`),
  KEY `idx_occurrence_slot` (`schedule_slot_id`),
  KEY `idx_occurrence_source` (`source_occurrence_id`),
  KEY `idx_occurrence_class_group` (`class_group_id`),
  CONSTRAINT `fk_occurrence_class_group` FOREIGN KEY (`class_group_id`) REFERENCES `class_group` (`id`),
  CONSTRAINT `fk_occurrence_classroom` FOREIGN KEY (`planned_classroom_id`) REFERENCES `classroom` (`id`),
  CONSTRAINT `fk_occurrence_package` FOREIGN KEY (`package_id`) REFERENCES `course_package` (`id`),
  CONSTRAINT `fk_occurrence_rule` FOREIGN KEY (`schedule_rule_id`) REFERENCES `recurring_schedule_rule` (`id`),
  CONSTRAINT `fk_occurrence_slot` FOREIGN KEY (`schedule_slot_id`) REFERENCES `recurring_schedule_slot` (`id`),
  CONSTRAINT `fk_occurrence_source` FOREIGN KEY (`source_occurrence_id`) REFERENCES `lesson_occurrence` (`id`),
  CONSTRAINT `fk_occurrence_student` FOREIGN KEY (`student_id`) REFERENCES `student` (`id`),
  CONSTRAINT `fk_occurrence_teacher` FOREIGN KEY (`planned_teacher_id`) REFERENCES `teacher` (`id`),
  CONSTRAINT `chk_occurrence_units` CHECK ((`planned_units` > 0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Lesson occurrences';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `lesson_record`
--

DROP TABLE IF EXISTS `lesson_record`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `lesson_record` (
  `id` bigint NOT NULL COMMENT 'Primary key ID',
  `occurrence_id` bigint NOT NULL COMMENT 'Planned occurrence ID',
  `package_id` bigint DEFAULT NULL COMMENT 'Course package ID',
  `student_id` bigint NOT NULL COMMENT 'Student ID',
  `planned_teacher_id` bigint DEFAULT NULL COMMENT 'Planned teacher ID snapshot',
  `actual_teacher_id` bigint DEFAULT NULL COMMENT 'Actual teacher ID',
  `planned_classroom_id` bigint DEFAULT NULL COMMENT 'Planned classroom ID snapshot',
  `actual_classroom_id` bigint DEFAULT NULL COMMENT 'Actual classroom ID',
  `result_status` varchar(32) NOT NULL COMMENT 'Result status: COMPLETED/FORMAL_LEAVE/TEMP_CANCEL/NO_SHOW/TEACHER_CANCEL/MAKEUP_COMPLETED/SUBSTITUTED',
  `actual_start_at` datetime(3) DEFAULT NULL COMMENT 'Actual start time',
  `actual_end_at` datetime(3) DEFAULT NULL COMMENT 'Actual end time',
  `actual_units` int NOT NULL DEFAULT '0' COMMENT 'Actual units',
  `should_deduct` tinyint(1) NOT NULL DEFAULT '0' COMMENT 'Deduct flag: 0=no, 1=yes',
  `deducted_units` int NOT NULL DEFAULT '0' COMMENT 'Deducted units',
  `should_extend_end_date` tinyint(1) NOT NULL DEFAULT '0' COMMENT 'Extend end date flag: 0=no, 1=yes',
  `confirm_stage` varchar(32) NOT NULL DEFAULT 'PENDING_ADMIN' COMMENT 'Confirm stage: PENDING_ADMIN/CONFIRMED/REJECTED',
  `confirmed_by_user_id` bigint DEFAULT NULL COMMENT 'Confirmed by user ID',
  `confirmed_at` datetime(3) DEFAULT NULL COMMENT 'Confirmed at',
  `teacher_confirmed_at` datetime(3) DEFAULT NULL COMMENT 'Teacher initial confirmed at',
  `teacher_confirmed_by_user_id` bigint DEFAULT NULL COMMENT 'Teacher initial confirmed by user ID',
  `admin_confirmed_at` datetime(3) DEFAULT NULL COMMENT 'Admin final confirmed at',
  `admin_confirmed_by_user_id` bigint DEFAULT NULL COMMENT 'Admin final confirmed by user ID',
  `reject_reason` varchar(500) DEFAULT NULL COMMENT 'Admin reject reason',
  `rejected_at` datetime(3) DEFAULT NULL COMMENT 'Rejected at',
  `rejected_by_user_id` bigint DEFAULT NULL COMMENT 'Rejected by user ID',
  `record_source` varchar(32) NOT NULL DEFAULT 'STAFF' COMMENT 'Record source: STAFF/TEACHER/SYSTEM',
  `notes` varchar(500) DEFAULT NULL COMMENT 'Notes',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT 'Created at',
  `created_by` bigint DEFAULT NULL COMMENT 'Created by user ID',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT 'Updated at',
  `updated_by` bigint DEFAULT NULL COMMENT 'Updated by user ID',
  `is_deleted` tinyint(1) NOT NULL DEFAULT '0' COMMENT 'Soft delete flag: 0=no, 1=yes',
  `deleted_at` datetime(3) DEFAULT NULL COMMENT 'Deleted at',
  `deleted_by` bigint DEFAULT NULL COMMENT 'Deleted by user ID',
  `version` int NOT NULL DEFAULT '0' COMMENT 'Optimistic lock version',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_lesson_record_occurrence_student` (`occurrence_id`,`student_id`),
  KEY `idx_lesson_record_package` (`package_id`,`confirmed_at` DESC),
  KEY `idx_lesson_record_student` (`student_id`,`confirmed_at` DESC),
  KEY `idx_lesson_record_actual_teacher` (`actual_teacher_id`,`confirmed_at` DESC),
  KEY `idx_lesson_record_status` (`result_status`,`confirmed_at` DESC),
  KEY `idx_lesson_record_confirm_user` (`confirmed_by_user_id`),
  KEY `fk_lesson_record_planned_teacher` (`planned_teacher_id`),
  KEY `fk_lesson_record_planned_classroom` (`planned_classroom_id`),
  KEY `fk_lesson_record_actual_classroom` (`actual_classroom_id`),
  CONSTRAINT `fk_lesson_record_actual_classroom` FOREIGN KEY (`actual_classroom_id`) REFERENCES `classroom` (`id`),
  CONSTRAINT `fk_lesson_record_actual_teacher` FOREIGN KEY (`actual_teacher_id`) REFERENCES `teacher` (`id`),
  CONSTRAINT `fk_lesson_record_confirm_user` FOREIGN KEY (`confirmed_by_user_id`) REFERENCES `sys_user` (`id`),
  CONSTRAINT `fk_lesson_record_occurrence` FOREIGN KEY (`occurrence_id`) REFERENCES `lesson_occurrence` (`id`),
  CONSTRAINT `fk_lesson_record_planned_classroom` FOREIGN KEY (`planned_classroom_id`) REFERENCES `classroom` (`id`),
  CONSTRAINT `fk_lesson_record_planned_teacher` FOREIGN KEY (`planned_teacher_id`) REFERENCES `teacher` (`id`),
  CONSTRAINT `fk_lesson_record_student` FOREIGN KEY (`student_id`) REFERENCES `student` (`id`),
  CONSTRAINT `chk_lesson_record_units` CHECK (((`actual_units` >= 0) and (`deducted_units` >= 0)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Lesson records';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `lesson_status_rule`
--

DROP TABLE IF EXISTS `lesson_status_rule`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `lesson_status_rule` (
  `id` bigint NOT NULL COMMENT 'Primary key ID',
  `status_code` varchar(32) NOT NULL COMMENT 'Lesson status code',
  `status_name` varchar(64) NOT NULL COMMENT 'Lesson status name',
  `default_should_deduct` tinyint(1) NOT NULL DEFAULT '0' COMMENT 'Default deduct flag: 0=no, 1=yes',
  `deduct_mode` varchar(32) NOT NULL DEFAULT 'NONE' COMMENT 'Deduct mode: NONE/ACTUAL_UNITS/PLANNED_UNITS/FIXED_UNITS',
  `fixed_units` int DEFAULT NULL COMMENT 'Fixed deduct units',
  `default_should_extend` tinyint(1) NOT NULL DEFAULT '0' COMMENT 'Default extend flag: 0=no, 1=yes',
  `allow_manual_override` tinyint(1) NOT NULL DEFAULT '1' COMMENT 'Manual override flag: 0=no, 1=yes',
  `status` varchar(32) NOT NULL DEFAULT 'ACTIVE' COMMENT 'Rule status: ACTIVE/DISABLED',
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
  UNIQUE KEY `uk_lesson_status_rule_code` (`status_code`),
  KEY `idx_lesson_status_rule_status` (`status`),
  CONSTRAINT `chk_lesson_status_rule_fixed_units` CHECK (((`fixed_units` is null) or (`fixed_units` >= 0)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Lesson status rules';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `notification_config`
--

DROP TABLE IF EXISTS `notification_config`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `notification_config` (
  `id` bigint NOT NULL COMMENT 'Primary key ID',
  `enabled` tinyint(1) NOT NULL DEFAULT '0' COMMENT 'Email enabled flag: 0=disabled, 1=enabled',
  `from_address` varchar(255) NOT NULL DEFAULT '' COMMENT 'SMTP sender email address',
  `from_name` varchar(128) NOT NULL DEFAULT 'JIN BEST TUTORING' COMMENT 'SMTP sender display name',
  `auth_code` varchar(255) NOT NULL DEFAULT '' COMMENT 'SMTP authorization code',
  `strict_allowlist` tinyint(1) NOT NULL DEFAULT '1' COMMENT 'Allowlist mode: 0=disabled, 1=enabled',
  `allowed_recipients` text COMMENT 'JSON array',
  `smtp_host` varchar(255) DEFAULT NULL COMMENT 'SMTP服务器地址',
  `smtp_port` int DEFAULT NULL COMMENT 'SMTP服务器端口',
  `smtp_username` varchar(255) DEFAULT NULL COMMENT 'SMTP登录账号',
  `smtp_password` varchar(255) DEFAULT NULL COMMENT 'SMTP登录密码/授权码',
  `low_balance_threshold` int NOT NULL DEFAULT '2' COMMENT 'Low balance threshold in 0.5h units',
  `low_balance_recipients` text COMMENT 'JSON array',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `created_by` bigint DEFAULT NULL,
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `updated_by` bigint DEFAULT NULL,
  `is_deleted` tinyint(1) NOT NULL DEFAULT '0' COMMENT 'Soft delete',
  `deleted_at` datetime(3) DEFAULT NULL,
  `deleted_by` bigint DEFAULT NULL,
  `version` int NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `notification_log`
--

DROP TABLE IF EXISTS `notification_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `notification_log` (
  `id` bigint NOT NULL COMMENT 'Primary key ID',
  `channel_type` varchar(32) NOT NULL DEFAULT 'EMAIL' COMMENT 'Channel type',
  `template_id` bigint DEFAULT NULL COMMENT 'Template ID',
  `student_id` bigint DEFAULT NULL COMMENT 'Student ID',
  `package_id` bigint DEFAULT NULL COMMENT 'Course package ID',
  `lesson_record_id` bigint DEFAULT NULL COMMENT 'Source lesson record ID',
  `recipient` varchar(255) NOT NULL COMMENT 'Recipient',
  `send_status` varchar(32) NOT NULL DEFAULT 'PENDING' COMMENT 'Send status: PENDING/PROCESSING/SUCCESS/FAILED',
  `request_payload` json DEFAULT NULL COMMENT 'Request snapshot',
  `response_payload` json DEFAULT NULL COMMENT 'Response snapshot',
  `error_message` varchar(500) DEFAULT NULL COMMENT 'Error message',
  `scheduled_at` datetime(3) DEFAULT NULL COMMENT 'Scheduled at',
  `sent_at` datetime(3) DEFAULT NULL COMMENT 'Sent at',
  `retry_count` int NOT NULL DEFAULT '0' COMMENT 'Retry count',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT 'Created at',
  `created_by` bigint DEFAULT NULL COMMENT 'Created by user ID',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT 'Updated at',
  `updated_by` bigint DEFAULT NULL COMMENT 'Updated by user ID',
  `is_deleted` tinyint(1) NOT NULL DEFAULT '0' COMMENT 'Soft delete flag: 0=no, 1=yes',
  `deleted_at` datetime(3) DEFAULT NULL COMMENT 'Deleted at',
  `deleted_by` bigint DEFAULT NULL COMMENT 'Deleted by user ID',
  `version` int NOT NULL DEFAULT '0' COMMENT 'Optimistic lock version',
  PRIMARY KEY (`id`),
  KEY `idx_notification_package` (`package_id`,`sent_at` DESC),
  KEY `idx_notification_record` (`lesson_record_id`),
  KEY `idx_notification_status` (`send_status`,`scheduled_at`),
  KEY `idx_notification_template` (`template_id`),
  KEY `idx_notification_student` (`student_id`),
  CONSTRAINT `fk_notification_lesson_record` FOREIGN KEY (`lesson_record_id`) REFERENCES `lesson_record` (`id`),
  CONSTRAINT `fk_notification_package` FOREIGN KEY (`package_id`) REFERENCES `course_package` (`id`),
  CONSTRAINT `fk_notification_student` FOREIGN KEY (`student_id`) REFERENCES `student` (`id`),
  CONSTRAINT `fk_notification_template` FOREIGN KEY (`template_id`) REFERENCES `notification_template` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Notification logs';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `notification_template`
--

DROP TABLE IF EXISTS `notification_template`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `notification_template` (
  `id` bigint NOT NULL COMMENT 'Primary key ID',
  `template_code` varchar(64) NOT NULL COMMENT 'Template code',
  `template_name` varchar(128) NOT NULL COMMENT 'Template name',
  `channel_type` varchar(32) NOT NULL DEFAULT 'EMAIL' COMMENT 'Channel type, EMAIL in phase one',
  `subject_template` varchar(255) NOT NULL COMMENT 'Subject template',
  `body_template` longtext NOT NULL COMMENT 'Body template',
  `status` varchar(32) NOT NULL DEFAULT 'ACTIVE' COMMENT 'Status: ACTIVE/INACTIVE',
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
  UNIQUE KEY `uk_notification_template_code` (`template_code`),
  KEY `idx_notification_template_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Notification templates';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `notification_trigger_rule`
--

DROP TABLE IF EXISTS `notification_trigger_rule`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `notification_trigger_rule` (
  `id` bigint NOT NULL COMMENT 'Primary key ID',
  `trigger_type` varchar(64) NOT NULL COMMENT 'Trigger type',
  `template_id` bigint DEFAULT NULL COMMENT 'Template ID',
  `enabled` tinyint(1) NOT NULL DEFAULT '0' COMMENT 'Enabled flag',
  `recipient_type` varchar(32) NOT NULL DEFAULT 'GUARDIAN_EMAIL' COMMENT 'Recipient type',
  `custom_recipients` text COMMENT 'JSON array',
  `threshold_value` int DEFAULT NULL COMMENT 'Threshold value',
  `remark` varchar(500) DEFAULT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `created_by` bigint DEFAULT NULL,
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `updated_by` bigint DEFAULT NULL,
  `is_deleted` tinyint(1) NOT NULL DEFAULT '0',
  `deleted_at` datetime(3) DEFAULT NULL,
  `deleted_by` bigint DEFAULT NULL,
  `version` int NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_trigger_type` (`trigger_type`,`is_deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `package_balance_snapshot`
--

DROP TABLE IF EXISTS `package_balance_snapshot`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `package_balance_snapshot` (
  `package_id` bigint NOT NULL COMMENT 'Course package ID',
  `total_units` int NOT NULL COMMENT 'Total units',
  `consumed_units` int NOT NULL DEFAULT '0' COMMENT 'Consumed units',
  `remaining_units` int NOT NULL DEFAULT '0' COMMENT 'Remaining units',
  `last_ledger_id` bigint DEFAULT NULL COMMENT 'Last ledger ID',
  `snapshot_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT 'Snapshot at',
  PRIMARY KEY (`package_id`),
  KEY `idx_balance_snapshot_ledger` (`last_ledger_id`),
  CONSTRAINT `fk_balance_snapshot_ledger` FOREIGN KEY (`last_ledger_id`) REFERENCES `hour_ledger` (`id`),
  CONSTRAINT `fk_balance_snapshot_package` FOREIGN KEY (`package_id`) REFERENCES `course_package` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Course package balance snapshot';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `package_teacher_assignment`
--

DROP TABLE IF EXISTS `package_teacher_assignment`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `package_teacher_assignment` (
  `id` bigint NOT NULL COMMENT 'Primary key ID',
  `package_id` bigint NOT NULL COMMENT 'Course package ID',
  `teacher_id` bigint NOT NULL COMMENT 'Teacher ID',
  `is_primary` tinyint(1) NOT NULL DEFAULT '1' COMMENT 'Primary teacher flag: 0=no, 1=yes',
  `effective_from` datetime(3) NOT NULL COMMENT 'Effective from',
  `effective_to` datetime(3) DEFAULT NULL COMMENT 'Effective to',
  `assignment_status` varchar(32) NOT NULL DEFAULT 'ACTIVE' COMMENT 'Assignment status: ACTIVE/ENDED',
  `change_reason` varchar(255) DEFAULT NULL COMMENT 'Change reason',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT 'Created at',
  `created_by` bigint DEFAULT NULL COMMENT 'Created by user ID',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT 'Updated at',
  `updated_by` bigint DEFAULT NULL COMMENT 'Updated by user ID',
  `is_deleted` tinyint(1) NOT NULL DEFAULT '0' COMMENT 'Soft delete flag: 0=no, 1=yes',
  `deleted_at` datetime(3) DEFAULT NULL COMMENT 'Deleted at',
  `deleted_by` bigint DEFAULT NULL COMMENT 'Deleted by user ID',
  `version` int NOT NULL DEFAULT '0' COMMENT 'Optimistic lock version',
  PRIMARY KEY (`id`),
  KEY `idx_assignment_package` (`package_id`,`effective_from` DESC),
  KEY `idx_assignment_teacher` (`teacher_id`,`assignment_status`,`effective_from` DESC),
  CONSTRAINT `fk_assignment_package` FOREIGN KEY (`package_id`) REFERENCES `course_package` (`id`),
  CONSTRAINT `fk_assignment_teacher` FOREIGN KEY (`teacher_id`) REFERENCES `teacher` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Package teacher assignment history';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `payment_method`
--

DROP TABLE IF EXISTS `payment_method`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `payment_method` (
  `id` bigint NOT NULL COMMENT 'Primary key ID',
  `method_code` varchar(32) NOT NULL COMMENT 'Unique method code (e.g. CASH, CREDIT_CARD)',
  `method_name` varchar(64) NOT NULL COMMENT 'Display name (e.g. Cash, Credit Card)',
  `sort_order` int NOT NULL DEFAULT '0' COMMENT 'Sort order (ascending)',
  `status` varchar(16) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE / INACTIVE',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `created_by` bigint DEFAULT NULL,
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `updated_by` bigint DEFAULT NULL,
  `is_deleted` tinyint(1) NOT NULL DEFAULT '0',
  `deleted_at` datetime(3) DEFAULT NULL,
  `deleted_by` bigint DEFAULT NULL,
  `version` int NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_method_code` (`method_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='User-configurable payment methods';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `payment_record`
--

DROP TABLE IF EXISTS `payment_record`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `payment_record` (
  `id` bigint NOT NULL COMMENT 'Primary key ID',
  `enrollment_order_id` bigint NOT NULL COMMENT 'Enrollment order ID',
  `payment_no` varchar(32) NOT NULL COMMENT 'Payment record number',
  `payment_method` varchar(32) NOT NULL DEFAULT 'CASH' COMMENT 'Payment method, fixed as cash in phase one',
  `payment_type` varchar(16) NOT NULL DEFAULT 'PAYMENT' COMMENT 'Record type: PAYMENT/REFUND',
  `payment_status` varchar(32) NOT NULL DEFAULT 'PAID' COMMENT 'Payment status',
  `amount` decimal(12,2) NOT NULL DEFAULT '0.00' COMMENT 'Payment amount',
  `paid_at` datetime(3) DEFAULT NULL COMMENT 'Paid at',
  `handled_by_user_id` bigint DEFAULT NULL COMMENT 'Handler user ID',
  `handled_by_name` varchar(64) DEFAULT NULL COMMENT 'Handler name snapshot',
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
  UNIQUE KEY `uk_payment_no` (`payment_no`),
  KEY `idx_payment_order` (`enrollment_order_id`,`paid_at` DESC),
  KEY `idx_payment_handler` (`handled_by_user_id`),
  CONSTRAINT `fk_payment_handler` FOREIGN KEY (`handled_by_user_id`) REFERENCES `sys_user` (`id`),
  CONSTRAINT `fk_payment_order` FOREIGN KEY (`enrollment_order_id`) REFERENCES `enrollment_order` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Enrollment payment records';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `receipt_record`
--

DROP TABLE IF EXISTS `receipt_record`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `receipt_record` (
  `id` bigint NOT NULL COMMENT 'Primary key ID',
  `receipt_no` varchar(32) NOT NULL COMMENT 'Receipt number',
  `enrollment_order_id` bigint NOT NULL COMMENT 'Enrollment order ID',
  `student_id` bigint NOT NULL COMMENT 'Student ID',
  `template_code` varchar(64) NOT NULL COMMENT 'Template code',
  `receipt_payload` json DEFAULT NULL COMMENT 'Receipt snapshot JSON',
  `issued_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT 'Issued at',
  `issued_by` bigint DEFAULT NULL COMMENT 'Issued by user ID',
  `file_url` varchar(500) DEFAULT NULL COMMENT 'Receipt file URL',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT 'Created at',
  `created_by` bigint DEFAULT NULL COMMENT 'Created by user ID',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT 'Updated at',
  `updated_by` bigint DEFAULT NULL COMMENT 'Updated by user ID',
  `is_deleted` tinyint(1) NOT NULL DEFAULT '0' COMMENT 'Soft delete flag: 0=no, 1=yes',
  `deleted_at` datetime(3) DEFAULT NULL COMMENT 'Deleted at',
  `deleted_by` bigint DEFAULT NULL COMMENT 'Deleted by user ID',
  `version` int NOT NULL DEFAULT '0' COMMENT 'Optimistic lock version',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_receipt_no` (`receipt_no`),
  KEY `idx_receipt_order` (`enrollment_order_id`,`issued_at` DESC),
  KEY `idx_receipt_student` (`student_id`),
  KEY `fk_receipt_issued_by` (`issued_by`),
  CONSTRAINT `fk_receipt_issued_by` FOREIGN KEY (`issued_by`) REFERENCES `sys_user` (`id`),
  CONSTRAINT `fk_receipt_order` FOREIGN KEY (`enrollment_order_id`) REFERENCES `enrollment_order` (`id`),
  CONSTRAINT `fk_receipt_student` FOREIGN KEY (`student_id`) REFERENCES `student` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Receipt records';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `recurring_schedule_rule`
--

DROP TABLE IF EXISTS `recurring_schedule_rule`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `recurring_schedule_rule` (
  `id` bigint NOT NULL COMMENT 'Primary key ID',
  `package_id` bigint NOT NULL COMMENT 'Course package ID',
  `primary_teacher_id` bigint DEFAULT NULL COMMENT 'Default primary teacher ID',
  `default_classroom_id` bigint DEFAULT NULL COMMENT 'Default classroom ID',
  `rule_status` varchar(32) NOT NULL DEFAULT 'ACTIVE' COMMENT 'Rule status: ACTIVE/PAUSED/ENDED',
  `start_date` date NOT NULL COMMENT 'Rule start date',
  `end_date` date DEFAULT NULL COMMENT 'Rule end date',
  `generation_horizon_days` int NOT NULL DEFAULT '90' COMMENT 'Occurrence generation horizon days',
  `notes` varchar(500) DEFAULT NULL COMMENT 'Notes',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT 'Created at',
  `created_by` bigint DEFAULT NULL COMMENT 'Created by user ID',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT 'Updated at',
  `updated_by` bigint DEFAULT NULL COMMENT 'Updated by user ID',
  `is_deleted` tinyint(1) NOT NULL DEFAULT '0' COMMENT 'Soft delete flag: 0=no, 1=yes',
  `deleted_at` datetime(3) DEFAULT NULL COMMENT 'Deleted at',
  `deleted_by` bigint DEFAULT NULL COMMENT 'Deleted by user ID',
  `version` int NOT NULL DEFAULT '0' COMMENT 'Optimistic lock version',
  PRIMARY KEY (`id`),
  KEY `idx_schedule_rule_package` (`package_id`,`rule_status`),
  KEY `idx_schedule_rule_teacher` (`primary_teacher_id`,`rule_status`),
  KEY `idx_schedule_rule_classroom` (`default_classroom_id`),
  CONSTRAINT `fk_schedule_rule_classroom` FOREIGN KEY (`default_classroom_id`) REFERENCES `classroom` (`id`),
  CONSTRAINT `fk_schedule_rule_package` FOREIGN KEY (`package_id`) REFERENCES `course_package` (`id`),
  CONSTRAINT `fk_schedule_rule_teacher` FOREIGN KEY (`primary_teacher_id`) REFERENCES `teacher` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Recurring schedule rules';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `recurring_schedule_slot`
--

DROP TABLE IF EXISTS `recurring_schedule_slot`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `recurring_schedule_slot` (
  `id` bigint NOT NULL COMMENT 'Primary key ID',
  `rule_id` bigint NOT NULL COMMENT 'Rule ID',
  `weekday` tinyint NOT NULL COMMENT 'Weekday: 1-7',
  `start_time` time NOT NULL COMMENT 'Start time',
  `end_time` time NOT NULL COMMENT 'End time',
  `planned_units` int NOT NULL COMMENT 'Planned units',
  `slot_status` varchar(32) NOT NULL DEFAULT 'ACTIVE' COMMENT 'Slot status: ACTIVE/INACTIVE',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT 'Created at',
  `created_by` bigint DEFAULT NULL COMMENT 'Created by user ID',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT 'Updated at',
  `updated_by` bigint DEFAULT NULL COMMENT 'Updated by user ID',
  `is_deleted` tinyint(1) NOT NULL DEFAULT '0' COMMENT 'Soft delete flag: 0=no, 1=yes',
  `deleted_at` datetime(3) DEFAULT NULL COMMENT 'Deleted at',
  `deleted_by` bigint DEFAULT NULL COMMENT 'Deleted by user ID',
  `version` int NOT NULL DEFAULT '0' COMMENT 'Optimistic lock version',
  PRIMARY KEY (`id`),
  KEY `idx_schedule_slot_rule` (`rule_id`,`weekday`,`start_time`),
  CONSTRAINT `fk_schedule_slot_rule` FOREIGN KEY (`rule_id`) REFERENCES `recurring_schedule_rule` (`id`),
  CONSTRAINT `chk_schedule_slot_units` CHECK ((`planned_units` > 0)),
  CONSTRAINT `chk_schedule_slot_weekday` CHECK ((`weekday` between 1 and 7))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Recurring schedule slots';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `student`
--

DROP TABLE IF EXISTS `student`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `student` (
  `id` bigint NOT NULL COMMENT 'Primary key ID',
  `student_no` varchar(32) NOT NULL COMMENT 'Student code',
  `student_name` varchar(64) NOT NULL COMMENT 'Student name',
  `english_name` varchar(64) DEFAULT NULL COMMENT 'English name',
  `gender` varchar(16) DEFAULT NULL COMMENT 'Gender',
  `birth_date` date DEFAULT NULL COMMENT 'Birth date',
  `source_channel` varchar(64) DEFAULT NULL COMMENT 'Lead source',
  `status` varchar(32) NOT NULL DEFAULT 'ACTIVE' COMMENT 'Status: ACTIVE/INACTIVE',
  `campus_id` bigint DEFAULT NULL COMMENT 'Campus ID',
  `notes` varchar(500) DEFAULT NULL COMMENT 'Notes',
  `guardian_name` varchar(64) DEFAULT NULL COMMENT 'Primary guardian name',
  `guardian_phone` varchar(32) DEFAULT NULL COMMENT 'Primary guardian phone',
  `guardian_email` varchar(128) DEFAULT NULL COMMENT 'Primary guardian email',
  `guardian_relation_type` varchar(32) DEFAULT NULL COMMENT 'Primary guardian relation type',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT 'Created at',
  `created_by` bigint DEFAULT NULL COMMENT 'Created by user ID',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT 'Updated at',
  `updated_by` bigint DEFAULT NULL COMMENT 'Updated by user ID',
  `is_deleted` tinyint(1) NOT NULL DEFAULT '0' COMMENT 'Soft delete flag: 0=no, 1=yes',
  `deleted_at` datetime(3) DEFAULT NULL COMMENT 'Deleted at',
  `deleted_by` bigint DEFAULT NULL COMMENT 'Deleted by user ID',
  `version` int NOT NULL DEFAULT '0' COMMENT 'Optimistic lock version',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_student_no` (`student_no`),
  KEY `idx_student_name` (`student_name`),
  KEY `idx_student_status` (`status`),
  KEY `idx_student_campus` (`campus_id`),
  CONSTRAINT `fk_student_campus` FOREIGN KEY (`campus_id`) REFERENCES `campus` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Student master';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `subject`
--

DROP TABLE IF EXISTS `subject`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `subject` (
  `id` bigint NOT NULL COMMENT 'Primary key ID',
  `subject_name` varchar(64) NOT NULL COMMENT 'Subject name',
  `display_order` int NOT NULL DEFAULT '99' COMMENT 'Display order for sorting',
  `status` varchar(32) NOT NULL DEFAULT 'ACTIVE' COMMENT 'Status: ACTIVE/INACTIVE',
  `notes` varchar(500) DEFAULT NULL COMMENT 'Notes',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT 'Created at',
  `created_by` bigint DEFAULT NULL COMMENT 'Created by user ID',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT 'Updated at',
  `updated_by` bigint DEFAULT NULL COMMENT 'Updated by user ID',
  `is_deleted` tinyint(1) NOT NULL DEFAULT '0' COMMENT 'Soft delete flag: 0=no, 1=yes',
  `deleted_at` datetime(3) DEFAULT NULL COMMENT 'Deleted at',
  `deleted_by` bigint DEFAULT NULL COMMENT 'Deleted by user ID',
  `version` int NOT NULL DEFAULT '0' COMMENT 'Optimistic lock version',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_subject_name` (`subject_name`),
  KEY `idx_subject_status` (`status`),
  KEY `idx_subject_display_order` (`display_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Course subjects (Math, English, Science, SAT, etc.)';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `sys_dict_item`
--

DROP TABLE IF EXISTS `sys_dict_item`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_dict_item` (
  `id` bigint NOT NULL COMMENT 'Primary key ID',
  `dict_type_id` bigint NOT NULL COMMENT 'Dictionary type ID',
  `item_code` varchar(64) NOT NULL COMMENT 'Dictionary item code',
  `item_name` varchar(128) NOT NULL COMMENT 'Dictionary item name',
  `item_value` varchar(128) NOT NULL COMMENT 'Dictionary item value',
  `sort_order` int NOT NULL DEFAULT '0' COMMENT 'Sort order',
  `is_default` tinyint(1) NOT NULL DEFAULT '0' COMMENT 'Default flag: 0=no, 1=yes',
  `status` varchar(32) NOT NULL DEFAULT 'ACTIVE' COMMENT 'Status: ACTIVE/DISABLED',
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
  UNIQUE KEY `uk_sys_dict_item_code` (`dict_type_id`,`item_code`),
  KEY `idx_sys_dict_item_value` (`dict_type_id`,`item_value`),
  KEY `idx_sys_dict_item_status` (`status`),
  CONSTRAINT `fk_sys_dict_item_type` FOREIGN KEY (`dict_type_id`) REFERENCES `sys_dict_type` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Dictionary items';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `sys_dict_type`
--

DROP TABLE IF EXISTS `sys_dict_type`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_dict_type` (
  `id` bigint NOT NULL COMMENT 'Primary key ID',
  `dict_code` varchar(64) NOT NULL COMMENT 'Dictionary type code',
  `dict_name` varchar(128) NOT NULL COMMENT 'Dictionary type name',
  `status` varchar(32) NOT NULL DEFAULT 'ACTIVE' COMMENT 'Status: ACTIVE/DISABLED',
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
  UNIQUE KEY `uk_sys_dict_type_code` (`dict_code`),
  KEY `idx_sys_dict_type_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Dictionary types';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `sys_login_log`
--

DROP TABLE IF EXISTS `sys_login_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_login_log` (
  `id` bigint NOT NULL COMMENT 'Primary key ID',
  `user_id` bigint DEFAULT NULL COMMENT 'User ID',
  `username_snapshot` varchar(64) NOT NULL COMMENT 'Username snapshot',
  `login_result` varchar(32) NOT NULL COMMENT 'Login result: SUCCESS/FAILED',
  `ip` varchar(64) DEFAULT NULL COMMENT 'Login IP',
  `user_agent` varchar(500) DEFAULT NULL COMMENT 'User agent',
  `login_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT 'Login time',
  PRIMARY KEY (`id`),
  KEY `idx_sys_login_log_user_time` (`user_id`,`login_at` DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Login logs';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `sys_operation_log`
--

DROP TABLE IF EXISTS `sys_operation_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_operation_log` (
  `id` bigint NOT NULL COMMENT 'Primary key ID',
  `operator_user_id` bigint DEFAULT NULL COMMENT 'Operator user ID',
  `operator_name_snapshot` varchar(64) NOT NULL COMMENT 'Operator name snapshot',
  `biz_type` varchar(64) NOT NULL COMMENT 'Business type',
  `biz_id` bigint DEFAULT NULL COMMENT 'Business record ID',
  `operation_code` varchar(64) NOT NULL COMMENT 'Operation code',
  `operation_desc` varchar(255) NOT NULL COMMENT 'Operation description',
  `request_snapshot` json DEFAULT NULL COMMENT 'Request snapshot',
  `response_snapshot` json DEFAULT NULL COMMENT 'Response snapshot',
  `operate_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT 'Operated at',
  PRIMARY KEY (`id`),
  KEY `idx_sys_operation_log_biz` (`biz_type`,`biz_id`,`operate_at` DESC),
  KEY `idx_sys_operation_log_operator` (`operator_user_id`,`operate_at` DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Operation audit logs';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `sys_permission`
--

DROP TABLE IF EXISTS `sys_permission`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_permission` (
  `id` bigint NOT NULL COMMENT 'Primary key ID',
  `permission_code` varchar(128) NOT NULL COMMENT 'Permission code',
  `permission_name` varchar(128) NOT NULL COMMENT 'Permission name',
  `permission_type` varchar(32) NOT NULL COMMENT 'Permission type: MENU/ACTION/API',
  `parent_id` bigint DEFAULT NULL COMMENT 'Parent permission ID',
  `path` varchar(255) DEFAULT NULL COMMENT 'Frontend route or API path',
  `method` varchar(16) DEFAULT NULL COMMENT 'HTTP method or action type',
  `status` varchar(32) NOT NULL DEFAULT 'ACTIVE' COMMENT 'Status: ACTIVE/DISABLED',
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
  UNIQUE KEY `uk_sys_permission_code` (`permission_code`),
  KEY `idx_sys_permission_parent` (`parent_id`),
  KEY `idx_sys_permission_type_status` (`permission_type`,`status`),
  CONSTRAINT `fk_sys_permission_parent` FOREIGN KEY (`parent_id`) REFERENCES `sys_permission` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='System permissions';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `sys_role`
--

DROP TABLE IF EXISTS `sys_role`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_role` (
  `id` bigint NOT NULL COMMENT 'Primary key ID',
  `role_code` varchar(64) NOT NULL COMMENT 'Role code',
  `role_name` varchar(64) NOT NULL COMMENT 'Role name',
  `status` varchar(32) NOT NULL DEFAULT 'ACTIVE' COMMENT 'Status: ACTIVE/DISABLED',
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
  UNIQUE KEY `uk_sys_role_code` (`role_code`),
  KEY `idx_sys_role_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='System roles';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `sys_role_permission`
--

DROP TABLE IF EXISTS `sys_role_permission`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_role_permission` (
  `id` bigint NOT NULL COMMENT 'Primary key ID',
  `role_id` bigint NOT NULL COMMENT 'Role ID',
  `permission_id` bigint NOT NULL COMMENT 'Permission ID',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT 'Created at',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_role_permission` (`role_id`,`permission_id`),
  KEY `idx_sys_role_permission_permission` (`permission_id`),
  CONSTRAINT `fk_sys_role_permission_permission` FOREIGN KEY (`permission_id`) REFERENCES `sys_permission` (`id`),
  CONSTRAINT `fk_sys_role_permission_role` FOREIGN KEY (`role_id`) REFERENCES `sys_role` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Role-permission mapping';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `sys_user`
--

DROP TABLE IF EXISTS `sys_user`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_user` (
  `id` bigint NOT NULL COMMENT 'Primary key ID',
  `username` varchar(64) NOT NULL COMMENT 'Username',
  `password_hash` varchar(255) NOT NULL COMMENT 'Password hash',
  `user_type` varchar(32) NOT NULL COMMENT 'User type: ADMIN/STAFF/TEACHER',
  `display_name` varchar(64) NOT NULL COMMENT 'Display name',
  `mobile` varchar(32) DEFAULT NULL COMMENT 'Mobile',
  `email` varchar(128) DEFAULT NULL COMMENT 'Email',
  `teacher_id` bigint DEFAULT NULL COMMENT 'Linked teacher ID, used by teacher accounts only',
  `student_id` bigint DEFAULT NULL,
  `status` varchar(32) NOT NULL DEFAULT 'ACTIVE' COMMENT 'Status: ACTIVE/DISABLED/LOCKED',
  `last_login_at` datetime(3) DEFAULT NULL COMMENT 'Last login time',
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
  UNIQUE KEY `uk_sys_user_username` (`username`),
  UNIQUE KEY `uk_sys_user_teacher_id` (`teacher_id`),
  UNIQUE KEY `idx_sys_user_student_id` (`student_id`),
  KEY `idx_sys_user_teacher_id` (`teacher_id`),
  KEY `idx_sys_user_type_status` (`user_type`,`status`),
  CONSTRAINT `fk_sys_user_student` FOREIGN KEY (`student_id`) REFERENCES `student` (`id`),
  CONSTRAINT `fk_sys_user_teacher` FOREIGN KEY (`teacher_id`) REFERENCES `teacher` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='System users';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `sys_user_role`
--

DROP TABLE IF EXISTS `sys_user_role`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_user_role` (
  `id` bigint NOT NULL COMMENT 'Primary key ID',
  `user_id` bigint NOT NULL COMMENT 'User ID',
  `role_id` bigint NOT NULL COMMENT 'Role ID',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT 'Created at',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_user_role` (`user_id`,`role_id`),
  KEY `idx_sys_user_role_role` (`role_id`),
  CONSTRAINT `fk_sys_user_role_role` FOREIGN KEY (`role_id`) REFERENCES `sys_role` (`id`),
  CONSTRAINT `fk_sys_user_role_user` FOREIGN KEY (`user_id`) REFERENCES `sys_user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='User-role mapping';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `teacher`
--

DROP TABLE IF EXISTS `teacher`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `teacher` (
  `id` bigint NOT NULL COMMENT 'Primary key ID',
  `teacher_no` varchar(32) NOT NULL COMMENT 'Teacher code',
  `teacher_name` varchar(64) NOT NULL COMMENT 'Teacher name',
  `email` varchar(128) DEFAULT NULL COMMENT 'Email',
  `mobile` varchar(32) DEFAULT NULL COMMENT 'Mobile',
  `subject_tags` json DEFAULT NULL COMMENT 'Subject tags JSON',
  `status` varchar(32) NOT NULL DEFAULT 'ACTIVE' COMMENT 'Status: ACTIVE/INACTIVE/DISABLED',
  `campus_id` bigint DEFAULT NULL COMMENT 'Campus ID',
  `hire_date` date DEFAULT NULL COMMENT 'Hire date',
  `notes` varchar(500) DEFAULT NULL COMMENT 'Notes',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT 'Created at',
  `created_by` bigint DEFAULT NULL COMMENT 'Created by user ID',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT 'Updated at',
  `updated_by` bigint DEFAULT NULL COMMENT 'Updated by user ID',
  `is_deleted` tinyint(1) NOT NULL DEFAULT '0' COMMENT 'Soft delete flag: 0=no, 1=yes',
  `deleted_at` datetime(3) DEFAULT NULL COMMENT 'Deleted at',
  `deleted_by` bigint DEFAULT NULL COMMENT 'Deleted by user ID',
  `version` int NOT NULL DEFAULT '0' COMMENT 'Optimistic lock version',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_teacher_no` (`teacher_no`),
  KEY `idx_teacher_name` (`teacher_name`),
  KEY `idx_teacher_status` (`status`),
  KEY `idx_teacher_campus` (`campus_id`),
  CONSTRAINT `fk_teacher_campus` FOREIGN KEY (`campus_id`) REFERENCES `campus` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Teacher master';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `teacher_pay_rate`
--

DROP TABLE IF EXISTS `teacher_pay_rate`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `teacher_pay_rate` (
  `id` bigint NOT NULL COMMENT 'Primary key ID',
  `teacher_id` bigint NOT NULL COMMENT 'Teacher ID',
  `course_product_id` bigint NOT NULL COMMENT 'Course product ID',
  `hourly_rate` decimal(10,2) NOT NULL COMMENT 'Per-hour rate in dollars',
  `notes` varchar(500) DEFAULT NULL COMMENT 'Notes',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT 'Created at',
  `created_by` bigint DEFAULT NULL COMMENT 'Created by user ID',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT 'Updated at',
  `updated_by` bigint DEFAULT NULL COMMENT 'Updated by user ID',
  `is_deleted` tinyint(1) NOT NULL DEFAULT '0' COMMENT 'Soft delete flag: 0=no, 1=yes',
  `deleted_at` datetime(3) DEFAULT NULL COMMENT 'Deleted at',
  `deleted_by` bigint DEFAULT NULL COMMENT 'Deleted by user ID',
  `version` int NOT NULL DEFAULT '0' COMMENT 'Optimistic lock version',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_teacher_product` (`teacher_id`,`course_product_id`),
  KEY `idx_pay_rate_teacher` (`teacher_id`),
  KEY `idx_pay_rate_product` (`course_product_id`),
  CONSTRAINT `fk_pay_rate_product` FOREIGN KEY (`course_product_id`) REFERENCES `course_product` (`id`),
  CONSTRAINT `fk_pay_rate_teacher` FOREIGN KEY (`teacher_id`) REFERENCES `teacher` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Teacher hourly pay rate configuration';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `teacher_payment`
--

DROP TABLE IF EXISTS `teacher_payment`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `teacher_payment` (
  `id` bigint NOT NULL COMMENT 'Primary key ID',
  `payment_no` varchar(32) NOT NULL COMMENT 'Payment number',
  `teacher_id` bigint NOT NULL COMMENT 'Teacher ID',
  `period_start` date NOT NULL COMMENT 'Settlement period start',
  `period_end` date NOT NULL COMMENT 'Settlement period end',
  `total_units` int NOT NULL COMMENT 'Total half-hour units',
  `total_hours` decimal(8,1) NOT NULL COMMENT 'Total hours (units/2)',
  `total_amount` decimal(10,2) NOT NULL COMMENT 'Total payment amount',
  `status` varchar(20) NOT NULL DEFAULT 'PENDING' COMMENT 'Status: PENDING/PAID/CANCELLED',
  `paid_at` datetime(3) DEFAULT NULL COMMENT 'Paid at timestamp',
  `paid_by` bigint DEFAULT NULL COMMENT 'Paid by user ID',
  `notes` varchar(500) DEFAULT NULL COMMENT 'Notes',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT 'Created at',
  `created_by` bigint DEFAULT NULL COMMENT 'Created by user ID',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT 'Updated at',
  `updated_by` bigint DEFAULT NULL COMMENT 'Updated by user ID',
  `is_deleted` tinyint(1) NOT NULL DEFAULT '0' COMMENT 'Soft delete flag: 0=no, 1=yes',
  `deleted_at` datetime(3) DEFAULT NULL COMMENT 'Deleted at',
  `deleted_by` bigint DEFAULT NULL COMMENT 'Deleted by user ID',
  `version` int NOT NULL DEFAULT '0' COMMENT 'Optimistic lock version',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_payment_no` (`payment_no`),
  KEY `idx_payment_teacher` (`teacher_id`),
  KEY `idx_payment_status` (`status`),
  CONSTRAINT `fk_payment_teacher` FOREIGN KEY (`teacher_id`) REFERENCES `teacher` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Teacher payment records';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `teacher_payment_item`
--

DROP TABLE IF EXISTS `teacher_payment_item`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `teacher_payment_item` (
  `id` bigint NOT NULL COMMENT 'Primary key ID',
  `payment_id` bigint NOT NULL COMMENT 'Payment ID',
  `lesson_record_id` bigint NOT NULL COMMENT 'Lesson record ID',
  `package_id` bigint DEFAULT NULL COMMENT 'Course package ID',
  `course_product_id` bigint DEFAULT NULL COMMENT 'Course product ID',
  `actual_units` int NOT NULL COMMENT 'Actual half-hour units',
  `hourly_rate` decimal(10,2) NOT NULL COMMENT 'Hourly rate snapshot at payment time',
  `amount` decimal(10,2) NOT NULL COMMENT 'Calculated amount (hours * rate)',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT 'Created at',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_payment_item_lesson_record` (`lesson_record_id`),
  KEY `idx_payment_item_payment` (`payment_id`),
  CONSTRAINT `fk_payment_item_lesson_record` FOREIGN KEY (`lesson_record_id`) REFERENCES `lesson_record` (`id`),
  CONSTRAINT `fk_payment_item_payment` FOREIGN KEY (`payment_id`) REFERENCES `teacher_payment` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Teacher payment item details';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping events for database 'class_deduction_system'
--

--
-- Dumping routines for database 'class_deduction_system'
--
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-06-18 21:46:10
