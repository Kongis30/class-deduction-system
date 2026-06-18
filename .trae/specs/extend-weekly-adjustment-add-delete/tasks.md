# Tasks
- [x] Task 1: 梳理现有本周调整实现
  - [x] SubTask 1.1: 确认前端 `Adjust This Week` 入口、弹窗、payload 和课时差额计算位置
  - [x] SubTask 1.2: 确认后端 weekly adjustment DTO、服务层事务、冲突校验、日志写入方式
  - [x] SubTask 1.3: 确认可复用的 lesson occurrence 字段和取消状态语义

- [x] Task 2: 扩展后端 weekly adjustment 契约
  - [x] SubTask 2.1: 将 weekly adjustment item 扩展为支持 `UPDATE`、`CREATE`、`DELETE` 动作
  - [x] SubTask 2.2: 对 `CREATE` 支持 plannedStartAt、plannedEndAt、plannedUnits、plannedTeacherId、plannedClassroomId、reason
  - [x] SubTask 2.3: 对 `DELETE` 要求 occurrenceId 和 reason，并采用取消课次语义
  - [x] SubTask 2.4: 结果 VO 返回新增、修改、删除数量，以及 originalWeeklyUnits、adjustedWeeklyUnits、unitDelta

- [x] Task 3: 实现后端增删改业务规则
  - [x] SubTask 3.1: 同一事务内处理本周新增、修改、删除动作，任一失败则整体回滚
  - [x] SubTask 3.2: 限制所有动作只作用于当前自然周和当前课包
  - [x] SubTask 3.3: 拒绝删除或修改已核销、已取消、非当前周、非当前课包课次
  - [x] SubTask 3.4: 对新增和修改后的有效课次执行学生、老师、教室冲突校验，并避免批次内误报
  - [x] SubTask 3.5: 每个动作写入调整日志，区分 `WEEKLY_TEMP_CREATE`、`WEEKLY_TEMP_UPDATE`、`WEEKLY_TEMP_DELETE`

- [x] Task 4: 实现前端本周课次增删交互
  - [x] SubTask 4.1: 在 `Adjust This Week` 弹窗中提供 `Add lesson` 独立按钮
  - [x] SubTask 4.2: 允许新增行填写时间、课时、老师、教室和原因
  - [x] SubTask 4.3: 允许已有未核销课次标记为删除，并在 UI 中保留删除态用于保存前确认
  - [x] SubTask 4.4: 继续支持已有课次修改时间、课时、老师、教室
  - [x] SubTask 4.5: 前端实时计算原计划课时、调整后课时、差额、新增数、修改数、删除数

- [x] Task 5: 完善保存确认与错误处理
  - [x] SubTask 5.1: 保存前展示英文影响提示，明确多上、少上、不变的后续影响
  - [x] SubTask 5.2: 新增或删除缺少原因时阻止保存
  - [x] SubTask 5.3: 保存成功后刷新当前课包 schedule 和本周课次数据
  - [x] SubTask 5.4: 后端冲突、状态不可改、非当前周等错误在前端明确展示

- [x] Task 6: 验证变更
  - [x] SubTask 6.1: 增加后端测试覆盖新增、删除、混合增删改、缺少原因、冲突、已核销/已取消拒绝、非当前周拒绝
  - [x] SubTask 6.2: 运行后端相关测试
  - [x] SubTask 6.3: 运行前端类型检查
  - [ ] SubTask 6.4: 手动验证新增课次、删除课次、混合保存、保存失败回滚、保存成功刷新

# Task Dependencies
- Task 2 depends on Task 1
- Task 3 depends on Task 2
- Task 4 depends on Task 2
- Task 5 depends on Task 3 and Task 4
- Task 6 depends on Task 3, Task 4, and Task 5
