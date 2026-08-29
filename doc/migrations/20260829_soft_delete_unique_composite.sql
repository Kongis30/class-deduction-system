-- ============================================================================
-- 20260829: 软删除 × 唯一索引 冲突修复
-- ----------------------------------------------------------------------------
-- 问题: 系统采用软删除(@TableLogic, is_deleted=1)。软删行仍占用数据库全局
--       唯一索引;而应用层唯一性查重只过滤未删除行(is_deleted=0)。于是
--       "删除某记录后用相同业务键重建" 时,应用层查重通过、落库却撞唯一键,
--       抛 500(Duplicate entry ... for key 'uk_xxx')。
--
--       客户实例: 删除老师(级联软删登录账号)后重新创建,同用户名建登录 →
--       Duplicate entry 'xxx' for key 'sys_user.uk_sys_user_username'。
--
-- 方案: 与代码库既有模式(teacher_pay_rate/class_enrollment/enrollment_order_item
--       /notification_trigger_rule 均为 (业务列, is_deleted) 复合唯一)对齐,
--       将所有"带软删除且唯一索引未含 is_deleted"的索引转换为
--       (原列, is_deleted)。软删行不再阻塞重建,活跃行唯一性仍由数据库保证。
--
-- 说明:
--   * 当前索引对 cols 全局唯一,转 (cols, is_deleted) 不会产生重复,存量安全。
--   * 脚本幂等: 若目标索引已含 is_deleted 列则跳过。
--   * 跳过项(审计确认无风险):
--       - auth_session.token          : 随机 token, 永不重用
--       - lesson_occurrence.lesson_no : 雪花 ID, 永不重用
--       - sys_role_permission / sys_user_role / teacher_payment_item : 无 is_deleted 列
-- ============================================================================

-- 1. campus
SELECT COUNT(*) INTO @done FROM information_schema.statistics
WHERE table_schema=DATABASE() AND table_name='campus' AND index_name='uk_campus_name' AND column_name='is_deleted';
SET @sql=IF(@done=0, 'ALTER TABLE `campus` DROP INDEX `uk_campus_name`, ADD UNIQUE KEY `uk_campus_name` (`campus_name`, `is_deleted`)', 'SELECT 1');
PREPARE statement FROM @sql; EXECUTE statement; DEALLOCATE PREPARE statement;

-- 2. classroom
SELECT COUNT(*) INTO @done FROM information_schema.statistics
WHERE table_schema=DATABASE() AND table_name='classroom' AND index_name='uk_room_code' AND column_name='is_deleted';
SET @sql=IF(@done=0, 'ALTER TABLE `classroom` DROP INDEX `uk_room_code`, ADD UNIQUE KEY `uk_room_code` (`room_code`, `is_deleted`)', 'SELECT 1');
PREPARE statement FROM @sql; EXECUTE statement; DEALLOCATE PREPARE statement;

-- 3. course_category
SELECT COUNT(*) INTO @done FROM information_schema.statistics
WHERE table_schema=DATABASE() AND table_name='course_category' AND index_name='uk_course_category_type_code' AND column_name='is_deleted';
SET @sql=IF(@done=0, 'ALTER TABLE `course_category` DROP INDEX `uk_course_category_type_code`, ADD UNIQUE KEY `uk_course_category_type_code` (`type_code`, `is_deleted`)', 'SELECT 1');
PREPARE statement FROM @sql; EXECUTE statement; DEALLOCATE PREPARE statement;

-- 4. course_package
SELECT COUNT(*) INTO @done FROM information_schema.statistics
WHERE table_schema=DATABASE() AND table_name='course_package' AND index_name='uk_course_package_no' AND column_name='is_deleted';
SET @sql=IF(@done=0, 'ALTER TABLE `course_package` DROP INDEX `uk_course_package_no`, ADD UNIQUE KEY `uk_course_package_no` (`package_no`, `is_deleted`)', 'SELECT 1');
PREPARE statement FROM @sql; EXECUTE statement; DEALLOCATE PREPARE statement;

-- 5. course_price_option
SELECT COUNT(*) INTO @done FROM information_schema.statistics
WHERE table_schema=DATABASE() AND table_name='course_price_option' AND index_name='uk_course_price_option_code' AND column_name='is_deleted';
SET @sql=IF(@done=0, 'ALTER TABLE `course_price_option` DROP INDEX `uk_course_price_option_code`, ADD UNIQUE KEY `uk_course_price_option_code` (`option_code`, `is_deleted`)', 'SELECT 1');
PREPARE statement FROM @sql; EXECUTE statement; DEALLOCATE PREPARE statement;

-- 6. course_product
SELECT COUNT(*) INTO @done FROM information_schema.statistics
WHERE table_schema=DATABASE() AND table_name='course_product' AND index_name='uk_course_product_code' AND column_name='is_deleted';
SET @sql=IF(@done=0, 'ALTER TABLE `course_product` DROP INDEX `uk_course_product_code`, ADD UNIQUE KEY `uk_course_product_code` (`product_code`, `is_deleted`)', 'SELECT 1');
PREPARE statement FROM @sql; EXECUTE statement; DEALLOCATE PREPARE statement;

-- 7. enrollment_order
SELECT COUNT(*) INTO @done FROM information_schema.statistics
WHERE table_schema=DATABASE() AND table_name='enrollment_order' AND index_name='uk_enrollment_no' AND column_name='is_deleted';
SET @sql=IF(@done=0, 'ALTER TABLE `enrollment_order` DROP INDEX `uk_enrollment_no`, ADD UNIQUE KEY `uk_enrollment_no` (`enrollment_no`, `is_deleted`)', 'SELECT 1');
PREPARE statement FROM @sql; EXECUTE statement; DEALLOCATE PREPARE statement;

-- 8. lesson_record
SELECT COUNT(*) INTO @done FROM information_schema.statistics
WHERE table_schema=DATABASE() AND table_name='lesson_record' AND index_name='uk_lesson_record_occurrence_student' AND column_name='is_deleted';
SET @sql=IF(@done=0, 'ALTER TABLE `lesson_record` DROP INDEX `uk_lesson_record_occurrence_student`, ADD UNIQUE KEY `uk_lesson_record_occurrence_student` (`occurrence_id`, `student_id`, `is_deleted`)', 'SELECT 1');
PREPARE statement FROM @sql; EXECUTE statement; DEALLOCATE PREPARE statement;

-- 9. lesson_status_rule
SELECT COUNT(*) INTO @done FROM information_schema.statistics
WHERE table_schema=DATABASE() AND table_name='lesson_status_rule' AND index_name='uk_lesson_status_rule_code' AND column_name='is_deleted';
SET @sql=IF(@done=0, 'ALTER TABLE `lesson_status_rule` DROP INDEX `uk_lesson_status_rule_code`, ADD UNIQUE KEY `uk_lesson_status_rule_code` (`status_code`, `is_deleted`)', 'SELECT 1');
PREPARE statement FROM @sql; EXECUTE statement; DEALLOCATE PREPARE statement;

-- 10. notification_template
SELECT COUNT(*) INTO @done FROM information_schema.statistics
WHERE table_schema=DATABASE() AND table_name='notification_template' AND index_name='uk_notification_template_code' AND column_name='is_deleted';
SET @sql=IF(@done=0, 'ALTER TABLE `notification_template` DROP INDEX `uk_notification_template_code`, ADD UNIQUE KEY `uk_notification_template_code` (`template_code`, `is_deleted`)', 'SELECT 1');
PREPARE statement FROM @sql; EXECUTE statement; DEALLOCATE PREPARE statement;

-- 11. payment_method
SELECT COUNT(*) INTO @done FROM information_schema.statistics
WHERE table_schema=DATABASE() AND table_name='payment_method' AND index_name='uk_method_code' AND column_name='is_deleted';
SET @sql=IF(@done=0, 'ALTER TABLE `payment_method` DROP INDEX `uk_method_code`, ADD UNIQUE KEY `uk_method_code` (`method_code`, `is_deleted`)', 'SELECT 1');
PREPARE statement FROM @sql; EXECUTE statement; DEALLOCATE PREPARE statement;

-- 12. payment_record
SELECT COUNT(*) INTO @done FROM information_schema.statistics
WHERE table_schema=DATABASE() AND table_name='payment_record' AND index_name='uk_payment_no' AND column_name='is_deleted';
SET @sql=IF(@done=0, 'ALTER TABLE `payment_record` DROP INDEX `uk_payment_no`, ADD UNIQUE KEY `uk_payment_no` (`payment_no`, `is_deleted`)', 'SELECT 1');
PREPARE statement FROM @sql; EXECUTE statement; DEALLOCATE PREPARE statement;

-- 13. receipt_record
SELECT COUNT(*) INTO @done FROM information_schema.statistics
WHERE table_schema=DATABASE() AND table_name='receipt_record' AND index_name='uk_receipt_no' AND column_name='is_deleted';
SET @sql=IF(@done=0, 'ALTER TABLE `receipt_record` DROP INDEX `uk_receipt_no`, ADD UNIQUE KEY `uk_receipt_no` (`receipt_no`, `is_deleted`)', 'SELECT 1');
PREPARE statement FROM @sql; EXECUTE statement; DEALLOCATE PREPARE statement;

-- 14. student
SELECT COUNT(*) INTO @done FROM information_schema.statistics
WHERE table_schema=DATABASE() AND table_name='student' AND index_name='uk_student_no' AND column_name='is_deleted';
SET @sql=IF(@done=0, 'ALTER TABLE `student` DROP INDEX `uk_student_no`, ADD UNIQUE KEY `uk_student_no` (`student_no`, `is_deleted`)', 'SELECT 1');
PREPARE statement FROM @sql; EXECUTE statement; DEALLOCATE PREPARE statement;

-- 15. subject
SELECT COUNT(*) INTO @done FROM information_schema.statistics
WHERE table_schema=DATABASE() AND table_name='subject' AND index_name='uk_subject_name' AND column_name='is_deleted';
SET @sql=IF(@done=0, 'ALTER TABLE `subject` DROP INDEX `uk_subject_name`, ADD UNIQUE KEY `uk_subject_name` (`subject_name`, `is_deleted`)', 'SELECT 1');
PREPARE statement FROM @sql; EXECUTE statement; DEALLOCATE PREPARE statement;

-- 16. sys_dict_item
SELECT COUNT(*) INTO @done FROM information_schema.statistics
WHERE table_schema=DATABASE() AND table_name='sys_dict_item' AND index_name='uk_sys_dict_item_code' AND column_name='is_deleted';
SET @sql=IF(@done=0, 'ALTER TABLE `sys_dict_item` DROP INDEX `uk_sys_dict_item_code`, ADD UNIQUE KEY `uk_sys_dict_item_code` (`dict_type_id`, `item_code`, `is_deleted`)', 'SELECT 1');
PREPARE statement FROM @sql; EXECUTE statement; DEALLOCATE PREPARE statement;

-- 17. sys_dict_type
SELECT COUNT(*) INTO @done FROM information_schema.statistics
WHERE table_schema=DATABASE() AND table_name='sys_dict_type' AND index_name='uk_sys_dict_type_code' AND column_name='is_deleted';
SET @sql=IF(@done=0, 'ALTER TABLE `sys_dict_type` DROP INDEX `uk_sys_dict_type_code`, ADD UNIQUE KEY `uk_sys_dict_type_code` (`dict_code`, `is_deleted`)', 'SELECT 1');
PREPARE statement FROM @sql; EXECUTE statement; DEALLOCATE PREPARE statement;

-- 18. sys_permission
SELECT COUNT(*) INTO @done FROM information_schema.statistics
WHERE table_schema=DATABASE() AND table_name='sys_permission' AND index_name='uk_sys_permission_code' AND column_name='is_deleted';
SET @sql=IF(@done=0, 'ALTER TABLE `sys_permission` DROP INDEX `uk_sys_permission_code`, ADD UNIQUE KEY `uk_sys_permission_code` (`permission_code`, `is_deleted`)', 'SELECT 1');
PREPARE statement FROM @sql; EXECUTE statement; DEALLOCATE PREPARE statement;

-- 19. sys_role
SELECT COUNT(*) INTO @done FROM information_schema.statistics
WHERE table_schema=DATABASE() AND table_name='sys_role' AND index_name='uk_sys_role_code' AND column_name='is_deleted';
SET @sql=IF(@done=0, 'ALTER TABLE `sys_role` DROP INDEX `uk_sys_role_code`, ADD UNIQUE KEY `uk_sys_role_code` (`role_code`, `is_deleted`)', 'SELECT 1');
PREPARE statement FROM @sql; EXECUTE statement; DEALLOCATE PREPARE statement;

-- 20. sys_user.username  ← 本次客户报障的核心索引
SELECT COUNT(*) INTO @done FROM information_schema.statistics
WHERE table_schema=DATABASE() AND table_name='sys_user' AND index_name='uk_sys_user_username' AND column_name='is_deleted';
SET @sql=IF(@done=0, 'ALTER TABLE `sys_user` DROP INDEX `uk_sys_user_username`, ADD UNIQUE KEY `uk_sys_user_username` (`username`, `is_deleted`)', 'SELECT 1');
PREPARE statement FROM @sql; EXECUTE statement; DEALLOCATE PREPARE statement;

-- 21. sys_user.teacher_id (同一账号表的同类风险)
SELECT COUNT(*) INTO @done FROM information_schema.statistics
WHERE table_schema=DATABASE() AND table_name='sys_user' AND index_name='uk_sys_user_teacher_id' AND column_name='is_deleted';
SET @sql=IF(@done=0, 'ALTER TABLE `sys_user` DROP INDEX `uk_sys_user_teacher_id`, ADD UNIQUE KEY `uk_sys_user_teacher_id` (`teacher_id`, `is_deleted`)', 'SELECT 1');
PREPARE statement FROM @sql; EXECUTE statement; DEALLOCATE PREPARE statement;

-- 22. teacher
SELECT COUNT(*) INTO @done FROM information_schema.statistics
WHERE table_schema=DATABASE() AND table_name='teacher' AND index_name='uk_teacher_no' AND column_name='is_deleted';
SET @sql=IF(@done=0, 'ALTER TABLE `teacher` DROP INDEX `uk_teacher_no`, ADD UNIQUE KEY `uk_teacher_no` (`teacher_no`, `is_deleted`)', 'SELECT 1');
PREPARE statement FROM @sql; EXECUTE statement; DEALLOCATE PREPARE statement;

-- 23. teacher_payment
SELECT COUNT(*) INTO @done FROM information_schema.statistics
WHERE table_schema=DATABASE() AND table_name='teacher_payment' AND index_name='uk_payment_no' AND column_name='is_deleted';
SET @sql=IF(@done=0, 'ALTER TABLE `teacher_payment` DROP INDEX `uk_payment_no`, ADD UNIQUE KEY `uk_payment_no` (`payment_no`, `is_deleted`)', 'SELECT 1');
PREPARE statement FROM @sql; EXECUTE statement; DEALLOCATE PREPARE statement;
