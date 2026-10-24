# JIN V2 部署：班级名单支持全部报名方案并单页多列打印

- 源码：`main_v2` 提交 `d819384`。
- 部署时间：2026-10-24（Asia/Shanghai）。
- 发布目录：`/home/class-deduction-system/releases/v2-20261024-d819384-class-roster`。
- 部署范围：前端、后端。
- 数据库：按本次部署要求，未执行任何迁移、备份、SQL 或数据修复操作。

## 业务结果

- Print class roster 只需选择班级，registration plan 变为可选，默认 `All plans`。
- 整班打印合并所有 registration plan 的学生，并按学生去重。
- 名单严格控制在一页内；超过目标行数时自动增加学生列，并按最长姓名/学校缩放字号。
- 打印页顶部依次显示 Product、Project · 班级、校区。
- Data Summary API 新增 `groupProjectName`，从班级、课程项、报名方案依次回退获取。

## 构建与测试

- 从 `d819384` 创建干净 detached worktree，执行 `npm ci && npm run build` 成功。
- 同一 worktree 执行后端 `mvn -Dmaven.test.skip=true package` 成功。
- `data-summary-roster-print.spec.ts` 1/1 通过，覆盖 plan 可选、整班去重、自动多列、PDF 单页和顶部信息。
- 后端 `DataSummaryServiceImplTest` 34/34 通过，包含 `groupProjectName` 断言。
- 前端 `index.html` SHA-256：`90244416b77e460247ee82d584a4fd23da0648adacd9bc63ebda716b5b22ecdf`。
- Data Summary JS（`DataSummaryView-C7hTToi9.js`）SHA-256：`fe3dde9542fa36faa7c47974664ba8d89d568c6e2c6e8a47d82a4888231c236a`。
- 后端 JAR SHA-256：`884dbe21e3c751fc9515aaa53c9e02623b7062a037e47239db21fee02ff9d944`。

## 切换内容

- 后端：仅更新 `class-deduction.service` 的 V2 drop-in，将 `ExecStart` 指向本发布目录的 `backend.jar`；未修改其他 systemd 服务。
- 前端：将 `/home/class-deduction-system/front/dist` 原子切换到本发布目录的 `front/dist`；未重载或重启 Nginx。
- 更新 `/home/class-deduction-system/current` 和 `backend/DEPLOY_VERSION`。

## 线上验证

- `class-deduction.service` 为 active，启动命令指向本发布目录的 JAR，启动日志显示 `Started ClassDeductionSystemApplication`。
- 公网 `/api/actuator/health` 返回 `{"status":"UP"}`。
- 公网首页 HTTP 200，SHA-256 与本地产物一致。
- 公网 Data Summary JS HTTP 200，SHA-256 与本地产物一致，包含 `All plans (optional)`、`__ALL_PLANS__` 和 `groupProjectName`。
- 重启后 `error.log` 无新增错误记录。

## 回滚

- 后端：将发布目录的 `backend/v2.conf.before`、`backend/DEPLOY_VERSION.before` 和 `backend/current.before` 恢复到原位置，执行 `systemctl daemon-reload && systemctl restart class-deduction.service`。
- 前端：将 `/home/class-deduction-system/front/dist` 指回 `front/dist.target.before` 记录的旧目录；无需操作 Nginx 或数据库。
