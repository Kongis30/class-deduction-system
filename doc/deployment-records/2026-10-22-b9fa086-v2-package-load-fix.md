# JIN V2 部署：修复课包页初始化失败

- 修复提交：`main_v2` `b9fa086`（`修复课包页移动端分页状态未定义`）。
- 线上基线：`2b557cd`（`v2-20261022-2b557cd-schedule-save`）。
- 兼容构建提交：`3def65d`，以线上基线为父提交，仅移植 `b9fa086` 的 7 行课包分页状态声明，避免连带发布其他尚未上线的提交。
- 部署日期：2026-10-22（Asia/Shanghai）。
- 部署范围：仅 V2 前端静态资源；后端、数据库结构和业务数据均未修改。
- 根因：课包页 setup 中的无限滚动观察器引用 `mobileLoadMoreSentinel` 等分页状态，但线上基线未声明这些状态，导致组件初始化抛 `ReferenceError` 并显示空白页。
- 防回归：发布构建必须执行 `npm ci && npm run build`；该命令先运行 `vue-tsc --noEmit`，检查通过后才由 Vite 打包。禁止用单独的 Vite build 绕过类型检查。

## 构建与发布

- 干净 worktree 执行 `npm ci && npm run build`，`vue-tsc` 和 Vite 构建均通过。
- 发布目录：`/home/class-deduction-system/releases/v2-20261022-3def65d-package-load-fix/front/dist`。
- 上传包 SHA-256：`d1e31b40c2da4f1c6ce0c8d766f1b17b209ac42f9e3e530910af40bb9d58b63b`。
- 新课包页面 JS：`CoursePackagesView-BiEaNV1d.js`，SHA-256 `9269addad1a61c0bed1b2d55edc84e72c6b28167cc20779c9e6f641e7c64f726`。
- 当前 `front/dist` 指向新发布目录；部署前目标保留为 `/home/class-deduction-system/releases/v2-20261022-2b557cd-schedule-save/front/dist`，可用于回滚。

## 验证

- `nginx -t` 通过，Nginx reload 后服务为 active。
- 公网首页与新课包页面 JS 均返回 HTTP 200。
- 公网课包页面 JS 的 SHA-256 与本地构建产物一致。
- 没有替换后端 JAR，没有数据库迁移。
