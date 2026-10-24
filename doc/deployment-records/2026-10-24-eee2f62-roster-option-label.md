# JIN V2 前端部署：精简名单打印班级选项

- 源码：`main_v2` 提交 `eee2f62`。
- 部署时间：2026-10-24（Asia/Shanghai）。
- 发布目录：`/home/class-deduction-system/releases/v2-20261024-eee2f62-roster-option-label`。
- 部署范围：仅前端静态资源。
- 数据库：未执行任何迁移、备份、SQL 或数据修复操作。

## 调整内容

- Print class roster 的班级下拉选项只显示班级名称、校区、project。
- 移除多余的 product 名称，避免选项过长。

## 验证

- 干净 worktree 执行 `npm ci && npm run build` 成功。
- `data-summary-roster-print.spec.ts` 1/1 通过。
- 前端 `index.html` SHA-256：`66fae9148544c6ec8fd3ca1efa9412577ba857c97ec625ce4d055edec9a7f4f6`。
- Data Summary JS（`DataSummaryView-COK5IHOA.js`）SHA-256：`77849c17b753c0388388609ae23de5aac0d138e4a79231f803d6c17b851d251c`。
- 公网首页与 Data Summary JS 的 SHA-256 均与构建产物一致。
- 公网 `/api/actuator/health` 返回 `{"status":"UP"}`。
- 未重启后端或 Nginx，未操作其他服务。

## 回滚

将 `/home/class-deduction-system/front/dist` 指回发布目录 `front/dist.target.before` 记录的旧目录；后端和数据库无需处理。
