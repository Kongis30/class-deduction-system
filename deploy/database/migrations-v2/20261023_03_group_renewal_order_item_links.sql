-- 只回填能够由现存班课权益唯一确认的购买行；不更改课时、老师分配或历史扣课。
UPDATE enrollment_order_item item
JOIN (
  SELECT entitlement.enrollment_order_item_id AS item_id,
         MIN(entitlement.logical_course_id) AS logical_course_id
  FROM class_enrollment entitlement
  JOIN enrollment_order_item purchase ON purchase.id = entitlement.enrollment_order_item_id
  JOIN enrollment_order receipt ON receipt.id = purchase.enrollment_order_id
  JOIN class_group actual_class ON actual_class.id = entitlement.class_id
  WHERE entitlement.logical_course_id IS NOT NULL
    AND entitlement.is_deleted = 0
    AND entitlement.status IN ('ACTIVE', 'EXPIRED')
    AND purchase.is_deleted = 0
    AND purchase.group_project_id = entitlement.group_project_id
    AND purchase.course_product_id = actual_class.product_id
    AND purchase.group_project_id = actual_class.group_project_id
    AND receipt.student_id = entitlement.student_id
  GROUP BY entitlement.enrollment_order_item_id
  HAVING COUNT(DISTINCT entitlement.logical_course_id) = 1
     AND COUNT(DISTINCT entitlement.student_id) = 1
) verified ON verified.item_id = item.id
SET item.logical_course_id = verified.logical_course_id
WHERE item.logical_course_id IS NULL
  AND item.is_deleted = 0;
