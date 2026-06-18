# Tasks

- [x] Task 1: 数据层与迁移：拆分 LOW_BALANCE 触发类型为三类
  - [x] SubTask 1.1: 在 `doc/migrations/` 新增迁移脚本：将现有 `trigger_type='LOW_BALANCE'` 规则改名为 `LOW_BALANCE_HOUR`（保留 template_id/threshold_value/enabled/收件人）；新增 `LOW_BALANCE_SESSION`、`LOW_BALANCE_MONTH` 两条规则（默认沿用 LOW_BALANCE 模板，给合理默认阈值，默认停用）
  - [x] SubTask 1.2: 同步更新 `backend/doc/seed_test_data.sql` 与 `doc/mysql_schema_from_db.sql`（如需新增列/记录），并新增低额度去重所需的标记字段或复用 `notification_log` 已发送记录做去重
  - [x] SubTask 1.3: 同步更新 `doc/DATABASE_DESIGN.md` 中通知触发规则相关说明

- [x] Task 2: 后端低额度判定与触发重构
  - [x] SubTask 2.1: 在 `NotificationOutboxServiceImpl` 中将 `queueLowBalanceWarning` 重构为按计费类型判定的统一逻辑，分别处理 HOUR(remainingUnits)/SESSION(剩余课次)/MONTH(剩余月份)，并扩展邮件变量（remainingSessions、remainingMonths、expiryMonth 等）
  - [x] SubTask 2.2: 修改 `LessonRecordServiceImpl.queueLowBalanceWarningIfNeeded` 改读 `LOW_BALANCE_HOUR` 规则，复用统一判定逻辑
  - [x] SubTask 2.3: 修改 `DashboardServiceImpl.getLowBalanceThreshold` 改读 `LOW_BALANCE_HOUR` 触发类型
  - [x] SubTask 2.4: 实现低额度去重：入队前检查是否已存在未过期/未失效的同类提醒，避免重复发送

- [x] Task 3: 新增低额度定时扫描任务（覆盖班课）
  - [x] SubTask 3.1: 新增 `LowBalanceScanScheduler`，按可配置间隔扫描有效 1v1 课包与班课报名(PER_SESSION/MONTHLY)，按各自启用规则与阈值入队提醒
  - [x] SubTask 3.2: 计算班课剩余课次（参考 `CoursePackageServiceImpl.resolveStudentTotalUnits` 与已核销课次）与剩余月份（基于 expiryDate/endMonth），传入统一入队逻辑

- [x] Task 4: 后端配置 API 与 VO 扩展
  - [x] SubTask 4.1: 确认 `NotificationTriggerRuleController`/`SystemNotificationServiceImpl` 能正确按三种新触发类型查询与更新规则（含 thresholdValue），必要时补充事件展示名映射（getTriggerDisplayName 增加三类）
  - [x] SubTask 4.2: 补充/调整后端单元测试覆盖三类触发判定与去重

- [x] Task 5: 前端 Notification 设置页改造
  - [x] SubTask 5.1: `@/types/domain.ts` 与 `@/api/system.ts` 增加/调整三类低额度触发规则相关类型与调用
  - [x] SubTask 5.2: `NotificationSettingsView.vue` Events 标签页改为「Low Balance」大类下展开三个子事件（下拉/分组），每个子事件提供独立开关、阈值输入（带单位提示：hours/sessions/months）、模板编辑入口
  - [x] SubTask 5.3: 阈值保存接入 `updateNotificationTriggerRule`，模板编辑沿用现有占位符面板并按事件补充对应占位符（remaining sessions/months）

# Task Dependencies
- Task 2 depends on Task 1
- Task 3 depends on Task 2
- Task 4 depends on Task 1
- Task 5 depends on Task 4
