# 部署记录：2026-11-12 — 彻底移除收据更正 reason 要求

## 版本
- Commit: `2823419`（main_v2）— fix: 完全移除收据更正的付款修改 reason 强制要求
- Release 目录: `/home/class-deduction-system/releases/v2-20261112-2823419-remove-correction-reason/`
- 上一版本: `v2-20261112-70811c0-allocation-replay-fix`

## 修复内容
96683cc 只删了前端 reason 弹窗，后端 `correctOrder` 仍在付款行变更时校验
`correctionReason` 并抛 `CORRECTION_PAYMENT_REASON_REQUIRED`（线上报
"Enter a reason for correcting the original payment details."）。

本次彻底移除该概念：
- 删除后端强制校验（`CORRECTION_PAYMENT_REASON_REQUIRED` 分支）
- 删除 `EnrollmentOrderCorrectionRequest.correctionReason` DTO 字段
- 删除前端 `EnrollmentOrderCorrectionPayload.correctionReason` 类型声明
- 更正日志 operationDesc 改为固定文案，不再拼 reason
- 顺带删除只服务于该校验的 `participantPaymentsChanged` / `normalizedPaymentRemark`

后端测试（EnrollmentOrderServiceImplTest 81 例）与前端 typecheck 均通过。

## 产物校验和（本地与服务器一致）
- backend.jar: `4669e91c861b6e82a7004e6f82830bc5f94367fdfb51b3db48516f385f1aed65`
- front/dist/index.html: `7c21c58386d7223798298a92bbb8ea8dba1355f153473496fe27da040c0f7540`

## DB 备份
- `/root/jin-v2-backups/pre-2823419-20261112-025636.sql.gz`（gzip -t 通过）

## 验证结果
- systemd `class-deduction.service`: active，NRestarts=0
- API 正常响应 JSON（auth/login INVALID_CREDENTIALS 格式正确）

## 回滚
符号链接切回 `v2-20261112-70811c0-allocation-replay-fix` 并重启服务。
