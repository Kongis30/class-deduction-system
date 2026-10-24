# JIN V2 前端部署：修复 Data Summary 空白页

- 源码：`main_v2` 提交 `9dbe316`。
- 部署时间：2026-10-24（Asia/Shanghai）。
- 发布目录：`/home/class-deduction-system/releases/v2-20261024-9dbe316-fix-visible-refund-rows`。
- 部署范围：仅前端静态资源。
- 数据库：未执行任何迁移、备份、SQL 或数据修复操作。

## 问题原因

- 移除 Saturday 季节筛选时，误删了 `visibleStoppedRows` 和 `visibleRefundRows` 两个 computed 定义。
- 模板仍引用 `visibleRefundRows`，页面加载触发 `ReferenceError: visibleRefundRows is not defined`，导致 Data Summary 空白。

## 修复内容

- 恢复 `visibleStoppedRows` 和 `visibleRefundRows` 计算逻辑，继续按月份和表格列筛选停课/退款行。
- 名单打印 E2E 增加 `pageerror` 收集断言，防止页面运行时异常再次被漏过。
- 使用构建产物启动本地 preview 执行回归，避免复用其他 dev server 造成假通过。
- 部署后直接对生产域名执行同一 E2E（API 全部 mock，不访问数据库），验证页面正常渲染和名单打印。

## 验证

- 干净 worktree 执行 `npm ci && npm run build` 成功。
- 本地 preview + `data-summary-roster-print.spec.ts` 1/1 通过。
- 生产域名 + 同一 E2E 1/1 通过，页面无运行时异常。
- 前端 `index.html` SHA-256：`22bf4db0d03ef89b49a9fe95b78c8588e72b324e1661e45ab0cf334825fdeeb6`。
- Data Summary JS（`DataSummaryView-B-FIULHA.js`）SHA-256：`2fa2d0c94165bfbb6f4c7cc25fa1bbe3c38ea794ff18b70caac79f1001e66083`。
- 公网首页与 Data Summary JS 的 SHA-256 均与构建产物一致。
- 公网 `/api/actuator/health` 返回 `{"status":"UP"}`。
- 未重启后端或 Nginx，未操作其他服务。

## 回滚

将 `/home/class-deduction-system/front/dist` 指回发布目录 `front/dist.target.before` 记录的旧目录；后端和数据库无需处理。
