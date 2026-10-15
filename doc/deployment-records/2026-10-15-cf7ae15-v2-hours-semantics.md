# JIN V2 部署：Starting、Allocated、Remaining 课时口径统一

- 部署对象：Class Deduction V2 前端静态资源与后端 JAR；没有数据库结构或业务数据迁移。
- 代码版本：`main_v2` 提交 `cf7ae15`（统一课时包起始与剩余课时口径）。
- 部署日期：2026-10-15（Asia/Shanghai）。
- 前端构建：干净提交 worktree 执行 `npm run build` 成功，共 88 个文件。
- 后端构建：JDK 17 执行 `mvn -Dmaven.test.skip=true package` 成功；定向测试因仓库中其他测试类与当前 API/构造器签名不匹配，未通过测试源码编译。
- 后端 JAR SHA-256：`6e4e9787b9aaf152def211638cfc9f2e6bdbca192f592fbce3e44401c41882ec`。
- 1-on-1 页面 JS：`assets/OneOnOneDeductionView-CtGJ4YW6.js`，SHA-256 `59304ccb1324060728bbf9be3c4c3f406603024c6dbb2ee677ada393e682745d`。
- Schedule 页面 JS：`assets/RecurringScheduleView-DAWkvmer.js`，SHA-256 `43c3231bb546895b2071c98ee12883da9a78728eb534336b92bc07abdb7d165a`。
- 线上发布目录：`/home/class-deduction-system/releases/v2-20261015-cf7ae15`。
- 前端回滚备份：`/home/class-deduction-system/front/dist.bak_cf7ae15_20261015T021906Z`。
- 部署前 systemd 配置与版本标记分别保存在发布目录的 `v2.conf.before_cf7ae15_20261015T021906Z`、`DEPLOY_VERSION.before_cf7ae15_20261015T021906Z`。

## 生产验证

- `class-deduction.service` 与 Nginx 均为 active。
- `https://class.jinbesttutoring.com/api/actuator/health` 返回 `{"status":"UP"}`；首页返回 HTTP 200。
- 线上 1-on-1 与 Schedule 页面 JS 的 SHA-256 均与本地构建一致。
- 部署过程中后端重启短暂返回 502；服务恢复后健康检查通过。

## 回滚

- 恢复 systemd 配置和 `DEPLOY_VERSION` 时，使用发布目录中对应的 `.before_cf7ae15_20261015T021906Z` 备份，并执行 `systemctl daemon-reload && systemctl restart class-deduction.service`。
- 将当前前端 `dist` 移开，并将 `dist.bak_cf7ae15_20261015T021906Z` 恢复为 `/home/class-deduction-system/front/dist`，然后执行 `nginx -t && systemctl reload nginx`。
