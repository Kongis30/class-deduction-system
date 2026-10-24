# JIN V2 前端部署：合并班级名单下拉中的重复班级

- 源码：`main_v2` 提交 `16a7a8c`。
- 部署时间：2026-10-24（Asia/Shanghai）。
- 发布目录：`/home/class-deduction-system/releases/v2-20261024-16a7a8c-dedupe-roster-classes`。
- 部署范围：仅前端静态资源。
- 数据库：未执行任何迁移、备份、SQL 或数据修复操作。

## 问题原因

- 班级下拉原先用 `product + campus + classId` 作为分组键。
- 历史季节班或迁移副本可能为同一个展示班级保留多个 `classId`，但班级名称、校区、project 完全相同。
- 下拉只显示班级、校区、project，因此出现两个看起来完全相同的选项。

## 调整内容

- 班级名单分组改为按展示身份合并：`product + campus + className + projectName`。
- 同一展示班级的多条历史班级记录会合并为一个选项，学生仍按 `studentId` 去重。
- 增加浏览器回归数据：同一展示班级使用两个不同 `classId`，断言下拉中只出现一次。

## 验证

- 干净 worktree 执行 `npm ci && npm run build` 成功。
- `data-summary-roster-print.spec.ts` 1/1 通过。
- 前端 `index.html` SHA-256：`d55eac50470461478c8a3862bcaaddb8611c79da1b6483217fe8424eebe1d307`。
- Data Summary JS（`DataSummaryView-D7cCxkZ0.js`）SHA-256：`b12278115d998b2d3a62d9e4ed5ee56b7bc32a02451ccb16a9d3cd3a787b634e`。
- 公网首页与 Data Summary JS 的 SHA-256 均与构建产物一致。
- 公网 `/api/actuator/health` 返回 `{"status":"UP"}`。
- 未重启后端或 Nginx，未操作其他服务。

## 回滚

将 `/home/class-deduction-system/front/dist` 指回发布目录 `front/dist.target.before` 记录的旧目录；后端和数据库无需处理。
