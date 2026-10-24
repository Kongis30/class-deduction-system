# 同一课程续课：当前代码与页面核对

> 2026-10-23，静态代码审计。目标规则见 [同一课程续课：业务规则与页面一致性方案](./logical-course-renewal-business-rules.md)。本报告描述当前工作区代码，不代表线上数据已经迁移或所有场景已通过端到端测试。

## 结论

当前实现**部分符合**：新购买和旧购买分开计价，私教续课复用原课包；班课续购创建独立权益段并关联 `class_enrollment.logical_course_id`，点名时按上课日期选段。**尚不符合**“所有业务页面只有一条课程主记录、累计余额和有效期一致”的目标。班课 Schedule 仍逐段返回记录；Data Summary 在浏览器合并，服务端及其他入口没有同一汇总口径。更正、退款和续购校验也有具体缺口。

## 页面与上下游现状

| 环节 | 当前代码行为 | 判定 |
| --- | --- | --- |
| Edit Receipt → Add course | 从原订单行带出来源权益；原行完整展示且不重复收费，新行选 Product、project、plan、实际班级与数量。相同 Product/project 时携带 `renewClassEnrollmentId`；不同则当作新课程。见 `RegistrationIntakeView.vue` 的 `startCourseRenewal`、`loadGroupClassRenewal`、`isSameRenewalGroupCourse` 和提交逻辑。 | **基本符合交互**。续购确实创建新订单；原行直接加月份走 `saveCorrection` 更正原单，界面文案仍易把真实续费与原单更正混淆。 |
| 续课保存与账务 | `RegistrationIntakeServiceImpl.submitIntake` 为新购买创建订单行与付款。私教 `createOneOnOneArtifact` 复用原 `course_package.id`，累加 total/remaining，不改 consumed 或老师分配；班课 `createV2GroupClassArtifact` 插入新 `class_enrollment`，旧权益不改。 | **主要账务结构符合**。历史方案价格保留，新方案只对本次购买计价。 |
| 私教 Schedule／Edit Schedule | `RecurringScheduleView.vue` 从同一 package 读取 remaining；新增小时成为课包可分配余额，老师 allocated 仍由 Edit Schedule 管理。 | **符合主要规则**。续费不自动分配给老师，也不自动生成课次。 |
| 1-on-1 消课 | `OneOnOneDeductionView.vue` 调用 `fetchOneOnOneDeductions`；后端按 `course_package` 组装页面。同一私教续课沿用 package ID，因此 Total/Remaining 与课次入口不拆开。 | **符合主要规则**。 |
| 班课 Schedule／学生名单 | `ClassGroupServiceImpl.listEnrolledStudents` 按每条 ACTIVE `class_enrollment` 循环组装；`listEnrolledByStudent` 也逐段返回。同班连续续购可出现同一学生多行，课次 remaining 仅是单段值，按月只有单段 expiry。 | **不符合**。缺少学生＋逻辑课程级汇总及购买段详情。 |
| Data Summary | 后端 `DataSummaryServiceImpl` 仍按订单行返回；前端 `mergeLogicalCourseRows` 依据 `logicalCourseId` 合并为一行，Period 取各行最早／最晚日期，金额按可见月份订单行求和。 | **仅当前页面展示符合一部分**。服务端、其他消费者和课程级余额没有统一口径；取消／退款后的 Period 仍可能把购买快照算入最晚日期。 |
| 班课点名／消课 | `ClassGroupServiceImpl.resolveV2EntitlementsForOccurrence` 按实际班级、上课日期、Attendance、剩余课次选唯一权益；多段同时覆盖同一学生课次时抛错，记录继续关联具体权益 ID。 | **权益归属方向符合**。不能为合并展示而抹掉段 ID；仍需验证跨班、Undo 和重复提交场景。 |
| 订单、收据、退款 | 每次真实续购保留独立订单和费率。私教共享课包多笔购买时，`requireStandaloneOneOnOnePurchase` 阻止自动退款／取消／删除。班课取消按当前订单行撤回对应权益。 | **保留历史符合；冲正能力未完成**。私教目前只能人工处理关联购买，不能按某次未用权益自动退款。 |
| 续费提醒 | `RenewalReminderServiceImpl.listGroupClassReminders` 逐条 ACTIVE `class_enrollment` 生成提醒，不按逻辑课程取最后有效段。 | **不符合**。旧段仍 ACTIVE 时可对已续课程重复提醒。 |

## 必须修复的逻辑缺口

### P0：班课原订单行缺失逻辑课程 ID，续后更正保护可能失效

班课续费只更新来源 `class_enrollment.logicalCourseId`，并把该 ID 写入新权益；`RegistrationIntakeServiceImpl.submitIntake` 的班课分支没有调用 `linkLogicalCourse` 更新新旧 `enrollment_order_item.logicalCourseId`。收据 API 的 `EnrollmentOrderServiceImpl.toOrderItemVO` 会从权益补出逻辑课程 ID，所以页面看似正常；但 `applyCorrectedItems` 的 `RENEWED_COURSE_TERMS_LOCKED` 实际只检查数据库订单行的字段。其结果是：续费后的原班课收据可能仍允许修改购买数量、方案或班级，随后 `migrateCorrectedClassEnrollment` 改写旧权益，而后续购买仍基于原来的续接关系。应在保存事务内同步来源与新订单行的逻辑课程 ID，并在更正时依据权益关系校验，不能只信任订单行快照。

### P0：服务端续购单位与行数校验不足

`RegistrationIntakeServiceImpl.createV2GroupClassArtifact` 只在**来源单位为 MONTH** 时要求新行也是 MONTH/ROLLING_MONTHS；来源为 SESSION 时缺少明确的 SESSION→SESSION 校验。前端 `groupPlansForLine` 过滤方案，但直接调用 API 可绕过。`validateRenewalLines` 只检查私教／班课类型，未限定同一来源续购只能附着到一个合适的课程行；提交循环会把同一个 `renewClassEnrollmentId` 传给所有班课行。应以服务端为准校验单位、来源、最多一个续接目标和价格范围；同单其他行如被允许，必须明确作为独立新课程。

### P1：班课课程级汇总未落在服务端

`ClassGroupServiceImpl.listEnrolledStudents` 和 `listEnrolledByStudent` 要区分**运营主记录**与**权益段**：同学生＋`logicalCourseId`（无 ID 时以原权益 ID）汇总累计购买、有效净已用与余额，并给出有效期及段列表；班级名单只能列出该班在对应时段有效的学生一次。按月显示日期／剩余天数，课次显示累计购买、已用、剩余，不能把月份写成小时。Data Summary 应消费相同课程身份与有效段口径；购买金额仍按订单和月份独立核算。服务端汇总后，前端可只负责格式化与交互，清理重复拼接逻辑。

### P1：Period 与提醒应只看有效续购段

`DataSummaryView.vue.mergeLogicalCourseRows` 对所有分组购买行直接取最早开始／最晚结束；`DataSummaryServiceImpl.resolveV2PeriodStartDate/EndDate` 在权益不再有效时会回退到方案或订单日期。取消且未退款的订单默认不进 Data Summary，但退款／停止记录仍可出现，因而最晚购买快照不一定是当前课程真实有效期。需要先按权益有效性和退款结果决定课程的有效段，再计算连续 Period；历史购买保留在财务明细中。`RenewalReminderServiceImpl` 应按逻辑课程取最新有效段，避免旧段单独提醒。

### P1：共享私教课包的续购退款规则尚未实现

已有防护会拒绝自动处理多笔购买共享的私教课包，这是合理的保底；但目标要求只冲回被退款购买中**尚未消耗、未占用**的权益，并保持老师 allocated 与历史消课不动。应先建立可核对的购买来源、消耗和占用判定，再开放按购买冲正；判定不清时继续明确拒绝。

### P2：入口与并发一致性

原收据的“增加 Months”实际是原单更正并按原费率重算，真实到期续课应该使用 Add course 的新购买路径；按钮说明需明确区分。班课续课检查“最新段”采用查询，但当前可见代码没有锁定同一逻辑课程续接源；两个并发请求都可能通过最新段检查。应在事务中锁定来源或用唯一的续接约束，并验证重复提交行为。

## 建议实施顺序与验收

1. 先补服务端写入与校验：逻辑 ID 同步到订单行、续购单位与单一目标校验、续购并发约束。已有数据只做经过审计的回填，保留原老师 allocated、历史扣课与订单价格。
2. 建立课程级**读取汇总**，让班课 Schedule、学生名单、Data Summary 和提醒复用；订单、收据、权益段仍分开存储。删除被替代的前端合并或逐段主列表逻辑。
3. 补有效段取消／退款后的 Period 与剩余量规则；私教多笔购买的自动退款在可精确归属前维持拒绝。
4. 按目标规范验收：四个月＋不同费率两个月、私教加小时、按课次续购、调班、原单更正与真实续购、取消／退款、并发重复提交；从页面刷新、打印及不同角色入口核对同一课程身份和余额。

本次只核对代码并形成审计文档，没有修改业务代码或线上数据，也没有运行新的测试。上一轮隔离验证已确认班课同逻辑课程产生两条 `class_enrollment`，Schedule 返回两行而 Data Summary 前端显示一行；本报告其余结论以所列代码路径为依据。
