-- MySQL dump 10.13  Distrib 5.7.24, for osx11.1 (x86_64)
--
-- Host: localhost    Database: class_deduction_system
-- ------------------------------------------------------
-- Server version	9.4.0

SET NAMES utf8mb4;

CREATE DATABASE IF NOT EXISTS `class_deduction_system`
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_0900_ai_ci;

USE `class_deduction_system`;

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!40101 SET NAMES utf8 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

--
-- Table structure for table `auth_session`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
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

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
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

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
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

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
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

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
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

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
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

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
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

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
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
  `start_month` date DEFAULT NULL COMMENT 'GROUP MONTHLY start month (1st day of month)',
  `end_month` date DEFAULT NULL COMMENT 'GROUP MONTHLY end month (1st day of month)',
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

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
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

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
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
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Discount presets configuration';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `enrollment_order`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
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

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
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

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
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

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
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

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `lesson_record` (
  `id` bigint NOT NULL COMMENT 'Primary key ID',
  `occurrence_id` bigint NOT NULL COMMENT 'Planned occurrence ID',
  `package_id` bigint DEFAULT NULL COMMENT 'Course package ID',
  `student_id` bigint NOT NULL COMMENT 'Student ID',
  `planned_teacher_id` bigint DEFAULT NULL COMMENT 'Planned teacher ID snapshot',
  `actual_teacher_id` bigint DEFAULT NULL COMMENT 'Actual teacher ID',
  `planned_classroom_id` bigint DEFAULT NULL COMMENT 'Planned classroom ID snapshot',
  `actual_classroom_id` bigint DEFAULT NULL COMMENT 'Actual classroom ID',
  `planned_start_at` datetime(3) DEFAULT NULL COMMENT 'Planned start time snapshot',
  `planned_end_at` datetime(3) DEFAULT NULL COMMENT 'Planned end time snapshot',
  `planned_units` int DEFAULT NULL COMMENT 'Planned units snapshot',
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
  KEY `idx_lesson_record_confirm_stage` (`confirm_stage`,`confirmed_at` DESC),
  KEY `idx_lesson_record_confirm_user` (`confirmed_by_user_id`),
  KEY `idx_lesson_record_teacher_confirm_user` (`teacher_confirmed_by_user_id`),
  KEY `idx_lesson_record_admin_confirm_user` (`admin_confirmed_by_user_id`),
  KEY `idx_lesson_record_reject_user` (`rejected_by_user_id`),
  KEY `fk_lesson_record_planned_teacher` (`planned_teacher_id`),
  KEY `fk_lesson_record_planned_classroom` (`planned_classroom_id`),
  KEY `fk_lesson_record_actual_classroom` (`actual_classroom_id`),
  CONSTRAINT `fk_lesson_record_actual_classroom` FOREIGN KEY (`actual_classroom_id`) REFERENCES `classroom` (`id`),
  CONSTRAINT `fk_lesson_record_actual_teacher` FOREIGN KEY (`actual_teacher_id`) REFERENCES `teacher` (`id`),
  CONSTRAINT `fk_lesson_record_admin_confirm_user` FOREIGN KEY (`admin_confirmed_by_user_id`) REFERENCES `sys_user` (`id`),
  CONSTRAINT `fk_lesson_record_confirm_user` FOREIGN KEY (`confirmed_by_user_id`) REFERENCES `sys_user` (`id`),
  CONSTRAINT `fk_lesson_record_occurrence` FOREIGN KEY (`occurrence_id`) REFERENCES `lesson_occurrence` (`id`),
  CONSTRAINT `fk_lesson_record_planned_classroom` FOREIGN KEY (`planned_classroom_id`) REFERENCES `classroom` (`id`),
  CONSTRAINT `fk_lesson_record_planned_teacher` FOREIGN KEY (`planned_teacher_id`) REFERENCES `teacher` (`id`),
  CONSTRAINT `fk_lesson_record_reject_user` FOREIGN KEY (`rejected_by_user_id`) REFERENCES `sys_user` (`id`),
  CONSTRAINT `fk_lesson_record_student` FOREIGN KEY (`student_id`) REFERENCES `student` (`id`),
  CONSTRAINT `fk_lesson_record_teacher_confirm_user` FOREIGN KEY (`teacher_confirmed_by_user_id`) REFERENCES `sys_user` (`id`),
  CONSTRAINT `chk_lesson_record_units` CHECK (((`actual_units` >= 0) and (`deducted_units` >= 0)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Lesson records';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `lesson_status_rule`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
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

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `notification_config` (
  `id` bigint NOT NULL COMMENT 'Primary key ID',
  `enabled` tinyint(1) NOT NULL DEFAULT '0' COMMENT 'Email enabled flag: 0=disabled, 1=enabled',
  `from_address` varchar(255) NOT NULL DEFAULT '' COMMENT 'SMTP sender email address',
  `from_name` varchar(128) NOT NULL DEFAULT 'JIN BEST TUTORING' COMMENT 'SMTP sender display name',
  `auth_code` varchar(255) NOT NULL DEFAULT '' COMMENT 'SMTP authorization code',
  `strict_allowlist` tinyint(1) NOT NULL DEFAULT '1' COMMENT 'Allowlist mode: 0=disabled, 1=enabled',
  `allowed_recipients` text COMMENT 'JSON array',
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

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
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

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
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

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
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

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
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

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
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

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
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

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `payment_record` (
  `id` bigint NOT NULL COMMENT 'Primary key ID',
  `enrollment_order_id` bigint NOT NULL COMMENT 'Enrollment order ID',
  `payment_no` varchar(32) NOT NULL COMMENT 'Payment record number',
  `payment_method` varchar(32) NOT NULL DEFAULT 'CASH' COMMENT 'Payment method, fixed as cash in phase one',
  `payment_type` varchar(16) NOT NULL DEFAULT 'PAYMENT' COMMENT 'Record type: PAYMENT/REFUND',
  `payment_status` varchar(32) NOT NULL DEFAULT 'PAID' COMMENT 'Payment status: PAID/REFUNDED',
  `amount` decimal(12,2) NOT NULL DEFAULT '0.00' COMMENT 'Payment amount (negative for refund)',
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

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
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

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
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

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
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

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
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

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
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

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
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

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
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

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
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

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
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

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
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

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
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

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
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

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
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

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
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

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
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

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
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

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
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

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
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

-- Dump completed on 2026-05-24 21:52:15
-- MySQL dump 10.13  Distrib 5.7.24, for osx11.1 (x86_64)
--
-- Host: localhost    Database: class_deduction_system
-- ------------------------------------------------------
-- Server version	9.4.0

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!40101 SET NAMES utf8 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

--
-- Dumping data for table `sys_role`
--

LOCK TABLES `sys_role` WRITE;
/*!40000 ALTER TABLE `sys_role` DISABLE KEYS */;
INSERT INTO `sys_role` (`id`, `role_code`, `role_name`, `status`, `remark`, `created_at`, `created_by`, `updated_at`, `updated_by`, `is_deleted`, `deleted_at`, `deleted_by`, `version`) VALUES (100,'ADMIN','Administrator','ACTIVE','System administrator with full permissions','2026-05-14 23:52:38.467',NULL,'2026-05-15 00:07:21.552',NULL,0,NULL,NULL,0),(110,'STAFF','Staff','ACTIVE','Internal staff responsible for day-to-day operations','2026-05-14 23:52:38.467',NULL,'2026-05-15 00:07:21.552',NULL,0,NULL,NULL,0),(120,'TEACHER','Teacher','ACTIVE','Future role limited to viewing a personal timetable and confirming personal lessons','2026-05-14 23:52:38.467',NULL,'2026-05-15 00:07:21.552',NULL,0,NULL,NULL,0),(130,'STUDENT','Student','ACTIVE','Student who can access their own learning portal','2026-05-24 01:54:08.652',NULL,'2026-05-24 01:54:08.652',NULL,0,NULL,NULL,0);
/*!40000 ALTER TABLE `sys_role` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Dumping data for table `sys_dict_type`
--

LOCK TABLES `sys_dict_type` WRITE;
/*!40000 ALTER TABLE `sys_dict_type` DISABLE KEYS */;
INSERT INTO `sys_dict_type` (`id`, `dict_code`, `dict_name`, `status`, `remark`, `created_at`, `created_by`, `updated_at`, `updated_by`, `is_deleted`, `deleted_at`, `deleted_by`, `version`) VALUES (1000,'USER_TYPE','User type','ACTIVE','System account user type','2026-05-14 23:52:38.470',NULL,'2026-05-15 00:07:21.552',NULL,0,NULL,NULL,0),(1010,'USER_STATUS','User status','ACTIVE','System account status','2026-05-14 23:52:38.470',NULL,'2026-05-15 00:07:21.552',NULL,0,NULL,NULL,0),(1020,'STUDENT_STATUS','Student status','ACTIVE','Student status','2026-05-14 23:52:38.470',NULL,'2026-05-15 00:07:21.552',NULL,0,NULL,NULL,0),(1030,'TEACHER_STATUS','Teacher status','ACTIVE','Teacher status','2026-05-14 23:52:38.470',NULL,'2026-05-15 00:07:21.552',NULL,0,NULL,NULL,0),(1040,'CLASSROOM_STATUS','Classroom status','ACTIVE','Classroom status','2026-05-14 23:52:38.470',NULL,'2026-05-15 00:07:21.552',NULL,0,NULL,NULL,0),(1050,'COURSE_PACKAGE_STATUS','Course package status','ACTIVE','Course package lifecycle status','2026-05-14 23:52:38.470',NULL,'2026-05-15 00:07:21.552',NULL,0,NULL,NULL,0),(1060,'PAYMENT_STATUS','Payment status','ACTIVE','Payment status','2026-05-14 23:52:38.470',NULL,'2026-05-15 00:07:21.552',NULL,0,NULL,NULL,0),(1070,'LESSON_PLANNING_STATUS','Lesson planning status','ACTIVE','Scheduling status','2026-05-14 23:52:38.470',NULL,'2026-05-15 00:07:21.552',NULL,0,NULL,NULL,0),(1080,'LESSON_RESULT_STATUS','Lesson result status','ACTIVE','Lesson result status','2026-05-14 23:52:38.470',NULL,'2026-05-15 00:07:21.552',NULL,0,NULL,NULL,0),(1090,'LESSON_LEDGER_TYPE','Unit ledger type','ACTIVE','Unit ledger type','2026-05-14 23:52:38.470',NULL,'2026-05-15 00:07:21.552',NULL,0,NULL,NULL,0),(1100,'TEACHING_MODE','Teaching mode','ACTIVE','Course delivery mode','2026-05-14 23:52:38.470',NULL,'2026-05-15 00:07:21.552',NULL,0,NULL,NULL,0);
/*!40000 ALTER TABLE `sys_dict_type` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Dumping data for table `sys_dict_item`
--

LOCK TABLES `sys_dict_item` WRITE;
/*!40000 ALTER TABLE `sys_dict_item` DISABLE KEYS */;
INSERT INTO `sys_dict_item` (`id`, `dict_type_id`, `item_code`, `item_name`, `item_value`, `sort_order`, `is_default`, `status`, `remark`, `created_at`, `created_by`, `updated_at`, `updated_by`, `is_deleted`, `deleted_at`, `deleted_by`, `version`) VALUES (10001,1000,'ADMIN','Administrator','ADMIN',10,0,'ACTIVE','Administrator account','2026-05-14 23:52:38.471',NULL,'2026-05-15 00:07:21.553',NULL,0,NULL,NULL,0),(10002,1000,'STAFF','Staff','STAFF',20,1,'ACTIVE','Staff account','2026-05-14 23:52:38.471',NULL,'2026-05-15 00:07:21.553',NULL,0,NULL,NULL,0),(10003,1000,'TEACHER','Teacher','TEACHER',30,0,'ACTIVE','Teacher account','2026-05-14 23:52:38.471',NULL,'2026-05-15 00:07:21.553',NULL,0,NULL,NULL,0),(10101,1010,'ACTIVE','Active','ACTIVE',10,1,'ACTIVE','Can log in normally','2026-05-14 23:52:38.471',NULL,'2026-05-15 00:07:21.553',NULL,0,NULL,NULL,0),(10102,1010,'DISABLED','Disabled','DISABLED',20,0,'ACTIVE','Account is disabled','2026-05-14 23:52:38.471',NULL,'2026-05-15 00:07:21.553',NULL,0,NULL,NULL,0),(10103,1010,'LOCKED','Locked','LOCKED',30,0,'ACTIVE','Account is locked','2026-05-14 23:52:38.471',NULL,'2026-05-15 00:07:21.553',NULL,0,NULL,NULL,0),(10201,1020,'ACTIVE','Enrolled','ACTIVE',10,1,'ACTIVE','Student is actively enrolled','2026-05-14 23:52:38.471',NULL,'2026-05-15 00:07:21.553',NULL,0,NULL,NULL,0),(10202,1020,'INACTIVE','Inactive','INACTIVE',20,0,'ACTIVE','Student is no longer active','2026-05-14 23:52:38.471',NULL,'2026-05-15 00:07:21.553',NULL,0,NULL,NULL,0),(10301,1030,'ACTIVE','Active','ACTIVE',10,1,'ACTIVE','Teacher is available for scheduling','2026-05-14 23:52:38.471',NULL,'2026-05-15 00:07:21.553',NULL,0,NULL,NULL,0),(10302,1030,'INACTIVE','Inactive','INACTIVE',20,0,'ACTIVE','Teacher is temporarily unavailable for scheduling','2026-05-14 23:52:38.471',NULL,'2026-05-15 00:07:21.553',NULL,0,NULL,NULL,0),(10303,1030,'DISABLED','Disabled','DISABLED',30,0,'ACTIVE','Account or profile is disabled','2026-05-14 23:52:38.471',NULL,'2026-05-15 00:07:21.553',NULL,0,NULL,NULL,0),(10401,1040,'ACTIVE','Active','ACTIVE',10,1,'ACTIVE','Classroom is available for scheduling','2026-05-14 23:52:38.471',NULL,'2026-05-15 00:07:21.553',NULL,0,NULL,NULL,0),(10402,1040,'DISABLED','Inactive','DISABLED',20,0,'ACTIVE','Classroom is inactive','2026-05-14 23:52:38.471',NULL,'2026-05-15 00:07:21.553',NULL,0,NULL,NULL,0),(10501,1050,'DRAFT','Draft','DRAFT',10,0,'ACTIVE','Draft package','2026-05-14 23:52:38.471',NULL,'2026-05-15 00:07:21.553',NULL,0,NULL,NULL,0),(10502,1050,'ACTIVE','Active','ACTIVE',20,1,'ACTIVE','Package is active','2026-05-14 23:52:38.471',NULL,'2026-05-15 00:07:21.553',NULL,0,NULL,NULL,0),(10503,1050,'COMPLETED','Completed','COMPLETED',30,0,'ACTIVE','Package units are fully consumed','2026-05-14 23:52:38.471',NULL,'2026-05-15 00:07:21.553',NULL,0,NULL,NULL,0),(10504,1050,'CLOSED','Closed','CLOSED',40,0,'ACTIVE','Closed manually','2026-05-14 23:52:38.471',NULL,'2026-05-15 00:07:21.553',NULL,0,NULL,NULL,0),(10601,1060,'UNPAID','Unpaid','UNPAID',10,1,'ACTIVE','Payment not received yet','2026-05-14 23:52:38.471',NULL,'2026-05-15 00:07:21.553',NULL,0,NULL,NULL,0),(10602,1060,'PARTIAL','Partially paid','PARTIAL',20,0,'ACTIVE','Payment partially collected','2026-05-14 23:52:38.471',NULL,'2026-05-15 00:07:21.553',NULL,0,NULL,NULL,0),(10603,1060,'PAID','Paid','PAID',30,0,'ACTIVE','Payment collected','2026-05-14 23:52:38.471',NULL,'2026-05-15 00:07:21.553',NULL,0,NULL,NULL,0),(10701,1070,'SCHEDULED','Scheduled','SCHEDULED',10,1,'ACTIVE','Regular planned lesson','2026-05-14 23:52:38.471',NULL,'2026-05-15 00:07:21.553',NULL,0,NULL,NULL,0),(10702,1070,'ADJUSTED','Adjusted','ADJUSTED',20,0,'ACTIVE','Adjusted planned lesson','2026-05-14 23:52:38.471',NULL,'2026-05-15 00:07:21.553',NULL,0,NULL,NULL,0),(10703,1070,'CANCELLED','Cancelled','CANCELLED',30,0,'ACTIVE','Cancelled planned lesson','2026-05-14 23:52:38.471',NULL,'2026-05-15 00:07:21.553',NULL,0,NULL,NULL,0),(10704,1070,'MERGED','Merged','MERGED',40,0,'ACTIVE','Merged lesson','2026-05-14 23:52:38.471',NULL,'2026-05-15 00:07:21.553',NULL,0,NULL,NULL,0),(10801,1080,'COMPLETED','Completed','COMPLETED',10,1,'ACTIVE','Lesson completed normally','2026-05-14 23:52:38.471',NULL,'2026-05-15 00:07:21.553',NULL,0,NULL,NULL,0),(10802,1080,'FORMAL_LEAVE','Formal leave','FORMAL_LEAVE',20,0,'ACTIVE','Formal leave does not deduct units','2026-05-14 23:52:38.471',NULL,'2026-05-15 00:07:21.553',NULL,0,NULL,NULL,0),(10803,1080,'TEMP_CANCEL','Temporary cancellation','TEMP_CANCEL',30,0,'ACTIVE','Temporary cancellation with configurable deduction behavior','2026-05-14 23:52:38.471',NULL,'2026-05-15 00:07:21.553',NULL,0,NULL,NULL,0),(10804,1080,'NO_SHOW','No-show','NO_SHOW',40,0,'ACTIVE','Student no-show deducts units by default','2026-05-14 23:52:38.471',NULL,'2026-05-15 00:07:21.553',NULL,0,NULL,NULL,0),(10805,1080,'TEACHER_CANCEL','Teacher cancellation','TEACHER_CANCEL',50,0,'ACTIVE','Teacher cancellation does not deduct units by default','2026-05-14 23:52:38.471',NULL,'2026-05-15 00:07:21.553',NULL,0,NULL,NULL,0),(10806,1080,'MAKEUP_COMPLETED','Makeup completed','MAKEUP_COMPLETED',60,0,'ACTIVE','Makeup completed','2026-05-14 23:52:38.471',NULL,'2026-05-15 00:07:21.553',NULL,0,NULL,NULL,0),(10807,1080,'SUBSTITUTED','Substitution completed','SUBSTITUTED',70,0,'ACTIVE','Substitution completed','2026-05-14 23:52:38.471',NULL,'2026-05-15 00:07:21.553',NULL,0,NULL,NULL,0),(10901,1090,'INITIAL_ALLOCATE','Initial allocation','INITIAL_ALLOCATE',10,1,'ACTIVE','Initial course package allocation','2026-05-14 23:52:38.471',NULL,'2026-05-15 00:07:21.553',NULL,0,NULL,NULL,0),(10902,1090,'DEDUCT','Deduction','DEDUCT',20,0,'ACTIVE','Standard unit deduction','2026-05-14 23:52:38.471',NULL,'2026-05-15 00:07:21.553',NULL,0,NULL,NULL,0),(10903,1090,'RESTORE','Restore','RESTORE',30,0,'ACTIVE','Restore units','2026-05-14 23:52:38.471',NULL,'2026-05-15 00:07:21.553',NULL,0,NULL,NULL,0),(10904,1090,'MANUAL_ADJUST','Manual adjustment','MANUAL_ADJUST',40,0,'ACTIVE','Manual unit adjustment','2026-05-14 23:52:38.471',NULL,'2026-05-15 00:07:21.553',NULL,0,NULL,NULL,0),(10905,1090,'NO_DEDUCT_AUDIT','No-deduct audit','NO_DEDUCT_AUDIT',50,0,'ACTIVE','No deduction but keep the audit record','2026-05-14 23:52:38.471',NULL,'2026-05-15 00:07:21.553',NULL,0,NULL,NULL,0),(11001,1100,'ONE_ON_ONE','One-on-one','ONE_ON_ONE',10,1,'ACTIVE','One-on-one course','2026-05-14 23:52:38.471',NULL,'2026-05-15 00:07:21.553',NULL,0,NULL,NULL,0),(11002,1100,'GROUP','Group','GROUP',20,0,'ACTIVE','Group course','2026-05-14 23:52:38.471',NULL,'2026-05-15 00:07:21.553',NULL,0,NULL,NULL,0);
/*!40000 ALTER TABLE `sys_dict_item` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Dumping data for table `sys_permission`
--

LOCK TABLES `sys_permission` WRITE;
/*!40000 ALTER TABLE `sys_permission` DISABLE KEYS */;
INSERT INTO `sys_permission` (`id`, `permission_code`, `permission_name`, `permission_type`, `parent_id`, `path`, `method`, `status`, `remark`, `created_at`, `created_by`, `updated_at`, `updated_by`, `is_deleted`, `deleted_at`, `deleted_by`, `version`) VALUES (10000,'dashboard','Dashboard','MENU',NULL,'/dashboard',NULL,'ACTIVE','Top-level menu: Dashboard','2026-05-14 23:52:38.472',NULL,'2026-05-15 00:07:21.553',NULL,0,NULL,NULL,0),(11000,'base_data','Base data','MENU',NULL,'/base-data',NULL,'ACTIVE','Top-level menu: Base data','2026-05-14 23:52:38.472',NULL,'2026-05-15 00:07:21.553',NULL,0,NULL,NULL,0),(11010,'student','Student profiles','MENU',11000,'/base-data/students',NULL,'ACTIVE','Second-level menu: Student profiles','2026-05-14 23:52:38.472',NULL,'2026-05-15 00:07:21.553',NULL,0,NULL,NULL,0),(11020,'teacher','Teacher profiles','MENU',11000,'/base-data/teachers',NULL,'ACTIVE','Second-level menu: Teacher profiles','2026-05-14 23:52:38.472',NULL,'2026-05-15 00:07:21.553',NULL,0,NULL,NULL,0),(11030,'classroom','Classroom management','MENU',11000,'/base-data/classrooms',NULL,'ACTIVE','Second-level menu: Classroom management','2026-05-14 23:52:38.472',NULL,'2026-05-15 00:07:21.553',NULL,0,NULL,NULL,0),(11040,'course_product','Course products','MENU',11000,'/base-data/course-products',NULL,'ACTIVE','Second-level menu: Course products','2026-05-14 23:52:38.472',NULL,'2026-05-15 00:07:21.553',NULL,0,NULL,NULL,0),(12000,'enrollment_package','Enrollment and packages','MENU',NULL,'/enrollment',NULL,'ACTIVE','Top-level menu: Enrollment and packages','2026-05-14 23:52:38.472',NULL,'2026-05-15 00:07:21.553',NULL,0,NULL,NULL,0),(12010,'enrollment_order','Enrollment orders','MENU',12000,'/enrollment/orders',NULL,'ACTIVE','Second-level menu: Enrollment orders','2026-05-14 23:52:38.472',NULL,'2026-05-15 00:07:21.553',NULL,0,NULL,NULL,0),(12020,'course_package','Course package management','MENU',12000,'/enrollment/packages',NULL,'ACTIVE','Second-level menu: Course package management','2026-05-14 23:52:38.472',NULL,'2026-05-15 00:07:21.553',NULL,0,NULL,NULL,0),(12030,'receipt_record','Receipt management','MENU',12000,'/enrollment/receipts',NULL,'ACTIVE','Second-level menu: Receipt management','2026-05-14 23:52:38.472',NULL,'2026-05-15 00:07:21.553',NULL,0,NULL,NULL,0),(13000,'schedule_center','Scheduling','MENU',NULL,'/schedule',NULL,'ACTIVE','Top-level menu: Scheduling','2026-05-14 23:52:38.472',NULL,'2026-05-15 00:07:21.553',NULL,0,NULL,NULL,0),(13010,'recurring_schedule','Recurring scheduling','MENU',13000,'/schedule/recurring',NULL,'ACTIVE','Second-level menu: Recurring scheduling','2026-05-14 23:52:38.472',NULL,'2026-05-15 00:07:21.553',NULL,0,NULL,NULL,0),(13020,'adjustment','Reschedules and makeup','MENU',13000,'/schedule/adjustments',NULL,'ACTIVE','Second-level menu: Reschedules and makeup','2026-05-14 23:52:38.472',NULL,'2026-05-15 00:07:21.553',NULL,0,NULL,NULL,0),(13030,'teacher_schedule','Teacher timetable','MENU',13000,'/schedule/teacher',NULL,'ACTIVE','Second-level menu: Teacher timetable','2026-05-14 23:52:38.472',NULL,'2026-05-15 00:07:21.553',NULL,0,NULL,NULL,0),(13040,'classroom_occupancy','Classroom occupancy','MENU',13000,'/schedule/classrooms',NULL,'ACTIVE','Second-level menu: Classroom occupancy','2026-05-14 23:52:38.472',NULL,'2026-05-15 00:07:21.553',NULL,0,NULL,NULL,0),(14000,'lesson_deduction','Lessons and deduction','MENU',NULL,'/lessons',NULL,'ACTIVE','Top-level menu: Lessons and deduction','2026-05-14 23:52:38.472',NULL,'2026-05-15 00:07:21.553',NULL,0,NULL,NULL,0),(14010,'lesson_record','Lesson records','MENU',14000,'/lessons/records',NULL,'ACTIVE','Second-level menu: Lesson records','2026-05-14 23:52:38.472',NULL,'2026-05-15 00:07:21.553',NULL,0,NULL,NULL,0),(14020,'lesson_exception','Exception records','MENU',14000,'/lessons/exceptions',NULL,'ACTIVE','Second-level menu: Exception records','2026-05-14 23:52:38.472',NULL,'2026-05-15 00:07:21.553',NULL,0,NULL,NULL,0),(14030,'hour_ledger','Deduction ledger','MENU',14000,'/lessons/ledger',NULL,'ACTIVE','Second-level menu: Deduction ledger','2026-05-14 23:52:38.472',NULL,'2026-05-15 00:07:21.553',NULL,0,NULL,NULL,0),(14040,'package_balance','Remaining units','MENU',14000,'/lessons/balance',NULL,'ACTIVE','Second-level menu: Remaining units','2026-05-14 23:52:38.472',NULL,'2026-05-15 00:07:21.553',NULL,0,NULL,NULL,0),(15000,'system_setting','System settings','MENU',NULL,'/system',NULL,'ACTIVE','Top-level menu: System settings','2026-05-14 23:52:38.472',NULL,'2026-05-15 00:07:21.553',NULL,0,NULL,NULL,0),(15010,'lesson_status_rule','Lesson status rules','MENU',15000,'/system/rules',NULL,'ACTIVE','Second-level menu: Lesson status rules','2026-05-14 23:52:38.472',NULL,'2026-05-15 00:07:21.553',NULL,0,NULL,NULL,0),(15020,'notification_template','Notification templates','MENU',15000,'/system/notifications',NULL,'ACTIVE','Second-level menu: Notification templates','2026-05-14 23:52:38.472',NULL,'2026-05-15 00:07:21.553',NULL,0,NULL,NULL,0),(15030,'brand_config','Brand settings','MENU',15000,'/system/brand',NULL,'ACTIVE','Second-level menu: Brand settings','2026-05-14 23:52:38.472',NULL,'2026-05-15 00:07:21.553',NULL,0,NULL,NULL,0),(15040,'account_permission','Accounts and permissions','MENU',15000,'/system/accounts',NULL,'ACTIVE','Second-level menu: Accounts and permissions','2026-05-14 23:52:38.472',NULL,'2026-05-15 00:07:21.553',NULL,0,NULL,NULL,0),(16010,'teacher:self:schedule:view','Teacher view own timetable','ACTION',NULL,NULL,NULL,'ACTIVE','Teacher-side extension permission','2026-05-14 23:52:38.472',NULL,'2026-05-15 00:07:21.553',NULL,0,NULL,NULL,0),(16020,'teacher:self:lesson:confirm','Teacher confirm own lessons','ACTION',NULL,NULL,NULL,'ACTIVE','Teacher-side extension permission','2026-05-14 23:52:38.472',NULL,'2026-05-15 00:07:21.553',NULL,0,NULL,NULL,0);
/*!40000 ALTER TABLE `sys_permission` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Dumping data for table `sys_role_permission`
--

LOCK TABLES `sys_role_permission` WRITE;
/*!40000 ALTER TABLE `sys_role_permission` DISABLE KEYS */;
INSERT INTO `sys_role_permission` (`id`, `role_id`, `permission_id`, `created_at`) VALUES (20001,100,10000,'2026-05-14 23:52:38.473'),(20002,100,11000,'2026-05-14 23:52:38.473'),(20003,100,11010,'2026-05-14 23:52:38.473'),(20004,100,11020,'2026-05-14 23:52:38.473'),(20005,100,11030,'2026-05-14 23:52:38.473'),(20006,100,11040,'2026-05-14 23:52:38.473'),(20007,100,12000,'2026-05-14 23:52:38.473'),(20008,100,12010,'2026-05-14 23:52:38.473'),(20009,100,12020,'2026-05-14 23:52:38.473'),(20010,100,12030,'2026-05-14 23:52:38.473'),(20011,100,13000,'2026-05-14 23:52:38.473'),(20012,100,13010,'2026-05-14 23:52:38.473'),(20013,100,13020,'2026-05-14 23:52:38.473'),(20014,100,13030,'2026-05-14 23:52:38.473'),(20015,100,13040,'2026-05-14 23:52:38.473'),(20016,100,14000,'2026-05-14 23:52:38.473'),(20017,100,14010,'2026-05-14 23:52:38.473'),(20018,100,14020,'2026-05-14 23:52:38.473'),(20019,100,14030,'2026-05-14 23:52:38.473'),(20020,100,14040,'2026-05-14 23:52:38.473'),(20021,100,15000,'2026-05-14 23:52:38.473'),(20022,100,15010,'2026-05-14 23:52:38.473'),(20023,100,15020,'2026-05-14 23:52:38.473'),(20024,100,15030,'2026-05-14 23:52:38.473'),(20025,100,15040,'2026-05-14 23:52:38.473'),(20026,100,16010,'2026-05-14 23:52:38.473'),(20027,100,16020,'2026-05-14 23:52:38.473'),(20101,110,10000,'2026-05-14 23:52:38.473'),(20102,110,11000,'2026-05-14 23:52:38.473'),(20103,110,11010,'2026-05-14 23:52:38.473'),(20104,110,11020,'2026-05-14 23:52:38.473'),(20105,110,11030,'2026-05-14 23:52:38.473'),(20106,110,11040,'2026-05-14 23:52:38.473'),(20107,110,12000,'2026-05-14 23:52:38.473'),(20108,110,12010,'2026-05-14 23:52:38.473'),(20109,110,12020,'2026-05-14 23:52:38.473'),(20110,110,12030,'2026-05-14 23:52:38.473'),(20111,110,13000,'2026-05-14 23:52:38.473'),(20112,110,13010,'2026-05-14 23:52:38.473'),(20113,110,13020,'2026-05-14 23:52:38.473'),(20114,110,13030,'2026-05-14 23:52:38.473'),(20115,110,13040,'2026-05-14 23:52:38.473'),(20116,110,14000,'2026-05-14 23:52:38.473'),(20117,110,14010,'2026-05-14 23:52:38.473'),(20118,110,14020,'2026-05-14 23:52:38.473'),(20119,110,14030,'2026-05-14 23:52:38.473'),(20120,110,14040,'2026-05-14 23:52:38.473'),(20201,120,16010,'2026-05-14 23:52:38.473'),(20202,120,16020,'2026-05-14 23:52:38.473');
/*!40000 ALTER TABLE `sys_role_permission` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Dumping data for table `lesson_status_rule`
--

LOCK TABLES `lesson_status_rule` WRITE;
/*!40000 ALTER TABLE `lesson_status_rule` DISABLE KEYS */;
INSERT INTO `lesson_status_rule` (`id`, `status_code`, `status_name`, `default_should_deduct`, `deduct_mode`, `fixed_units`, `default_should_extend`, `allow_manual_override`, `status`, `remark`, `created_at`, `created_by`, `updated_at`, `updated_by`, `is_deleted`, `deleted_at`, `deleted_by`, `version`) VALUES (30001,'COMPLETED','Completed',1,'ACTUAL_UNITS',NULL,0,1,'ACTIVE','Completed lesson; deduct actual units','2026-05-14 23:52:38.473',NULL,'2026-05-15 00:07:21.554',1,0,NULL,NULL,1),(30002,'FORMAL_LEAVE','Formal leave',0,'NONE',NULL,1,1,'ACTIVE','Formal leave does not deduct units and extends the end date','2026-05-14 23:52:38.473',NULL,'2026-05-15 00:07:21.554',NULL,0,NULL,NULL,0),(30003,'TEMP_CANCEL','Temporary cancellation',0,'NONE',NULL,1,1,'ACTIVE','Temporary cancellation does not deduct units by default and can be overridden','2026-05-14 23:52:38.473',NULL,'2026-05-15 00:07:21.554',NULL,0,NULL,NULL,0),(30004,'NO_SHOW','No-show',1,'PLANNED_UNITS',NULL,0,1,'ACTIVE','No-show deducts planned units by default','2026-05-14 23:52:38.473',NULL,'2026-05-15 00:07:21.554',NULL,0,NULL,NULL,0),(30005,'TEACHER_CANCEL','Teacher cancellation',0,'NONE',NULL,1,1,'ACTIVE','Teacher cancellation does not deduct units and extends the end date','2026-05-14 23:52:38.473',NULL,'2026-05-15 00:07:21.554',NULL,0,NULL,NULL,0),(30006,'MAKEUP_COMPLETED','Makeup completed',1,'ACTUAL_UNITS',NULL,0,1,'ACTIVE','Makeup completed; deduct actual units','2026-05-14 23:52:38.473',NULL,'2026-05-15 00:07:21.554',NULL,0,NULL,NULL,0),(30007,'SUBSTITUTED','Substitution completed',1,'ACTUAL_UNITS',NULL,0,1,'ACTIVE','Substitution completed; deduct actual units','2026-05-14 23:52:38.473',NULL,'2026-05-15 00:07:21.554',NULL,0,NULL,NULL,0),(2054958630222540801,'CANCELLED','取消',0,'NONE',NULL,1,0,'ACTIVE','课程取消，不扣课时，顺延一节','2026-05-14 12:14:51.388',NULL,'2026-05-14 12:14:51.389',NULL,0,NULL,NULL,0);
/*!40000 ALTER TABLE `lesson_status_rule` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Dumping data for table `brand_config`
--

LOCK TABLES `brand_config` WRITE;
/*!40000 ALTER TABLE `brand_config` DISABLE KEYS */;
INSERT INTO `brand_config` (`id`, `brand_name`, `logo_url`, `theme_color`, `support_email`, `receipt_footer_text`, `status`, `created_at`, `created_by`, `updated_at`, `updated_by`, `is_deleted`, `deleted_at`, `deleted_by`, `version`) VALUES (40001,'JIN BEST TUTORING','','#18b58b','support@example.com','Thank you for choosing JIN BEST TUTORING.','ACTIVE','2026-05-14 23:52:38.474',NULL,'2026-05-15 00:07:21.554',1,0,NULL,NULL,1);
/*!40000 ALTER TABLE `brand_config` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Dumping data for table `notification_template`
--

LOCK TABLES `notification_template` WRITE;
/*!40000 ALTER TABLE `notification_template` DISABLE KEYS */;
INSERT INTO `notification_template` (`id`, `template_code`, `template_name`, `channel_type`, `subject_template`, `body_template`, `status`, `remark`, `created_at`, `created_by`, `updated_at`, `updated_by`, `is_deleted`, `deleted_at`, `deleted_by`, `version`) VALUES (1,'LESSON_CONFIRMED_DEFAULT','Lesson Confirmed - Default','EMAIL','${brandName} - Lesson Confirmed: ${studentName}','Dear Parent,\n\nThe lesson for ${studentName} has been confirmed.\n\nPackage: ${packageName}\nStatus: ${lessonStatus}\nHours deducted: ${deductHours}\nRemaining hours: ${remainingHours}\nEstimated completion: ${estimatedEndDate}\n\nBest regards,\n${brandName}','ACTIVE','Default email sent when lesson is marked as COMPLETED','2024-01-01 00:00:00.000',1,'2024-01-01 00:00:00.000',1,0,NULL,NULL,0),(2,'LOW_BALANCE_DEFAULT','Low Balance Warning - Default','EMAIL','${brandName} - Low Balance Warning: ${studentName}','Dear Parent,\n\nThis is to inform you that ${studentName}\'s course package is running low.\n\nPackage: ${packageName}\nRemaining hours: ${remainingHours}\n\nPlease consider purchasing additional hours to avoid interruption.\n\nBest regards,\n${brandName}','ACTIVE','Email sent when package balance drops below threshold','2024-01-01 00:00:00.000',1,'2024-01-01 00:00:00.000',1,0,NULL,NULL,0),(50001,'LESSON_BALANCE_EMAIL','Post-lesson remaining units notice','EMAIL','[${brandName}] ${studentName} lesson summary and remaining units','Dear parent,\n\nStudent: ${studentName}\nCourse package: ${packageName}\nLesson status: ${lessonStatus}\nUnits deducted this time: ${deductHours}\nRemaining units: ${remainingHours}\nEstimated end date: ${estimatedEndDate}\n\nIf anything looks incorrect, please contact the front desk team.\n\n${brandName}','ACTIVE','Remaining-unit email template sent to guardians after lesson confirmation','2026-05-14 23:52:38.474',NULL,'2026-05-15 00:07:21.554',NULL,0,NULL,NULL,0);
/*!40000 ALTER TABLE `notification_template` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Dumping data for table `notification_config`
--

LOCK TABLES `notification_config` WRITE;
/*!40000 ALTER TABLE `notification_config` DISABLE KEYS */;
INSERT INTO `notification_config` (`id`, `enabled`, `from_address`, `from_name`, `auth_code`, `strict_allowlist`, `allowed_recipients`, `low_balance_threshold`, `low_balance_recipients`, `created_at`, `created_by`, `updated_at`, `updated_by`, `is_deleted`, `deleted_at`, `deleted_by`, `version`) VALUES (1,0,'','JIN BEST TUTORING','',1,'[]',2,'[]','2026-05-15 01:51:38.000',NULL,'2026-05-15 01:51:38.000',NULL,0,NULL,NULL,0);
/*!40000 ALTER TABLE `notification_config` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Dumping data for table `notification_trigger_rule`
--

LOCK TABLES `notification_trigger_rule` WRITE;
/*!40000 ALTER TABLE `notification_trigger_rule` DISABLE KEYS */;
INSERT INTO `notification_trigger_rule` (`id`, `trigger_type`, `template_id`, `enabled`, `recipient_type`, `custom_recipients`, `threshold_value`, `remark`, `created_at`, `created_by`, `updated_at`, `updated_by`, `is_deleted`, `deleted_at`, `deleted_by`, `version`) VALUES (1,'LESSON_CONFIRMED',50001,0,'GUARDIAN_EMAIL','',4,'Send email to guardian after lesson confirmation','2026-05-15 01:51:38.000',NULL,'2026-05-15 01:51:38.000',1,0,NULL,NULL,18),(2,'LOW_BALANCE_HOUR',50001,0,'CUSTOM_EMAIL','',4,'Notify guardian when 1v1 remaining hour units drop below threshold (unit = 0.5h)','2026-05-15 01:51:38.000',NULL,'2026-05-15 01:51:38.000',1,0,NULL,NULL,16),(3,'LOW_BALANCE_SESSION',50001,0,'GUARDIAN_EMAIL','',2,'Notify guardian when GROUP PER_SESSION remaining sessions drop below threshold (unit = session)','2026-05-15 01:51:38.000',NULL,'2026-05-15 01:51:38.000',1,0,NULL,NULL,0),(4,'LOW_BALANCE_MONTH',50001,0,'GUARDIAN_EMAIL','',1,'Notify guardian when GROUP MONTHLY remaining months drop below threshold (unit = month)','2026-05-15 01:51:38.000',NULL,'2026-05-15 01:51:38.000',1,0,NULL,NULL,0);
/*!40000 ALTER TABLE `notification_trigger_rule` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Dumping data for table `payment_method`
--

LOCK TABLES `payment_method` WRITE;
/*!40000 ALTER TABLE `payment_method` DISABLE KEYS */;
INSERT INTO `payment_method` (`id`, `method_code`, `method_name`, `sort_order`, `status`, `created_at`, `created_by`, `updated_at`, `updated_by`, `is_deleted`, `deleted_at`, `deleted_by`, `version`) VALUES (1001,'CASH','Cash',1,'ACTIVE','2026-05-17 18:03:11.453',NULL,'2026-05-17 18:03:11.453',NULL,0,NULL,NULL,0),(1002,'CREDIT_CARD','Credit Card',2,'ACTIVE','2026-05-17 18:03:11.453',NULL,'2026-05-17 18:03:11.453',NULL,0,NULL,NULL,0),(1003,'DEBIT_CARD','Debit Card',3,'ACTIVE','2026-05-17 18:03:11.453',NULL,'2026-05-17 06:04:45.162',NULL,1,NULL,NULL,0),(1004,'BANK_TRANSFER','Bank Transfer',4,'ACTIVE','2026-05-17 18:03:11.453',NULL,'2026-05-17 06:04:46.589',NULL,1,NULL,NULL,0),(1005,'CHECK','Check',5,'ACTIVE','2026-05-17 18:03:11.453',NULL,'2026-05-17 18:03:11.453',NULL,0,NULL,NULL,0),(1006,'OTHER','Other',6,'ACTIVE','2026-05-17 18:03:11.453',NULL,'2026-05-17 18:03:11.453',NULL,0,NULL,NULL,0),(2055954183303540738,'WECHAT_PAY','wechat pay',0,'ACTIVE','2026-05-17 06:10:49.739',NULL,'2026-05-17 06:10:49.740',NULL,0,NULL,NULL,0);
/*!40000 ALTER TABLE `payment_method` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Dumping data for table `discount_preset`
--

LOCK TABLES `discount_preset` WRITE;
/*!40000 ALTER TABLE `discount_preset` DISABLE KEYS */;
INSERT INTO `discount_preset` (`id`, `preset_name`, `discount_type`, `discount_value`, `sort_order`, `status`, `is_deleted`, `created_at`, `created_by`, `updated_at`, `updated_by`, `deleted_at`, `deleted_by`, `version`) VALUES (1,'Sibling 10%','PERCENTAGE',10.00,1,'ACTIVE',0,'2026-05-17 16:18:42',NULL,'2026-05-17 16:18:42',NULL,NULL,NULL,0),(2,'Sibling 20%','PERCENTAGE',20.00,2,'ACTIVE',0,'2026-05-17 16:18:42',NULL,'2026-05-17 16:18:42',NULL,NULL,NULL,0),(3,'Early bird $50','FIXED',50.00,3,'ACTIVE',0,'2026-05-17 16:18:42',NULL,'2026-05-17 16:18:42',NULL,NULL,NULL,0),(4,'Referral $30','FIXED',30.00,4,'ACTIVE',0,'2026-05-17 16:18:42',NULL,'2026-05-17 16:18:42',NULL,NULL,NULL,0),(2055929652182618114,'1%discount','PERCENTAGE',1.00,0,'ACTIVE',0,'2026-05-17 04:33:21',NULL,'2026-05-17 04:33:21',NULL,NULL,NULL,0);
/*!40000 ALTER TABLE `discount_preset` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-05-24 21:52:15
-- MySQL dump 10.13  Distrib 5.7.24, for osx11.1 (x86_64)
--
-- Host: localhost    Database: class_deduction_system
-- ------------------------------------------------------
-- Server version	9.4.0

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!40101 SET NAMES utf8 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

--
-- Dumping data for table `sys_user`
--
-- WHERE:  username='admin'

LOCK TABLES `sys_user` WRITE;
/*!40000 ALTER TABLE `sys_user` DISABLE KEYS */;
INSERT INTO `sys_user` (`id`, `username`, `password_hash`, `user_type`, `display_name`, `mobile`, `email`, `teacher_id`, `student_id`, `status`, `last_login_at`, `remark`, `created_at`, `created_by`, `updated_at`, `updated_by`, `is_deleted`, `deleted_at`, `deleted_by`, `version`) VALUES (1,'admin','{bcrypt}$2b$12$zXiRuqYhdEukoa5UEO5sOuMXLD/g1KKQMKwWKAFdjK3QnnDVgFBei','ADMIN','System Admin',NULL,NULL,NULL,NULL,'ACTIVE','2026-05-24 08:55:21.609',NULL,'2026-05-14 23:55:35.000',NULL,'2026-05-14 23:55:35.000',NULL,0,NULL,NULL,236);
/*!40000 ALTER TABLE `sys_user` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-05-24 21:52:15
-- MySQL dump 10.13  Distrib 5.7.24, for osx11.1 (x86_64)
--
-- Host: localhost    Database: class_deduction_system
-- ------------------------------------------------------
-- Server version	9.4.0

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!40101 SET NAMES utf8 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

--
-- Dumping data for table `sys_user_role`
--
-- WHERE:  user_id=1 AND role_id=100

LOCK TABLES `sys_user_role` WRITE;
/*!40000 ALTER TABLE `sys_user_role` DISABLE KEYS */;
INSERT INTO `sys_user_role` (`id`, `user_id`, `role_id`, `created_at`) VALUES (1,1,100,'2026-05-14 23:55:35.000');
/*!40000 ALTER TABLE `sys_user_role` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-05-24 21:52:15
