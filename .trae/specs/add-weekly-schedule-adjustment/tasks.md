# Tasks
- [x] Task 1: 梳理现有 scheduling 调整数据流
  - [x] SubTask 1.1: 确认前端 1v1 课包课次列表的数据字段是否包含 packageId、studentId、plannedStartAt、plannedEndAt、plannedUnits、老师、教室和状态
  - [x] SubTask 1.2: 确认后端现有单课次调课接口、冲突校验和调整日志能否复用

- [x] Task 2: 设计并实现后端本周临时调整接口
  - [x] SubTask 2.1: 增加批量周调整请求 DTO，包含 packageId、weekStartDate、调整课次列表、原因和操作人
  - [x] SubTask 2.2: 增加周调整结果 VO，返回原本周课时、调整后课时、课时差额和被修改课次
  - [x] SubTask 2.3: 服务层只允许修改当前周内、未核销、未取消、属于同一学生课包的课次
  - [x] SubTask 2.4: 复用学生、老师、教室冲突校验，并为每个修改课次写入调整日志

- [x] Task 3: 实现前端本周临时调整交互
  - [x] SubTask 3.1: 在 1v1 课包展开区域增加 `This week adjustment` 入口
  - [x] SubTask 3.2: 弹窗展示本周可调整课次，支持修改时间、老师、教室和课时
  - [x] SubTask 3.3: 前端实时计算调整前后本周总课时差额
  - [x] SubTask 3.4: 保存前用英文提示多上、少上或不变时对后续排课的影响

- [x] Task 4: 刷新与错误处理
  - [x] SubTask 4.1: 保存成功后刷新当前课包课次列表
  - [x] SubTask 4.2: 冲突、不可修改状态和参数错误需要在页面展示明确英文错误

- [x] Task 5: 验证变更
  - [x] SubTask 5.1: 为后端周调整服务增加单元测试或覆盖现有测试
  - [x] SubTask 5.2: 运行后端相关测试
  - [x] SubTask 5.3: 运行前端类型检查
  - [x] SubTask 5.4: 手动验证本周多上、少上、课时不变和冲突失败场景

- [x] Task 6: 修复 Task 5 验证失败项
  - [x] SubTask 6.1: 修复前端 weekly adjustment 保存 payload 与后端 DTO 不一致的问题。当前前端 `ScheduleAdjustmentsView.vue`/`frontend/src/api/schedule.ts` 提交字段为 `adjustments`，后端 `WeeklyScheduleAdjustmentRequest` 接收字段为 `items`，会导致 `/v1/schedule/adjustments/weekly` 校验失败，影响保存成功和保存后刷新验证。
  - [x] SubTask 6.2: 修复后端 weekly adjustment 只校验 requested week、未强制当前自然周的问题。当前 `ScheduleAdjustmentServiceImpl.adjustWeekly` 会接受任意 `weekStartDate` 的周内课次，不满足拒绝非当前周课次的 checkpoint。
  - [x] SubTask 6.3: 增加/补充后端测试覆盖非当前周拒绝，并补充前后端 weekly payload 契约验证，修复后重新执行 Task 5 checklist。

# Task Dependencies
- Task 2 depends on Task 1
- Task 3 depends on Task 1
- Task 4 depends on Task 2 and Task 3
- Task 5 depends on Task 2, Task 3, and Task 4
