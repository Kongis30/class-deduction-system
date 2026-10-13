# JIN V2 部署：Schedule 课时口径修正

- 部署对象：Class Deduction V2 前端静态文件（Nginx `class.jinbesttutoring.com`）；后端和数据库未变更。
- 代码来源：`main_v2` 提交 `903a8e9`（Schedule 起始课时、已分配、已消课和剩余课时计算修正）。
- 部署日期：2026-10-13（Asia/Shanghai）。
- 构建：`frontend/npm run build` 成功；前端 dist 共 87 个文件。
- 线上路径：`/home/class-deduction-system/front/dist`。
- 部署前备份：`/home/class-deduction-system/front/dist.bak_903a8e9_20261013T023534Z`。
- Schedule chunk：`assets/RecurringScheduleView-BpdCSLFk.js`，SHA-256 `92de11e0cc019fdd9a0e7b454925f591b5f14ce0f72f90bb02e85f848a5fdb9e`。

## 变更

- Starting Hours 使用课包总课时减去录入前已用课时计算，并保持为可调整的起始值。
- Already deducted 显示课包录入后已确认消课；Remaining 读取实际余额，不提前扣除待确认课时。
- Allocated 显示排课槽位保存的累计分配值；额度校验仍扣除待确认课时及其他排课占用。

## 验证

- 前端 `npm run build`（含 `vue-tsc --noEmit`）通过。
- 线上首页 HTTP 200；新 Schedule chunk HTTP 200，旧 chunk HTTP 404。
- Nginx 配置检查通过并 reload；Nginx 与 `class-deduction.service` 均 active。

## 回滚

```bash
cd /home/class-deduction-system/front
mv dist dist.failed_903a8e9_rollback
mv dist.bak_903a8e9_20261013T023534Z dist
systemctl reload nginx
```
