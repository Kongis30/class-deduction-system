# JIN V2 部署：按排课周期分隔 1-on-1 课次记录

- 代码来源：`main_v2` 提交 `7bb04468826bc7468f365df18c046116e47e64c3`。
- 部署时间：2026-10-16 23:17 Asia/Shanghai。
- 部署范围：V2 后端 JAR 与完整前端静态资源；无数据库迁移或数据修复。
- 修复内容：Lesson records 按 `scheduleRuleId` 归入排课周期，再按周期顺序插入 Schedule adjusted 分隔线；周期内仍按课程日期排序。过去调课生成的日期早于调整时间的新课次也会显示在新周期分隔线之后。
- 构建：干净 worktree 中前端 `npm ci && npm run build` 成功（88 个文件）；后端 Java 17 `mvn -Dmaven.test.skip=true package` 成功。Playwright 排课周期分隔 E2E 测试通过。
- 发布目录：`/home/class-deduction-system/releases/v2-20261016-7bb0446`。
- 后端 JAR SHA-256：`a9cf184a852fd3313859a9a859e179bc62612bfdee0e6baf8cf74d9f1823eec8`。
- 前端 `index.html` SHA-256：`ab23837be88f3fa78b3b80db9ceae70d4e02a1d716ee8a80ca33252ef7a98d2b`。
- 1-on-1 页面 JS SHA-256：`0d129fab739bd7c7c8b957d285af0e4c429aedb6c23eb15773238ef39688e936`。
- 回滚依据：发布目录中的 `backend/v2.conf.before_20261016T151744Z`、`backend/DEPLOY_VERSION.before_20261016T151744Z`、`front/dist.before`；旧版产物仍保留在发布目录 `v2-20261016-4fe979f`。

## 生产验证

- `class-deduction.service` 与 Nginx 均为 active；公网首页和 1-on-1 页面 JS 均返回 HTTP 200，健康接口返回 `{"status":"UP"}`。
- 公网 `index.html` 与 1-on-1 页面 JS 的 SHA-256 均与本地构建一致。
- `nginx -t` 通过；部署后 10 分钟 `class-deduction.service` 错误级日志无记录。
