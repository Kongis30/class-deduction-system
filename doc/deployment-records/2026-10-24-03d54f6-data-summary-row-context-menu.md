# JIN V2 前端部署：Data Summary 整行右键菜单

- 修复提交：`main_v2` `03d54f6`（`修复 Data Summary 整行右键菜单`），基于线上版本 `4d1bb4d`。
- 部署日期：2026-10-24（Asia/Shanghai）。
- 部署范围：仅替换 Data Summary 所在的前端静态资源；后端、数据库结构及业务数据均未修改。
- 根因：右键监听器只绑定在 Student Name 单元格，右键记录行的其他单元格不会触发订单操作菜单。现在监听器绑定在整行，View receipt 仍使用当前行对应的订单 ID。
- 构建与回归：从干净 detached worktree 执行 `npm ci && npm run build` 成功；`receipt-payment-and-purchase-actions.spec.ts` 3/3 通过，覆盖含多个购买行的同一课程记录右键打开原订单收据。
- 发布目录：`/home/class-deduction-system/releases/v2-20261024-03d54f6-row-context-menu/front/dist`。
- 发布包 SHA-256：`505e1f99806e3645e5277f74e184ea093ea5fd2fd068b2b6f1b0d784ebd4f3ca`。
- 前端 `index.html` SHA-256：`13e5994df6b1f0ad6083ef38ab396c2e4a467a99d94c3c96467ae00ce572ea5b`。
- Data Summary JS SHA-256：`d475692cd54a3cb6b835882aa8821c82593c0dd7a9855da3f6bde453540ca328`。

## 线上验证

- `/home/class-deduction-system/front/dist` 已切换到本次发布目录；后端 `current` 继续指向 `v2-20261024-4d1bb4d-same-order-renewal`，后端无需重启。
- Nginx 配置检查与 reload 成功；Nginx、`class-deduction.service` 均为 active。
- 公网首页 HTTP 200，首页 SHA-256 与本地产物一致；Data Summary JS HTTP 200，SHA-256 与本地产物一致。
- 公网 `/api/actuator/health` 返回 `{"status":"UP"}`；部署后检查时错误级服务日志无新增记录。

## 回滚

将 `/home/class-deduction-system/front/dist` 恢复为 `/home/class-deduction-system/releases/v2-20261024-4d1bb4d-same-order-renewal/front/dist`，然后执行 `nginx -t && systemctl reload nginx`。后端和数据库无需处理。
