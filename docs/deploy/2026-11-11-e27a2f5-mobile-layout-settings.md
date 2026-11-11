# 2026-11-11 部署记录：e27a2f5 移动端布局与显示设置

## 版本
- commit: `e27a2f5`（移动端显示设置并入 Back office 下拉菜单，顶部只保留一个齿轮按钮）
- release: `/home/class-deduction-system/releases/v2-20261111-e27a2f5-mobile-layout-settings/`
- 部署时间: 2026-11-11T03:53Z
- 回滚目标: `v2-20261110-0b4e227-datasummary-undo-entry`

## 本次内容（全前端，后端 src/main 无改动）
- 修复 scoped 移动端样式选择器编译错误（`956eed0`）：`html.mobile-layout &` 编译为 `html.mobile-layout :scope .foo[data-v]`，永不命中，导致手机端注册页表格卡片化失效、平板 Data Summary 工具栏堆叠错乱。统一改为 `:global(html.mobile-layout) &`。
- 补齐 index.css 全局移动端样式迁移（`514a291`）。
- 移动端顶部新增显示设置入口（`3bc7175` → `2fb9a61` → `e27a2f5`），最终形态：并入 Back office 下拉菜单底部分组，顶部仅一个齿轮按钮。

## 产物校验
| 文件 | SHA-256 |
|---|---|
| backend.jar | b1c7dcb258bef2b919c31a7b267e9797bae681040b4015b6c07c2f18a4c5d8aa |
| front/dist/index.html | 50e0cd06b8588dab7b0a7754a12a8ad51155698bfef20e7d0c85016cc17f2363 |

本地编译 → rsync → 服务器端 sha256sum 复核一致。

## 数据库
无 DDL / 无迁移。部署前全量备份：
`/root/jin-v2-backups/pre-e27a2f5-20261111T035226Z.sql.gz`（1.3M，`gzip -t` 通过，sha256 `33e4becc5582f5318e7e1b5e744b1320d32bdec3db6b0ea9ba677ac63642a753`）

## 验证结果
- systemd `class-deduction.service` = active
- 本地 `http://127.0.0.1:8084/api/health` = 200，status UP
- 公网 `https://class.jinbesttutoring.com/` = 200
- 公网 `/api/health` = 200
- 公网 `/api/v1/base/students`（无 token）= 401
- 公网 index.html sha256 与 release 一致
- 前端产物含新代码（`app-header__settings-panel` 出现在 `index-Ba3a0BfL.js`）

## 回滚步骤
```bash
REL=/home/class-deduction-system/releases/v2-20261110-0b4e227-datasummary-undo-entry
sed -i "s|/home/class-deduction-system/releases/v2-[^/]*/backend/backend.jar|$REL/backend/backend.jar|" \
  /etc/systemd/system/class-deduction.service.d/v2.conf
ln -sfn $REL/front/dist /home/class-deduction-system/front/dist.new
mv -Tf /home/class-deduction-system/front/dist.new /home/class-deduction-system/front/dist
systemctl daemon-reload && systemctl restart class-deduction.service
```
