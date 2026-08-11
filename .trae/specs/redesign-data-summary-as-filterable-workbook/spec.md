# Data Summary Excel 化重做 Spec

## Why

当前 Data Summary 包含页签、标题说明、卡片、搜索区、摘要区和操作区，不符合用户直接查看 Excel 数据表的习惯。页面需要完全参考用户提供的截图，打开后只展示一张可从表头筛选的紧凑宽表。

## What Changes

- 将 Data Summary 页面直接改为接近截图的 Excel 式宽表。
- 以一条报名课程明细为一行，直接展开截图中的学生、学校、年级、报名、电话、接送、付款和金额字段。
- 使用深色紧凑表头、浅蓝数据行、班级分组和分组间隔，支持固定表头及横向滚动。
- 在表头提供列级筛选入口，并集中提供清除全部筛选的能力。
- 移除页签、页面标题说明、汇总卡片、独立搜索区、辅助摘要、详情抽屉和付款操作等表格之外的信息与交互。
- 不新增 Excel 文件导出、表格内直接编辑、数据库字段或后端聚合口径。

## Impact

- Affected specs: Data Summary、报名数据汇总、表头筛选
- Affected code:
  - `frontend/src/views/data-summary/DataSummaryView.vue`
  - `frontend/src/api/data-summary.ts`（仅在前端类型需要同步时）
  - `frontend/e2e/acceptance/` 下的 Data Summary 验收覆盖
- Backend impact: 复用现有 `/v1/data-summary` 接口和现有字段，原则上无需修改

## ADDED Requirements

### Requirement: Data Summary 应呈现为 Excel 式报名宽表

系统 SHALL 只展示一张紧凑宽表，不展示表格之外的业务信息区。

#### Scenario: 用户进入 Data Summary

- **WHEN** 管理员或员工进入 Data Summary
- **THEN** 系统直接展示报名数据宽表
- **AND** 表格至少依次展示 Class、Student Name、School、Grade、Sign Up Date、Phone Number、Phone Number #2、Pick up、Drop off、Pay Method、Period、Tuition、Pickup/Dropoff、Discount、Paid、Balance
- **AND** 每条报名课程明细独占一行，不将多个业务字段合并成摘要卡片
- **AND** 页面不展示页签、标题说明、指标卡片、独立搜索区、辅助摘要或操作列

#### Scenario: 宽表超出可视区域

- **WHEN** 所有列无法在当前窗口宽度内完整显示
- **THEN** 表格区域提供横向滚动
- **AND** 表头在纵向浏览数据时保持可见
- **AND** 列内容保持单行、紧凑且不因自动换行破坏行列对齐

### Requirement: 表格视觉应贴近参考 Excel

系统 SHALL 使用参考截图的分组式表格视觉，同时保持现有系统可读性。

#### Scenario: 展示班级分组

- **WHEN** 当前结果中存在多个班级或课程分组
- **THEN** 数据按 Class 分组连续排列
- **AND** 同一分组使用一致的浅蓝底色
- **AND** 不同分组之间存在清晰的空白或分隔边界
- **AND** Class 值在每个分组起始位置明确展示

#### Scenario: 展示布尔值、付款方式和金额

- **WHEN** 表格渲染 Pick up、Drop off、Pay Method 和金额字段
- **THEN** Pick up 与 Drop off 使用紧凑的 Yes/No 状态样式
- **AND** Pay Method 使用易区分但不过度装饰的紧凑状态样式
- **AND** Tuition、Pickup/Dropoff Fee、Discount、Paid、Balance 使用统一货币格式并右对齐
- **AND** 欠费 Balance 使用醒目的红色表达

### Requirement: 用户应能从表头筛选数据

系统 SHALL 在可筛选列的表头直接提供筛选入口，筛选结果应用于当前宽表。

#### Scenario: 筛选文本列

- **WHEN** 用户打开 Student Name、School、Phone Number 或 Phone Number #2 的表头筛选
- **THEN** 用户可输入关键词筛选当前数据
- **AND** 筛选按不区分大小写的包含关系匹配

#### Scenario: 筛选枚举和布尔列

- **WHEN** 用户打开 Class、Grade、Pick up、Drop off 或 Pay Method 的表头筛选
- **THEN** 系统提供基于当前数据生成的可选值
- **AND** 用户选择值后仅显示匹配行

#### Scenario: 筛选日期和金额列

- **WHEN** 用户打开 Sign Up Date 或 Period 的表头筛选
- **THEN** 用户可按日期或日期范围限制结果
- **WHEN** 用户打开 Tuition、Pickup/Dropoff、Discount、Paid 或 Balance 的表头筛选
- **THEN** 用户可按最小值和最大值限制结果

#### Scenario: 多列组合筛选

- **WHEN** 用户同时设置多个表头筛选
- **THEN** 系统按 AND 关系组合所有有效条件
- **AND** 已启用筛选的表头具有明确激活状态
- **AND** 用户可一键清除全部表头筛选

#### Scenario: 筛选结果为空

- **WHEN** 当前筛选条件没有匹配记录
- **THEN** 表格内部展示明确空状态
- **AND** 空状态提供清除全部筛选的入口

## MODIFIED Requirements

### Requirement: Reports 应接近 Excel 汇总表

系统 SHALL 将 Data Summary 实现为以学生报名明细为行、以原始业务字段为列的单一 Excel 式宽表。筛选入口 SHALL 位于对应列的表头，表格 SHALL 支持班级分组、固定表头和横向滚动。

## REMOVED Requirements

### Requirement: Data Summary 现有附加区域与行级操作

**Reason**: 页签、标题说明、搜索栏、汇总指标、辅助摘要、详情抽屉和付款操作都不在参考 Excel 中，会干扰单表查看目标。

**Migration**: 原有筛选能力迁移到对应列表头。汇总与详情接口可继续保留，但 Data Summary 页面不再展示这些附加区域和操作。
