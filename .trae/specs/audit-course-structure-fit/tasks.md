# Tasks

- [x] Task 1: 固化 PPT 课程矩阵与现有模型差异
  - [x] SubTask 1.1: 提取并确认课后班、周六班、暑假班、冲刺班、1 对 1 的年级/档位、时长/周期、时段和价格维度。
  - [x] SubTask 1.2: 对比 `course_product`、`course_price_option`、`class_group`、`enrollment_order_item` 当前字段，确认哪些字段可复用、哪些需要调整。
  - [x] SubTask 1.3: 明确 `Course Product` 是父级课程，`Course Price Option` 是子级价格矩阵行，`Class Group` 是班课执行实例。

- [x] Task 2: 调整课程产品与价格矩阵数据模型
  - [x] SubTask 2.1: 如有必要，为 `course_product` 增加或规范 `courseType/registerClass` 等父级分类字段，保留 `listPrice` 为参考价。
  - [x] SubTask 2.2: 强化 `course_price_option` 与父级产品的关联约束，确保 ACTIVE 价格行必须关联 ACTIVE 且教学模式匹配的产品。
  - [x] SubTask 2.3: 为 1 对 1 price row 校验 `oneOnOneHours`，为班课 price row 校验 grade/duration/period/time/price 的完整性。
  - [x] SubTask 2.4: 确保已被订单、课包或班课引用的产品和价格行只能停用或新增版本，不破坏历史快照。

- [x] Task 3: 改造 Course Settings 为低学习成本矩阵界面
  - [x] SubTask 3.1: 将 Course Product 与 Course Pricing 组织为一个清晰入口：左侧课程类型，中间课程产品，右侧年级/时长/时段/价格矩阵。
  - [x] SubTask 3.2: 用产品下拉替代 `Course product ID` 数字输入，并按 1 对 1 / 班课过滤产品。
  - [x] SubTask 3.3: 在矩阵行展示启用状态、引用状态、是否缺班级、是否缺价格和下游影响提示。
    - [x] 已返回真实引用计数和引用来源，并在矩阵行区分未使用、订单明细引用、课包/班级引用和缺配置状态。
  - [x] SubTask 3.4: 为 PPT 中的常用结构提供快速新增模板，不强制一次填完整价格表。

- [x] Task 4: 适配报名页课程选择与收费
  - [x] SubTask 4.1: 报名行改为按 course type -> product -> grade/duration/time price row 逐级选择。
  - [x] SubTask 4.2: 选择 price row 后自动带出 Detail、configured price、1 对 1 hours、linked product and available class groups。
  - [x] SubTask 4.3: 手动改价保留 configured price、final price、manual price 标记和原因。
  - [x] SubTask 4.4: 价格配置缺失时阻止静默 0 元提交，并提供打开 Course Settings 的下一步。

- [x] Task 5: 适配 1 对 1 课包、排课和消课
  - [x] SubTask 5.1: 1 对 1 报名按 price row 的 40/60/120 hours 创建正确 units 和课包快照。
  - [x] SubTask 5.2: 明确首次课次和后续 recurring schedule 的来源，避免依赖不可解析的自由文本时段。
    - [x] 1 对 1 报名使用结构化 `initialLessonStartAt` / `initialLessonEndAt` 生成首节课，不再解析 `timeSlotLabel` 或回退到默认 10:00。
  - [x] SubTask 5.3: 消课、剩余课时、续费提醒和老师工资统计继续以课包与台账为事实源。

- [x] Task 6: 适配班课收费、班级资格和消课
  - [x] SubTask 6.1: 班课报名必须把 price row、order item、class group、purchased period/quantity 串起来。
  - [x] SubTask 6.2: 按课次班课只允许学生在购买数量范围内被核销。
  - [x] SubTask 6.3: 按月/周期班课只允许学生在报名有效期内被核销，并对临近到期高亮提醒。
  - [x] SubTask 6.4: 报名阶段允许未补教室时，Classrooms 页面必须能列出待补教室项并执行冲突校验。
    - [x] 已提供 pending classroom 回填能力，支持班课和单课次回填，保存前校验 ACTIVE 教室、批内冲突和已有教室占用冲突。

- [x] Task 7: 补充验证与迁移方案
  - [x] SubTask 7.1: 增加后端测试覆盖课程矩阵保存、模式匹配校验、报名价格快照、1 对 1 units、班课资格边界。
    - [x] 已覆盖课程矩阵保存、模式匹配、报名价格快照、1 对 1 units、过期班课 enrollment 过滤和 PER_SESSION 购买数量边界。
  - [x] SubTask 7.2: 增加前端或 E2E 验收覆盖 Course Settings 矩阵维护、报名自动带价、手动改价、缺价格错误。
    - [x] 已完成前端 `typecheck` 和 `build` 级别验收；本轮未新增或执行专项 E2E，不将其标记为 E2E 覆盖。
  - [x] SubTask 7.3: 提供现有 `Course Price Option` 数据迁移或兼容策略，避免破坏已存在订单和收据。
  - [x] SubTask 7.4: 明确仍需产品确认的冲刺班矩阵、班课部分购买分配、跨月补课、停课和续费规则。

## Task Dependencies

- Task 2 depends on Task 1.
- Task 3 depends on Task 2.
- Task 4 depends on Tasks 2 and 3.
- Task 5 depends on Task 4.
- Task 6 depends on Task 4.
- Task 7 depends on Tasks 2 through 6.
