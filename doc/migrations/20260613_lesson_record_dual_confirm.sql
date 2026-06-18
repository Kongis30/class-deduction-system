-- ============================================================================
-- Migration: 消课记录二阶段确认字段扩展
-- Date: 2026-06-13
-- 适用表: lesson_record
--
-- 说明:
--   本项目无 Flyway/Liquibase，schema 变更需手工执行本脚本，并同步
--   doc/mysql_schema_from_db.sql。请在执行前备份数据库。
--
--   历史已确认记录已完成扣课/台账/计薪语义，统一回填为 CONFIRMED。
-- ============================================================================

ALTER TABLE `lesson_record`
  ADD COLUMN `confirm_stage` varchar(32) NOT NULL DEFAULT 'PENDING_ADMIN'
    COMMENT 'Confirm stage: PENDING_ADMIN/CONFIRMED/REJECTED' AFTER `should_extend_end_date`,
  ADD COLUMN `teacher_confirmed_at` datetime(3) DEFAULT NULL
    COMMENT 'Teacher initial confirmed at' AFTER `confirmed_at`,
  ADD COLUMN `teacher_confirmed_by_user_id` bigint DEFAULT NULL
    COMMENT 'Teacher initial confirmed by user ID' AFTER `teacher_confirmed_at`,
  ADD COLUMN `admin_confirmed_at` datetime(3) DEFAULT NULL
    COMMENT 'Admin final confirmed at' AFTER `teacher_confirmed_by_user_id`,
  ADD COLUMN `admin_confirmed_by_user_id` bigint DEFAULT NULL
    COMMENT 'Admin final confirmed by user ID' AFTER `admin_confirmed_at`,
  ADD COLUMN `reject_reason` varchar(500) DEFAULT NULL
    COMMENT 'Admin reject reason' AFTER `admin_confirmed_by_user_id`,
  ADD COLUMN `rejected_at` datetime(3) DEFAULT NULL
    COMMENT 'Rejected at' AFTER `reject_reason`,
  ADD COLUMN `rejected_by_user_id` bigint DEFAULT NULL
    COMMENT 'Rejected by user ID' AFTER `rejected_at`,
  ADD KEY `idx_lesson_record_confirm_stage` (`confirm_stage`, `confirmed_at` DESC),
  ADD KEY `idx_lesson_record_teacher_confirm_user` (`teacher_confirmed_by_user_id`),
  ADD KEY `idx_lesson_record_admin_confirm_user` (`admin_confirmed_by_user_id`),
  ADD KEY `idx_lesson_record_reject_user` (`rejected_by_user_id`),
  ADD CONSTRAINT `fk_lesson_record_teacher_confirm_user`
    FOREIGN KEY (`teacher_confirmed_by_user_id`) REFERENCES `sys_user` (`id`),
  ADD CONSTRAINT `fk_lesson_record_admin_confirm_user`
    FOREIGN KEY (`admin_confirmed_by_user_id`) REFERENCES `sys_user` (`id`),
  ADD CONSTRAINT `fk_lesson_record_reject_user`
    FOREIGN KEY (`rejected_by_user_id`) REFERENCES `sys_user` (`id`);

-- 历史已确认记录已发生扣课/台账写入，迁移后视为管理员终确认完成。
UPDATE `lesson_record`
SET `confirm_stage` = 'CONFIRMED',
    `admin_confirmed_at` = `confirmed_at`,
    `admin_confirmed_by_user_id` = `confirmed_by_user_id`
WHERE `confirmed_at` IS NOT NULL
   OR `confirmed_by_user_id` IS NOT NULL;
