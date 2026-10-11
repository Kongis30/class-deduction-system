# JIN V2 私教消课科目修复部署

- 目标：`class.jinbesttutoring.com` 的 V2 后端与 `class_deduction_system_v2`。
- 时间：2026-10-11 16:25 UTC（北京时间 2026-10-12 00:25）。
- 发布代码：`codex/production-subject-repair@e32040c`，以线上原版本 `9584655` 为基线，移植 `main_v2@a86a56a` 的科目修复及新课次科目快照。未包含尚未上线的私教签字历史功能。
- 发布目录：`/home/class-deduction-system/releases/v2-20261011-e32040c`。
- JAR SHA-256：`7a9516d540c9bb5d8de69a482d66f35269f85b4fa228b319693512d90f8a124e`。
- 前端与数据库结构未变更。

## 数据修复

更新前已备份整个 V2 数据库：`/home/class-deduction-system/db-backups/pre_subject_repair_20261011T162402Z.sql.gz`，SHA-256 为 `2ede9ab3595d63aec15e7d0ea2b825cc730e5f6cc2a5eabf3ad6f846f74c6c8a`，`gzip -t` 通过。

1. 从原排课时段的 `subject_name` 补齐 **92 条**已产生消课记录的常规课次。审计查询中出现的 99 行包含同一课次关联多条 `lesson_record` 的重复，实际课次为 92 条。修复后常规课次的已记录 subject 缺失数为 0。
2. 一条已确认临时课次与被删除的原常规课次在课包、学生、老师、起止时间上完全一致，原时段科目为 `Vocabulary`；据此补齐 **1 条**。
3. 另有一条 Jennie Yu 于 2026-09-30 16:30–18:30 的线上临时课次，创建时未存科目，且没有同时间的原课次可供证明。等待业务确认后再补齐，避免将后来配置的科目直接推断为该课科目。

## 验证与回滚

- `RecurringScheduleRuleServiceImplTest`、`LessonOccurrenceServiceImplTest`、`LessonRecordServiceImplTest` 共 67 项通过；Maven 打包完成。
- 服务重启后 `class-deduction.service` 为 `active`；内网和公网 `/api/actuator/health` 均返回 HTTP 200、`UP`；`journalctl` 的近期错误级别日志为空。
- 服务配置备份：`/home/class-deduction-system/releases/v2-20261011-e32040c/v2.conf.before`。如需回滚代码，恢复该配置，执行 `systemctl daemon-reload && systemctl restart class-deduction.service`。
- 数据库完整备份可用于提取单条原值。生产环境持续写入时，不应直接整体覆盖恢复，以免丢失备份之后的新业务数据。
