-- Data Summary 周六班 Product Tab 特殊显示修复：为存量周六班产品补写 course_type。
-- 背景：course_product.course_type 长期无 UI 维护入口，周六班产品该字段为 NULL，
-- 导致 Data Summary 的 SATURDAY tab 识别分支失效（特显逻辑要求 courseType = 'SATURDAY'）。
-- 执行前请核对匹配到的产品（SELECT 语句），确认无误后再执行 UPDATE。

-- 1) 预览将被更新的产品
SELECT id, product_name, course_type, teaching_mode
FROM course_product
WHERE is_deleted = 0
  AND teaching_mode = 'GROUP'
  AND (product_name LIKE '%周六%' OR product_name LIKE '%Saturday%' OR product_name LIKE '%SATURDAY%');

-- 2) 数据修复：周六班产品统一标记 course_type = 'SATURDAY'
UPDATE course_product
SET course_type = 'SATURDAY'
WHERE is_deleted = 0
  AND teaching_mode = 'GROUP'
  AND (product_name LIKE '%周六%' OR product_name LIKE '%Saturday%' OR product_name LIKE '%SATURDAY%');
