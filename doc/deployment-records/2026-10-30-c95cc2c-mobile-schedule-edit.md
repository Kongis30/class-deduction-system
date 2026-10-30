# JIN V2 部署：移动端与 Edit Schedule 更新

- 源码分支：`main_v2`，代码提交 `c95cc2c`（包含 `52a28e9` 的 Edit Schedule 第一页移除历史继承选项）。
- 发布目录：`/home/class-deduction-system/releases/v2-20261030-c95cc2c-full-stack`。
- 前后端由同一干净提交工作树构建；未提交的 Edit Receipt 工作区改动未包含在发布中。
- 前端首页 SHA-256：`fe3c97afa4109ab87c74c205abe036b588ede4502797a16c4c25beb13e4d3f5d`。
- 后端 JAR SHA-256：`bf023d04581b9ea71bdb0682a4bb46f34d105357e969c67b09b0b6d0191e5cd2`。
- 数据库：本次代码差异未包含 schema 或迁移变更，无需执行数据库迁移。部署前手动运行 `jin-db-backup.service` 成功，备份快照 `20261030T052109Z` 已加密推送。

## 验证

- `npm run build` 成功；`mvn -DskipTests package` 成功。
- `class-deduction.service` 与 Nginx 均为 active；公网 `/api/actuator/health` 返回 `{"status":"UP"}`。
- 线上运行 JAR SHA-256 与发布 JAR 一致；公网首页 SHA-256 与发布产物一致。
- 发布后 10 分钟内无新的后端 error 级日志。

## 回滚

- 本次部署前 `current` 和 `front/dist` 均指向 `/home/class-deduction-system/releases/v2-20261030-5ebd7cc-full-stack`。
- 发布目录保存前端入口、`current` 原目标和 `backend/v2.conf.before`；另保存了部署前 systemd 实际运行 JAR 为 `backend/backend.running.before.jar`。
- 回滚时恢复两个入口及 `backend/v2.conf.before`，执行 `systemctl daemon-reload && systemctl restart class-deduction.service && nginx -t && systemctl reload nginx`。
