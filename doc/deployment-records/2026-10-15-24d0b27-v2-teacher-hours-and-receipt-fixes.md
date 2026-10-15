# JIN V2 部署：教师课时口径与收据记录修复

- 代码来源：`main_v2` 提交 `24d0b27a215c3ff67dd363e399568fb27990f269`，从干净 worktree 构建。
- 部署时间：2026-10-15 07:21 UTC（2026-10-15 15:21 Asia/Shanghai）。
- 部署范围：V2 后端 JAR 与完整前端静态资源；无数据库结构变更或数据迁移。
- 变更包括：按老师显示固定分配、该老师已消课和该老师剩余课时；保持 Starting Hours 不受消课或排课编辑影响；显示有效收据数量并在删除后刷新；兼容缺少收据快照的历史订单。
- 构建：干净提交 worktree 中 `npm ci && npm run build` 成功；`mvn -Dmaven.test.skip=true package` 成功，后端测试源码未编译或运行。
- 后端 JAR：`/home/class-deduction-system/releases/v2-20261015-24d0b27/backend/v2-backend-24d0b27.jar`；SHA-256：`8360dc6b2816ed96b0f738e187cc78138fd6b9422db3b06caca3c98295595b30`。
- 前端发布包 SHA-256：`b101bbdbee51f0b16a3460b926bc5904c88def2d4513267852ea5794e51d6285`；线上目录：`/home/class-deduction-system/front/dist`。
- 关键前端资源 SHA-256：
  - `OneOnOneDeductionView-u2SsvAoc.js`：`001be73844e2d14fc3c416829f2517f6271816aa2e9caecae9348247c0045532`
  - `RecurringScheduleView-DkfGA6dZ.js`：`db7c86cc68625a7e153bf2e8f486945e9ad5b5c086d77ce1871a291a2866ffd0`
  - `ReceiptRecordsView-BcRN9LJC.js`：`fe0cf4b97a830222259805698c3cd97b141b1e4e27168b74fdd7afcdc1b44b57`
  - `RegistrationIntakeView-DwPYF-CR.js`：`8eb7133bdd858d54eca36e6eb18742494fccffa66883e432dbf93ba8300a7546`
- 回滚备份：前端 `/home/class-deduction-system/front/dist.bak_24d0b27_20261015T072050Z`；systemd V2 配置及版本标记备份在发布目录的 `backend/v2.conf.before_24d0b27_20261015T072050Z`、`backend/DEPLOY_VERSION.before_24d0b27_20261015T072050Z`；上一个后端 JAR 保留在 `v2-20261015-b28960f/backend/v2-backend-b28960f.jar`。

## 生产验证

- `nginx -t` 通过；Nginx 与 `class-deduction.service` 均为 active。
- `https://class.jinbesttutoring.com/api/actuator/health` 返回 `{"status":"UP"}`；首页返回 HTTP 200。
- 上述四个线上页面资源的 SHA-256 与同一提交的本地构建完全一致。
- 重启后 10 分钟错误级别日志无记录。

## 回滚

- 恢复 `v2.conf.before_24d0b27_20261015T072050Z` 和 `DEPLOY_VERSION.before_24d0b27_20261015T072050Z`，将 `ExecStart` 指回 `v2-20261015-b28960f/backend/v2-backend-b28960f.jar`，再执行 `systemctl daemon-reload && systemctl restart class-deduction.service`。
- 将当前 `front/dist` 移走，并将 `front/dist.bak_24d0b27_20261015T072050Z` 恢复为 `front/dist`，再执行 `nginx -t && systemctl reload nginx`。
