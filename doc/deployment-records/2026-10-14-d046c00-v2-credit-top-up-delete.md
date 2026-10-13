# JIN V2 部署：允许删除已部分使用的 Credit 充值收据

- 部署对象：Class Deduction V2 前端静态资源与后端 JAR；没有数据库结构或数据迁移。
- 代码版本：`main_v2` 提交 `d046c00`（功能提交 `8e8abb5`，并包含旧页面兼容调整）。
- 部署时间：2026-10-14 00:32（Asia/Shanghai，2026-10-13 16:32 UTC）。
- 前端构建：`npm run build` 成功，共 87 个文件。
- 后端构建：JDK 24，`mvn -DskipTests package` 成功。
- 后端 JAR SHA-256：`91b5aaa3a6152a1904f106d56bc7a2af6b0aeb1da86e0b8b777b550724639348`。
- 收据页 JS：`assets/ReceiptRecordsView-BoQe9qvE.js`，SHA-256 `2f2c39fa2548d5e283d0d575b77291677194db99a010e005636de1e535dc5671`。
- 发布目录：`/home/class-deduction-system/releases/v2-20261014-d046c00`。
- 前端备份：`/home/class-deduction-system/front/dist.bak_d046c00_20261013T163257Z`；切换前目录保留在 `dist.old_d046c00_20261013T163257Z`。
- systemd 配置及版本标记备份位于发布目录，文件名为 `v2.conf.before_d046c00_20261013T163257Z` 和 `DEPLOY_VERSION.before_d046c00_20261013T163257Z`。

## 业务变更

- 可用 Credit 足够时，继续撤销整笔充值并软删除收据、付款及充值订单。
- 可用 Credit 不足时，把余额扣到 `$0` 并软删除收据；付款和充值订单留在财务历史中，已用 Credit 对应的课程付款不变。
- 新页面会提交确认时显示的余额，后端锁定学生并核对余额变化。旧页面不传该参数时仍可用当前余额处理。

## 生产验证

- `class-deduction.service` 和 Nginx 均为 active，Nginx 配置检查通过。
- `https://class.jinbesttutoring.com/api/actuator/health` 返回 `{"status":"UP"}`；首页返回 HTTP 200。
- 线上收据页 JS SHA-256 与本地构建一致。
- 数据库未执行 DDL 或数据修改。

## 回滚

将 systemd drop-in 和 `/home/class-deduction-system/backend/DEPLOY_VERSION` 恢复为发布目录里的 `.before_d046c00_20261013T163257Z` 备份，执行 `systemctl daemon-reload && systemctl restart class-deduction.service`。将当前 `front/dist` 移走后，从 `dist.bak_d046c00_20261013T163257Z` 恢复旧静态文件，再执行 `systemctl reload nginx`。
