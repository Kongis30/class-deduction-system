# JIN V2 部署：移除老师确认时间窗口

- 源码提交：`main_v2` `d6d143c`（`Remove teacher lesson confirmation time window`）。
- 部署日期：2026-10-24（Asia/Shanghai）。
- 发布目录：`/home/class-deduction-system/releases/v2-20261024-d6d143c-no-confirm-window`。
- 部署范围：前端静态资源与后端 JAR；未修改数据库结构或业务数据。

## 修复内容

- 老师可以确认任意未来日期的 1-on-1、老师自助课次和团课；教师身份、可操作状态及管理员审核流程保持原样。
- 移除用户指南中“仅可提前 14 天确认”的说明，并增加远期确认回归测试。

## 部署问题与处理

- 首次启动时，`DatabaseTargetGuard` 解析到旧库 `class_deduction_system`，而 V2 profile 要求 `class_deduction_system_v2`，应用按保护逻辑拒绝启动；回滚后旧版本健康，业务数据未触碰。
- 服务器 `DB_V2_URL` 已确认指向本机 `class_deduction_system_v2`。为避免服务配置覆盖 profile 数据源目标，在 `class-deduction.service` 的 V2 启动参数中显式将 `DB_V2_URL`、`DB_V2_USERNAME`、`DB_V2_PASSWORD` 绑定到 `spring.datasource.*`。保护检查和连接池因此使用同一组 V2 配置。
- 当前 systemd 覆盖配置使用显式 V2 参数；后续发布应保留这些参数，不能仅替换 JAR 路径。

## 构建与验证

- 老师自助确认、1-on-1 远期确认、团课远期确认及老师交接定向用例通过；后端生产构建和前端构建通过。全量测试未全部通过，失败项包含既有的交接行为断言与测试夹具问题，详见本次工作记录。
- 后端 JAR SHA-256：`ea992bb1e8f5596f99723679f654c8ee96ac85c543f262c2e9156c733e4ee1a0`。
- 前端 `index.html` SHA-256：`8d52188ae86b3880e13340177ff873ddbe3a5d69c60270ea3e89c26bb59f5bd9`。
- JIN 上发布 JAR 与首页哈希均与本地产物一致；公网健康接口返回 `{"status":"UP"}`，首页 HTTP 200，Nginx 与 `class-deduction.service` 均 active，`nginx -t` 通过。
- 本次没有执行数据库写入或修复线上记录。

## 回滚

- 后端上一运行版本：`/home/class-deduction-system/releases/v2-20261024-cb990b5-weekly-birthday-messages/backend/backend.jar`。
- 前端上一目录：`/home/class-deduction-system/releases/v2-20261024-d64d377-system-message-inbox/front/dist`。
- 将 `class-deduction.service` 的 `ExecStart` 指回上一 JAR，并保留 `EnvironmentFile` 与原有 `WorkingDirectory`；恢复上述前端目录，执行 `systemctl daemon-reload && systemctl restart class-deduction.service && nginx -t && systemctl reload nginx`。
