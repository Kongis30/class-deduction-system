# JIN V2 部署：1-on-1 消课课时口径对齐

- 部署对象：Class Deduction V2 前端静态资源与后端 JAR；未修改数据库结构或业务数据。
- 代码版本：`main_v2` 提交 `f092bdb80ef436fb3d7f82237d64dfa70c3a7a5b`。
- 部署日期：2026-10-13（Asia/Shanghai）。
- 前端构建：`npm run build` 成功，发布 87 个文件。
- 后端构建：JDK 24，`mvn -Dmaven.test.skip=true package` 成功；跳过测试源码编译。
- 后端 JAR SHA-256：`eaba8dbd7d2c54213b811bfb5ee1630a3b85ec53a60cab20549d4b5e58feb331`。
- 1-on-1 页面 JS：`assets/OneOnOneDeductionView-zsszxhOZ.js`，SHA-256 `7f893c187db339b98ecc33f5fb756a1ca48c24acbfb5b6bcb72f8fb39930d6d7`。
- 线上发布目录：`/home/class-deduction-system/releases/v2-20261013-f092bdb`。
- 前端回滚备份：`/home/class-deduction-system/front/dist.bak_f092bdb_20261013T034623Z`。
- 部署时更新 systemd V2 drop-in 指向新 JAR，并更新 `DEPLOY_VERSION`。

## 变更

- 1-on-1 页面 Remaining 显示课包已确认消课后的实际余额，不再预先扣减待确认课时。
- Allocated 按与 Schedule 相同的最新 ACTIVE 排课规则累计，消课后保持不变。
- 移除 Total 后赠课括号说明。

## 生产验证

- `class-deduction.service` 为 active，运行 JAR 指向本次发布目录。
- `https://class.jinbesttutoring.com/api/actuator/health` 返回 `{"status":"UP"}`；首页 HTTP 200。
- 线上 1-on-1 JS/CSS 与本地构建 SHA-256 一致，页面资源可访问。
- Nginx 配置检查通过；重启后 error-priority journal 无记录。

## 回滚

- 恢复 systemd 配置：`/home/class-deduction-system/backend/v2.conf.before_f092bdb_20261013T034623Z`。
- 恢复前端：将 `/home/class-deduction-system/front/dist.bak_f092bdb_20261013T034623Z` 还原到 `/home/class-deduction-system/front/dist`。
- 恢复版本标记：`/home/class-deduction-system/backend/DEPLOY_VERSION.bak_f092bdb_20261013T034623Z`，然后执行 `systemctl daemon-reload && systemctl restart class-deduction.service`。
