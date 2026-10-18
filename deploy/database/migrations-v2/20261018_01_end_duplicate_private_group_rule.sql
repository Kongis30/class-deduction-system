-- 共享 1-on-2 课组的第二条规则与原规则三段课位完全相同，且没有课次。
-- 保留承载课次和消课的原规则；只结束空的重复规则，不移动课程或更改分配额。
-- 执行前备份两条 recurring_schedule_rule 行并人工核对课位；可重复执行。
UPDATE recurring_schedule_rule AS duplicate_rule
SET duplicate_rule.rule_status = 'ENDED',
    duplicate_rule.version = duplicate_rule.version + 1
WHERE duplicate_rule.id = 2103117340461289474
  AND duplicate_rule.package_id = 2103113875337039874
  AND duplicate_rule.private_lesson_group_id = 2103113875303485442
  AND duplicate_rule.rule_status = 'ACTIVE'
  AND duplicate_rule.supersedes_rule_id IS NULL
  AND duplicate_rule.is_deleted = 0
  AND NOT EXISTS (
      SELECT 1 FROM lesson_occurrence AS lesson
      WHERE lesson.schedule_rule_id = duplicate_rule.id AND lesson.is_deleted = 0
  )
  AND (
      SELECT COUNT(*) FROM recurring_schedule_slot AS slot
      WHERE slot.rule_id = duplicate_rule.id
        AND slot.slot_status = 'ACTIVE' AND slot.is_deleted = 0
  ) = 3;

SELECT id, rule_status, package_id, private_lesson_group_id
FROM recurring_schedule_rule
WHERE id IN (2103116509590638594, 2103117340461289474)
ORDER BY created_at;
