# 部署记录：2026-11-12 96683cc — 去掉 Payment correction reason 弹窗（前端-only）

- **commit**: 96683cc（main_v2）
- **release**: `/home/class-deduction-system/releases/v2-20261112-96683cc-remove-payment-reason`
- **backend**: 复用 dc78de9 的 backend.jar（jar 未变，无需重建）
- **front/dist/index.html md5**: `405f2a4fba91f3f31946bf3dde704518`（与本地构建一致）
- **DB**: 无变更、无备份（纯前端交互改动）
- **验证**: systemd active、8084 监听、API 鉴权响应正常、dist md5 一致
- **e2e**: receipt-payment-and-purchase-actions.spec.ts 5/5 通过（reason 弹窗断言改为 correctionReason 不提交）
