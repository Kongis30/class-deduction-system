# JIN V2 前端部署：课包详情直接打印签字表

- 源码分支与提交：`main_v2` `2d0c6e80272b5d887fcb2097841cf3b6dbcdbcf2`。
- 部署时间：2026-10-20 10:22 Asia/Shanghai。
- 部署范围：仅前端静态资源；后端和数据库未变更。
- 发布目录：`/home/class-deduction-system/releases/v2-20261020-2d0c6e8-package-direct-print/front/dist`。
- 修改内容：在 Package 详情弹窗中点击 Print Parent Sheet 或 Print Teacher Sheet 后，直接调用浏览器打印预览；移除中间的应用内预览步骤，签字表仍复用 1-on-1 打印模板。
- 构建：干净 worktree 中执行 `npm ci && npm run build` 成功，包含 `vue-tsc --noEmit` 与 Vite production build。Vite 提示既有大 chunk，不影响构建。
- SHA-256：`index.html` `334773bd2067f21ab2b0b98792d2d2d8c4a33c7f927d4839024ea96af4512591`；`CoursePackagesView-OVwvd_SK.js` `7ee2e0ed2b498f03d667afca4933a6cbc36fea16f4d944306883d67760926e9c`；`CoursePackagesView-C_igJ5A1.css` `8318ab57b66a60b5ce81a6b4a1626e1903aeb877826aaa730deae64b9196629c`。

## 生产验证

- 发布目录原子切换成功；`nginx -t` 通过并已 reload。
- Nginx 与 `class-deduction.service` 均为 active；公网首页返回 HTTP 200。
- 公网首页与 Package 页面 JS 的 SHA-256 与本地产物一致。

## 回滚

- 发布前 `front/dist` 指向 `/home/class-deduction-system/releases/v2-20261020-38741e5-package-print-dialog/front/dist`，旧路径记录于本发布目录的 `front/dist.previous-target`。
- 恢复该链接并执行 `nginx -t && systemctl reload nginx`。
