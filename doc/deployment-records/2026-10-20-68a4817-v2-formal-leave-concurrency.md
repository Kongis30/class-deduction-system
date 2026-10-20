# JIN V2 部署：修复 Formal Leave 确认时课包版本冲突

- 源码分支与提交：`main_v2` `68a4817c6e626ab3a873586a7bfacab3c0568511`。
- 部署日期：2026-10-20（Asia/Shanghai）。
- 发布目录：`/home/class-deduction-system/releases/v2-20261020-68a4817-formal-leave`。
- 部署范围：V2 后端 JAR 与完整前端静态资源；无数据库结构变更或数据修复。
- 修复内容：课次确认后刷新结束日期时锁定并读取最新课包版本及余额，避免排课校准更新结束日期后又用旧实体触发乐观锁冲突；前端增加重复提交保护。
- 构建：干净检出 `68a4817`；前端 `npm ci && npm run build` 成功；后端 Java 17 `mvn -Dmaven.test.skip=true clean package` 成功；未运行测试。
- 后端 JAR SHA-256：`10ab00ca14042e6d0b4f7ce48c2b7b365cf0a24e66ab3f88960f14df691f5c56`。
- 前端 `index.html` SHA-256：`1f3dbc541c9be0b065429ba7d698971ce82fe7f0e57c6f73f2b32a6bc92a2a6d`。
- 1-on-1 页面 JS `OneOnOneDeductionView-DeCVxyjB.js` SHA-256：`24fb069837f36f6acb582ee89429964cf732323cbd8484fb3ad2424ab864b768`；CSS `OneOnOneDeductionView-pZpVqp4r.css` SHA-256：`f31933e86613ca45a09d2519479f686b24db4e07632063328887cdf0f60a6a12`。

## 生产验证

- `class-deduction.service` 与 Nginx 均为 active；本机和公网 `/api/actuator/health` 返回 `{"status":"UP"}`，公网首页 HTTP 200。
- systemd 启动命令与 `current` 均指向本发布目录；公网课消页面 JS 的 SHA-256 与本地产物一致。
- 部署后服务错误级日志无新增条目。

## 回滚

- 将 systemd 配置恢复为发布目录中的 `backend/v2.conf.before_68a4817`，并恢复 `backend/DEPLOY_VERSION.before_68a4817`。
- 将 `front/dist` 和 `current` 分别恢复为 `front/dist.previous-target`、`current.previous-target` 记录的目录，再执行 `systemctl daemon-reload && systemctl restart class-deduction.service && nginx -t && systemctl reload nginx`。
