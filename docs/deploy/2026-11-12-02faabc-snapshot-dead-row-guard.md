# 部署记录：2026-11-12 — 快照行归属匹配补 enrollmentOrderItemId

## 版本
- Commit: `02faabc`（main_v2）— fix: 快照行归属匹配补 enrollmentOrderItemId，杜绝死行产生
- Release 目录: `/home/class-deduction-system/releases/v2-20261112-02faabc-snapshot-dead-row-guard/`
- 上一版本: `v2-20261112-401e793-detached-id-match`（前端）

## 修复内容
死行产生的根因：删除课程行时，快照清理 `belongsToOrderItem` 只匹配
`orderItemId`/`orderItemLineNo`/`lineNo`，而老格式快照行只有
`enrollmentOrderItemId`，匹配失败 → 快照行残留成死行。

本次补 `enrollmentOrderItemId` 匹配（String.valueOf 比较，null 安全），应用于：
- `syncReceiptAfterOrderItemDeletion`：快照 items/extraFees/discounts/artifacts 过滤
- `filterOrderItemLines`：订单明细行过滤
- `requireSeparableOrderItemLines`：删除课程的费用/折扣分离性校验
- `participantCourseDiscount`：折扣归属匹配（同步补 `hasCourseAssignments` 检测
  `enrollmentOrderItemId`，避免老格式明细被误判为"无归属"走比例分摊）

新订单删除课程行后快照同步清理，死行不再新增；存量死行兼容逻辑（detached
标记、换课、卡片认领）保留。

验证：EnrollmentOrderServiceImplTest 全绿；Node 模拟线上真实快照（老格式行只有
enrollmentOrderItemId）删除目标行后快照仅剩活行、无误伤、null 安全。

## 存量数据核查
生产库扫描：`enrollment_order_item` 软删行 83 行，其中"订单同时存在活行且收据
未删"的只有订单 `2099257910598565890`（Ze Mu Ma，1 个死行）——即唯一受死行
bug 影响的订单，其余软删行均为整单删除场景。无需数据修复。

## 产物校验和（本地与服务器一致）
- backend.jar: `bb6a2be13afbc11b40f3dca98e3617a6cff437af82863258770a939c7a8f0807`
- front/dist/index.html: `79dc15e3c129429ac3d3100233f884822b0c0efdc7c1c80f67cd05d04e5d7c38`（401e793 前端，未变）

## DB 备份
- `/root/jin-v2-backups/pre-02faabc-*.sql.gz`（gzip -t 通过）

## 验证结果
- systemd `class-deduction.service`: active，NRestarts=0
- API 正常响应 JSON（auth 校验格式正确）

## 回滚
符号链接切回上一 release 并重启服务。
