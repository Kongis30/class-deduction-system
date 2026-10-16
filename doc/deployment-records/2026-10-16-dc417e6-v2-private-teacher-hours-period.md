# JIN V2 部署：老师课时统计按最近排课周期计算

- 代码来源：`main_v2` 提交 `dc417e6`。
- 部署时间：2026-10-16 22:14 Asia/Shanghai。
- 部署范围：V2 后端 JAR 与完整前端静态资源；未执行数据库迁移或数据修复。
- 业务口径：当前排课周期从最近一次排课调整开始。老师的 Allocated 是该周期分配课时；Deducted 是该周期已确认消课课时；Remaining 是该周期尚未消课的课时，包含过期但未确认及未来课时。三者满足 `Allocated = Deducted + Remaining`。
- 构建：前端 `npm run build` 成功（88 个文件）；后端 Java 17 `mvn -Dmaven.test.skip=true package` 成功。相关定向测试已在提交前运行并通过。
- 发布目录：`/home/class-deduction-system/releases/v2-20261016-dc417e6`。
- 后端 JAR SHA-256：`0e8d8cc7bcbb2ff474d4dcedb98a0008b2ad27a3afefe20137b7f838ae4efc35`。
- 前端 `index.html` SHA-256：`743ea12aaef3fe14771f37a31ff1e8ca04c50368b4ae8dab48eac5ffc6002208`。
- 回滚文件：`backend/v2.conf.before`、`backend/DEPLOY_VERSION.before` 和 `front/dist.before` 均保存在发布目录中；旧前端发布目录仍保留。

## 生产验证

- `class-deduction.service` 与 Nginx 均为 active；后端运行于 Java 17。
- 本机与公网 `/api/actuator/health` 均返回 `{"status":"UP"}`；公网首页 HTTP 200。
- `nginx -t` 通过；服务启动后错误级日志无记录。
