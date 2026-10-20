# JIN V2 前端部署：课包打印预览与 Available 列布局

- 源码分支与提交：`main_v2` `8abf9f2`。
- 部署日期：2026-10-20（Asia/Shanghai）。
- 发布目录：`/home/class-deduction-system/releases/v2-20261020-8abf9f2-package-print-preview/front/dist`。
- 部署范围：仅前端静态资源；无后端、数据库结构或数据变更。
- 修改内容：Package 详情内的打印按钮直接打开签字表预览，不再跳转到 1-on-1 消课页；打印表格不显示 Status 列；详情弹窗按钮顺序调整；Available 列的课时值保持单行，进度条居中并固定在下方。
- 构建：`frontend` 执行 `npm run build` 成功（包含 `vue-tsc --noEmit` 与 Vite production build）。
- 前端 `index.html` SHA-256：`283ed1091caa1b5f3636669df45fbbeb8702abac3d25ed6afc4b2054cbc486ca`。
- Package 页面 JS SHA-256：`3343b0606861ff24f5ce26ff5173b390fdb31f1f7e1ef50538dca0787c47ff4b`。
- Package 页面 CSS SHA-256：`80ff68fa03c8a7acb6be1026d7dd011189ffd1530929c2e088655da419ca943e`。

## 生产验证

- 公网首页 HTTP 200；首页及 Package 页面 JS/CSS 的线上 SHA-256 与本地构建产物一致。
- Nginx 与 `class-deduction.service` 均为 active；`front/dist` 指向本次发布目录。
- `nginx -t` 通过，Nginx reload 成功。

## 回滚

- 将 `/home/class-deduction-system/front/dist` 符号链接切回本发布目录中的 `front/dist.previous-target` 所记录的旧目录，然后执行 `nginx -t && systemctl reload nginx`。
