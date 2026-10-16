# JIN V2 前端补充部署：按旧规则快照边界拆分课次

- 代码来源：`main_v2` 提交 `b0c5394`。
- 部署时间：2026-10-17（Asia/Shanghai）。
- 部署范围：前端静态资源；复用 `51691c2` 后端 JAR，无数据库迁移或数据修复。
- 修复内容：同一旧规则 ID 被复用时，前端按 API 返回的 `legacySlotBoundary` 和课次日期把历史课次分到边界前，避免已消课历史被显示在当前周期分隔线之后。
- 构建：前端 `npm ci && npm run build` 成功。
- 发布目录：`/home/class-deduction-system/releases/v2-20261017-b0c5394`。
- 前端 `index.html` SHA-256：`e0bd9c8fae0902ddb8ff0bbbb52cfe1c8c6d783d0c725de85eca4ebfe5567fcf`。
- 后端复用：`/home/class-deduction-system/releases/v2-20261017-b0c5394/backend/v2-backend-51691c2.jar`。
- 回滚目标：`/home/class-deduction-system/releases/v2-20261017-51691c2`。

## 生产验证

- `class-deduction.service` 为 active，systemd 启动命令指向本次发布目录中的后端 JAR。
- 公网 `/api/actuator/health` 返回 `{"status":"UP"}`；公网首页 HTTP 200。
- `nginx -t` 通过；部署后最近 5 分钟服务错误级日志无记录。

## 复核说明

Bowen Deng / Ms. Lau 当前排课周期从 2026-10-11 15:16 开始，当前两个有效时段合计分配 8 小时。11 小时已确认消课均在该边界之前，属于历史周期；本次只修正前端明细分组，没有修改课时数据或余额口径。
