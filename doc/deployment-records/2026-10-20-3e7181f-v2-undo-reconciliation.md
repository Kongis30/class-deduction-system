# JIN V2 部署：修复消课撤销的后续排课处理

- 源码分支与提交：`main_v2` `3e7181f433fe523732b6ff9f12f2084714fedbbf`。
- 部署时间：2026-10-20（Asia/Shanghai）。
- 发布目录：`/home/class-deduction-system/releases/v2-20261020-3e7181f-undo-fix`。
- 部署范围：仅 V2 后端 JAR；前端和数据库未变更。
- 修复内容：撤销普通消课／收费取消时按实际扣除课时冲正，并依据当前有效排课重新校准后续课次；不再要求历史未来课次快照，当前周期 Allocated 不变。
- 构建：干净检出 `3e7181f`；Java 24 使用 Java 17 release 编译；`mvn -Dmaven.test.skip=true clean package` 成功。计划 1.5h、实际扣 2h 的定向撤销测试通过。全量测试未运行；现有其他测试源码存在构造器签名不一致，无法通过统一 test-compile。
- 后端 JAR SHA-256：`03714c16d5792083cb246c2f626316aa855493ad9540fe17b49e7fd0fdde499e`。
- 回滚依据：发布目录中的 `backend/v2.conf.before`、`backend/DEPLOY_VERSION.before` 和 `current.previous-target`。恢复这些文件并将 `current` 指回原版本后，执行 `systemctl daemon-reload && systemctl restart class-deduction.service`。

## 生产验证

- `class-deduction.service` 为 active，systemd 命令及 `current` 均指向本次发布；部署版本标记为 `3e7181f`。
- 公网 `/api/actuator/health` 返回 `{"status":"UP"}`，首页 HTTP 200。
- JIN 发布 JAR SHA-256 与本地构建产物一致；部署后 systemd 错误级日志无新增条目。
