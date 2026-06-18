# 低额度提醒邮件按计费类型重构 Spec

## Why
当前 Notification 设置页的 Low Balance 邮件存在两类问题：
1. **管理员无法自助设置阈值**：Events 标签页只有开关与模板编辑，阈值 `thresholdValue` 被静默透传，UI 无任何入口可改。
2. **阈值语义与计费口径不匹配且覆盖不全**：系统存在三种计费口径——1v1 按课时（0.5h units）、班课 `PER_SESSION` 按课次、班课 `MONTHLY` 按月份；而当前只有单一 0.5h-unit 阈值，且低额度邮件仅在 1v1 核销路径触发（[LessonRecordServiceImpl.java#L322-335](file:///Users/bytedance/whw/test/class-deduction-system/backend/src/main/java/com/classdeduction/system/modules/lesson/service/impl/LessonRecordServiceImpl.java#L322-L335)），班课从不触发。

## What Changes
- 将单一 `LOW_BALANCE` 触发事件拆分为三个**相互独立、可单独开关**的事件，同属「Low Balance」大类，在 UI 中以下拉/分组形式聚合展示：
  - `LOW_BALANCE_HOUR`：1v1 按课时，阈值单位 = 0.5h units（剩余 `remainingUnits` ≤ 阈值时提醒）。
  - `LOW_BALANCE_SESSION`：班课 `PER_SESSION` 按课次，阈值单位 = 课次数（剩余课次 ≤ 阈值时提醒）。
  - `LOW_BALANCE_MONTH`：班课 `MONTHLY` 按月份，阈值单位 = 剩余月份数（到期月份距当前 ≤ 阈值时提醒）。
- 每个事件可**独立配置**：启用开关、阈值、邮件模板、收件人。
- 新增**定时扫描任务**统一评估三类低额度并入队邮件，使班课（`PER_SESSION` / `MONTHLY`）也纳入提醒范围；1v1 保留核销时即时触发并复用同一判定逻辑。
- UI（[NotificationSettingsView.vue](file:///Users/bytedance/whw/test/class-deduction-system/frontend/src/views/system/NotificationSettingsView.vue)）Events 标签页：Low Balance 大类下展开三个子事件，每个子事件提供阈值输入框（带单位提示）、开关、模板编辑入口。
- **去重**：同一课包/报名在阈值满足时，避免每轮扫描重复发送（按已发送状态去重）。
- **BREAKING**：触发类型枚举由 `LOW_BALANCE` 变更为三个新类型；需提供数据迁移把现有 `LOW_BALANCE` 规则迁移到 `LOW_BALANCE_HOUR` 并新增另外两条规则。

## Impact
- Affected specs: 通知触发规则、低额度提醒、Dashboard 低额度统计（阈值来源变化）。
- Affected code:
  - 后端：`NotificationTriggerRuleEntity`、`NotificationOutboxServiceImpl`、`LessonRecordServiceImpl`、新增低额度扫描 Scheduler、`SystemNotificationServiceImpl`、`DashboardServiceImpl`（`getLowBalanceThreshold` 改读 `LOW_BALANCE_HOUR`）、seed/migration SQL。
  - 前端：`NotificationSettingsView.vue`、`@/api/system.ts`、`@/types/domain.ts`。

## ADDED Requirements

### Requirement: 三类独立的低额度触发事件
系统 SHALL 提供三个相互独立、可单独启用/停用的低额度触发事件，分别对应三种计费口径，且在概念上同属「Low Balance」大类。

#### Scenario: 1v1 按课时阈值
- **WHEN** 一个 1v1 课包的 `remainingUnits` 小于等于 `LOW_BALANCE_HOUR` 规则的阈值（单位 0.5h units），且该规则启用
- **THEN** 系统入队一封低额度提醒邮件，邮件正文展示剩余课时（hours）

#### Scenario: 班课按课次阈值
- **WHEN** 一个 `PER_SESSION` 班课报名的剩余课次小于等于 `LOW_BALANCE_SESSION` 规则的阈值（单位：课次），且该规则启用
- **THEN** 系统入队一封低额度提醒邮件，邮件正文展示剩余课次

#### Scenario: 班课按剩余月份阈值
- **WHEN** 一个 `MONTHLY` 班课报名的到期月份距当前月份的剩余月份数小于等于 `LOW_BALANCE_MONTH` 规则的阈值（单位：月份），且该规则启用
- **THEN** 系统入队一封低额度提醒邮件，邮件正文展示剩余月份与到期月份

#### Scenario: 单独开关互不影响
- **WHEN** 管理员仅停用 `LOW_BALANCE_MONTH`
- **THEN** `LOW_BALANCE_HOUR` 与 `LOW_BALANCE_SESSION` 仍按各自配置正常触发，月份事件不再发送

### Requirement: 管理员自助配置阈值
系统 SHALL 允许管理员在 Notification 设置页为每个低额度事件独立设置阈值、开关与邮件模板，并按单位给出提示文案。

#### Scenario: 修改并保存阈值
- **WHEN** 管理员在 Events 标签页将 `LOW_BALANCE_SESSION` 阈值改为 3 并保存
- **THEN** 后端持久化该阈值，后续扫描以剩余课次 ≤ 3 作为触发条件

#### Scenario: 阈值单位提示
- **WHEN** 管理员查看三个子事件的阈值输入
- **THEN** 每个输入框旁分别展示其单位（课时 hours / 课次 sessions / 月份 months）的说明

### Requirement: 定时扫描触发班课低额度提醒
系统 SHALL 通过定时任务周期性扫描所有有效课包/班课报名，按对应计费类型与阈值判定低额度并入队邮件，并对已提醒对象去重，避免重复发送。

#### Scenario: 班课纳入提醒
- **WHEN** 定时任务运行且存在剩余课次低于阈值的 `PER_SESSION` 班课报名
- **THEN** 系统为该报名的监护人入队一封低额度提醒邮件

#### Scenario: 去重不重复发送
- **WHEN** 同一课包在连续两轮扫描中均处于低额度状态，且上一封提醒尚未失效
- **THEN** 第二轮扫描不再重复入队相同提醒

## MODIFIED Requirements

### Requirement: 1v1 核销时的低额度提醒
1v1 课程核销完成后，系统 SHALL 依据 `LOW_BALANCE_HOUR` 规则（而非旧的 `LOW_BALANCE`）判定是否入队低额度提醒，判定逻辑与定时扫描保持一致。

#### Scenario: 核销后即时提醒
- **WHEN** 1v1 课程核销后课包 `remainingUnits` ≤ `LOW_BALANCE_HOUR` 阈值且规则启用
- **THEN** 系统在核销事务后入队低额度提醒邮件

## REMOVED Requirements

### Requirement: 单一 LOW_BALANCE 触发类型
**Reason**: 单一 0.5h-unit 阈值无法表达课次/月份语义，且无法分别开关。
**Migration**: 数据迁移将现有 `trigger_type='LOW_BALANCE'` 规则改名为 `LOW_BALANCE_HOUR`（保留其 `template_id`、`threshold_value`、`enabled`、收件人配置），并新增 `LOW_BALANCE_SESSION`、`LOW_BALANCE_MONTH` 两条规则（默认沿用同一模板、合理默认阈值、默认启用状态可保守设为停用）。
