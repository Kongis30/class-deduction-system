-- Required seed data for a clean class_deduction_system database.
-- Import after doc/init_schema_current.sql.
-- Admin login: admin / Jin2025 (bcrypt hash below).
-- Course matrix seed is loaded from doc/migrations/20260727_seed_ppt_course_matrix.sql.
USE `class_deduction_system`;

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
-- Dumping data for table `sys_role`
--

LOCK TABLES `sys_role` WRITE;
/*!40000 ALTER TABLE `sys_role` DISABLE KEYS */;
REPLACE INTO `sys_role` (`id`, `role_code`, `role_name`, `status`, `remark`, `created_at`, `created_by`, `updated_at`, `updated_by`, `is_deleted`, `deleted_at`, `deleted_by`, `version`) VALUES (100,'ADMIN','Administrator','ACTIVE','System administrator with full permissions','2026-05-14 23:52:38.467',NULL,'2026-05-15 00:07:21.552',NULL,0,NULL,NULL,0),(110,'STAFF','Staff','ACTIVE','Internal staff responsible for day-to-day operations','2026-05-14 23:52:38.467',NULL,'2026-05-15 00:07:21.552',NULL,0,NULL,NULL,0),(120,'TEACHER','Teacher','ACTIVE','Future role limited to viewing a personal timetable and confirming personal lessons','2026-05-14 23:52:38.467',NULL,'2026-05-15 00:07:21.552',NULL,0,NULL,NULL,0),(130,'STUDENT','Student','ACTIVE','Student who can access their own learning portal','2026-05-24 01:54:08.652',NULL,'2026-05-24 01:54:08.652',NULL,0,NULL,NULL,0);
/*!40000 ALTER TABLE `sys_role` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Dumping data for table `sys_dict_type`
--

LOCK TABLES `sys_dict_type` WRITE;
/*!40000 ALTER TABLE `sys_dict_type` DISABLE KEYS */;
REPLACE INTO `sys_dict_type` (`id`, `dict_code`, `dict_name`, `status`, `remark`, `created_at`, `created_by`, `updated_at`, `updated_by`, `is_deleted`, `deleted_at`, `deleted_by`, `version`) VALUES (1000,'USER_TYPE','User type','ACTIVE','System account user type','2026-05-14 23:52:38.470',NULL,'2026-05-15 00:07:21.552',NULL,0,NULL,NULL,0),(1010,'USER_STATUS','User status','ACTIVE','System account status','2026-05-14 23:52:38.470',NULL,'2026-05-15 00:07:21.552',NULL,0,NULL,NULL,0),(1020,'STUDENT_STATUS','Student status','ACTIVE','Student status','2026-05-14 23:52:38.470',NULL,'2026-05-15 00:07:21.552',NULL,0,NULL,NULL,0),(1030,'TEACHER_STATUS','Teacher status','ACTIVE','Teacher status','2026-05-14 23:52:38.470',NULL,'2026-05-15 00:07:21.552',NULL,0,NULL,NULL,0),(1040,'CLASSROOM_STATUS','Classroom status','ACTIVE','Classroom status','2026-05-14 23:52:38.470',NULL,'2026-05-15 00:07:21.552',NULL,0,NULL,NULL,0),(1050,'COURSE_PACKAGE_STATUS','Course package status','ACTIVE','Course package lifecycle status','2026-05-14 23:52:38.470',NULL,'2026-05-15 00:07:21.552',NULL,0,NULL,NULL,0),(1060,'PAYMENT_STATUS','Payment status','ACTIVE','Payment status','2026-05-14 23:52:38.470',NULL,'2026-05-15 00:07:21.552',NULL,0,NULL,NULL,0),(1070,'LESSON_PLANNING_STATUS','Lesson planning status','ACTIVE','Scheduling status','2026-05-14 23:52:38.470',NULL,'2026-05-15 00:07:21.552',NULL,0,NULL,NULL,0),(1080,'LESSON_RESULT_STATUS','Lesson result status','ACTIVE','Lesson result status','2026-05-14 23:52:38.470',NULL,'2026-05-15 00:07:21.552',NULL,0,NULL,NULL,0),(1090,'LESSON_LEDGER_TYPE','Unit ledger type','ACTIVE','Unit ledger type','2026-05-14 23:52:38.470',NULL,'2026-05-15 00:07:21.552',NULL,0,NULL,NULL,0),(1100,'TEACHING_MODE','Teaching mode','ACTIVE','Course delivery mode','2026-05-14 23:52:38.470',NULL,'2026-05-15 00:07:21.552',NULL,0,NULL,NULL,0);
/*!40000 ALTER TABLE `sys_dict_type` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Dumping data for table `sys_dict_item`
--

LOCK TABLES `sys_dict_item` WRITE;
/*!40000 ALTER TABLE `sys_dict_item` DISABLE KEYS */;
REPLACE INTO `sys_dict_item` (`id`, `dict_type_id`, `item_code`, `item_name`, `item_value`, `sort_order`, `is_default`, `status`, `remark`, `created_at`, `created_by`, `updated_at`, `updated_by`, `is_deleted`, `deleted_at`, `deleted_by`, `version`) VALUES (10001,1000,'ADMIN','Administrator','ADMIN',10,0,'ACTIVE','Administrator account','2026-05-14 23:52:38.471',NULL,'2026-05-15 00:07:21.553',NULL,0,NULL,NULL,0),(10002,1000,'STAFF','Staff','STAFF',20,1,'ACTIVE','Staff account','2026-05-14 23:52:38.471',NULL,'2026-05-15 00:07:21.553',NULL,0,NULL,NULL,0),(10003,1000,'TEACHER','Teacher','TEACHER',30,0,'ACTIVE','Teacher account','2026-05-14 23:52:38.471',NULL,'2026-05-15 00:07:21.553',NULL,0,NULL,NULL,0),(10101,1010,'ACTIVE','Active','ACTIVE',10,1,'ACTIVE','Can log in normally','2026-05-14 23:52:38.471',NULL,'2026-05-15 00:07:21.553',NULL,0,NULL,NULL,0),(10102,1010,'DISABLED','Disabled','DISABLED',20,0,'ACTIVE','Account is disabled','2026-05-14 23:52:38.471',NULL,'2026-05-15 00:07:21.553',NULL,0,NULL,NULL,0),(10103,1010,'LOCKED','Locked','LOCKED',30,0,'ACTIVE','Account is locked','2026-05-14 23:52:38.471',NULL,'2026-05-15 00:07:21.553',NULL,0,NULL,NULL,0),(10201,1020,'ACTIVE','Enrolled','ACTIVE',10,1,'ACTIVE','Student is actively enrolled','2026-05-14 23:52:38.471',NULL,'2026-05-15 00:07:21.553',NULL,0,NULL,NULL,0),(10202,1020,'INACTIVE','Inactive','INACTIVE',20,0,'ACTIVE','Student is no longer active','2026-05-14 23:52:38.471',NULL,'2026-05-15 00:07:21.553',NULL,0,NULL,NULL,0),(10301,1030,'ACTIVE','Active','ACTIVE',10,1,'ACTIVE','Teacher is available for scheduling','2026-05-14 23:52:38.471',NULL,'2026-05-15 00:07:21.553',NULL,0,NULL,NULL,0),(10302,1030,'INACTIVE','Inactive','INACTIVE',20,0,'ACTIVE','Teacher is temporarily unavailable for scheduling','2026-05-14 23:52:38.471',NULL,'2026-05-15 00:07:21.553',NULL,0,NULL,NULL,0),(10303,1030,'DISABLED','Disabled','DISABLED',30,0,'ACTIVE','Account or profile is disabled','2026-05-14 23:52:38.471',NULL,'2026-05-15 00:07:21.553',NULL,0,NULL,NULL,0),(10401,1040,'ACTIVE','Active','ACTIVE',10,1,'ACTIVE','Classroom is available for scheduling','2026-05-14 23:52:38.471',NULL,'2026-05-15 00:07:21.553',NULL,0,NULL,NULL,0),(10402,1040,'DISABLED','Inactive','DISABLED',20,0,'ACTIVE','Classroom is inactive','2026-05-14 23:52:38.471',NULL,'2026-05-15 00:07:21.553',NULL,0,NULL,NULL,0),(10501,1050,'DRAFT','Draft','DRAFT',10,0,'ACTIVE','Draft package','2026-05-14 23:52:38.471',NULL,'2026-05-15 00:07:21.553',NULL,0,NULL,NULL,0),(10502,1050,'ACTIVE','Active','ACTIVE',20,1,'ACTIVE','Package is active','2026-05-14 23:52:38.471',NULL,'2026-05-15 00:07:21.553',NULL,0,NULL,NULL,0),(10503,1050,'COMPLETED','Completed','COMPLETED',30,0,'ACTIVE','Package units are fully consumed','2026-05-14 23:52:38.471',NULL,'2026-05-15 00:07:21.553',NULL,0,NULL,NULL,0),(10504,1050,'CLOSED','Closed','CLOSED',40,0,'ACTIVE','Closed manually','2026-05-14 23:52:38.471',NULL,'2026-05-15 00:07:21.553',NULL,0,NULL,NULL,0),(10601,1060,'UNPAID','Unpaid','UNPAID',10,1,'ACTIVE','Payment not received yet','2026-05-14 23:52:38.471',NULL,'2026-05-15 00:07:21.553',NULL,0,NULL,NULL,0),(10602,1060,'PARTIAL','Partially paid','PARTIAL',20,0,'ACTIVE','Payment partially collected','2026-05-14 23:52:38.471',NULL,'2026-05-15 00:07:21.553',NULL,0,NULL,NULL,0),(10603,1060,'PAID','Paid','PAID',30,0,'ACTIVE','Payment collected','2026-05-14 23:52:38.471',NULL,'2026-05-15 00:07:21.553',NULL,0,NULL,NULL,0),(10701,1070,'SCHEDULED','Scheduled','SCHEDULED',10,1,'ACTIVE','Regular planned lesson','2026-05-14 23:52:38.471',NULL,'2026-05-15 00:07:21.553',NULL,0,NULL,NULL,0),(10702,1070,'ADJUSTED','Adjusted','ADJUSTED',20,0,'ACTIVE','Adjusted planned lesson','2026-05-14 23:52:38.471',NULL,'2026-05-15 00:07:21.553',NULL,0,NULL,NULL,0),(10703,1070,'CANCELLED','Cancelled','CANCELLED',30,0,'ACTIVE','Cancelled planned lesson','2026-05-14 23:52:38.471',NULL,'2026-05-15 00:07:21.553',NULL,0,NULL,NULL,0),(10704,1070,'MERGED','Merged','MERGED',40,0,'ACTIVE','Merged lesson','2026-05-14 23:52:38.471',NULL,'2026-05-15 00:07:21.553',NULL,0,NULL,NULL,0),(10801,1080,'COMPLETED','Completed','COMPLETED',10,1,'ACTIVE','Lesson completed normally','2026-05-14 23:52:38.471',NULL,'2026-05-15 00:07:21.553',NULL,0,NULL,NULL,0),(10802,1080,'FORMAL_LEAVE','Formal leave','FORMAL_LEAVE',20,0,'ACTIVE','Formal leave does not deduct units','2026-05-14 23:52:38.471',NULL,'2026-05-15 00:07:21.553',NULL,0,NULL,NULL,0),(10803,1080,'TEMP_CANCEL','Temporary cancellation','TEMP_CANCEL',30,0,'ACTIVE','Temporary cancellation with configurable deduction behavior','2026-05-14 23:52:38.471',NULL,'2026-05-15 00:07:21.553',NULL,0,NULL,NULL,0),(10804,1080,'NO_SHOW','No-show','NO_SHOW',40,0,'ACTIVE','Student no-show deducts units by default','2026-05-14 23:52:38.471',NULL,'2026-05-15 00:07:21.553',NULL,0,NULL,NULL,0),(10805,1080,'TEACHER_CANCEL','Teacher cancellation','TEACHER_CANCEL',50,0,'ACTIVE','Teacher cancellation does not deduct units by default','2026-05-14 23:52:38.471',NULL,'2026-05-15 00:07:21.553',NULL,0,NULL,NULL,0),(10806,1080,'MAKEUP_COMPLETED','Makeup completed','MAKEUP_COMPLETED',60,0,'ACTIVE','Makeup completed','2026-05-14 23:52:38.471',NULL,'2026-05-15 00:07:21.553',NULL,0,NULL,NULL,0),(10807,1080,'SUBSTITUTED','Substitution completed','SUBSTITUTED',70,0,'ACTIVE','Substitution completed','2026-05-14 23:52:38.471',NULL,'2026-05-15 00:07:21.553',NULL,0,NULL,NULL,0),(10901,1090,'INITIAL_ALLOCATE','Initial allocation','INITIAL_ALLOCATE',10,1,'ACTIVE','Initial course package allocation','2026-05-14 23:52:38.471',NULL,'2026-05-15 00:07:21.553',NULL,0,NULL,NULL,0),(10902,1090,'DEDUCT','Deduction','DEDUCT',20,0,'ACTIVE','Standard unit deduction','2026-05-14 23:52:38.471',NULL,'2026-05-15 00:07:21.553',NULL,0,NULL,NULL,0),(10903,1090,'RESTORE','Restore','RESTORE',30,0,'ACTIVE','Restore units','2026-05-14 23:52:38.471',NULL,'2026-05-15 00:07:21.553',NULL,0,NULL,NULL,0),(10904,1090,'MANUAL_ADJUST','Manual adjustment','MANUAL_ADJUST',40,0,'ACTIVE','Manual unit adjustment','2026-05-14 23:52:38.471',NULL,'2026-05-15 00:07:21.553',NULL,0,NULL,NULL,0),(10905,1090,'NO_DEDUCT_AUDIT','No-deduct audit','NO_DEDUCT_AUDIT',50,0,'ACTIVE','No deduction but keep the audit record','2026-05-14 23:52:38.471',NULL,'2026-05-15 00:07:21.553',NULL,0,NULL,NULL,0),(11001,1100,'ONE_ON_ONE','One-on-one','ONE_ON_ONE',10,1,'ACTIVE','One-on-one course','2026-05-14 23:52:38.471',NULL,'2026-05-15 00:07:21.553',NULL,0,NULL,NULL,0),(11002,1100,'GROUP','Group','GROUP',20,0,'ACTIVE','Group course','2026-05-14 23:52:38.471',NULL,'2026-05-15 00:07:21.553',NULL,0,NULL,NULL,0);
/*!40000 ALTER TABLE `sys_dict_item` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Dumping data for table `sys_permission`
--

LOCK TABLES `sys_permission` WRITE;
/*!40000 ALTER TABLE `sys_permission` DISABLE KEYS */;
REPLACE INTO `sys_permission` (`id`, `permission_code`, `permission_name`, `permission_type`, `parent_id`, `path`, `method`, `status`, `remark`, `created_at`, `created_by`, `updated_at`, `updated_by`, `is_deleted`, `deleted_at`, `deleted_by`, `version`) VALUES (10000,'dashboard','Dashboard','MENU',NULL,'/dashboard',NULL,'ACTIVE','Top-level menu: Dashboard','2026-05-14 23:52:38.472',NULL,'2026-05-15 00:07:21.553',NULL,0,NULL,NULL,0),(11000,'base_data','Base data','MENU',NULL,'/base-data',NULL,'ACTIVE','Top-level menu: Base data','2026-05-14 23:52:38.472',NULL,'2026-05-15 00:07:21.553',NULL,0,NULL,NULL,0),(11010,'student','Student profiles','MENU',11000,'/base-data/students',NULL,'ACTIVE','Second-level menu: Student profiles','2026-05-14 23:52:38.472',NULL,'2026-05-15 00:07:21.553',NULL,0,NULL,NULL,0),(11020,'teacher','Teacher profiles','MENU',11000,'/base-data/teachers',NULL,'ACTIVE','Second-level menu: Teacher profiles','2026-05-14 23:52:38.472',NULL,'2026-05-15 00:07:21.553',NULL,0,NULL,NULL,0),(11030,'classroom','Classroom management','MENU',11000,'/base-data/classrooms',NULL,'ACTIVE','Second-level menu: Classroom management','2026-05-14 23:52:38.472',NULL,'2026-05-15 00:07:21.553',NULL,0,NULL,NULL,0),(11040,'course_product','Course products','MENU',11000,'/base-data/course-products',NULL,'ACTIVE','Second-level menu: Course products','2026-05-14 23:52:38.472',NULL,'2026-05-15 00:07:21.553',NULL,0,NULL,NULL,0),(12000,'enrollment_package','Enrollment and packages','MENU',NULL,'/enrollment',NULL,'ACTIVE','Top-level menu: Enrollment and packages','2026-05-14 23:52:38.472',NULL,'2026-05-15 00:07:21.553',NULL,0,NULL,NULL,0),(12010,'enrollment_order','Enrollment orders','MENU',12000,'/enrollment/orders',NULL,'ACTIVE','Second-level menu: Enrollment orders','2026-05-14 23:52:38.472',NULL,'2026-05-15 00:07:21.553',NULL,0,NULL,NULL,0),(12020,'course_package','Course package management','MENU',12000,'/enrollment/packages',NULL,'ACTIVE','Second-level menu: Course package management','2026-05-14 23:52:38.472',NULL,'2026-05-15 00:07:21.553',NULL,0,NULL,NULL,0),(12030,'receipt_record','Receipt management','MENU',12000,'/enrollment/receipts',NULL,'ACTIVE','Second-level menu: Receipt management','2026-05-14 23:52:38.472',NULL,'2026-05-15 00:07:21.553',NULL,0,NULL,NULL,0),(13000,'schedule_center','Scheduling','MENU',NULL,'/schedule',NULL,'ACTIVE','Top-level menu: Scheduling','2026-05-14 23:52:38.472',NULL,'2026-05-15 00:07:21.553',NULL,0,NULL,NULL,0),(13010,'recurring_schedule','Recurring scheduling','MENU',13000,'/schedule/recurring',NULL,'ACTIVE','Second-level menu: Recurring scheduling','2026-05-14 23:52:38.472',NULL,'2026-05-15 00:07:21.553',NULL,0,NULL,NULL,0),(13020,'adjustment','Reschedules and makeup','MENU',13000,'/schedule/adjustments',NULL,'ACTIVE','Second-level menu: Reschedules and makeup','2026-05-14 23:52:38.472',NULL,'2026-05-15 00:07:21.553',NULL,0,NULL,NULL,0),(13030,'teacher_schedule','Teacher timetable','MENU',13000,'/schedule/teacher',NULL,'ACTIVE','Second-level menu: Teacher timetable','2026-05-14 23:52:38.472',NULL,'2026-05-15 00:07:21.553',NULL,0,NULL,NULL,0),(13040,'classroom_occupancy','Classroom occupancy','MENU',13000,'/schedule/classrooms',NULL,'ACTIVE','Second-level menu: Classroom occupancy','2026-05-14 23:52:38.472',NULL,'2026-05-15 00:07:21.553',NULL,0,NULL,NULL,0),(14000,'lesson_deduction','Lessons and deduction','MENU',NULL,'/lessons',NULL,'ACTIVE','Top-level menu: Lessons and deduction','2026-05-14 23:52:38.472',NULL,'2026-05-15 00:07:21.553',NULL,0,NULL,NULL,0),(14010,'lesson_record','Lesson records','MENU',14000,'/lessons/records',NULL,'ACTIVE','Second-level menu: Lesson records','2026-05-14 23:52:38.472',NULL,'2026-05-15 00:07:21.553',NULL,0,NULL,NULL,0),(14020,'lesson_exception','Exception records','MENU',14000,'/lessons/exceptions',NULL,'ACTIVE','Second-level menu: Exception records','2026-05-14 23:52:38.472',NULL,'2026-05-15 00:07:21.553',NULL,0,NULL,NULL,0),(14030,'hour_ledger','Deduction ledger','MENU',14000,'/lessons/ledger',NULL,'ACTIVE','Second-level menu: Deduction ledger','2026-05-14 23:52:38.472',NULL,'2026-05-15 00:07:21.553',NULL,0,NULL,NULL,0),(14040,'package_balance','Remaining units','MENU',14000,'/lessons/balance',NULL,'ACTIVE','Second-level menu: Remaining units','2026-05-14 23:52:38.472',NULL,'2026-05-15 00:07:21.553',NULL,0,NULL,NULL,0),(15000,'system_setting','System settings','MENU',NULL,'/system',NULL,'ACTIVE','Top-level menu: System settings','2026-05-14 23:52:38.472',NULL,'2026-05-15 00:07:21.553',NULL,0,NULL,NULL,0),(15010,'lesson_status_rule','Lesson status rules','MENU',15000,'/system/rules',NULL,'ACTIVE','Second-level menu: Lesson status rules','2026-05-14 23:52:38.472',NULL,'2026-05-15 00:07:21.553',NULL,0,NULL,NULL,0),(15020,'notification_template','Notification templates','MENU',15000,'/system/notifications',NULL,'ACTIVE','Second-level menu: Notification templates','2026-05-14 23:52:38.472',NULL,'2026-05-15 00:07:21.553',NULL,0,NULL,NULL,0),(15030,'brand_config','Brand settings','MENU',15000,'/system/brand',NULL,'ACTIVE','Second-level menu: Brand settings','2026-05-14 23:52:38.472',NULL,'2026-05-15 00:07:21.553',NULL,0,NULL,NULL,0),(15040,'account_permission','Accounts and permissions','MENU',15000,'/system/accounts',NULL,'ACTIVE','Second-level menu: Accounts and permissions','2026-05-14 23:52:38.472',NULL,'2026-05-15 00:07:21.553',NULL,0,NULL,NULL,0),(16010,'teacher:self:schedule:view','Teacher view own timetable','ACTION',NULL,NULL,NULL,'ACTIVE','Teacher-side extension permission','2026-05-14 23:52:38.472',NULL,'2026-05-15 00:07:21.553',NULL,0,NULL,NULL,0),(16020,'teacher:self:lesson:confirm','Teacher confirm own lessons','ACTION',NULL,NULL,NULL,'ACTIVE','Teacher-side extension permission','2026-05-14 23:52:38.472',NULL,'2026-05-15 00:07:21.553',NULL,0,NULL,NULL,0);
/*!40000 ALTER TABLE `sys_permission` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Dumping data for table `sys_role_permission`
--

LOCK TABLES `sys_role_permission` WRITE;
/*!40000 ALTER TABLE `sys_role_permission` DISABLE KEYS */;
REPLACE INTO `sys_role_permission` (`id`, `role_id`, `permission_id`, `created_at`) VALUES (20001,100,10000,'2026-05-14 23:52:38.473'),(20002,100,11000,'2026-05-14 23:52:38.473'),(20003,100,11010,'2026-05-14 23:52:38.473'),(20004,100,11020,'2026-05-14 23:52:38.473'),(20005,100,11030,'2026-05-14 23:52:38.473'),(20006,100,11040,'2026-05-14 23:52:38.473'),(20007,100,12000,'2026-05-14 23:52:38.473'),(20008,100,12010,'2026-05-14 23:52:38.473'),(20009,100,12020,'2026-05-14 23:52:38.473'),(20010,100,12030,'2026-05-14 23:52:38.473'),(20011,100,13000,'2026-05-14 23:52:38.473'),(20012,100,13010,'2026-05-14 23:52:38.473'),(20013,100,13020,'2026-05-14 23:52:38.473'),(20014,100,13030,'2026-05-14 23:52:38.473'),(20015,100,13040,'2026-05-14 23:52:38.473'),(20016,100,14000,'2026-05-14 23:52:38.473'),(20017,100,14010,'2026-05-14 23:52:38.473'),(20018,100,14020,'2026-05-14 23:52:38.473'),(20019,100,14030,'2026-05-14 23:52:38.473'),(20020,100,14040,'2026-05-14 23:52:38.473'),(20021,100,15000,'2026-05-14 23:52:38.473'),(20022,100,15010,'2026-05-14 23:52:38.473'),(20023,100,15020,'2026-05-14 23:52:38.473'),(20024,100,15030,'2026-05-14 23:52:38.473'),(20025,100,15040,'2026-05-14 23:52:38.473'),(20026,100,16010,'2026-05-14 23:52:38.473'),(20027,100,16020,'2026-05-14 23:52:38.473'),(20101,110,10000,'2026-05-14 23:52:38.473'),(20102,110,11000,'2026-05-14 23:52:38.473'),(20103,110,11010,'2026-05-14 23:52:38.473'),(20104,110,11020,'2026-05-14 23:52:38.473'),(20105,110,11030,'2026-05-14 23:52:38.473'),(20106,110,11040,'2026-05-14 23:52:38.473'),(20107,110,12000,'2026-05-14 23:52:38.473'),(20108,110,12010,'2026-05-14 23:52:38.473'),(20109,110,12020,'2026-05-14 23:52:38.473'),(20110,110,12030,'2026-05-14 23:52:38.473'),(20111,110,13000,'2026-05-14 23:52:38.473'),(20112,110,13010,'2026-05-14 23:52:38.473'),(20113,110,13020,'2026-05-14 23:52:38.473'),(20114,110,13030,'2026-05-14 23:52:38.473'),(20115,110,13040,'2026-05-14 23:52:38.473'),(20116,110,14000,'2026-05-14 23:52:38.473'),(20117,110,14010,'2026-05-14 23:52:38.473'),(20118,110,14020,'2026-05-14 23:52:38.473'),(20119,110,14030,'2026-05-14 23:52:38.473'),(20120,110,14040,'2026-05-14 23:52:38.473'),(20201,120,16010,'2026-05-14 23:52:38.473'),(20202,120,16020,'2026-05-14 23:52:38.473');
/*!40000 ALTER TABLE `sys_role_permission` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Dumping data for table `lesson_status_rule`
--

LOCK TABLES `lesson_status_rule` WRITE;
/*!40000 ALTER TABLE `lesson_status_rule` DISABLE KEYS */;
REPLACE INTO `lesson_status_rule` (`id`, `status_code`, `status_name`, `default_should_deduct`, `deduct_mode`, `fixed_units`, `default_should_extend`, `allow_manual_override`, `status`, `remark`, `created_at`, `created_by`, `updated_at`, `updated_by`, `is_deleted`, `deleted_at`, `deleted_by`, `version`) VALUES (30001,'COMPLETED','Completed',1,'ACTUAL_UNITS',NULL,0,1,'ACTIVE','Completed lesson; deduct actual units','2026-05-14 23:52:38.473',NULL,'2026-05-15 00:07:21.554',1,0,NULL,NULL,1),(30002,'FORMAL_LEAVE','Formal leave',0,'NONE',NULL,1,1,'ACTIVE','Formal leave does not deduct units and extends the end date','2026-05-14 23:52:38.473',NULL,'2026-05-15 00:07:21.554',NULL,0,NULL,NULL,0),(30003,'TEMP_CANCEL','Temporary cancellation',1,'PLANNED_UNITS',NULL,0,0,'ACTIVE','Student cancellation, deduct planned units','2026-05-14 23:52:38.473',NULL,'2026-07-29 13:31:36.606',NULL,0,NULL,NULL,0),(30004,'NO_SHOW','No-show',1,'PLANNED_UNITS',NULL,0,1,'ACTIVE','No-show deducts planned units by default','2026-05-14 23:52:38.473',NULL,'2026-05-15 00:07:21.554',NULL,0,NULL,NULL,0),(30005,'TEACHER_CANCEL','Teacher cancellation',0,'NONE',NULL,1,1,'ACTIVE','Teacher cancellation does not deduct units and extends the end date','2026-05-14 23:52:38.473',NULL,'2026-05-15 00:07:21.554',NULL,0,NULL,NULL,0),(30006,'MAKEUP_COMPLETED','Makeup completed',1,'ACTUAL_UNITS',NULL,0,1,'ACTIVE','Makeup completed; deduct actual units','2026-05-14 23:52:38.473',NULL,'2026-05-15 00:07:21.554',NULL,0,NULL,NULL,0),(30007,'SUBSTITUTED','Substitution completed',1,'ACTUAL_UNITS',NULL,0,1,'ACTIVE','Substitution completed; deduct actual units','2026-05-14 23:52:38.473',NULL,'2026-05-15 00:07:21.554',NULL,0,NULL,NULL,0),(2054958630222540801,'CANCELLED','取消',1,'PLANNED_UNITS',NULL,0,0,'ACTIVE','Student cancellation, deduct planned units','2026-05-14 12:14:51.388',NULL,'2026-07-29 13:31:36.606',NULL,0,NULL,NULL,0);
/*!40000 ALTER TABLE `lesson_status_rule` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Dumping data for table `brand_config`
--

LOCK TABLES `brand_config` WRITE;
/*!40000 ALTER TABLE `brand_config` DISABLE KEYS */;
REPLACE INTO `brand_config` (`id`, `brand_name`, `logo_url`, `theme_color`, `support_email`, `receipt_footer_text`, `status`, `created_at`, `created_by`, `updated_at`, `updated_by`, `is_deleted`, `deleted_at`, `deleted_by`, `version`) VALUES (40001,'JIN BEST TUTORING','','#18b58b','support@example.com','Thank you for choosing JIN BEST TUTORING.','ACTIVE','2026-05-14 23:52:38.474',NULL,'2026-05-15 00:07:21.554',1,0,NULL,NULL,1);
/*!40000 ALTER TABLE `brand_config` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Dumping data for table `notification_template`
--

LOCK TABLES `notification_template` WRITE;
/*!40000 ALTER TABLE `notification_template` DISABLE KEYS */;
REPLACE INTO `notification_template` (`id`, `template_code`, `template_name`, `channel_type`, `subject_template`, `body_template`, `status`, `remark`, `created_at`, `created_by`, `updated_at`, `updated_by`, `is_deleted`, `deleted_at`, `deleted_by`, `version`) VALUES (1,'LESSON_CONFIRMED_DEFAULT','Lesson Confirmed - Default','EMAIL','${brandName} - Lesson Confirmed: ${studentName}','Dear Parent,\n\nThe lesson for ${studentName} has been confirmed.\n\nPackage: ${packageName}\nStatus: ${lessonStatus}\nHours deducted: ${deductHours}\nRemaining hours: ${remainingHours}\nEstimated completion: ${estimatedEndDate}\n\nBest regards,\n${brandName}','ACTIVE','Default email sent when lesson is marked as COMPLETED','2024-01-01 00:00:00.000',1,'2024-01-01 00:00:00.000',1,0,NULL,NULL,0),(2,'LOW_BALANCE_DEFAULT','Low Balance Warning - Default','EMAIL','${brandName} - Low Balance Warning: ${studentName}','Dear Parent,\n\nThis is to inform you that ${studentName}\'s course package is running low.\n\nPackage: ${packageName}\nRemaining hours: ${remainingHours}\n\nPlease consider purchasing additional hours to avoid interruption.\n\nBest regards,\n${brandName}','ACTIVE','Email sent when package balance drops below threshold','2024-01-01 00:00:00.000',1,'2024-01-01 00:00:00.000',1,0,NULL,NULL,0),(50001,'LESSON_BALANCE_EMAIL','Post-lesson remaining units notice','EMAIL','[${brandName}] ${studentName} lesson summary and remaining units','Dear parent,\n\nStudent: ${studentName}\nCourse package: ${packageName}\nLesson status: ${lessonStatus}\nUnits deducted this time: ${deductHours}\nRemaining units: ${remainingHours}\nEstimated end date: ${estimatedEndDate}\n\nIf anything looks incorrect, please contact the front desk team.\n\n${brandName}','ACTIVE','Remaining-unit email template sent to guardians after lesson confirmation','2026-05-14 23:52:38.474',NULL,'2026-05-15 00:07:21.554',1,0,NULL,NULL,1);
/*!40000 ALTER TABLE `notification_template` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Dumping data for table `notification_trigger_rule`
--

LOCK TABLES `notification_trigger_rule` WRITE;
/*!40000 ALTER TABLE `notification_trigger_rule` DISABLE KEYS */;
REPLACE INTO `notification_trigger_rule` (`id`, `trigger_type`, `template_id`, `enabled`, `recipient_type`, `custom_recipients`, `threshold_value`, `remark`, `created_at`, `created_by`, `updated_at`, `updated_by`, `is_deleted`, `deleted_at`, `deleted_by`, `version`) VALUES (1,'LESSON_CONFIRMED',50001,0,'GUARDIAN_EMAIL','',4,'Send email to guardian after lesson confirmation','2026-05-15 01:51:38.000',NULL,'2026-05-15 01:51:38.000',1,0,NULL,NULL,18),(2,'LOW_BALANCE_HOUR',50001,1,'CUSTOM_EMAIL','',4,'Notify guardian when 1v1 remaining hour units drop below threshold (unit = 0.5h)','2026-05-15 01:51:38.000',NULL,'2026-06-14 10:59:18.321',1,0,NULL,NULL,17),(3,'LOW_BALANCE_SESSION',2,0,'GUARDIAN_EMAIL','',3,'Notify guardian when GROUP PER_SESSION remaining sessions drop below threshold (unit = session)','2026-06-14 10:59:18.350',NULL,'2026-06-14 10:59:18.350',1,0,NULL,NULL,1),(4,'LOW_BALANCE_MONTH',2,1,'GUARDIAN_EMAIL','',2,'Notify guardian when GROUP MONTHLY remaining months drop below threshold (unit = month)','2026-06-14 10:59:18.353',NULL,'2026-06-14 10:59:18.353',1,0,NULL,NULL,2),(2080921058378960898,'RENEWAL_EXPIRY_DAYS',NULL,0,'GUARDIAN_EMAIL',NULL,30,'Days before expected end date','2026-07-25 03:40:16.619',1,'2026-07-25 03:40:16.619',1,0,NULL,NULL,1),(2080921058429292545,'RENEWAL_ONE_ON_ONE_LESSONS',NULL,0,'GUARDIAN_EMAIL',NULL,2,'One-on-one lessons remaining','2026-07-25 03:40:16.631',1,'2026-07-25 03:40:16.631',1,0,NULL,NULL,1);
/*!40000 ALTER TABLE `notification_trigger_rule` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Dumping data for table `payment_method`
--

LOCK TABLES `payment_method` WRITE;
/*!40000 ALTER TABLE `payment_method` DISABLE KEYS */;
REPLACE INTO `payment_method` (`id`, `method_code`, `method_name`, `sort_order`, `status`, `created_at`, `created_by`, `updated_at`, `updated_by`, `is_deleted`, `deleted_at`, `deleted_by`, `version`) VALUES (1001,'CASH','Cash',1,'ACTIVE','2026-05-17 18:03:11.453',NULL,'2026-05-17 18:03:11.453',NULL,0,NULL,NULL,0),(1002,'CREDIT_CARD','Credit Card',2,'ACTIVE','2026-05-17 18:03:11.453',NULL,'2026-05-17 18:03:11.453',NULL,0,NULL,NULL,0),(1003,'DEBIT_CARD','Debit Card',3,'ACTIVE','2026-05-17 18:03:11.453',NULL,'2026-05-17 06:04:45.162',NULL,1,NULL,NULL,0),(1004,'BANK_TRANSFER','Bank Transfer',4,'ACTIVE','2026-05-17 18:03:11.453',NULL,'2026-05-17 06:04:46.589',NULL,1,NULL,NULL,0),(1005,'CHECK','Check',5,'ACTIVE','2026-05-17 18:03:11.453',NULL,'2026-05-17 18:03:11.453',NULL,0,NULL,NULL,0),(1006,'OTHER','Other',6,'ACTIVE','2026-05-17 18:03:11.453',NULL,'2026-05-17 18:03:11.453',NULL,0,NULL,NULL,0),(2055954183303540738,'WECHAT_PAY','wechat pay',0,'ACTIVE','2026-05-17 06:10:49.739',NULL,'2026-05-17 06:10:49.740',NULL,0,NULL,NULL,0),(2080601870154256386,'ZELLE','Zelle',20,'ACTIVE','2026-07-24 06:31:56.216',NULL,'2026-07-24 06:31:56.217',NULL,0,NULL,NULL,0),(2081708359868559361,'TRANSFER','Transfer',30,'ACTIVE','2026-07-27 07:48:43.920',NULL,'2026-07-27 07:48:43.920',NULL,0,NULL,NULL,0);
/*!40000 ALTER TABLE `payment_method` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Dumping data for table `discount_preset`
--

LOCK TABLES `discount_preset` WRITE;
/*!40000 ALTER TABLE `discount_preset` DISABLE KEYS */;
REPLACE INTO `discount_preset` (`id`, `preset_name`, `discount_type`, `discount_value`, `sort_order`, `status`, `is_deleted`, `created_at`, `created_by`, `updated_at`, `updated_by`, `deleted_at`, `deleted_by`, `version`) VALUES (2080875960911974402,'10%','PERCENTAGE',10.00,0,'ACTIVE',0,'2026-07-25 00:41:05',NULL,'2026-07-25 00:41:05',NULL,NULL,NULL,0);
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

-- Dump completed on 2026-07-30 18:50:37

-- Notification transport is intentionally disabled in seed data. Configure SMTP per environment.
REPLACE INTO `notification_config` (
  `id`, `enabled`, `from_address`, `from_name`, `auth_code`, `strict_allowlist`, `allowed_recipients`,
  `smtp_host`, `smtp_port`, `smtp_username`, `smtp_password`, `low_balance_threshold`, `low_balance_recipients`,
  `is_deleted`, `version`
) VALUES (
  1, 0, '', 'JIN BEST TUTORING', '', 1, '[]', '', 587, '', '', 2, '[]', 0, 0
);

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
-- Dumping data for table `sys_user`
--
-- WHERE:  username='admin'

LOCK TABLES `sys_user` WRITE;
/*!40000 ALTER TABLE `sys_user` DISABLE KEYS */;
REPLACE INTO `sys_user` (`id`, `username`, `password_hash`, `user_type`, `display_name`, `mobile`, `email`, `teacher_id`, `student_id`, `status`, `last_login_at`, `remark`, `created_at`, `created_by`, `updated_at`, `updated_by`, `is_deleted`, `deleted_at`, `deleted_by`, `version`) VALUES (1,'admin','{bcrypt}$2b$12$zXiRuqYhdEukoa5UEO5sOuMXLD/g1KKQMKwWKAFdjK3QnnDVgFBei','ADMIN','System Admin',NULL,NULL,NULL,NULL,'ACTIVE','2026-07-30 03:39:15.939',NULL,'2026-05-14 23:55:35.000',NULL,'2026-05-14 23:55:35.000',NULL,0,NULL,NULL,1528);
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

-- Dump completed on 2026-07-30 18:50:37
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
-- Dumping data for table `sys_user_role`
--
-- WHERE:  user_id=1

LOCK TABLES `sys_user_role` WRITE;
/*!40000 ALTER TABLE `sys_user_role` DISABLE KEYS */;
REPLACE INTO `sys_user_role` (`id`, `user_id`, `role_id`, `created_at`) VALUES (1,1,100,'2026-05-14 23:55:35.000');
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

-- Dump completed on 2026-07-30 18:50:37

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
-- Dumping data for table `campus`
--
-- WHERE:  id < 9100000

LOCK TABLES `campus` WRITE;
/*!40000 ALTER TABLE `campus` DISABLE KEYS */;
REPLACE INTO `campus` (`id`, `campus_name`, `display_order`, `status`, `default_pickup_fee_amount`, `default_dropoff_fee_amount`, `notes`, `created_at`, `created_by`, `updated_at`, `updated_by`, `is_deleted`, `deleted_at`, `deleted_by`, `version`) VALUES (9000001,'South Campus',1,'ACTIVE',0.00,0.00,'Demo campus for daily operations','2026-07-26 16:22:04.192',NULL,'2026-07-26 16:22:04.192',NULL,0,NULL,NULL,0),(9000002,'North Campus',2,'ACTIVE',0.00,0.00,'Demo campus for overflow classes','2026-07-26 16:22:04.192',NULL,'2026-07-26 16:22:04.192',NULL,0,NULL,NULL,0);
/*!40000 ALTER TABLE `campus` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-07-30 18:54:15
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
-- Dumping data for table `classroom`
--
-- WHERE:  id < 9100000

LOCK TABLES `classroom` WRITE;
/*!40000 ALTER TABLE `classroom` DISABLE KEYS */;
REPLACE INTO `classroom` (`id`, `room_code`, `room_name`, `campus`, `campus_id`, `capacity`, `floor`, `status`, `notes`, `created_at`, `created_by`, `updated_at`, `updated_by`, `is_deleted`, `deleted_at`, `deleted_by`, `version`) VALUES (9000101,'S-101','Room 101','South Campus',9000001,12,'1F','ACTIVE','Demo group classroom','2026-07-26 16:22:04.193',NULL,'2026-07-26 16:22:04.193',NULL,0,NULL,NULL,0),(9000102,'S-102','Room 102','South Campus',9000001,4,'1F','ACTIVE','Demo 1-on-1 room','2026-07-26 16:22:04.193',NULL,'2026-07-26 16:22:04.193',NULL,0,NULL,NULL,0),(9000103,'N-201','Room 201','North Campus',9000002,16,'2F','ACTIVE','Demo Saturday classroom','2026-07-26 16:22:04.193',NULL,'2026-07-26 16:22:04.193',NULL,0,NULL,NULL,0);
/*!40000 ALTER TABLE `classroom` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-07-30 18:54:15

-- Load customer-required course product presets. This file is idempotent.
SOURCE doc/migrations/20260727_seed_ppt_course_matrix.sql;
