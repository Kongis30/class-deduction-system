# JIN V2 部署记录：退款学生并入 Data Summary 主表底部

- 部署对象：Class Deduction V2 前端（静态 `dist`，由 nginx `class.jinbesttutoring.com` 提供）。
- 代码来源：`main_v2` 提交 `05ad155`，前端对应改动为 `8ee41eb`（Data Summary 退款学生并入主表底部随滚动显示）。
- 部署时间：2026-10-10 17:07 UTC。
- 部署方式：**仅替换前端 `dist`**，后端与数据库无变更。

## 变更内容

- 退款学生（Refund Student）不再使用独立分栏 / 可拖拽面板，改为作为 `Refund Student` 分组行拼接在同一张主表底部，向下滚动即可查看。
- 移除原 `refund-records` 分栏、`resize-handle` 拖拽等逻辑（该文件删减 278 行）。
- 这是 `a9bfd40`（线上原前端版本）到 `main_v2` HEAD 之间**唯一**的前端改动，因此本次发布不夹带其他前端变化。

## 产物

- 发布包：本地 `dist-9584655.tar.gz`，SHA-256 `6ac767002b62a5ce8925b8605850224abd51cc08b730c7d4bc007388d48f1f96`。
- 文件数：87；`DataSummaryView` chunk：`assets/DataSummaryView-Mnh0C69x.js`。
- 线上路径：`/home/class-deduction-system/front/dist`。
- 部署前备份：`/home/class-deduction-system/front/dist.bak_9584655_20261010T170724Z`。

## 验证

- `nginx -t` 通过，`systemctl reload nginx` 成功。
- `https://class.jinbesttutoring.com/` 返回 HTTP 200；`index` 引用新的 `index-Dc4i1q8L.js`。
- `https://class.jinbesttutoring.com/assets/DataSummaryView-Mnh0C69x.js` 返回 HTTP 200，且包含 `workbook-data-row--refund`（合并行样式）、不含 `refund-records__resize-handle`。

## 回滚

```bash
cd /home/class-deduction-system/front
mv dist dist.failed_$(date -u +%Y%m%dT%H%M%SZ)
mv dist.bak_9584655_20261010T170724Z dist
systemctl reload nginx
```
