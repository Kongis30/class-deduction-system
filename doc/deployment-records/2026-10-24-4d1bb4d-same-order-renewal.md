# JIN V2 部署：续课、加课与付款保留在同一订单

- 源码提交：`main_v2` `4d1bb4d34a8c5c648aa79cba2f07bf637b490e1a`（`Keep receipt renewals and payments on the same order`）。
- 发布目录：`/home/class-deduction-system/releases/v2-20261024-4d1bb4d-same-order-renewal`。
- 部署范围：V2 后端 JAR 与前端静态资源；不涉及数据库结构变更或数据迁移。
- 业务约束：Edit Receipt 中续课或新增课程时，所有课程行仍属于当前订单；付款记录继续追加在当前订单下，Data Summary 的 View receipt 仍打开该订单。相同产品和项目的购买行合并为同一逻辑课包，供排课、课时余额及 1-on-1 消课按同一课程管理。
- 构建验证：干净 worktree 执行 `npm ci && npm run build` 成功（含 TypeScript 检查）；Java 24 环境按 Java 17 release 执行 `mvn -Dmaven.test.skip=true clean package` 成功，后端测试源码未编译。
- 端到端验证：Playwright `receipt-payment-and-purchase-actions.spec.ts` 3/3 通过，覆盖 Data Summary 保持原订单跳转、Add payment 保留原订单以及 Add course 追加课程并保留收据。
- 后端 JAR SHA-256：`ac6927d6d638fd215be791753da4676c33e958d76ca23b81dcf1116c4084a252`。
- 前端 `index.html` SHA-256：`1d77ab5adbb4789771dc4edd5bcfda93529232c4bea9cbec32343e99d68eb1cb`。
- Edit Receipt JS SHA-256：`9ba58a2686a72be4d58849273eb3ee0f5f73d2d327a833417c05bf3cbf180618`。

## 生产验证

- JIN `class-deduction.service` 与 Nginx 均为 active；本机和公网健康接口均返回 `{"status":"UP"}`。
- 公网首页和 Edit Receipt JS 均返回 HTTP 200，内容 SHA-256 与部署产物一致。
- 数据库迁移：无。

## 回滚

- 后端上一版本：`/home/class-deduction-system/releases/v2-20261023-651b7f0-monthly-plan-rate`。
- 前端上一版本：`/home/class-deduction-system/releases/v2-20261023-9f716ea-receipt-month-load/front/dist`。
- 新版本目录中的 `backend/v2.conf.before` 保存了部署前的 systemd V2 配置；恢复该配置与以上前端/后端路径后执行 `systemctl daemon-reload && systemctl restart class-deduction.service && nginx -t && systemctl reload nginx`。
