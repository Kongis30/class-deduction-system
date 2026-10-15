# JIN V2 部署：按老师统计待确认课时

- 代码来源：`main_v2` 提交 `a3ca763edd3d74139d9482550e63fa589478052d`，从干净 worktree 构建。
- 部署时间：2026-10-15 15:22 UTC（2026-10-15 23:22 Asia/Shanghai）。
- 部署范围：V2 后端 JAR 与完整前端静态资源；无数据库结构变更或数据迁移。
- 变更：1-on-1 消课页的 `Pending deduction` 按待确认记录的老师归属分别统计；旧记录缺少老师快照时回退到课次原始排课老师。课包级 Pending 汇总仍用于余额计算。
- 构建：干净提交 worktree 中 `npm ci && npm run build` 成功；`mvn -Dmaven.test.skip=true package` 成功，后端测试源码未编译或运行。
- 后端 JAR：`/home/class-deduction-system/releases/v2-20261015-a3ca763/backend/v2-backend-a3ca763.jar`；SHA-256：`433907fa01d2392bb99ea9ba42d5e403386af4b12354028505014ced4a7a489a`。
- 前端发布包 SHA-256：`578e9a4ce6acd6dc107806dd1279ec74d6ee40a783f38c20ee636ecc3e3142a0`；线上目录：`/home/class-deduction-system/front/dist`。
- 关键前端资源 `OneOnOneDeductionView-DOIviMTB.js` SHA-256：`4388f7b3c662f2438f1cb17b0ce38de997b6f7fb37e027c23b0193d7aafd7ba4`。
- 回滚备份：前端 `/home/class-deduction-system/front/dist.bak_a3ca763`；V2 systemd 配置和版本标记分别备份在发布目录 `backend/v2.conf.before_a3ca763`、`backend/DEPLOY_VERSION.before_a3ca763`；上一版 JAR 保留在 `v2-20261015-2f5550e/backend/v2-backend-2f5550e.jar`。

## 生产验证

- `nginx -t` 通过；Nginx 与 `class-deduction.service` 均为 active。
- `https://class.jinbesttutoring.com/` 返回 HTTP 200；`/api/actuator/health` 返回 `{"status":"UP"}`。
- `OneOnOneDeductionView-DOIviMTB.js` 返回 HTTP 200，线上 SHA-256 与本地构建一致。
- 检查时近 5 分钟错误级别日志无记录。
