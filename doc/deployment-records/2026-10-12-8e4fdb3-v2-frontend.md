# JIN V2 前端部署：Edit Weekly Schedule 未排课时数

- 部署对象：Class Deduction V2 前端静态文件；后端服务和数据库未变更。
- 代码来源：`main_v2` 提交 `8e4fdb3`（Edit Weekly Schedule 将余额汇总改为显示 Unallocated）。
- 部署时间：2026-10-12 16:35 Asia/Shanghai。
- 构建：`npm run build` 成功；前端 dist 共 87 个文件。
- 发布包 SHA-256：`f9e47ec2977f120acd2a34a6b908a780f23f6cdbad574243be8bfe1f0228d391`。
- 线上路径：`/home/class-deduction-system/front/dist`。
- 部署前静态文件备份：`/home/class-deduction-system/front/dist.bak_8e4fdb3_20261012T083555Z`。
- 验证：`nginx -t` 通过；Nginx 与 `class-deduction.service` 均 active；线上首页 HTTP 200；`RecurringScheduleView-BSiYXfD_.js` 返回 HTTP 200 且包含 `Unallocated`。

## 回滚

```bash
cd /home/class-deduction-system/front
mv dist dist.failed_8e4fdb3
mv dist.bak_8e4fdb3_20261012T083555Z dist
systemctl reload nginx
```
