# 部署记录：2026-11-12 — Edit Receipt 支持删除付款行

## 版本
- Commit: `5fde3ed`（main_v2）— feat: Edit Receipt 支持直接删除已保存的付款行
- Release 目录: `/home/class-deduction-system/releases/v2-20261112-5fde3ed-payment-row-delete/`
- 上一版本: `v2-20261112-02faabc-snapshot-dead-row-guard`

## 变更内容
按用户明确的业务定位：Edit Receipt 就是让管理员任意修改这笔历史报名，包括已保存的
付款，不要求填 reason、不要求绕到退款流程。

后端：
- 更正里未出现的已保存付款视为删除：软删付款流水 + 清除课程分摊；
  `paymentRemoved` 纳入 `moneyChanged` 触发订单金额重算（已收/应收/余额/状态）
- Credit 抵扣的付款删除时归还当时扣减的 Credit
  （`StudentCreditService.restoreConsumedCredit`，新增 `CREDIT_RESTORED` 流水）
- 移除 `CORRECTION_PAYMENT_MISSING` 拦截；退款流水仍只读

前端：
- 付款行去掉"仅非更正模式且 >1 行"的显示条件，Edit Receipt 每行都有 × 删除按钮
- 删除不再受"至少保留一行"限制，可删到零（订单变为未收款）
- 清理失效的 `removePaymentLine` 与 `preservesOriginalCorrectionPayments`

文档：
- `docs/edit-receipt-business-rules.md` 写入核心原则并更新付款章节、验收场景

## 测试
- 新增 `shouldSoftDeletePaymentOmittedFromCorrectionAndRecalculateReceived`
- EnrollmentOrderServiceImplTest 82 例全绿
- StudentCreditServiceImplTest(13) + PaymentAllocationServiceTest(5) 全绿
- 前端 typecheck 通过

## 产物校验和（本地与服务器一致）
- backend.jar: `9de3810f57efebaa385d21d60e5317de325e33d6ea89ee5841e0d27200d43f32`
- front/dist/index.html: `cf72ea88f087f50c7bfcc114d82bd8ecb012156d214fd97aebb7e7639163dccf`

## DB 备份
- `/root/jin-v2-backups/pre-5fde3ed-121603.sql.gz`（gzip -t 通过）

## 验证结果
- systemd `class-deduction.service`: active，NRestarts=0
- API 正常响应 JSON（参数校验格式正确）

## 回滚
符号链接切回 `v2-20261112-02faabc-snapshot-dead-row-guard` 并重启服务。
