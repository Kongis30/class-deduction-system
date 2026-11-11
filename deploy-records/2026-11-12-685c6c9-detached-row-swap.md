# 部署记录：2026-11-12 685c6c9 — 死快照行换课修复

- **commit**: 685c6c9（main_v2，后端 impl 部分经并行会话提交 619980d 入库，内容完整）
- **release**: `/home/class-deduction-system/releases/v2-20261112-685c6c9-detached-row-swap`
- **backend.jar sha256**: `8621499524e501f81944714f631861085bb5003369c80823a719f0a8d2e2f011`（本地/服务器一致）
- **front/dist/index.html sha256**: `7c21c58386d7223798298a92bbb8ea8dba1355f153473496fe27da040c0f7540`（一致）
- **DB 备份**: `/root/jin-v2-backups/pre-685c6c9-20261111T182335Z.sql.gz`（1.35MB）
- **迁移**: 无 DDL
- **验证**: systemd active、8084 监听、启动日志无 error、API 鉴权响应正常、dist 链接正确

## 修复内容

Ze Mu Ma 收据（老快照行只有 enrollmentOrderItemId 无 lineNo）在死行上直接换课时报
"Original course rows cannot be removed or replaced in Edit Receipt"：
- 死行 orderItemId 不再进更正 payload；编辑打开时记录死行基线（行号+原订单行 ID）
- 未变化的死行不提交（避免被后端当成新增课程重复下单）
- 死行换课 = 移除老快照行（removedReceiptItemOrderItemIds 按 ID 匹配，兼容精度丢失）+ 新增课程行
