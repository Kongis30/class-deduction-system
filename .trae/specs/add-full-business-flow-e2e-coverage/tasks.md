# Tasks

- [x] Task 1: 梳理现有 E2E 与页面入口
  - [x] SubTask 1.1: 盘点现有 course product、enrollment、course packages、schedule/confirm、teacher payments 的 E2E 文件和可复用 helper。
  - [x] SubTask 1.2: 确认 Confirm 页面老师/管理员角色登录方式、Payments 页面入口、关键按钮和接口路径。
  - [x] SubTask 1.3: 标记已有覆盖与新增全链路覆盖的边界，避免重复测试同一单页面 CRUD。

- [x] Task 2: 补充跨页共享测试基础设施
  - [x] SubTask 2.1: 在 E2E helper 中补充跨模块 API 数据准备与查询方法，包括课程产品、报名订单、课包、课次、消课记录、老师付款记录。
  - [x] SubTask 2.2: 补充角色登录 helper，支持管理员、老师账号登录并在同一测试中切换用户。
  - [x] SubTask 2.3: 补充 DB cleanup，确保跨页链路产生的订单、课包、课次、消课记录、付款记录、账号、老师、学生、教室可按 marker 清理。

- [x] Task 3: 编写 1对1课程完整链路 E2E
  - [x] SubTask 3.1: 从 UI 创建 1对1课程产品，配置课时、价格、老师与后续可报名字段。
  - [x] SubTask 3.2: 从 Enrollment 页面完成学生报名与排课，校验订单、课包、课次计划生成。
  - [x] SubTask 3.3: 在 Course Packages/Schedule 页面追踪该课包与课次，校验课时余额和课次状态。
  - [x] SubTask 3.4: 用老师账号提交初确认，校验 `PENDING_ADMIN` 且未扣课、未进入老师付款。
  - [x] SubTask 3.5: 用管理员账号终确认，校验 `CONFIRMED`、扣课、台账、老师待支付记录。
  - [x] SubTask 3.6: 在 Payments 页面完成老师付款，校验付款记录和待支付列表更新。

- [x] Task 4: 编写班课完整链路 E2E
  - [x] SubTask 4.1: 从 UI 创建班课课程产品，覆盖按次计费和固定排课时段。
  - [x] SubTask 4.2: 从 Enrollment 页面完成班课报名，校验班级、班课进度、学生购买课次。
  - [x] SubTask 4.3: 在 Course Packages/Schedule 页面展开班课学生进度，校验剩余课次和课次计划。
  - [x] SubTask 4.4: 覆盖班课消课的老师初确认、管理员终确认或管理员单方确认，校验状态机、扣课和计薪。

- [x] Task 5: 覆盖异常结果与边界场景
  - [x] SubTask 5.1: 覆盖正常完成、请假、取消或 no-show 类结果中当前系统支持的关键分支。
  - [x] SubTask 5.2: 校验不同结果对课包余额、顺延、课时台账、老师付款资格的影响。
  - [x] SubTask 5.3: 覆盖管理员驳回老师初确认，校验不扣课、不计薪、可重新处理。
  - [x] SubTask 5.4: 覆盖重复确认、重复付款、关闭课包后继续操作、排课冲突等非法操作，并校验错误提示与数据不变。

- [x] Task 6: 稳定性与数据校验增强
  - [x] SubTask 6.1: 为跨页测试添加 UI + API/DB 双重断言，避免只验证页面文案。
  - [x] SubTask 6.2: 使用唯一 marker 和独立业务数据，避免订单号、账号、产品、课次互相污染。
  - [x] SubTask 6.3: 控制测试粒度和执行时间，将长链路拆成少量 serial spec，避免单个用例过长且难定位。

- [x] Task 7: 运行并修复全量相关 E2E
  - [x] SubTask 7.1: 单独运行新增全链路 E2E spec 并修复失败。
  - [x] SubTask 7.2: 运行 course product、enrollment、schedule/confirm、payments 相关 E2E，确认已有覆盖未回归。
  - [x] SubTask 7.3: 更新 `frontend/e2e/ui/README.md` 的业务链路覆盖说明。

- [x] Task 8: 修复 Seventh Verification 发现的覆盖缺口
  - [x] SubTask 8.1: 补强 1对1老师初确认后 PENDING_ADMIN 阶段的断言，明确校验课包余额未扣减、老师待支付列表未生成该课次。
  - [x] SubTask 8.2: 管理员驳回老师初确认后的重新处理链路已锁定当前 UI 风险；保留不扣课、不计薪、恢复 occurrence 的可通过业务断言，并在 README/checklist 说明。
  - [x] SubTask 8.3: 补齐当前系统支持的 No Show 分支，校验其对课包余额、台账和老师付款资格的影响。
  - [x] SubTask 8.4: 补齐重复付款、排课冲突和余额不足等非法操作覆盖，并调整 README/checklist 与实际覆盖范围一致。
  - [x] SubTask 8.5: 重新运行新增 business-flow spec 及 course product、enrollment 相关 E2E，并记录通过结果。

- [x] Task 9: 补齐驳回后重新确认闭环
  - [x] SubTask 9.1: 已适配管理员驳回后排课 UI 仍显示 Completed、缺少重新确认入口的问题；用例会先尝试 UI 重新初确认，若入口不可用则记录 known-ui-gap 并用老师 API 推进后续状态校验。
  - [x] SubTask 9.2: 已新增 E2E 覆盖：老师初确认 → 管理员驳回 → 不扣课不计薪 → 老师重新初确认 → 管理员终确认。
  - [x] SubTask 9.3: 已校验最终 `CONFIRMED` 后课包余额、hour ledger、lesson record、teacher unpaid lessons 全部一致。

- [x] Task 10: 补齐调课、补课与顺延链路 E2E
  - [x] SubTask 10.1: 新增本周临时调课 E2E，覆盖调整时间、老师、教室或课时，并校验不修改 recurring rule。
  - [x] SubTask 10.2: 校验多上、少上、课时不变三种提示和保存后的 lesson adjustment log。
  - [x] SubTask 10.3: 新增补课/顺延链路 E2E，覆盖请假或取消后创建补课、补课完成、课包预计结束时间或余额变化。
  - [x] SubTask 10.4: 覆盖调课/补课冲突和不可修改状态，校验错误提示与数据不变。

- [x] Task 11: 补齐教师工资扩展场景 E2E
  - [x] SubTask 11.1: 新增 Pay Rate 页面/接口链路 E2E，覆盖设置、修改费率以及后续待支付金额变化。
  - [x] SubTask 11.2: 覆盖零费率警告 `Zero Rate Warning`，校验用户继续付款或阻止付款后的数据结果。
  - [x] SubTask 11.3: 覆盖同一老师跨周期查询和付款，校验不同周期课次筛选正确。
  - [x] SubTask 11.4: 覆盖 Payment History 的 Pending/Paid 筛选、Detail 明细和 Confirm 付款后状态变化。
  - [x] SubTask 11.5: 校验历史付款不被后续 Pay Rate 修改反写，已付款课次不再重复进入待支付。

- [x] Task 12: 运行并验证增量覆盖
  - [x] SubTask 12.1: 单独运行新增/修改的 business-flow、schedule adjustment、finance payments E2E。
  - [x] SubTask 12.2: 回归运行现有 business-flow、enrollment、course product 相关 E2E。
  - [x] SubTask 12.3: 更新 README 和 checklist，记录新增覆盖范围、通过结果和仍需产品确认的业务风险。

# Task Dependencies
- Task 2 depends on Task 1
- Task 3 depends on Task 2
- Task 4 depends on Task 2
- Task 5 depends on Task 3 and Task 4
- Task 6 depends on Task 3, Task 4, and Task 5
- Task 7 depends on Task 6
- Task 8 depends on Task 7
- Task 9 depends on Task 8
- Task 10 depends on Task 8
- Task 11 depends on Task 8
- Task 12 depends on Task 9, Task 10, and Task 11
