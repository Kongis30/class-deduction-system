# JIN V2 部署：Edit Schedule 提示区间距

- 部署源码：`main_v2` 提交 `7932997`。
- 发布目录：`/home/class-deduction-system/releases/v2-20261030-7932997-full-stack`。
- 前后端从同一干净提交构建；工作区中未提交的 Edit Receipt 相关改动未包含。
- 前端首页 SHA-256：`84767e069b3d35e997fb1b3e4c97698f19b3373ed08f90079455c4096c784155`。
- 后端 JAR SHA-256：`da439048e8359522352e1e97374499cf79f052f876d5eac079e87f282cf17aa6`。
- 本次仅包含界面间距调整，没有数据库结构或数据迁移。沿用本次维护窗口前已完成的加密数据库备份 `20261030T052109Z`。

## 验证

- 前端 `npm run build` 成功；后端 `mvn -DskipTests package` 成功。
- `class-deduction.service` 与 Nginx 均为 active；Nginx 配置检查成功。
- 本机及公网 `/api/actuator/health` 返回 `{"status":"UP"}`。
- 线上运行 JAR 与发布 JAR 哈希一致；公网首页哈希与本地构建一致；发布后未发现新的 error 级服务日志。

## 回滚

- 发布前 `current` 与 `front/dist` 均指向 `/home/class-deduction-system/releases/v2-20261030-c95cc2c-full-stack`。
- 发布目录保存 `current.previous-target`、`front/dist.previous-target`、`backend/v2.conf.before` 及部署前运行 JAR 备份 `backend/backend.running.before.jar`。
- 回滚时恢复两个入口及 systemd 配置，执行 `systemctl daemon-reload && systemctl restart class-deduction.service && nginx -t && systemctl reload nginx`。
