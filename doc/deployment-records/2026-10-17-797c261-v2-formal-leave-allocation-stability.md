# JIN V2 部署：请假补课不重置老师课时周期

- 代码来源：`main_v2` 提交 `797c2619e9cf6f803559e65a3f52851c1867f4a7`。
- 部署时间：2026-10-17（Asia/Shanghai）。
- 部署范围：V2 后端 JAR 与完整前端静态资源；无数据库迁移或线上历史数据修复。
- 修复内容：请假生成的补课是原课次分配的延续，不会额外计入 Allocated；暂停／恢复等不改变课位安排的操作复用原课位，避免重建课位而误改变周期。Allocated、Deducted by、Remaining with 字段名称不变。
- 构建：干净检出 `797c261` 后，前端 `npm ci && npm run build` 成功（88 个文件），后端 Java 17 `mvn -Dmaven.test.skip=true package` 成功；12 个相关定向测试通过。
- 发布目录：`/home/class-deduction-system/releases/v2-20261016-797c261`。
- 后端 JAR SHA-256：`4ab1bf72f798d6ec1e1080c0e520bf533e15e278205903ecec91a0d4dce54a98`。
- 前端 `index.html` SHA-256：`88beb80602899bb3f91c4dbd9fe752f10fd1353d96fca8c9ccec83f716b06a62`。
- 回滚依据：发布目录内 `backend/v2.conf.before_20261016T160145576308852Z`、`backend/DEPLOY_VERSION.before_20261016T160145576308852Z` 与 `front/dist.before`；上一版发布仍保留在 `v2-20261016-7bb0446`。

## 生产验证

- `class-deduction.service` 与 Nginx 均为 active；本机与公网 `/api/actuator/health` 返回 `{"status":"UP"}`。
- 公网首页 HTTP 200，返回内容哈希与本地构建一致。
- `nginx -t` 通过；部署后最近 5 分钟服务错误级日志无记录。

## 回滚

- 将 systemd V2 配置和 `DEPLOY_VERSION` 恢复为上述 `.before_...` 文件，再执行 `systemctl daemon-reload && systemctl restart class-deduction.service`。
- 将 `/home/class-deduction-system/front/dist` 切回 `front/dist.before` 指向的上一版目录，然后执行 `nginx -t && systemctl reload nginx`。
