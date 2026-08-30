# Grade 改名与排序功能设计

> 日期：2026-08-30
> 状态：待确认
> 范围：Prices（Pricing Matrix）页面 Group Class 标签页

## 1. 需求背景

Prices 页面选中某个课程产品后，Group Class 标签页按 `grade_band` 把价格行分组成多个 grade 班卡片。当前问题：

1. **顺序固定**：分组按 `gradeLabel.localeCompare()` 字典序排序（CoursePriceOptionsView.vue `groupedOptions`），管理员无法调整显示顺序。
2. **名称不可改**：`grade_band` 是散落在多张表里的自由文本，没有可编辑的入口；写错了只能逐行改价格行，且班级（class_group）、老师费率作用域（teacher_pay_rate）不会跟着变。

需求：增加"管理 grade"功能，支持**修改 grade 名称**和**调整 grade 显示顺序**。

## 2. 已确认的设计决策

| 决策点 | 结论 |
|---|---|
| 改名级联范围 | **级联全部**：同一课程产品下 `course_price_option.grade_band`、`class_group.grade_band`、`teacher_pay_rate.grade_band` 同步更新；历史订单快照（`enrollment_order_item` 等）不动 |
| 配置作用域 | **按课程产品（per-product）**：每个课程产品独立配置自己的 grade 名称与顺序 |

## 3. 数据模型

### 3.1 新表 `product_grade_config`

现有表结构无法满足需求（grade_band 是自由文本、无归属实体、无顺序字段），需新增配置表。参照 `course_category` 模式：

```sql
CREATE TABLE `product_grade_config` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'Primary key ID',
  `course_product_id` bigint NOT NULL COMMENT 'Course product ID',
  `grade_label` varchar(64) NOT NULL COMMENT 'Current grade label',
  `display_order` int NOT NULL DEFAULT '99' COMMENT 'Display order for sorting',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `created_by` bigint DEFAULT NULL,
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `updated_by` bigint DEFAULT NULL,
  `is_deleted` tinyint(1) NOT NULL DEFAULT '0',
  `deleted_at` datetime(3) DEFAULT NULL,
  `deleted_by` bigint DEFAULT NULL,
  `version` int NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_product_grade_label` (`course_product_id`,`grade_label`,`is_deleted`),
  KEY `idx_product_grade_order` (`course_product_id`,`display_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Per-product grade label & display order config';
```

迁移脚本：`doc/migrations/20260830_product_grade_config.sql`（幂等写法，参照现有迁移风格）。

### 3.2 配置表与实际数据的关系

- 配置表是**覆盖层**：页面分组仍以价格行实际存在的 `grade_band` 为准；配置表只决定顺序，改名时负责级联。
- 未配置过的 grade（老数据）读取时自动发现（从价格行去重得到），`display_order` 缺省 99，按字典序兜底。
- 价格行全部删除后残留的配置行无害，不提供单独删除入口（保存时全量 upsert + 软删本次提交中消失的行）。

## 4. API 设计

### 4.1 查询 grade 配置

```
GET /v1/base/course-products/{productId}/grade-configs
```

响应 `data`: `[{ id, gradeLabel, displayOrder }]`

逻辑：配置表该产品的未删行 ∪ 价格行中该产品的 distinct `grade_band`（自动发现，未入库的给默认 order），合并后按 `displayOrder` → 字典序排序。

### 4.2 批量保存（改名 + 排序）

```
PUT /v1/base/course-products/{productId}/grade-configs
body: { items: [{ currentLabel, newLabel, displayOrder }] }
```

`newLabel` 省略或与 `currentLabel` 相同表示不改名。单事务内：

1. **校验**：`newLabel` 非空、长度 ≤ 64；同产品内 `newLabel` 不得与其他 grade 的当前名重复（含本次批量内互撞，防止把两个班合成一个）。
2. **级联改名**（对 `newLabel != currentLabel` 的项）：
   - `UPDATE course_price_option SET grade_band = new WHERE course_product_id = ? AND grade_band = old AND is_deleted = 0`
   - `UPDATE class_group SET grade_band = new WHERE product_id = ? AND grade_band = old AND is_deleted = 0`
   - `UPDATE teacher_pay_rate SET grade_band = new WHERE course_product_id = ? AND grade_band = old AND is_deleted = 0`
   - 历史订单快照不动（商业快照原则）。
3. **upsert 配置**：以提交的 `newLabel` 全量重建该产品的配置行（软删消失项、更新/插入存在项）。

事务保证：任一步失败全部回滚。

## 5. 后端改动

`modules/base/` 下新增：

- `entity/ProductGradeConfigEntity.java`
- `mapper/ProductGradeConfigMapper.java`
- `dto/ProductGradeConfigSaveRequest.java`（含内嵌 Item）
- `vo/ProductGradeConfigVO.java`
- `service/ProductGradeConfigService.java` + `impl/ProductGradeConfigServiceImpl.java`（级联更新复用现有 mapper：`CoursePriceOptionMapper`、`ClassGroupMapper`、`TeacherPayRateMapper`，用 LambdaUpdateWrapper 批量 UPDATE，无需新增 SQL）
- `controller/ProductGradeConfigController.java`

## 6. 前端改动（CoursePriceOptionsView.vue）

1. **入口**：Group Class 模式下、已选中课程产品时，工具栏显示 `Manage grades` 按钮。
2. **管理弹窗**（参照现有 course category 弹窗风格）：
   - 每行：grade 名称（可编辑输入框）+ 顺序调整（上移/下移按钮）。
   - 行集合 = 当前产品下实际存在的 grade（来自 4.1 接口，含自动发现）。
   - "Grade not set"（空 grade_band 归拢组）不进入管理弹窗。
   - 保存调用 4.2；成功后刷新价格行列表。
3. **分组排序**：`groupedOptions` 的最终排序改为 `displayOrder` 优先（从接口数据建 map，key 为 gradeLabel 小写），未配置的按字典序排在已配置项之后。
4. **表单 grade 下拉**：`gradeOptionsForForm` 合并"当前产品已有 grade（含改名后）"到硬编码 `pptGradeOptions` 之前，避免改名后下拉仍引导创建旧名 grade。

## 7. 影响范围评估

| 模块 | 影响 |
|---|---|
| Prices 页面 | 分组顺序、grade 名称展示变化（正向） |
| 报名页（RegistrationIntakeView） | 展示的价格行 grade 名称随级联更新自动生效，无需改动 |
| 老师费率匹配 | 级联保证改名后 `teacher_pay_rate.grade_band` 仍能命中新名称 |
| 历史数据 | 订单/收据快照不受影响 |
| 1-on-1 标签页 | 排序逻辑共享，若该产品配置了顺序则同样生效；管理入口仅 Group Class 显示（可后续按需开放） |

## 8. 边界情况

1. 改名为已有其他 grade 的名称 → 校验拒绝，报错提示。
2. 批量内两行改成同一个名 → 校验拒绝。
3. 配置行存在但价格行已清空 → 读取时该 grade 不展示（分组以价格行为准），配置残留无副作用。
4. 未选中产品 / GROUP 模式之外 → 不显示入口。
5. 空名称 → 校验拒绝。
6. 并发改名两次保存 → 后提交者以提交内容为准，级联 UPDATE 按旧名匹配，若旧名已不存在则 0 行命中，幂等无副作用。

## 9. 验收标准

1. Group Class 标签页选中产品后可打开 Manage grades，弹窗列出该产品全部 grade。
2. 修改名称并保存后：价格行、班级、老师费率作用域同步更新；历史订单快照不变。
3. 调整顺序并保存后：页面 grade 班卡片按配置顺序显示；刷新后保持。
4. 未配置顺序的 grade 按字典序排在已配置项之后。
5. 表单 grade 下拉包含改名后的新名称。
6. 改名撞已有名称时保存被拒绝并有明确提示。
7. 后端单测覆盖：级联更新正确性、重名校验、自动发现合并逻辑。
