# JIN V2 前端部署：名单打印班级选择按校区分组

- 源码：`main_v2` 提交 `dfb782b`。
- 部署时间：2026-10-24（Asia/Shanghai）。
- 发布目录：`/home/class-deduction-system/releases/v2-20261024-dfb782b-roster-picker-campus-groups`。
- 部署范围：仅前端静态资源。
- 数据库：未执行任何迁移、备份、SQL 或数据修复操作。

## 调整内容

- Print class roster 弹窗宽度调整为 640px，标题、说明文字和下拉框高度按参考截图优化。
- 班级下拉按校区分组，校区作为分组标题显示。
- 每个班级选项左侧显示班级名称，右侧独立显示 project 和去重后的学生人数，不再把班级和 project 拼成一段文字。
- 选中班级后才显示 registration plan 下拉框；默认仍为 `All plans`。
- 保留此前按展示身份合并重复班级的逻辑，同一展示班级只出现一次。

## 验证

- 干净 worktree 执行 `npm ci && npm run build` 成功。
- `data-summary-roster-print.spec.ts` 1/1 通过，覆盖校区分组、班级选项、去重和 plan 选择。
- 前端 `index.html` SHA-256：`a90c5f96ec7ff9e3c0ff25d7caccec7788aa47c681a9c32c809302eff3ee1518`。
- Data Summary JS（`DataSummaryView-CqPF_vcb.js`）SHA-256：`92b5f0668f6eb68dc0d9f9ee8fa1fd2e19098a7a9c24f52ce17140eedfb45733`。
- 公网首页与 Data Summary JS 的 SHA-256 均与构建产物一致，线上 JS 包含校区分组样式。
- 公网 `/api/actuator/health` 返回 `{"status":"UP"}`。
- 未重启后端或 Nginx，未操作其他服务。

## 回滚

将 `/home/class-deduction-system/front/dist` 指回发布目录 `front/dist.target.before` 记录的旧目录；后端和数据库无需处理。
