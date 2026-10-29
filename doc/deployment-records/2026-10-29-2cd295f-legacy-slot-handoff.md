# JIN V2 部署：历史课位映射与老师交接修复

- 源码分支与修复提交：`main_v2`，`2cd295f`。
- 部署范围：同一 release 内同时更新前端静态资源和后端 JAR。
- 发布目录：`/home/class-deduction-system/releases/v2-20261029-2cd295f-legacy-slot-handoff`。
- 数据库：没有 schema、迁移脚本或线上数据改动。
- 回滚目标：`/home/class-deduction-system/releases/v2-20261029-0e2c166-full-stack`。

## 发布内容

- 修复同一排课规则下旧版重建课位后，历史 occurrence 仍关联软删除 slot ID，导致编辑排课时历史课时没有计入当前 slot、教师交接和余额核算不一致的问题。
- 旧课位仅在同规则且星期、时段、单节课时唯一匹配时映射到当前课位；保留课次和实际授课教师快照。
- 前端待处理课次汇总同一课包的各教师入口并按 occurrence 去重，使用服务端返回的课时单位展示。
- 前端首页 SHA-256：`e8c6953502d4a9cc29baf1d391feda1825e9fbbd094c813d55d01bb830e72958`。
- 排课页面脚本 SHA-256：`6942507fa4e6183cca21389c6ec6a4fcb32a82cdb95a8627d2c6d04bb5bba94e`。
- 后端 JAR SHA-256：`11e3009b9209aaa48ebd0ba48b35d5493f77c3d5a5f5096cd368ade5564b80b7`。

## 验证

- 本地排课场景复现覆盖仅换老师和减少 0.5 小时；验证旧课位历史课次纳入新课位核算、课时及老师入口正确，且实际授课老师和历史记录保留。
- 后端相关测试 102 项通过；`LessonRecordServiceImplTest` 完整类另有既存失败，已用基线确认失败与本次改动无关。
- `npm run build`、`mvn -DskipTests package`、`git diff --check` 通过。Maven 打包使用 Java 24；系统默认 Java 11 不满足 Spring Boot 插件的运行要求。
- Jin `class-deduction.service` 和 Nginx 均为 active，`nginx -t` 通过。
- Jin 本机和公网健康接口均返回 `{"status":"UP"}`；公网首页与排课脚本哈希均和本地构建一致。
- 当前 `current` 与 `/home/class-deduction-system/front/dist` 均指向本发布目录。

## 回滚

- 将 `/home/class-deduction-system/current` 和 `/home/class-deduction-system/front/dist` 分别恢复为本 release 的 `current.previous-target` 与 `front/dist.previous-target` 记录的路径。
- 将 `/etc/systemd/system/class-deduction.service.d/v2.conf` 恢复为本 release 的 `backend/v2.conf.before`，恢复 `/home/class-deduction-system/backend/DEPLOY_VERSION`，然后执行 `systemctl daemon-reload && systemctl restart class-deduction.service && nginx -t && systemctl reload nginx`。
