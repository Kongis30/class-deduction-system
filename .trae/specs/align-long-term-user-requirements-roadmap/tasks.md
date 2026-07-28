# Tasks

- [x] Task 1: 固化长期优先级与当前缺口清单
  - [x] SubTask 1.1: 对齐 PPT、需求整理、现有 specs，列出已完成、部分完成、未完成和需产品确认事项。
  - [x] SubTask 1.2: 将缺口按用户频率和数据风险分为 P0、P1、P2。
  - [x] SubTask 1.3: 把每个缺口映射到用户入口和受影响的数据对象。

- [x] Task 2: 第一阶段修复数据可信度问题
  - [x] SubTask 2.1: 统一 Students、Orders、Reports 对取消、退款、已收、余额的统计口径。
  - [x] SubTask 2.2: 明确多课程订单后续补款的订单级与课包级金额语义，避免重复放大。
  - [x] SubTask 2.3: 增加 Reports 一致性检查，至少覆盖 payment_record vs order amount、hour_ledger vs package balance。
  - [x] SubTask 2.4: 补充 Reports 和金额/课时一致性相关测试。

- [x] Task 3: 第二阶段补齐高频运营闭环
  - [x] SubTask 3.1: 完成老师工资统计闭环，支持按老师和日期范围汇总终确认消课、费率和应付金额。
  - [x] SubTask 3.2: 完成教室后补闭环，从待补教室列表到保存校验再到 Room Calendar 复查。
  - [x] SubTask 3.3: 为排课和教室冲突提供结构化冲突详情，方便前端展示冲突资源、时间和已有课次入口。
  - [x] SubTask 3.4: 补强报名初始 1-on-1 课次的学生时间冲突校验。

- [x] Task 4: 第三阶段推动产品确认事项
  - [x] SubTask 4.1: 梳理部分退款规则，需要确认退款粒度、已消课处理、教师工资影响和收据展示。
  - [x] SubTask 4.2: 梳理 credit / 预存款规则，需要确认是否跨订单抵扣、是否可退款、是否进入 Reports。
  - [x] SubTask 4.3: 梳理冲刺班完整矩阵、班课部分购买、跨月补课、停课续费规则。
  - [x] SubTask 4.4: 梳理 Excel 导出、线上课税号、支票号结构化字段规则。

- [x] Task 5: 持续优化交互与代码结构
  - [x] SubTask 5.1: 每个核心入口保留清晰功能边界，避免菜单和页面重复暴露同一能力。
  - [x] SubTask 5.2: 每次页面改动都检查“入口、下一步、错误修正”是否清楚。
  - [x] SubTask 5.3: 保持代码最小改动，优先复用现有 API、类型和本地组件。
  - [x] SubTask 5.4: 每个阶段结束后运行对应后端测试、前端 typecheck/build，并补充必要回归测试。

## Task Dependencies

- Task 2 depends on Task 1
- Task 3 depends on Task 1
- Task 4 can run in parallel with Task 2 and Task 3, but implementation must wait for product confirmation
- Task 5 runs throughout Tasks 1-4
