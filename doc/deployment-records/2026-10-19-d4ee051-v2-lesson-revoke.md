# JIN V2：撤销管理员最终处理的课次

- 源码提交：`main_v2` `d4ee051`（`支持撤销已确认课次并留存操作审计`）。
- 发布目录：`/home/class-deduction-system/releases/v2-20261019-d4ee051-lesson-revoke`。
- 后端 JAR SHA-256：`42d649d2e2a7b7aeee3ac0e7458c9a9470e2fe9284482800dc8dbe634f6650fe`。
- 前端 `index.html` SHA-256：`9207612d5a4e21e87884c01dcfde4f44b8450a705c0e7fcac485c1de9784245b`。
- 1-on-1 页面 JS SHA-256：`708e3148df350975c9262d1a615ff2bfaf2f6c4cb284f505a3d060d5e9288cfa`；CSS SHA-256：`fbacd9a59872e8b3306c1005331a6d913e1f9ebffc33c07ea7abe60f49ddf98e`。
- 前后端均从干净 worktree `d4ee051` 构建；前端 `npm run build` 通过，后端 Java 17 `mvn -Dmaven.test.skip=true package` 成功，未运行测试。
- 发布前数据库备份：`/home/class-deduction-system/backups/class_deduction_system_v2-before-d4ee051-20261019.sql.gz`；SHA-256：`63ac4e8759632ea4e4c84e900a7e5ba80f735c2df9a10e41c79f9d6968d0de1c`。
- 已应用 `20261019_01_lesson_revoke_audit_and_schedule_effects.sql`，并确认 `lesson_record_revoke_audit`、`lesson_record_schedule_effect`、`lesson_record_makeup_effect` 三张表存在。
- systemd 服务 `class-deduction.service` 为 `active`；`https://class.jinbesttutoring.com/api/actuator/health` 返回 `{"status":"UP"}`；首页 HTTP 200；Nginx 配置检查通过并 reload；启动错误日志无新增条目。
- 页面功能包含仅管理员撤销最终课次、可选填写原因、保留撤销审计、已结算课次保护，以及撤销按钮与扣课信息同行显示。

## 回滚

将 `/etc/systemd/system/class-deduction.service.d/v2.conf` 恢复为本发布目录中的 `v2.conf.before`，把 `/home/class-deduction-system/front/dist` 指回 `frontend.previous-target` 文件记录的旧目录，然后 `systemctl daemon-reload && systemctl restart class-deduction.service && nginx -t && systemctl reload nginx`。数据库迁移只新增表；若需要恢复数据库，先评估发布后产生的数据，再使用上述备份制定恢复步骤。
