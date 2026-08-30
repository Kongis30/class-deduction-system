-- ============================================================================
-- 20260830: 新增 product_grade_config 表（grade 改名与显示顺序配置）
-- ----------------------------------------------------------------------------
-- 背景: grade_band 是散落在 course_price_option / class_group / teacher_pay_rate
--       中的自由文本, 无归属配置实体, 导致 Prices 页面 grade 班分组顺序固定
--       (字典序) 且名称无法修改。
--
-- 方案: 新增按课程产品维度的配置表 product_grade_config, 保存每个产品下
--       grade 的显示名称与顺序。配置表是覆盖层: 页面分组仍以价格行实际存在
--       的 grade_band 为准, 本表只决定排序; 改名时由后端在同一事务内级联
--       更新三张表的 grade_band(历史订单快照不动)。
--
-- 设计文档: doc/新需求改动/Grade改名与排序功能设计.md
-- 幂等: 若表已存在则跳过建表。
-- ============================================================================

SET @has_table = (
  SELECT COUNT(*) FROM information_schema.tables
  WHERE table_schema = DATABASE() AND table_name = 'product_grade_config'
);
SET @sql = IF(@has_table = 0, '
CREATE TABLE `product_grade_config` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT ''Primary key ID'',
  `course_product_id` bigint NOT NULL COMMENT ''Course product ID'',
  `grade_label` varchar(64) NOT NULL COMMENT ''Current grade label'',
  `display_order` int NOT NULL DEFAULT ''99'' COMMENT ''Display order for sorting'',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT ''Created at'',
  `created_by` bigint DEFAULT NULL COMMENT ''Created by user ID'',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT ''Updated at'',
  `updated_by` bigint DEFAULT NULL COMMENT ''Updated by user ID'',
  `is_deleted` tinyint(1) NOT NULL DEFAULT ''0'' COMMENT ''Soft delete flag: 0=no, 1=yes'',
  `deleted_at` datetime(3) DEFAULT NULL COMMENT ''Deleted at'',
  `deleted_by` bigint DEFAULT NULL COMMENT ''Deleted by user ID'',
  `version` int NOT NULL DEFAULT ''0'' COMMENT ''Optimistic lock version'',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_product_grade_label` (`course_product_id`, `grade_label`, `is_deleted`),
  KEY `idx_product_grade_order` (`course_product_id`, `display_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT=''Per-product grade label & display order config''
', 'SELECT 1');
PREPARE statement FROM @sql; EXECUTE statement; DEALLOCATE PREPARE statement;
