# JIN V2 部署：当前周期老师课时统计修复

- 代码来源：`main_v2` 提交 `4fe979f489640b5e9d68ece25b703ed308215d2a`。
- 部署时间：2026-10-16 23:11 Asia/Shanghai。
- 部署范围：V2 后端 JAR 与完整前端静态资源；无数据库迁移或线上数据修复。
- 修复内容：老师级 `Remaining` 按当前周期 `Allocated − Deducted` 计算；同一排课规则原位更换老师时，当前周期内过去待确认课次仍保留给历史老师。
- 构建：干净 worktree 中前端 `npm ci && npm run build` 成功（88 个文件）；后端 Java 17 `mvn -Dmaven.test.skip=true package` 成功。相关定向单测已在提交前运行通过；完整 Maven 测试受仓库中既有测试源码编译问题影响，未能全量运行。
- 发布目录：`/home/class-deduction-system/releases/v2-20261016-4fe979f`。
- 后端 JAR SHA-256：`642d80f3486ebc258e7531e55023327f5d36502c4b0bb0dc23612adce46f2e02`。
- 前端 `index.html` SHA-256：`743ea12aaef3fe14771f37a31ff1e8ca04c50368b4ae8dab48eac5ffc6002208`。
- 回滚依据：发布目录中的 `backend/v2.conf.before_20261016T151139Z`、`backend/DEPLOY_VERSION.before_20261016T151139Z` 和 `front/dist.before`；上一版后端与前端仍保留在 `v2-20261016-dc417e6`。

## 生产验证

- `class-deduction.service` 与 Nginx 均为 active；本机及公网健康接口返回 `{"status":"UP"}`。
- 公网首页返回 HTTP 200；公网 `index.html` 的 SHA-256 与本地构建一致。
- `nginx -t` 通过；部署后 10 分钟 `class-deduction.service` 错误级日志无记录。
