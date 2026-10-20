# JIN V2 前端部署：Package 弹窗内签字表打印预览

- 源码分支与提交：`main_v2` `38741e5b7c1696e1e359c77596e48992ae1cd3cd`。
- 部署时间：2026-10-20 10:14 Asia/Shanghai。
- 部署范围：仅前端静态资源；后端和数据库未变更。
- 发布目录：`/home/class-deduction-system/releases/v2-20261020-38741e5-package-print-dialog/front/dist`。
- 修改内容：Package 详情弹窗直接显示 Parent/Teacher Sheet 打印预览，可在弹窗内返回、打印或关闭；复用 1-on-1 页面签字表模板。
- 构建：干净 worktree 中执行 `npm ci && npm run build` 成功，包含 `vue-tsc --noEmit` 与 Vite production build。Vite 报告既有大 chunk 提示，不影响构建。
- SHA-256：`index.html` `ee109ad4fcb4f002c489114d85c1211b29092ea3de8e3dc2c270537aefa9d298`；`CoursePackagesView-_KKnN76N.js` `b11227c643374935fc0dbe9d0803a42a732abb4c985e07f7948f2b447aa1ccbe`；`CoursePackagesView-CE0sM6Xo.css` `0593ec15587371d7c67b31e4dd08829162f6b36ff35cb01bf393221bb2e4f73a`。

## 生产验证

- 发布前端目录原子切换成功；Nginx 配置检查通过并 reload。
- Nginx 与 `class-deduction.service` 均为 active；公网首页 HTTP 200。
- 公网首页、Package 页面 JS/CSS 的 SHA-256 与本地产物一致。

## 回滚

- 发布前 `front/dist` 指向 `/home/class-deduction-system/releases/v2-20261020-8abf9f2-package-print-preview/front/dist`，该路径记录于新发布目录的 `front/dist.previous-target`。
- 恢复该链接并执行 `nginx -t && systemctl reload nginx`。
