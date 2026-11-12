# 部署记录：2026-11-12 — 删除课程行保留付款归属，死行删除同步删归属卡片

## 版本
- Commit: `5d3b9ae`（main_v2）— fix: 删除课程行保留付款归属，Edit Receipt 死行删除同步删归属卡片
- Release 目录: `/home/class-deduction-system/releases/v2-20261112-5d3b9ae-detached-row-cards/`
- 上一版本: `v2-20261112-2823419-remove-correction-reason`

## 修复内容
用户报"删 course row 1 后，course row 2 的支付/折扣信息被删/错乱"。根因：删除课程行时
付款分摊只被软删，订单详情与收据快照都不再携带行级归属，前端加载时把无归属付款
兜底挂到第一个课程名下，删除时按 rowKey 联动误删其他课程卡片。

- 订单详情 `payments.allocations` 返回含软删分摊（`deleted` 标记）
- 收据快照 `receiptPaymentPayloads` 写入 allocations 行级归属（含软删行）
- `PaymentAllocationMapper` 新增 `selectAllByOrderIdIncludingDeleted`（原生 SQL 绕过 TableLogic）
- 前端删除死快照行时归属该行的付款/折扣卡片随行删除（`removeDetachedCourseCards`）
- 前端 `livePayments` 用软删分摊把无归属付款挂回死行名下

验证：EnrollmentOrderServiceImplTest 全绿；前端 typecheck 通过；全量后端测试
失败项经 git stash 基线对比确认为既有问题（LessonRecord/ClassHoliday 等）。

## 产物校验和（本地与服务器一致）
- backend.jar: `7b3a2b57ccdb62f2e8d7e182a89c74c21a0089692c74c0d9536c2e4fa49dc4f8`
- front/dist/index.html: `678a7fbc683acace6f5efe3728ddcd7f41589af627aefae2b109944cac5810a8`

## DB 备份
- `/root/jin-v2-backups/pre-5d3b9ae-104036.sql.gz`（gzip -t 通过）

## 验证结果
- systemd `class-deduction.service`: active，NRestarts=0
- 应用启动日志正常（SpringDoc 警告为常规提示）
- API 正常响应 JSON

## 回滚
符号链接切回 `v2-20261112-2823419-remove-correction-reason` 并重启服务。
