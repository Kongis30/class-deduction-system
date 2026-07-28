# Checklist

- [x] 已从 PPT 和需求文档中固化课后班、周六班、暑假班、冲刺班、1 对 1 的课程矩阵维度。

- [x] `Course Product` 被定义为父级课程，`Course Price Option` 被定义为年级/时长/时段/价格矩阵行。

- [x] ACTIVE price row 必须关联 ACTIVE 且教学模式匹配的 course product。

- [x] Course Settings 不再要求用户手填 `Course product ID`，而是使用可搜索、可过滤的产品选择。

- [x] Course Settings 提供“课程类型 -> 产品 -> 年级/时长/时段/价格行”的清晰维护体验。

- [x] 1 对 1 price row 能表达 G1-G5、G6-G8、G9-G12、AP 与 40/60/120 小时的不同价格。

- [x] 班课 price row 能表达课后班、周六班、暑假班的年级/档位、周期、半天/全天或具体时段和价格。

- [x] 报名页按 course type -> product -> price row 逐级选择，并自动带出 Detail 和 Price。

- [x] 报名页在价格配置缺失时阻止静默 0 元提交，并提供打开 Course Settings 的下一步。

- [x] 手动改价保留 configured price、final price、manual price 标记和原因。

- [x] 1 对 1 报名根据 40/60/120 hours 创建正确 course package units 和历史快照。

- [x] 1 对 1 的首次课次和后续排课不依赖不可解析的自由文本时段。

- [x] 1 对 1 消课、剩余课时、续费提醒和老师工资统计继续以课包与台账为事实源。

- [x] 班课报名把 price row、order item、class group、purchased period/quantity 串联起来。

- [x] 按课次班课的购买数量能限制学生可核销课次范围。

- [x] 按月/周期班课的报名有效期能限制学生可核销课次范围，并支持临近到期提醒。

- [x] 报名阶段未补教室时，Classrooms 页面能列出待补教室项并进行冲突校验。

- [x] 已引用的产品和价格行不会被结构性修改破坏历史订单、收据、课包或班课报名。

- [x] 已补充课程矩阵、报名带价、1 对 1 units、班课资格边界的后端测试。

- [x] 已补充 Course Settings、报名自动带价、手动改价和缺价格错误的前端 typecheck/build 级别验收；本轮未新增或执行专项 E2E。

- [x] 已定义现有数据迁移或兼容策略。

- [x] 冲刺班矩阵、班课部分购买分配、跨月补课、停课和续费规则仍需产品确认，不被隐式实现。
