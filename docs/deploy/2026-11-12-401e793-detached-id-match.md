# 部署记录：2026-11-12 — 死行 ID 匹配修复（前端）

## 版本
- Commit: `401e793`（main_v2）— fix: 死行保留原订单行 ID，软删分摊认领与卡片删除按 ID 匹配
- Release 目录: `/home/class-deduction-system/releases/v2-20261112-401e793-detached-id-match/`（仅前端）
- 上一版本: `v2-20261112-5d3b9ae-detached-row-cards`

## 修复内容
5d3b9ae 上线后线上验证发现死行认领仍失败：软删分摊（enrollmentOrderItemId=930）
匹配不到任何课程行，5 笔付款全部挤到剩余课程名下（Paid $3,244）。原因：上一版
移除了死行从快照 enrollmentOrderItemId 取 orderItemId 的兜底，死行 ID 为 undefined。

本版恢复死行保留快照原订单行 ID 作"匹配用身份"：软删分摊按该 ID 把付款挂回死行、
删除死行时按 ID 认领归属卡片；保存 payload 时死行仍不带 orderItemId（换课语义不变）。

精度安全：订单详情与收据快照的响应都经 preprocessLargeIds 在原始 JSON 字符串层
把 Snowflake ID 转字符串，无精度丢失。Node 模拟验证：500/744/501 挂死行并随行
删除，499 留在课程 2。

## 产物校验和（本地与服务器一致）
- front/dist/index.html: `79dc15e3c129429ac3d3100233f884822b0c0efdc7c1c80f67cd05d04e5d7c38`
- backend.jar 无改动，仍为 5d3b9ae 版本（`7b3a2b57...`）

## 回滚
符号链接切回 `v2-20261112-5d3b9ae-detached-row-cards/front`。
