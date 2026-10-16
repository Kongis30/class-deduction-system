# JIN V2 部署：旧规则复用 ID 时显示排课周期分隔线

- 代码来源：`main_v2` 提交 `51691c2`。
- 部署时间：2026-10-17（Asia/Shanghai）。
- 部署范围：V2 后端 JAR 与完整前端静态资源；无数据库迁移或线上历史数据修复。
- 修复内容：对旧数据中原位替换课位、复用规则 ID 的情况，从当前有效课位快照推断周期边界，并让 1-on-1 消课页面在同一规则 ID 内显示排课周期分隔线，与服务端当前周期统计边界一致。
- 构建：前端 `npm ci && npm run build` 成功；后端 Java 17 目标 `mvn -Dmaven.test.skip=true clean package` 成功。
- 发布目录：`/home/class-deduction-system/releases/v2-20261017-51691c2`。
- 后端 JAR SHA-256：`7d6e76de77ed1421fff6ef77622e26b717c1a1a4b75357606cdff9d468c2959f`。
- 前端 `index.html` SHA-256：`b59e9254776f4a8e6823d7204a3ab04a6133c9a53d641d307dede1632a41c934`。
- 回滚目标：上一版 `/home/class-deduction-system/releases/v2-20261016-797c261`；本次配置备份为新发布目录中的 `backend/v2.conf.before_20261017T0030`，旧前端发布目录仍保留。

## 生产验证

- `class-deduction.service` 为 active，systemd 当前启动命令指向本次 JAR。
- 公网 `/api/actuator/health` 返回 `{"status":"UP"}`；公网首页 HTTP 200。
- `nginx -t` 通过；部署后最近 5 分钟服务错误级日志无记录。
