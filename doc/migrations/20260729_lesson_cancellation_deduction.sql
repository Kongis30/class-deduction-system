-- Student cancellation consumes the planned lesson. Approved leave remains free
-- and is compensated by the automatically generated makeup occurrence.
INSERT INTO lesson_status_rule (
    status_code,
    status_name,
    default_should_deduct,
    deduct_mode,
    fixed_units,
    default_should_extend,
    allow_manual_override,
    status,
    remark,
    is_deleted,
    version
)
SELECT
    'CANCELLED',
    'Cancelled',
    1,
    'PLANNED_UNITS',
    NULL,
    0,
    0,
    'ACTIVE',
    'Student cancellation, deduct planned units',
    0,
    0
WHERE NOT EXISTS (
    SELECT 1
    FROM lesson_status_rule
    WHERE status_code = 'CANCELLED'
      AND is_deleted = 0
);

UPDATE lesson_status_rule
SET default_should_deduct = 1,
    deduct_mode = 'PLANNED_UNITS',
    fixed_units = NULL,
    default_should_extend = 0,
    allow_manual_override = 0,
    status = 'ACTIVE',
    remark = 'Student cancellation, deduct planned units'
WHERE status_code IN ('CANCELLED', 'TEMP_CANCEL')
  AND is_deleted = 0;
