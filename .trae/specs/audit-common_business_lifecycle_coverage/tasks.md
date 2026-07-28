# Tasks

- [x] Task 1: 审计付款与订单金额完整性
  - [x] SubTask 1.1: 验证报名时多笔付款、报名后补款、退款流水与订单 Paid / Balance / payment status 的一致性。
  - [x] SubTask 1.2: 审计多课程、多课包订单的补款归属，确认是否存在 package received 重复累计。
  - [x] SubTask 1.3: 明确订单级付款、课程明细级付款、课包级实收三个概念的权威语义。
  - [x] SubTask 1.4: 设计用户查看付款时间线、余额变化和退款状态的最小交互。

- [x] Task 2: 审计实际课时与排课余额完整性
  - [x] SubTask 2.1: 验证实际课时短于、等于、长于计划课时的扣课、台账和未来排课调整结果。
  - [x] SubTask 2.2: 验证没有未来可调整课次时，实际课时变更的错误和用户下一步。
  - [x] SubTask 2.3: 验证 Formal Leave、No Show、取消、补课和管理员驳回对余额、排课和老师工资资格的影响。
  - [x] SubTask 2.4: 设计计划、实际、扣课、未来排课变化的统一解释视图。

- [x] Task 3: 审计退课、退款和 credit 边界
  - [x] SubTask 3.1: 验证当前 Full refund 对订单、付款流水、课包、班课报名、未来课次和 Reports 的影响。
  - [x] SubTask 3.2: 标记部分退款、按课程明细退款、已消课退课、credit/预存款为未实现能力。
  - [x] SubTask 3.3: 对齐 Registration、Receipt 和退款对话框的政策文案与当前实际能力。
  - [x] SubTask 3.4: 列出部分退款和 credit 进入实现前必须由产品确认的规则。

- [x] Task 4: 审计跨模块数据统计一致性
  - [x] SubTask 4.1: 对比 Students、订单详情、Reports 对 CANCELLED / REFUNDED 订单的金额、余额和状态口径。
  - [x] SubTask 4.2: 设计 payment_record vs order amount、course package balance vs hour ledger 的一致性检查。
  - [x] SubTask 4.3: 设计 Reports `Needs review` 标记和钻取到订单、付款、课包、台账详情的入口。

- [ ] Task 5: 补齐高风险测试与用户体验方案
  - [x] SubTask 5.1: 为多次付款、多课包补款、实际课时偏差、整单退款、退款后补款拒绝补充后端测试。
  - [ ] SubTask 5.2: 为付款异常、课时无法平衡、退款确认和报表差异补充前端或 E2E 验收场景。
    - [ ] 后端未启动导致前端 E2E 无法执行（`127.0.0.1:8084 ECONNREFUSED`）；需使用本地无数据库模式启动前后端后重跑 `enrollment-orders-1on1` spec。
  - [x] SubTask 5.3: 形成按风险优先级排序的后续实现任务，不在本审计阶段直接实现未定义的部分退款或 credit。

- [x] Task 6: 修复审计确认的安全业务缺口
  - [x] SubTask 6.1: 停止将订单级后续补款重复写入每个关联课包的实收金额。
  - [x] SubTask 6.2: Full refund 后清零订单余额，并统一 Students / Reports 对取消和退款订单的统计口径。
  - [x] SubTask 6.3: 订单补款界面统一使用 amountDue / balanceAmount，并展示 PAYMENT / REFUND 类型。
  - [x] SubTask 6.4: 将报名表、收据与退款对话框文案对齐为当前可执行的 Full refund 能力。
  - [x] SubTask 6.5: 教师付款创建前重新校验老师归属、终确认状态和可计薪结果。
  - [x] SubTask 6.6: Formal Leave 自动补课前校验学生、老师、教室冲突，冲突时整体回滚。
  - [x] SubTask 6.7: 为已修复路径补充后端测试和前端验收。

- [x] Task 7: 补齐 Reports 审计验证与钻取
  - [x] SubTask 7.1: 为付款流水与订单快照、课包与课时台账差异补充 `DataSummaryServiceImpl` 专项测试。
  - [x] SubTask 7.2: 在 `Needs review` 行提供订单、付款、课包和课时台账的只读钻取入口。

## Task Dependencies

- Task 2 depends on Task 1
- Task 3 can run in parallel with Task 2
- Task 4 depends on Task 1 and Task 2
- Task 5 depends on Tasks 1 through 4
- Task 6 depends on Tasks 1 through 3
