# JIN V2 前端部署：Formal Leave 显示教师课时

- 代码版本：`main_v2` 提交 `f031f4e`。
- 部署时间：2026-10-15 10:36 Asia/Shanghai（2026-10-15 02:36 UTC）。
- 部署对象：仅 Class Deduction V2 前端静态资源；后端、数据库结构和业务数据未变更。
- 构建：干净 worktree 执行 `npm ci && npm run build` 成功，前端共 88 个文件。
- 发布包 SHA-256：`db4ae1b2aea4b1bbd5109fdf67eec0293e4856ad0bc69af02b13ea281b4147ea`。
- 关键资源 `assets/OneOnOneDeductionView-CMobM_J4.js` SHA-256：`78f5a1717422a4b6baf42c0e016797d7aa50c572116774928a658b1b61fa6d3a`。
- jin 部署前端目录备份：`/home/class-deduction-system/front/dist.bak_f031f4e_20261015T023625Z`。
- 后端版本未变，线上 `DEPLOY_VERSION` 仍为 `cf7ae15 main_v2 2026-10-15 (hours semantics alignment)`。

## 变更

- 1-on-1 消课窗口选择 Formal Leave 时，显示本节课排定教师及本节课课时。
- 保留 Formal Leave 不扣课并自动追加补课的既有行为。

## 生产验证

- `nginx -t` 通过；Nginx 与 `class-deduction.service` 均为 active。
- 公网首页返回 HTTP 200；`/api/actuator/health` 返回 HTTP 200 / `UP`。
- 线上课消 JS 资源 SHA-256 与本地产物一致，且包含提示文案。

## 回滚

将当前 `/home/class-deduction-system/front/dist` 移走，并将 `/home/class-deduction-system/front/dist.bak_f031f4e_20261015T023625Z` 恢复为 `dist`，然后执行 `nginx -t && systemctl reload nginx`。
