-- 一门课可以有多笔独立计价的购买；购买行保留原订单和收据。
ALTER TABLE `enrollment_order_item`
  ADD COLUMN `logical_course_id` bigint DEFAULT NULL COMMENT 'Shared course identity for renewal purchase rows' AFTER `enrollment_order_id`,
  ADD KEY `idx_order_item_logical_course` (`logical_course_id`,`is_deleted`);

-- 班课允许一笔订单对应多位学生，归属必须放在逐学生权益上。
ALTER TABLE `class_enrollment`
  ADD COLUMN `logical_course_id` bigint DEFAULT NULL COMMENT 'First enrollment in this student course renewal chain' AFTER `enrollment_order_item_id`,
  ADD KEY `idx_class_enrollment_logical_course` (`logical_course_id`,`student_id`,`is_deleted`);

-- 只回填能够唯一确认归属的当前购买行。更早的历史行在首次续课时由服务端关联。
UPDATE `enrollment_order_item` item
JOIN `course_package` package ON package.enrollment_order_item_id = item.id AND package.is_deleted = 0
SET item.logical_course_id = package.id
WHERE item.logical_course_id IS NULL AND item.course_type = 'ONE_ON_ONE';

UPDATE `enrollment_order_item` item
JOIN `private_lesson_group` lesson_group ON lesson_group.enrollment_order_item_id = item.id AND lesson_group.is_deleted = 0
SET item.logical_course_id = lesson_group.id
WHERE item.course_type = 'ONE_ON_ONE';
