# JIN V2 私教消课科目修复部署

- 目标：`class.jinbesttutoring.com` 的 V2 后端与 `class_deduction_system_v2`。
- 时间：2026-10-11 20:21 UTC（北京时间 2026-10-12 04:21）。
- 发布代码：`codex/production-subject-repair@9c256d9`，以线上原版本 `9584655` 为基线，移植 `main_v2@a86a56a` 的科目修复及临时课科目快照。未包含尚未上线的私教签字历史功能。
- 发布目录：`/home/class-deduction-system/releases/v2-20261012-9c256d9`。
- JAR SHA-256：`e7ad526b1eae4778da060c28dee7e3eea0f55be8bb213e6141269213bf549449`。
- 前端与数据库结构未变更。

## 数据修复

更新前已备份整个 V2 数据库：`/home/class-deduction-system/db-backups/pre_subject_repair_20261011T162402Z.sql.gz`，SHA-256 为 `2ede9ab3595d63aec15e7d0ea2b825cc730e5f6cc2a5eabf3ad6f846f74c6c8a`，`gzip -t` 通过。

1. 从原排课时段的 `subject_name` 补齐 **92 条**已产生消课记录的常规课次。审计查询中出现的 99 行包含同一课次关联多条 `lesson_record` 的重复，实际课次为 92 条。修复后常规课次的已记录 subject 缺失数为 0。
2. 一条临时课次与已删除的原常规课次在课包、学生、老师、起止时间上完全一致，原时段科目为 `Vocabulary`；据此补齐 **1 条**。
3. 剩余一条临时课次所在课包的所有有效排课时段均为 `AP`，按课包唯一有效科目规则补齐 **1 条**。后端新增相同规则：仅当课包有效排课科目唯一时，创建临时课次才继承该科目；多科目课包不自动推断。

最终复查：**94 条**已记录课次的 subject 缺失已清零（92 条常规课次及 2 条临时课次）。

## 验证与回滚

- `RecurringScheduleRuleServiceImplTest`、`LessonOccurrenceServiceImplTest`、`LessonRecordServiceImplTest`、`ScheduleAdjustmentServiceImplTest` 共 95 项通过；Maven 打包完成。
- 最终服务重启后 `class-deduction.service` 为 `active`；公网 `/api/actuator/health` 返回 HTTP 200、`UP`；`journalctl` 的近期错误级别日志为空。
- 当前版本配置备份：`/home/class-deduction-system/releases/v2-20261012-9c256d9/v2.conf.before`。如需回滚代码，恢复该配置，执行 `systemctl daemon-reload && systemctl restart class-deduction.service`。
- 数据库完整备份可用于提取单条原值。生产环境持续写入时，不应直接整体覆盖恢复，以免丢失备份之后的新业务数据。
