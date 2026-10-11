# JIN V2 部署记录：Record Payment 付款行 UI 修复（前端）

- 部署对象：Class Deduction V2 前端静态 `dist`（nginx `class.jinbesttutoring.com`）。
- 代码来源：**前端基线 `main_v2@05ad155` + cherry-pick `0774b75`、`44d2005`**。
  - 刻意**不包含**同分支上的「私教签字历史」功能（其前端会调用尚未上线的后端接口与数据表）。
- 部署时间：2026-10-11 14:46 UTC。
- 部署方式：仅替换前端 `dist`；后端与数据库无变更。

## 变更内容

1. `修正 Record Payment 新增付款行控件不齐`：付款行（`EnrollmentOrdersView.vue`）在 grid 布局下统一控件宽度、移除无效的内联 `flex:1`、统一高度，消除重叠与高矮不一。
2. `Record Payment 付款方式不再预置默认值`：新增付款行的付款方式不再默认 Student Credit，下拉显示占位符，由用户选择。

## 产物

- 发布包：`dist-uifix.tar.gz`，SHA-256 `c8818a7bf1eb7977ed3bd011fdf28d48670dd18c0befe66172a396bef01d89a6`。
- 文件数：87；`EnrollmentOrdersView` chunk：`assets/EnrollmentOrdersView-G1RbPul2.js`。
- 线上路径：`/home/class-deduction-system/front/dist`。
- 部署前备份：`/home/class-deduction-system/front/dist.bak_uifix_20261011T144627Z`。
- 校验：`dist` 中不含 `private-signature-history`（确认未夹带签字历史功能）。

## 验证

- `systemctl reload nginx` 成功。
- `https://class.jinbesttutoring.com/` → HTTP 200，`index` 引用新的 `index-C8H9FUsI.js`。
- 新 `assets/EnrollmentOrdersView-G1RbPul2.js` → HTTP 200；旧 `EnrollmentOrdersView-BlPrDcDV.js` → HTTP 404。
- 本地 `npm ci && npm run build` 通过。

## 回滚

```bash
cd /home/class-deduction-system/front
mv dist dist.failed_$(date -u +%Y%m%dT%H%M%SZ)
mv dist.bak_uifix_20261011T144627Z dist
systemctl reload nginx
```
