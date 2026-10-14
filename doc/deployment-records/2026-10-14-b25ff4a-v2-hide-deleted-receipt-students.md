# JIN V2 部署：收据页隐藏已删除学生

- 代码版本：`main_v2` 提交 `b25ff4a`。
- 部署时间：2026-10-14 23:38 Asia/Shanghai（2026-10-14 15:38 UTC）。
- 部署对象：仅后端 JAR；前端静态资源、数据库结构和数据均未修改。
- 后端构建：JDK 24，`mvn -Dmaven.test.skip=true package` 成功。
- 后端 JAR SHA-256：`efb509de15dde16341f91ee011a3f0a463d46388589e113ed5f94d12a8f97305`。
- 线上发布目录：`/home/class-deduction-system/releases/v2-20261014-b25ff4a`。
- systemd 配置和版本标记备份：发布目录 `backend/v2.conf.before_b25ff4a`、`backend/DEPLOY_VERSION.before_b25ff4a`。

## 变更

- 收据页学生名单始终过滤软删除档案；有效学生仍可通过有效收据记录进入名单，即使没有活动报名单。
- 已删除学生关联的收据历史没有删除，财务及审计记录保持不变。

## 生产验证

- `class-deduction.service` 为 active，运行 JAR 指向本次发布目录。
- `https://class.jinbesttutoring.com/api/actuator/health` 返回 `{"status":"UP"}`。
- 未运行测试。仓库现有测试源码与当前排课服务签名不匹配，默认 Maven 打包在 testCompile 阶段失败；生产源码编译通过，并通过 `-Dmaven.test.skip=true` 完成生产包构建。

## 回滚

- 将 `/etc/systemd/system/class-deduction.service.d/v2.conf` 恢复为发布目录中的 `backend/v2.conf.before_b25ff4a`，将 `/home/class-deduction-system/backend/DEPLOY_VERSION` 恢复为 `backend/DEPLOY_VERSION.before_b25ff4a`，然后运行 `systemctl daemon-reload && systemctl restart class-deduction.service`。
