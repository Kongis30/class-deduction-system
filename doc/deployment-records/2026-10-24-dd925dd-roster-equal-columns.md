# JIN V2 前端部署：班级名单 Student / School 列等宽

- 源码：`main_v2` 提交 `dd925dd`。
- 部署时间：2026-10-24（Asia/Shanghai）。
- 发布目录：`/home/class-deduction-system/releases/v2-20261024-dd925dd-roster-equal-columns`。
- 部署范围：仅前端静态资源。
- 数据库：未执行任何迁移、备份、SQL 或数据修复操作。

## 调整内容

- 打印名单的 Student 和 School 列由 60% / 40% 改为各 50%。
- 多列布局下每对 Student / School 列宽度一致。
- 字号估算同步按等宽单元格计算，继续保证单页打印。

## 验证

- 干净 worktree 执行 `npm ci && npm run build` 成功。
- `data-summary-roster-print.spec.ts` 1/1 通过，并新增所有打印列宽度一致的断言。
- 前端 `index.html` SHA-256：`e22710b8aa17ba2b9f1f567b4f04ec7afa36f2782718066e7ef21a71c77f5788`。
- Data Summary JS（`DataSummaryView-TmDoRQ8m.js`）SHA-256：`ce2d6da8269ae87d502320c64b919380129cb0cd8d75fc73cf8516b111f4e63b`。
- 公网首页与 Data Summary JS 的 SHA-256 均与构建产物一致。
- 公网 `/api/actuator/health` 返回 `{"status":"UP"}`。
- 未重启后端或 Nginx，未操作其他服务。

## 回滚

将 `/home/class-deduction-system/front/dist` 指回发布目录 `front/dist.target.before` 记录的旧目录；后端和数据库无需处理。
