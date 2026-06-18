# Checklist

- [x] 数据迁移脚本将现有 `LOW_BALANCE` 规则迁移为 `LOW_BALANCE_HOUR`，并新增 `LOW_BALANCE_SESSION`、`LOW_BALANCE_MONTH` 两条规则
- [x] seed_test_data.sql 与 mysql_schema_from_db.sql 与迁移保持一致
- [x] 1v1 核销路径按 `LOW_BALANCE_HOUR` 规则及阈值（0.5h units）判定并入队提醒
- [x] PER_SESSION 班课按剩余课次与 `LOW_BALANCE_SESSION` 阈值判定并入队提醒
- [x] MONTHLY 班课按剩余月份与 `LOW_BALANCE_MONTH` 阈值判定并入队提醒
- [x] 三个事件可分别独立启用/停用，互不影响
- [x] 定时扫描任务可周期性触发班课低额度提醒，且对已提醒对象去重不重复发送
- [x] DashboardServiceImpl 低额度阈值来源切换为 `LOW_BALANCE_HOUR`，统计不回归
- [x] 邮件变量按事件类型正确渲染（剩余课时/课次/月份、到期月份）
- [x] 前端 Events 页将 Low Balance 大类展开为三个子事件，分别可配置开关与阈值
- [x] 前端阈值输入展示对应单位提示（hours/sessions/months）并能保存成功
- [x] 后端单元测试覆盖三类触发判定与去重逻辑且通过
