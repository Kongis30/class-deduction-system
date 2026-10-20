# JIN V2 前端部署：消课列表仅显示进行中课包

- 源码分支与提交：`main_v2` `b6eac7b`。
- 部署日期：2026-10-20（Asia/Shanghai）。
- 部署范围：仅前端静态资源；后端与数据库未变更。
- 发布目录：`/home/class-deduction-system/releases/v2-20261020-b6eac7b-active-only/front/dist`。
- 修改内容：1-on-1 消课页左侧只显示 `ACTIVE` 课包；停课、结课及其他非进行中状态均不显示。学生仍有其他进行中课包时，保留其对应入口。
- 构建：`npm run build` 成功，包含 `vue-tsc --noEmit` 与 Vite production build；Vite 有既有大 chunk 提示。
- 校验：Nginx 配置检查通过并 reload；Nginx 与后端服务均为 active，`/api/actuator/health` 返回 `UP`。线上课消页面 JS SHA-256 与本地产物一致：`6d3c62d40336eeb469ad1136ede6e3bb685de8c27b1d69d5668e4c3363fd5496`。
- 回滚：`/home/class-deduction-system/front/dist.previous-target` 的同目录文件记录了部署前目标：`/home/class-deduction-system/releases/v2-20261020-24b31c4-adjust-subject-name/front/dist`。将 `front/dist` 符号链接切回该目录后执行 `nginx -t && systemctl reload nginx`。
