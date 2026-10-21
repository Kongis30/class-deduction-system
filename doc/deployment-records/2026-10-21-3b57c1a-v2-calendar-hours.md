# JIN V2 前端部署：周日历时间范围扩展至 06:30

- 代码分支与提交：`main_v2` `3b57c1a`（`Expand schedule calendar to 06:30`）。
- 部署时间：2026-10-21 23:12 Asia/Shanghai。
- 部署范围：仅 V2 前端静态资源；后端和数据库未变更。
- 发布目录：`/home/class-deduction-system/releases/v2-20261021-3b57c1a-calendar-range/front/dist`。
- 修改内容：周日历统一从 06:30 显示到 24:00；Schedule 时间选择器允许 06:30 至 23:30 开始的半小时课次；范围外课程按可见区域裁切显示。
- 构建：提交 `3b57c1a` 的干净 detached worktree 中执行 `npm ci && npm run build` 成功（包含 `vue-tsc --noEmit`）。
- 前端归档包 SHA-256：`58fbbd70303d78d259b9d565faad51533b8163018e35aa73f9f3e14a3c399c6a`。
- 部署前备份：`/home/class-deduction-system/front/dist.bak_3b57c1a_20261021T151214Z`；旧目标为 `v2-20261020-b6eac7b-active-only/front/dist`。

## 部署验证

- `nginx -t` 通过并已 reload；Nginx 与 `class-deduction.service` 均为 active，后端最近 20 分钟无错误日志。
- 公网首页 HTTP 200，`/api/actuator/health` HTTP 200 且状态为 `UP`。
- 管理员登录接口 HTTP 200；教师日历分类接口 HTTP 200 / `OK`。
- 公网 `index-DZMTxKy8.js`、`WeeklyTimeSelector-BzWjMEPk.js`、`RecurringScheduleView-B22AFMOh.js` 和 `schedule-time-DOxPRUXe.js` 均与本地构建逐字节校验和一致。

## 回滚

- 将 `/home/class-deduction-system/front/dist` 恢复为 `/home/class-deduction-system/releases/v2-20261020-b6eac7b-active-only/front/dist`，再执行 `nginx -t && systemctl reload nginx`。
