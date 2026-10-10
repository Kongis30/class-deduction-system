-- 补足旧私教课次缺失的课程名称快照；读取已软删的旧时段，不改老师、时间或消课结果。
UPDATE lesson_occurrence occurrence
JOIN recurring_schedule_slot slot ON slot.id = occurrence.schedule_slot_id
SET occurrence.subject_name = slot.subject_name
WHERE occurrence.is_deleted = 0
  AND occurrence.class_group_id IS NULL
  AND (occurrence.subject_name IS NULL OR occurrence.subject_name = '')
  AND slot.subject_name IS NOT NULL
  AND slot.subject_name <> '';
