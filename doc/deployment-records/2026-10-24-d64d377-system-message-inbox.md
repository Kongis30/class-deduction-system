# JIN V2 部署：系统消息收件箱

- 源码提交：`d64d377`（`main_v2`）。
- 发布目录：`/home/class-deduction-system/releases/v2-20261024-d64d377-system-message-inbox`。
- 部署范围：仅前端；后端和数据库无需变更。
- 前端 `index.html` SHA-256：`874ef00fe4853272a380debd058f02dca1358755f1f6813d62091b448e316f98`。

## 验证

- `frontend` 执行 `npm run build` 成功。
- Jin 前端软链接指向本发布目录；Nginx 配置检查及 reload 成功。
- Nginx 与 `class-deduction.service` 均为 active。
- 通过本机 HTTPS 获取的首页 SHA-256 与构建产物一致；`/api/actuator/health` 返回 `UP`。
