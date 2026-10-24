# JIN V2 前端部署：移除 Saturday 名单季节筛选按钮

- 源码：`main_v2` 提交 `ef5b40c`。
- 部署时间：2026-10-24（Asia/Shanghai）。
- 发布目录：`/home/class-deduction-system/releases/v2-20261024-ef5b40c-remove-saturday-season-buttons`。
- 部署范围：仅前端静态资源。
- 数据库：未执行任何迁移、备份、SQL 或数据修复操作。

## 调整内容

- 移除 Saturday 产品页签工具栏中的 Fall roster / Spring roster / All roster 按钮。
- 移除移动端更多菜单中的对应入口。
- 移除按季节过滤 Data Summary 行、退款行、停课行的逻辑，以及未分类季节提示。
- 删除不再使用的 `frontend/src/utils/saturday-roster.ts`。
- 班级名单打印的 plan 选择不受影响。

## 验证

- 干净 worktree 执行 `npm ci && npm run build` 成功。
- `data-summary-roster-print.spec.ts` 1/1 通过。
- 前端 `index.html` SHA-256：`985b802d01ccb738a852763cdb3c6dc6209d9f0159def842ae9e0e19614c0523`。
- Data Summary JS（`DataSummaryView-CTCFUtrV.js`）SHA-256：`e37c9fca43ef8e726610f849f19be286eff03a8da2d79b6065fe45bf32fb9646`。
- 公网首页与 Data Summary JS 的 SHA-256 均与构建产物一致；线上 JS 已不含 `Fall roster`。
- 公网 `/api/actuator/health` 返回 `{"status":"UP"}`。
- 未重启后端或 Nginx，未操作其他服务。

## 回滚

将 `/home/class-deduction-system/front/dist` 指回发布目录 `front/dist.target.before` 记录的旧目录；后端和数据库无需处理。
