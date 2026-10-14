# JIN V2 部署：删除 Credit top-up 收据时同步移除 Data Summary 行

- 代码版本：`main_v2` 提交 `275a3da`。
- 部署时间：2026-10-14 18:59 Asia/Shanghai（2026-10-14 10:59 UTC）。
- 部署对象：后端 JAR 与完整前端静态资源；无数据库迁移。
- 构建：前端 `npm ci && npm run build` 成功（88 个文件）；后端 Java 17 `mvn -Dmaven.test.skip=true package` 成功。
- 后端 JAR SHA-256：`b65ed6956697d07aa498cad83bab78db0bad27aa0787ad99ecd41cf478054f69`。
- 前端 `index.html` SHA-256：`cb1edfbc6e222ebfa724793068a57d63d3eb1e1981a31474f7fe7f4895af03a7`。
- 线上发布目录：`/home/class-deduction-system/releases/v2-20261014-275a3da`。
- 前端备份：`/home/class-deduction-system/front/dist.bak_275a3da_20261014T105633Z`。
- systemd 配置和版本标记备份：发布目录 `backend/v2.conf.before_retry_20261014T105912Z`、`backend/DEPLOY_VERSION.before_retry_20261014T105912Z`。

## 变更

- 删除 top-up 收据时，即使部分 Credit 已使用，也会软删除充值订单，使其从 Data Summary 活动行和顶部统计中消失。
- 已发生的付款流水和 Credit 消耗记录保留；未使用的 Credit 仍按原逻辑冲回。
- 线上数据库没有执行 DDL 或数据修改。

## 生产验证

- 前端和后端均从 `275a3da` 的干净 worktree 构建并部署。
- `class-deduction.service`、`nginx` 均为 active；`nginx -t` 通过。
- `https://class.jinbesttutoring.com/api/actuator/health` 返回 `{"status":"UP"}`。
- 首次切换遗漏了 systemd drop-in 中的 `WorkingDirectory=/home/class-deduction-system`，V2 数据库保护检查阻止新进程启动；自动回滚恢复了旧版本。补回原工作目录后重新部署成功，版本标记为 `275a3da`。
- Maven 默认打包曾尝试编译仓库中既有测试源码并失败；生产 JAR 使用 `-Dmaven.test.skip=true` 打包成功，未运行测试。

## 回滚

- 将 `/etc/systemd/system/class-deduction.service.d/v2.conf` 恢复为发布目录 `backend/v2.conf.before_retry_20261014T105912Z`，将 `/home/class-deduction-system/backend/DEPLOY_VERSION` 恢复为同目录的 `backend/DEPLOY_VERSION.before_retry_20261014T105912Z`，执行 `systemctl daemon-reload && systemctl restart class-deduction.service`。
- 将当前 `/home/class-deduction-system/front/dist` 移走，再把 `/home/class-deduction-system/front/dist.bak_275a3da_20261014T105633Z` 恢复为 `dist`，然后执行 `nginx -t && systemctl reload nginx`。
