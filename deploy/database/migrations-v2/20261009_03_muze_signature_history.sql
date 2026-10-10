-- 客户 2026-10-09 提供的 Muze 手签课表：仅补签字表历史，不触碰课包余额、消课记录及工资。
INSERT INTO private_signature_history
    (package_id, lesson_date, teacher_id, teacher_name, subject_name, planned_units, signature_text, source_reference)
SELECT cp.id, source_rows.lesson_date, 2095304710883418113, 'Claudia Peng', NULL, 4,
       'Ms.Claudia', 'Customer signed sheet supplied 2026-10-09'
FROM course_package cp
JOIN student student ON student.id = cp.student_id
JOIN (
    SELECT DATE('2026-09-20') AS lesson_date
    UNION ALL SELECT DATE('2026-09-26')
) source_rows
WHERE cp.id = 2103231174551326722
  AND student.id = 2096712568053260289
  AND student.student_name = 'Muze Song'
  AND NOT EXISTS (
      SELECT 1 FROM lesson_occurrence occurrence
      WHERE occurrence.package_id = cp.id
        AND occurrence.is_deleted = 0
        AND DATE(occurrence.planned_start_at) = source_rows.lesson_date
  )
  AND NOT EXISTS (
      SELECT 1 FROM private_signature_history history
      WHERE history.package_id = cp.id
        AND history.lesson_date = source_rows.lesson_date
        AND history.teacher_name = 'Claudia Peng'
  );
