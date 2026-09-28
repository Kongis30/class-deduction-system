-- 将原 Course 范围的班课假期拆分为 Course + Grade。
-- 每个旧假期复制到该 Course 下实际存在的所有 Grade，保持迁移前的排课覆盖范围。
-- 本迁移需在 MySQL 8 执行；临时映射表用于把已被假期取消的课次关联到对应 Grade 的新假期行。
-- 执行迁移时应暂停应用写入，确保 MAX(id) 分配期间没有并发新增假期。

ALTER TABLE `class_group`
  ADD COLUMN `schedule_base_end_date` date DEFAULT NULL COMMENT 'User-configured schedule end before holiday extensions' AFTER `end_date`;

UPDATE `class_group`
SET `schedule_base_end_date` = `end_date`
WHERE `schedule_base_end_date` IS NULL;

ALTER TABLE `course_holiday`
  ADD COLUMN `grade_band` varchar(64) DEFAULT NULL COMMENT 'Grade scope within this course product' AFTER `course_product_id`;

CREATE TEMPORARY TABLE `tmp_course_holiday_grade_map` AS
SELECT
  ranked.`old_holiday_id`,
  ranked.`course_product_id`,
  ranked.`grade_band`,
  ranked.`grade_rank`,
  CASE WHEN ranked.`grade_rank` = 1 THEN ranked.`old_holiday_id`
       ELSE ranked.`max_holiday_id` + ranked.`migration_rank`
  END AS `new_holiday_id`
FROM (
  SELECT
    holiday.`id` AS `old_holiday_id`,
    holiday.`course_product_id`,
    grade.`grade_band`,
    (SELECT MAX(`id`) FROM `course_holiday`) AS `max_holiday_id`,
    ROW_NUMBER() OVER (PARTITION BY holiday.`id` ORDER BY grade.`grade_band`) AS `grade_rank`,
    ROW_NUMBER() OVER (ORDER BY holiday.`id`, grade.`grade_band`) AS `migration_rank`
  FROM `course_holiday` holiday
  JOIN (
    SELECT DISTINCT `product_id` AS `course_product_id`, TRIM(`grade_band`) AS `grade_band`
    FROM `class_group`
    WHERE `product_id` IS NOT NULL AND `grade_band` IS NOT NULL AND TRIM(`grade_band`) <> ''
    UNION
    SELECT DISTINCT `course_product_id`, TRIM(`grade_band`)
    FROM `course_price_option`
    WHERE `course_product_id` IS NOT NULL AND `grade_band` IS NOT NULL AND TRIM(`grade_band`) <> ''
    UNION
    SELECT DISTINCT `course_product_id`, TRIM(`grade_label`)
    FROM `product_grade_config`
    WHERE `course_product_id` IS NOT NULL AND `grade_label` IS NOT NULL AND TRIM(`grade_label`) <> ''
  ) grade ON grade.`course_product_id` = holiday.`course_product_id`
) ranked;

-- 没有关联 Grade 的旧 Course 假期保留在 General，避免丢失可见的配置数据。
UPDATE `course_holiday`
SET `grade_band` = 'General'
WHERE `id` NOT IN (SELECT `old_holiday_id` FROM `tmp_course_holiday_grade_map` WHERE `grade_rank` = 1);

-- 原始行承接排序最前的 Grade，其余 Grade 新增完整副本（包括软删除状态和审计字段）。
UPDATE `course_holiday` holiday
JOIN `tmp_course_holiday_grade_map` mapping
  ON mapping.`old_holiday_id` = holiday.`id` AND mapping.`grade_rank` = 1
SET holiday.`grade_band` = mapping.`grade_band`;

INSERT INTO `course_holiday` (
  `id`, `course_product_id`, `grade_band`, `holiday_name`, `start_date`, `end_date`,
  `created_at`, `created_by`, `updated_at`, `updated_by`, `is_deleted`, `deleted_at`, `deleted_by`, `version`
)
SELECT
  mapping.`new_holiday_id`, holiday.`course_product_id`, mapping.`grade_band`, holiday.`holiday_name`,
  holiday.`start_date`, holiday.`end_date`, holiday.`created_at`, holiday.`created_by`,
  holiday.`updated_at`, holiday.`updated_by`, holiday.`is_deleted`, holiday.`deleted_at`,
  holiday.`deleted_by`, holiday.`version`
FROM `tmp_course_holiday_grade_map` mapping
JOIN `course_holiday` holiday ON holiday.`id` = mapping.`old_holiday_id`
WHERE mapping.`grade_rank` > 1;

-- 将历史假期取消标记指向对应班级 Grade 的假期副本，保证删除/编辑假期时可正确恢复课次。
UPDATE `lesson_occurrence` occurrence
JOIN `class_group` class_group ON class_group.`id` = occurrence.`class_group_id`
JOIN `tmp_course_holiday_grade_map` mapping
  ON mapping.`old_holiday_id` = occurrence.`course_holiday_id`
 AND mapping.`course_product_id` = class_group.`product_id`
 AND mapping.`grade_band` = TRIM(class_group.`grade_band`)
SET occurrence.`course_holiday_id` = mapping.`new_holiday_id`
WHERE occurrence.`course_holiday_id` IS NOT NULL;

DROP TEMPORARY TABLE `tmp_course_holiday_grade_map`;

ALTER TABLE `course_holiday`
  MODIFY COLUMN `grade_band` varchar(64) NOT NULL COMMENT 'Grade scope within this course product',
  DROP KEY `idx_course_holiday_dates`,
  ADD KEY `idx_course_holiday_dates` (`course_product_id`,`grade_band`,`is_deleted`,`start_date`,`end_date`);
