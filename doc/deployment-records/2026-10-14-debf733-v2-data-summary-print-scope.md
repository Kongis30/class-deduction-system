# JIN V2 部署：隔离 Data Summary 名单打印样式

- 部署目标：JIN V2 前端静态资源与 Spring Boot 后端 JAR。
- 代码版本：`main_v2` 提交 `debf733`。
- 部署时间：2026-10-14 12:33 Asia/Shanghai。
- 前端：`npm ci && npm run build` 成功；线上 `DataSummaryView-CRaLa-hV.css` SHA-256 为 `a12bd978d0a7bf6d424eaee2db44a8890bf1f029f2933b1eb40b48d87ff63da8`。
- 后端：Java 17 下 `mvn -Dmaven.test.skip=true package` 成功；JAR SHA-256 为 `8656358b2ae53ffe357297d3da567a5563b9e97403029e98998d4d6181069ce8`。
- 数据库：本次没有 schema 或业务数据变更，未执行数据库迁移。
- 线上发布目录：`/home/class-deduction-system/releases/v2-20261014-debf733`。
- 前端备份：`/home/class-deduction-system/front/dist.bak_debf733_20261014T043230Z`。
- systemd 配置备份：`/home/class-deduction-system/releases/v2-20261014-debf733/backend/v2.conf.before_backend_retry_20261014T043347Z`。

## 变更

- Data Summary 打印隐藏规则只作用于 `.data-summary-page`，不再隐藏 SPA 路由打开的收据或 1-on-1 页面。
- roster 使用独立命名打印页面，避免该页面的 `@page` 边距设置串到其他页面。

## 生产验证

- `class-deduction.service`、`nginx` 均为 active；`nginx -t` 通过。
- `/api/actuator/health` 返回 HTTP 200 和 `{"status":"UP"}`；首页返回 HTTP 200。
- 登录端点可达，空请求返回 HTTP 400；未登录 Data Summary API 返回 HTTP 401。
- 线上打印样式资源 SHA-256 与本地干净构建产物一致；部署后服务错误日志无记录。
- 首次切换后端 JAR 时遗漏 systemd drop-in 中的 V2 `EnvironmentFile`，导致启动失败。恢复完整原配置后仅替换 `ExecStart` 并再次启动，最终线上运行的是 `debf733` JAR，V2 数据库环境文件配置正常。

## 回滚

- 将 `class-deduction.service.d/v2.conf` 中的 `ExecStart` 恢复到部署前的 `v2-20261014-41c178b/backend/v2-backend-41c178b.jar`，保留原 V2 `EnvironmentFile` 行，然后执行 `systemctl daemon-reload && systemctl restart class-deduction.service`。
- 将线上 `/home/class-deduction-system/front/dist` 移开，并把 `dist.bak_debf733_20261014T043230Z` 恢复为 `dist`，然后执行 `nginx -t && systemctl reload nginx`。
