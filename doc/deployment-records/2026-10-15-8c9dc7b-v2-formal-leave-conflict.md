# JIN V2 部署：Formal Leave 跳过原课次冲突校验

- 代码版本：`main_v2` 提交 `8c9dc7b`。
- 部署日期：2026-10-15（Asia/Shanghai）。
- 部署对象：Class Deduction V2 前端静态资源与后端 Spring Boot JAR；无数据库结构或业务数据变更。
- 前端：干净 worktree 执行 `npm ci && npm run build` 成功，共 88 个文件。
- 后端：JDK 17 下 `mvn -Dmaven.test.skip=true package` 成功；本次未运行测试套件。
- 后端 JAR SHA-256：`a18e1eb14d504ec74829e18f6a4193d5004e5d803614fb6d51790df142017636`。
- 线上 1-on-1 页面 JS SHA-256：`78f5a1717422a4b6baf42c0e016797d7aa50c572116774928a658b1b61fa6d3a`。
- 线上发布目录：`/home/class-deduction-system/releases/v2-20261015-8c9dc7b`。
- 前端备份：`/home/class-deduction-system/front/dist.bak_8c9dc7b_20261015T035038Z`。
- 回滚前端目录：`/home/class-deduction-system/front/dist.rollback_8c9dc7b_20261015T035038Z`。

## 变更

- Formal Leave 不再校验原课次时段的学生、教师和教室冲突，因为请假不实际占用原时段。
- 生成的补课课次仍由既有逻辑单独校验资源冲突；其他消课结果保留原冲突校验。

## 生产验证

- `class-deduction.service` 与 Nginx 均为 active；`nginx -t` 通过。
- 公网首页 HTTP 200；`/api/actuator/health` HTTP 200 / `UP`。
- 空登录请求返回 HTTP 400；未登录 Data Summary API 返回 HTTP 401。
- 线上后端 JAR 与前端 1-on-1 页面 JS 校验和均与干净构建产物一致。
- 新后端启动后 journal error-priority 日志无记录。
