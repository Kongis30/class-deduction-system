-- Migration: Add flat guardian fields to student table
-- Run this against your existing class_deduction_system database

USE `class_deduction_system`;

ALTER TABLE `student`
  ADD COLUMN `guardian_name` VARCHAR(64) DEFAULT NULL COMMENT 'Primary guardian name' AFTER `notes`,
  ADD COLUMN `guardian_phone` VARCHAR(32) DEFAULT NULL COMMENT 'Primary guardian phone' AFTER `guardian_name`,
  ADD COLUMN `guardian_email` VARCHAR(128) DEFAULT NULL COMMENT 'Primary guardian email' AFTER `guardian_phone`,
  ADD COLUMN `guardian_relation_type` VARCHAR(32) DEFAULT NULL COMMENT 'Primary guardian relation type' AFTER `guardian_email`;
