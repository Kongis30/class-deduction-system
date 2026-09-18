-- ============================================================================
-- 20260918: 收据/支付流水软删撞唯一键修复（dead row no-space release）
-- ----------------------------------------------------------------------------
-- 问题: 删除报名单时级联软删收据/支付流水（UPDATE is_deleted=1），撞
--       uk_receipt_no(receipt_no, is_deleted) / uk_payment_no(payment_no, is_deleted)。
--       报错: Duplicate entry 'RCP202609050008-1' for key 'uk_receipt_no'。
-- 根因: 同一业务单号被两个不同订单使用（生成逻辑只看 is_deleted=0 的最大号，
--       软删号会被复用）。历史软删行占用 (no,1) 键位后，另一订单的同号 active 行
--       再软删 0→1 时必撞复合唯一键（该键全局只允许一行 (no,1)）。
-- 线上实例: receipt_record 2 组（RCP202609050007/008）、payment_record 1 组
--           （PMT202609050002）。
-- 方案: 一次性把"存在同号 active 行"的软删行改号为 原号-DEAD-<id>（行级唯一），
--       释放 (no,1) 键位。只动 is_deleted=1 的行（应用层不可见），幂等——
--       改号后不再与 active 行同号，重复执行 JOIN 不命中。
--       代码侧防复发: EnrollmentOrderServiceImpl.deleteOrderFinancialArtifacts
--       软删前先将号改写为 原号-DEL-<id>，此后软删不再占用 (no,1) 键位。
-- ============================================================================

-- 1) receipt_record：改号释放键位（幂等）
UPDATE receipt_record r
JOIN (
    SELECT DISTINCT a.id
    FROM receipt_record a
    JOIN receipt_record b
      ON b.receipt_no = a.receipt_no AND b.id <> a.id AND b.is_deleted = 0
    WHERE a.is_deleted = 1
) t ON t.id = r.id
SET r.receipt_no = CONCAT(r.receipt_no, '-DEAD-', r.id);

-- 2) payment_record：同上（幂等）
UPDATE payment_record r
JOIN (
    SELECT DISTINCT a.id
    FROM payment_record a
    JOIN payment_record b
      ON b.payment_no = a.payment_no AND b.id <> a.id AND b.is_deleted = 0
    WHERE a.is_deleted = 1
) t ON t.id = r.id
SET r.payment_no = CONCAT(r.payment_no, '-DEAD-', r.id);

-- 3) 验证（预期均返回 0）
SELECT COUNT(*) AS receipt_conflict_left
FROM receipt_record a
JOIN receipt_record b
  ON b.receipt_no = a.receipt_no AND b.id <> a.id AND b.is_deleted = 0
WHERE a.is_deleted = 1;

SELECT COUNT(*) AS payment_conflict_left
FROM payment_record a
JOIN payment_record b
  ON b.payment_no = a.payment_no AND b.id <> a.id AND b.is_deleted = 0
WHERE a.is_deleted = 1;
