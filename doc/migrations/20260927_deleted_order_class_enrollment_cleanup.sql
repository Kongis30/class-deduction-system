-- 清理旧版本按订单 class_id 删除时漏掉的班课在册记录。
-- 只处理能通过 enrollment_order_item_id 精确关联到已删除课程/订单的记录；共享班课排课不删除。
-- 部署前先运行 SELECT 核对目标行；UPDATE 与应用代码发布配套执行。

SELECT ce.id, ce.class_id, ce.student_id, ce.enrollment_order_item_id,
       eo.enrollment_no, ce.status
FROM class_enrollment ce
JOIN enrollment_order_item item ON item.id = ce.enrollment_order_item_id
JOIN enrollment_order eo ON eo.id = item.enrollment_order_id
WHERE ce.is_deleted = 0 AND ce.status = 'ACTIVE'
  AND (item.is_deleted = 1 OR eo.is_deleted = 1);

START TRANSACTION;
UPDATE class_enrollment ce
JOIN enrollment_order_item item ON item.id = ce.enrollment_order_item_id
JOIN enrollment_order eo ON eo.id = item.enrollment_order_id
SET ce.status = 'WITHDRAWN', ce.is_deleted = 1, ce.deleted_at = NOW(3)
WHERE ce.is_deleted = 0 AND ce.status = 'ACTIVE'
  AND (item.is_deleted = 1 OR eo.is_deleted = 1);
COMMIT;
