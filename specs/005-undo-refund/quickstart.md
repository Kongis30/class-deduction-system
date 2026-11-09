# 验证指南：撤销退款

本文件是实施后的验证清单；当前规划阶段未运行测试、连接数据库或执行迁移。测试使用隔离环境，禁止对真实退款做实验。

## 本次执行记录（2026-11-09）

- 后端：`EnrollmentOrderServiceImplTest` 57 例（51 通过 / 6 失败）、`DataSummaryServiceImplTest` 34 例（全部通过）。因其他会话未提交改动导致 `mvn test-compile` 失败，本次改用独立 classpath 单独编译并运行这两个测试类。
- 6 个失败在 `e45ddf7` 干净 worktree 上同样失败（`shouldCorrectV2ReceiptWhenProjectGradeDiffersFromActualClass`、`shouldExposeV2GroupLinksAndClassNameInOrderDetailForReceiptRecovery`、`shouldKeepSpentTopUpPaymentAndOnlyHideReceiptWhenAvailableCreditIsInsufficient`、`shouldNotRestoreCancelledEnrollmentWhenCorrectingReceipt`、`shouldNotSelfHealOrphanWhenOverlappingActiveEntitlementExists`、`shouldPreserveExistingSessionEnrollmentWhenOnlyReceiptPriceChanges`），与撤销无关。
- 前端：`npm run typecheck` 通过。
- 文档：`doc/API_CONTRACT.md` 已记录候选查询与撤销端点、失败错误码；两个前端入口的按钮及确认文案已改为撤销语义。
- 尚未执行：真实 MySQL 并发集成、Playwright 验收、浏览器实际交互、旧快照真实数据验证。

## 前置检查

1. 确认已获得技术方案实施授权、明确集成基线且保护其他会话的未提交改动。按项目规则选择临时分支；不要从 dirty worktree 部署。
2. 核对实际数据库 `information_schema`、迁移记录和快照字段容量；仅查询，不直接执行未经审核的 DDL。检查样本是否有完整快照及唯一退款关联，禁止将不可证明的数据作为可撤销样本。
3. 构造单笔整单退款、部分课程退款、多人共享私教、班课、多个退款及 Credit 测试数据，固定 America/New_York 时间边界。

## 后端测试

- 成功路径逐项断言退款前订单/课程/参与人/课包/班级权益/分摊恢复，未来实例及周期规则和时段恢复；原本停课/结课保持不变。历史课次、lesson record、hour ledger 和共享班课不变。
- 同一显示行多购买记录、一购买多次退款、其他学生及课程不受影响、历史整单/跨行退款不得部分撤销；参与人歧义、缺分摊、重复收据、无快照、损坏/不完整/旧快照、空值、逻辑删除、金额 scale 和时间精度；不可证明时全部拒绝，无 legacy 降级。
- 退款后产生付款、退款、课时、排课或其他事实；相冲突拒绝。退款后的已过期课次不复活，未来规则不生成历史实例。
- Credit 已使用、余额不足、原流水不匹配时拒绝；验证正常冲正及账本留痕。注入每个恢复阶段失败，检查数据库全部回滚及失败审计持久化；成功审计与业务状态一致。
- 测试 MANAGER 及其他角色拒绝、快照不暴露；验证旧收据入口遵守同一规则。
- 使用真实 MySQL 的并发集成测试：并发重复撤销最多一个成功；撤销与排课新增/调整、课时确认、Credit 使用、订单更正交错，在冲突检查与提交之间插入竞争，确认共同锁协议阻止幻读/覆盖或安全拒绝。仅 mock 不可作为并发安全证据。

参考运行：`cd backend && mvn test -Dtest=EnrollmentOrderServiceImplTest,DataSummaryServiceImplTest`。2026-11-09 在基于 `main_v2` 的集成 worktree（`d75f407`）执行：`EnrollmentOrderServiceImplTest` 68 个用例、`DataSummaryServiceImplTest` 39 个用例全部通过；`shouldCorrectV2ReceiptWhenProjectGradeDiffersFromActualClass` 在 `main_v2` `0f14653` 基线同样失败，与本次改动无关。真实 MySQL 并发集成套件仍未执行。

## 前端验收

在 Refund Student 上验证桌面、键盘和移动操作入口；一行多笔明确选择、整笔影响范围、外部资金警告、取消和防重复提交；错误提示能引导刷新/人工核查。成功后保留产品 tab、月份、筛选，若仍有其他退款则行保留；收据页执行相同确认流程。检查 MANAGER 无按钮、无候选数据和无快照。

在 `frontend` 执行 `npm run typecheck` 与对应 Playwright acceptance 测试；根据环境需要运行 `npm run build`。记录实际命令、结果和无法验证项。
