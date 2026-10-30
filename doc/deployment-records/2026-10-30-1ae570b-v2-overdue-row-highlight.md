# JIN V2 部署：历史未处理课次背景高亮

- 部署源码：`main_v2` 提交 `1ae570b`。
- 部署范围：同一提交构建的完整前端静态资源与后端 JAR。
- 发布目录：`/home/class-deduction-system/releases/v2-20261030-1ae570b-overdue-highlight`。
- 前端首页 SHA-256：`423662a659b3ddda35868723ef668705c7a23008f9dd5c57f8a75e694c838c45`。
- 后端 JAR SHA-256：`279ed8cb5fcd6ae72558bec9bd39c956c9db158aacd492f2d3bbfc8e1a0277a9`。
- 数据库：未修改 schema、migration 或线上业务数据。

## 验证

- 前端类型检查及生产构建成功；后端生产包构建成功。
- 部署后 `class-deduction.service` 与 Nginx 均为 active，公网 `/api/actuator/health` 返回 `{"status":"UP"}`。
- 公网首页与发布目录首页 SHA-256 一致；1-on-1 消课页面的 JS 与 CSS 静态资源均返回 HTTP 200。
- 部署后服务 error 级日志无新增条目。

## 回滚

- 发布前 `current` 与 `front/dist` 均指向 `/home/class-deduction-system/releases/v2-20261030-ed04633-edit-receipt`。
- 新发布目录保留 `current.previous-target`、`front/dist.previous-target`、`backend/v2.conf.before` 和 `backend/DEPLOY_VERSION.before`。
- 回滚时恢复前后端入口与 systemd 配置，执行 `systemctl daemon-reload && systemctl restart class-deduction.service && nginx -t && systemctl reload nginx`。
