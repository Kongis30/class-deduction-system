# Tasks

- [x] Task 1: 梳理当前端到端数据流
  - [x] SubTask 1.1: 画清 Registration intake 的数据链路：student、enrollment_order、enrollment_order_item、course_package/class_enrollment、payment_record、receipt_record、user_account、lesson_occurrence。
  - [x] SubTask 1.2: 梳理排课来源：报名初始课次、recurring schedule rule、weekly adjustment、classroom backfill。
  - [x] SubTask 1.3: 梳理消课确认链路：teacher confirmation、admin confirmation、hour ledger、package balance、teacher payment。
  - [x] SubTask 1.4: 梳理 Reports 的数据来源和字段解释规则。

- [x] Task 2: 定义数据主从关系和不可变快照
  - [x] SubTask 2.1: 明确学生当前档案与报名订单快照的边界。
  - [x] SubTask 2.2: 明确课程价格配置与订单明细快照的边界。
  - [x] SubTask 2.3: 明确订单金额、付款流水、余额、退款和 credit 的当前规则与未决规则。
  - [x] SubTask 2.4: 明确课包余额、课次、消课记录和课时台账之间的主从关系。

- [x] Task 3: 定义冲突校验矩阵
  - [x] SubTask 3.1: 列出报名阶段校验：重复学生、重复班课报名、缺价格、缺老师、付款超额、班级不可用。
  - [x] SubTask 3.2: 列出排课阶段校验：学生冲突、老师冲突、教室冲突、slot 自身重叠、课包余额上限、已确认课次保留。
  - [x] SubTask 3.3: 列出本周调整校验：当前周边界、课包归属、已核销不可改、已取消不可改、缺原因、批次内冲突。
  - [x] SubTask 3.4: 列出消课阶段校验：老师仅确认本人课次、管理员复核、重复确认、驳回后不扣课不计薪。

- [x] Task 4: 设计用户交互解决方案
  - [x] SubTask 4.1: 为报名错误设计下一步：补课程价格、选择老师、选择班级、查看已有学生、修正付款。
  - [x] SubTask 4.2: 为排课冲突设计下一步：换时间、换老师、换教室、取消本次调整、查看冲突详情。
  - [x] SubTask 4.3: 为消课异常设计下一步：查看驳回原因、重新确认、管理员代确认、查看余额影响。
  - [x] SubTask 4.4: 为 Reports 异常设计下一步：打开报名历史、付款时间线、课包详情或一致性检查。

- [x] Task 5: 盘点当前实现差距并形成后续修复任务
  - [x] SubTask 5.1: 对照当前代码，标记已实现、部分实现、缺失的系统职责。
  - [x] SubTask 5.2: 将缺失项拆成后续可实施任务，按数据风险和用户频率排序。
  - [x] SubTask 5.3: 明确哪些缺失不应现在实现，只需产品确认，例如 credit、Excel export、线上课税号、完整价格矩阵。

- [x] Task 6: 验证现有覆盖是否支撑数据准确性
  - [x] SubTask 6.1: 对照现有后端测试和 E2E，确认报名、付款、排课、冲突、消课、Reports 的覆盖边界。
  - [x] SubTask 6.2: 标记缺少的测试：Reports 业务逻辑、数据一致性校验、冲突详情 UI、真实端到端打印/导出。
  - [x] SubTask 6.3: 给出最小回归命令和人工验收路径。

## Task Dependencies

- Task 2 depends on Task 1
- Task 3 depends on Task 1 and Task 2
- Task 4 depends on Task 3
- Task 5 depends on Tasks 2 through 4
- Task 6 depends on Task 5
