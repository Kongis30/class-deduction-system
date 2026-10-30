# JIN V2 部署：移动端适配与排课/收据修正合并发布

- 部署源码：`main_v2`，`5ebd7cc`（相对上次发布 `1bb9f2e` 共 22 个提交，含移动端适配、排课回收与收据更正等前后端改动）。
- 部署范围：前端完整静态资源与匹配的后端 JAR 一起切换到同一 release。
- 发布目录：`/home/class-deduction-system/releases/v2-20261030-5ebd7cc-full-stack`。
- 后端 JAR SHA-256：`1aa9b7ac8b5c97333863d080b5945667e79ce2bbf4a00399e146f230843dc101`；前端首页 SHA-256：`41bcbd339e545489d78edda7015535630003b6a03da03852058aa37379e9f8c7`。服务器、公网首页哈希与构建产物一致。
- 数据库：本次改动未新增/修改表结构（`ScheduleSchemaCompatibilityInitializer` 仅更新字段注释，`ClassGroupEntity` 仅注释），无需数据库迁移。

## 部署前备份

- 数据库备份：`/root/jin-v2-backups/pre-5ebd7cc-20261030T050936Z.sql.gz`，SHA-256 `d73678a516aa2af0374f44f51dfd8112003bb1ba57445ad351e037530c9e056c`，gzip 完整性检查通过。
- 运行中后端 JAR 备份：`/home/class-deduction-system/backend/class-deduction-system-backend-running-SNAPSHOT.jar.bak_5ebd7cc_<UTC 时间戳>`。
- 发布前 systemd 配置：`<release>/backend/v2.conf.before`。

## 验证

- `class-deduction.service`、`nginx` 均为 active；本机与公网 `GET /api/actuator/health` 均返回 `{"status":"UP"}`。
- 运行中后端 JAR SHA-256 与发布目录 `backend.jar` 一致；`front/dist/index.html` 与本地构建产物一致。
- 公网 `https://class.jinbesttutoring.com/` 首页 SHA-256 与发布包一致。
- 登录接口、`/v1/schedule/students/one-on-one`、`/v1/schedule/rules` 均返回 200；本次启动无新的错误级日志。
- 前端 `npm run build` 成功；后端 `mvn -DskipTests package` 成功。

## 回滚

- 前一 release：`/home/class-deduction-system/releases/v2-20261030-1bb9f2e-student-account-groups`。
- 前一前端入口：`/home/class-deduction-system/releases/v2-20261030-1bb9f2e-student-account-groups/front/dist`。
- 回滚步骤：恢复 `current` 与 `front/dist` 两个软链，恢复运行中 JAR 备份与 `<release>/backend/v2.conf.before`，执行 `systemctl daemon-reload && systemctl restart class-deduction.service && nginx -t && systemctl reload nginx`。本次无表结构变更，代码回滚无需删除列；如需完整数据恢复使用上述数据库备份。
