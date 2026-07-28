# 系统使用逻辑与数据准确性职责 Spec

## Why

当前系统已经具备报名、课程记录、排课、消课、付款、收据和报表能力，但这些能力分散在多个模块中。系统本质上是一个运营数据统计系统：每次报名、排课、消课、补款、退款和调整都会影响后续统计、余额、课时和提醒。因此需要明确端到端使用逻辑、数据主从关系、冲突校验边界和用户交互解决方案，避免用户不知道下一步做什么，也避免数据被重复生成、错误覆盖或冲突污染。

## What Changes

- 建立从 Registration 到 Reports 的端到端数据职责模型。
- 明确报名如何生成学生、订单、课程明细、课包/班课报名、付款、收据、初始课次。
- 明确课程记录的主数据、快照数据和派生数据边界。
- 明确排课表生成来源：报名初始课次、固定排课规则、本周临时调整、教室后补。
- 明确每个环节的数据准确性风险：重复学生、重复报名、超额付款、课程配置缺失、排课冲突、课时余额不一致、历史快照被覆盖。
- 明确冲突校验：学生、老师、教室、课包状态、课次状态、当前周边界、已核销边界。
- 明确用户交互：遇到冲突、缺配置、欠费、低课时、已报名、已确认、已取消时，页面应给出可执行的下一步。
- 不立即新增数据库字段，不改变当前已实现业务语义；先形成系统职责和后续实现/验收标准。
- **BREAKING**：无。

## Impact

- Affected specs:
  - Registration intake
  - Student registration history
  - Course package and class enrollment
  - Recurring schedule and weekly adjustment
  - Lesson deduction double confirmation
  - Data summary and alerts
  - Full business flow E2E coverage
- Affected code:
  - `backend/src/main/java/com/classdeduction/system/modules/enrollment/service/impl/RegistrationIntakeServiceImpl.java`
  - `backend/src/main/java/com/classdeduction/system/modules/schedule/service/impl/RecurringScheduleRuleServiceImpl.java`
  - `backend/src/main/java/com/classdeduction/system/modules/schedule/service/impl/ScheduleAdjustmentServiceImpl.java`
  - `backend/src/main/java/com/classdeduction/system/modules/lesson/service/impl/LessonRecordServiceImpl.java`
  - `backend/src/main/java/com/classdeduction/system/modules/enrollment/service/impl/DataSummaryServiceImpl.java`
  - `frontend/src/views/registration/RegistrationIntakeView.vue`
  - `frontend/src/views/one-on-one/OneOnOneDeductionView.vue`
  - `frontend/src/views/base-data/StudentsView.vue`
  - `frontend/src/views/schedule/*`
  - `frontend/src/views/data-summary/DataSummaryView.vue`

## Current System Analysis

本节记录 change-id `define-operational-data-integrity-model` 的当前实现分析，目标是把现有系统的数据职责、已覆盖边界和风险缺口放到同一张图里。该章节只描述现状和后续优先级，不要求本次立即修改业务代码。

### 1. Registration 到 Reports 的端到端数据流

当前主链路可以抽象为：

`student -> enrollment_order -> enrollment_order_item -> course_package/class_enrollment -> payment_record -> receipt_record -> user_account -> lesson_occurrence -> lesson_record/hour ledger -> teacher payment -> Reports`

| 环节 | 当前职责 | 下游影响 |
| --- | --- | --- |
| `student` | 保存学生当前档案。报名时按学生姓名 + 主家长电话复用，复用后会更新当前学生档案。 | Students 页面展示当前档案；报名订单保存快照，避免历史报名完全依赖当前档案。 |
| `enrollment_order` | 保存一次报名事件、金额汇总、付款状态、学生/校区/接送等报名快照。 | Students 汇总、订单详情、收据、Reports 的订单级金额和历史报名字段。 |
| `enrollment_order_item` | 保存每一条课程明细快照，包括 Register Class、Detail、Price、课程产品、老师、班级、1-on-1 课时等。 | 生成课包或班课报名；Reports 的课程行维度来自这里。 |
| `course_package` / `class_enrollment` | 1-on-1 生成课包和老师分配；班课生成班级报名。 | 课包余额、后续排课、消课、低课时提醒；班课学生进度和班级课程统计。 |
| `payment_record` | 保存报名收款和后续补款流水。 | 订单已收/余额状态，收据展示，后续应支持与订单金额一致性校验。 |
| `receipt_record` | 保存收据 payload 快照。 | 打印/回看收据时使用历史 payload，不应被后续学生或课程配置变化覆盖。 |
| `user_account` | 1-on-1 报名时确保学生账号和老师账号存在，并绑定角色。 | 学生/老师后续登录、老师自确认入口。 |
| `lesson_occurrence` | 保存计划课次。来源包括报名初始课次、固定排课规则续期、本周临时调整、班课/教室占用。 | 老师课程页、教室占用、消课确认入口、未来余额预测。 |
| `lesson_record` / `hour_ledger` | 老师初确认生成 Pending Admin；管理员终确认后写消课记录、扣课包余额、写课时台账和余额快照。 | Reports 课时字段、低课时提醒、教师待结算列表。 |
| teacher payment | 从已终确认且可计薪的 `lesson_record` 生成教师结算批次，确认后排除已结算课次。 | Finance/Teacher Payments；Reports 目前未形成财务一致性视图。 |
| Reports | 当前由 `DataSummaryServiceImpl` 从订单、明细、课包、班课报名、课程产品、老师分配等实时派生宽表。 | 展示报名、付款、余额、课时、到期、低课时和欠费提醒，但缺少跨源一致性校验。 |

### 2. 报名成功后生成对象的职责和失败回滚语义

报名提交由 `RegistrationIntakeServiceImpl.submitIntake` 作为事务边界，当前语义是“报名事件一次性落库”。成功后会创建或复用学生，生成订单和多条订单明细，再按明细生成 1-on-1 课包或班课报名；如存在付款会写 `payment_record`，默认会写 `receipt_record`；若包含 1-on-1，会确保学生账号和对应老师账号存在，并生成初始 `lesson_occurrence`。

失败回滚语义：该服务使用事务包住学生复用/更新、订单、明细、课包/班课报名、付款、收据、账号和初始课次。任一步抛出业务异常或运行时异常时，整次报名应回滚，不应留下半个订单、半个课包或孤立付款。已覆盖的失败类型包括缺 Register Class、缺 Detail、1-on-1 缺老师、无 active 课程产品、班课无 classId、重复 active 班课报名、付款超过应付、折扣超过原价、收据或账号角色缺失等。当前风险是价格字段如果前端/请求没有传入且课程价格配置为空，后端 `defaultAmount` 会把空金额归零，可能让不完整价格静默进入订单。

### 3. 主数据 / 快照 / 派生数据矩阵

| 数据 | 类型 | 当前写入/更新规则 | 消费方 | 当前风险 |
| --- | --- | --- | --- | --- |
| 学生当前档案 | 主数据 | `student` 保存当前姓名、年级、学校、电话、接送、校区。报名复用学生时会更新当前档案。 | Students、账号、后续排课和查询。 | 历史报名不能直接依赖当前档案，否则会被后续更新覆盖。 |
| 课程价格配置 | 主数据 | Course Product / Course Price Option 保存 active 产品、价格、课时、班课计费和时段配置。 | Registration 解析报名明细，班课生成课次。 | 后端对空价格可能默认为 0；需要明确“价格缺失”必须阻断还是允许人工 0 元。 |
| 订单快照 | 快照数据 | `enrollment_order` 保存学生、校区、接送、金额、支付状态、报名周期等历史快照。 | 订单详情、收据、Reports、Students 汇总。 | Students 汇总未排除 CANCELLED 订单，Reports 已排除，两个入口口径不一致。 |
| 订单明细快照 | 快照数据 | `enrollment_order_item` 保存 Register Class、Detail、Price、manual override、课程产品、老师/班级等。 | 课包/班课报名、收据 payload、Reports 行维度。 | 如果课程配置后续变更，Reports 应优先使用订单明细快照解释历史。 |
| 课包余额 | 主状态 + 派生快照 | `course_package` 保存 total/consumed/remaining；管理员终确认时写 `hour_ledger` 并 upsert `package_balance_snapshot`。 | 1-on-1 Lessons、低课时提醒、Reports、课包详情。 | Reports 缺少 package balance vs ledger 一致性校验；需要暴露不一致入口。 |
| 付款流水 | 事实流水 | 报名付款和后续补款写 `payment_record`；后续补款会更新订单 received/balance/status。 | 订单付款时间线、收据、Students 汇总、Reports 金额。 | 后续补款对多课包订单会把整笔补款加到订单下每个课包 received，可能重复增加 package received。 |
| 收据 payload | 快照数据 | `receipt_record.receipt_payload` 固化报名时收据展示数据、课程明细、付款和 artifacts。 | 收据打印/回看。 | 收据回看应只读 payload，不能重新实时拼导致历史漂移。 |
| Reports 派生 | 派生数据 | `DataSummaryServiceImpl` 实时从订单、明细、课包、班课报名、课程产品、老师分配派生。 | Data Summary / Reports 页面。 | 缺少 payment_record vs order amount、package balance vs ledger 一致性校验；缺业务单测。 |

### 4. 订单金额、付款流水、余额和退款 / credit 当前规则

当前金额链路的权威顺序是：订单明细价格先汇总到 `enrollment_order`，报名付款和后续补款再写入 `payment_record`，订单的 received、balance 和 payment status 作为运营展示状态同步更新。Reports 和 Students 可以展示订单级金额、付款状态和余额，但当前还没有独立的一致性检查入口去对齐 `payment_record` 合计、订单 received/balance、课包 received 和退款状态。

| 事项 | 当前规则 | 当前边界 / 缺口 |
| --- | --- | --- |
| 报名付款 | Registration 首次提交时，如果填写付款信息，系统在报名事务内写入 `payment_record`，并同步订单 received、balance、payment status；付款金额不得超过本次 Amount due。 | 已实现超额阻止，但仍需要把缺价格导致的 0 元订单与明确 0 元优惠区分开。 |
| 后续补款 | 学生详情 / 订单付款入口支持追加付款流水，并更新订单 received、balance、payment status；后续补款不得超过当前 Balance。 | 对多课包订单，补款如何分摊到具体课包 received 的语义仍不清晰，可能导致课包维度实收重复放大。 |
| 超额阻止 | 报名付款和后续补款都应阻止超过 Amount due 或当前 Balance 的金额，阻止后不改变订单金额、付款流水或余额。 | 当前逻辑以订单级余额为主，未来如果支持 credit / 预存款，必须拆成独立模型，不能绕过超额阻止。 |
| 订单余额 | 订单余额由订单总额减订单级 received 得出，并驱动欠费提醒和付款状态展示。 | Reports 缺少 payment_record 合计 vs order received/balance 的差异检查；Students 与 Reports 对取消/退款订单的统计口径需要统一。 |
| 整单退款 | 当前测试覆盖了订单取消/退款的基础链路，系统可把订单排除或标记为取消/退款口径。 | 整单退款在 Students、Reports、收据、付款流水里的权威口径需要明确为同一套规则，避免某个入口仍计入应收或已收。 |
| 部分退款 | 未定义。当前没有清晰规则说明部分退款如何影响订单余额、付款流水、课包 received、收据和 Reports。 | 需要产品确认是否支持、按订单还是按明细退款、是否允许已消课课包退款、退款后课时和工资如何处理。 |
| credit / 预存款 | 未定义。当前超额付款应被阻止，不能把超过应付的金额静默记为 credit。 | 需要产品确认是否有预存款账户、是否跨订单使用、是否可退款、是否进入收据和 Reports。 |

### 5. 排课表生成来源和边界

| 来源 | 当前生成方式 | 边界 | 风险 |
| --- | --- | --- | --- |
| 报名初始课次 | 1-on-1 报名成功后，按报名周期开始日期和 `timeSlotLabel` 解析首节课；不可解析时默认 10:00，并写 notes。 | 只生成初始 1-on-1 `lesson_occurrence`，不是长期固定规则。 | 初始课次当前只校验老师冲突，不校验同一学生时间冲突；无教室时不校验教室。 |
| recurring schedule rule | 创建固定排课规则后按 slot、起止日期、generation horizon 生成课次。 | 受课包余额、已确认/已保留课次、既有课次去重和冲突校验约束；更新规则时保留历史已确认/已调整/已取消课次。 | 冲突返回仍是普通错误文本，缺结构化冲突对象。 |
| renewal | `renewActiveRuleOccurrences` 对 active 规则续期补生成未来课次。 | 单条规则续期失败会记录并继续其他规则；跳过该规则下已存在的非取消同开始时间课次。 | 续期失败后的用户可见修复入口不明确。 |
| 本周临时调整 | `ScheduleAdjustmentServiceImpl.adjustWeekly` 支持当前自然周内 CREATE/UPDATE/DELETE，并写调整日志。 | 只作用当前周；不修改 recurring rule；已核销/已取消课次不可改；调整总课时差额会反向调整未来课次保持课包排课总量。 | 批次冲突有校验，但前端拿到的是错误码 + 文本，不是可定位到资源/时间的结构化详情。 |
| classroom backfill | 班课/教室占用可从 class group、course product 默认教室或 class confirm 的实际教室形成占用视图。 | 报名阶段允许无教室；后续 Classrooms/班级页面负责补录或展示教室占用。 | 教室 backfill 没有独立闭环：缺少“待补录教室列表 -> 校验冲突 -> 保存 -> 复查占用”的明确流程和验收。 |

### 6. 冲突校验矩阵

| 阶段 | 已覆盖 | 主要风险 |
| --- | --- | --- |
| 报名阶段 | 重复学生按姓名 + 主家长电话识别复用；班课阻止同一学生重复加入同一个 active class；1-on-1 要求老师和 active 产品；班课要求 classId、active class、group product；付款和折扣有上限校验。 | 后端空价格可能默认为 0；报名初始 1-on-1 课次只做老师冲突、不做学生冲突；报名错误主要是文本提示，缺少跳转目标和结构化字段。 |
| 排课阶段 | recurring rule 校验 slot 自身、老师/学生/教室时间重叠、课包余额上限、已确认课次保留、非取消课次去重。 | 冲突详情不是结构化对象，前端无法稳定展示冲突资源、冲突时间和冲突课次入口。 |
| 本周调整 | 限制当前自然周；校验课包归属、未核销、未取消、时间/课时匹配、CREATE 必填字段、批次内学生/老师/教室冲突、数据库已有课次冲突。 | 调整失败时缺少可视化冲突详情；未来课次不足或未来课次冲突时下一步不明确。 |
| 消课阶段 | 老师只能确认自己的课次；老师初确认只进入 Pending Admin，不扣课不计薪；管理员终确认写 lesson_record、hour_ledger、package_balance_snapshot 并更新课包；重复确认、余额不足、取消/合并课次、驳回场景有保护；教师付款只取 CONFIRMED 且可计薪状态。 | Reports 没有把消课台账与课包余额做显式一致性检查；异常状态下用户需要更明确的“重新确认/查看余额影响/排除工资”的入口。 |

### 7. 用户交互解决方案

| 异常场景 | 用户下一步 |
| --- | --- |
| 报名错误 | 缺价格或空价格时打开 Course Pricing 或明确选择 manual override；缺老师时回到课程行选择老师；班课不可用或重复报名时打开学生历史或班级详情；付款超过应付时修正付款 split；学生复用时提示当前档案会更新、历史订单仍保留快照。 |
| 排课冲突 | 展示冲突类型、冲突资源、冲突时间和已有课次入口；提供换时间、换老师、换教室、取消本次调整、打开对应日历视图。当前后端还缺结构化对象，前端只能基于错误文本提示。 |
| 消课异常 | 老师端显示是否超出确认窗口、是否非本人课次、是否已被确认；管理员端显示驳回原因、重新确认入口、余额影响、是否进入教师待结算；余额不足时引导查看课包和补课/续费。 |
| Reports 异常 | 报表行应提供打开报名历史、付款时间线、课包详情、课时台账和一致性检查的入口；当订单金额、付款流水、课包余额、台账不一致时，应显示异常来源而不是静默展示数字。 |

### 8. 需要产品确认后才能实现的事项

以下事项不是单纯技术缺口，需要先形成产品规则、运营口径和验收标准，再进入实现。否则系统会出现“看似可用、但不同入口统计口径不同”的问题。

| 事项 | 需要确认的问题 | 未确认前的处理原则 |
| --- | --- | --- |
| credit / 预存款 | 是否建立学生级预存款账户；是否允许跨订单抵扣；是否可退款；是否进入收据、Reports 和欠费提醒。 | 继续阻止超额付款，不把多付金额静默写成付款。 |
| Excel export | 导出范围是 Reports 宽表、学生付款时间线、教师工资还是完整审计包；字段、排序、权限和脱敏规则是什么。 | 先保证页面数据可解释，不先承诺导出字段。 |
| 线上课税号 | 线上课是否使用北校区/南校区税号，还是独立税号；Show EIN 对线上课如何展示。 | 收据仅使用已确认校区税号规则，线上课税号不做推断。 |
| 完整价格矩阵 | 不同 Register Class、Detail、Price、时段、校区、折扣、手动 override 的组合是否都需要配置；缺价格时是否允许人工 0 元。 | 缺价格默认阻断，只有明确 manual override 或确认 0 元规则后才能入库。 |
| 支票 check number | 支票号是必填还是可选；是否允许后补；是否要在收据、付款时间线和导出中展示。 | 现有付款方式先不强制额外字段，避免把 check number 当备注散落存储。 |
| 部分退款规则 | 部分退款按订单、按明细还是按课包执行；是否允许已消课后退款；退款如何影响课时余额、教师工资、收据和 Reports。 | 仅保留整单取消/退款口径，不在未定义规则下实现部分退款。 |

### 9. 当前实现缺口和后续任务优先级

| 优先级 | 缺口 | implementation status | user frequency | 影响 | 后续任务 |
| --- | --- | --- | --- | --- | --- |
| P0 | 后端空价格可能默认为 0。 | partial | high | 报名可生成 0 元订单明细，污染金额统计。 | 在报名解析阶段区分“明确 0 元”和“缺价格”，缺价格阻断并给出 Course Pricing / manual override 下一步。 |
| P0 | Students 汇总与 Reports 对 CANCELLED / REFUNDED 订单口径不一致。 | partial | high | 同一学生在 Students 和 Reports 看到的应收、已收、余额不同。 | 统一 CANCELLED / REFUNDED 订单统计口径，并补充口径说明。 |
| P0 | 后续补款对多课包订单可能重复增加 package received。 | partial | high | 一个多课包订单补款会被加到每个课包，课包实收合计被放大。 | 补款时按订单明细/课包分摊或只更新订单级 received，明确课包 received 的权威语义。 |
| P0 | Reports 缺少 payment_record vs order amount、package balance vs ledger 一致性校验。 | missing | high | 报表无法发现付款流水与订单、课包余额与台账之间的不一致。 | 增加一致性检查接口/视图，至少暴露差异订单、差异课包和修复建议。 |
| P0 | 部分退款规则未定义。 | needs product decision | medium | 未定义情况下实现会影响订单余额、课包余额、教师工资和 Reports 口径。 | 产品确认部分退款范围、退款粒度、已消课处理、工资影响和收据展示后再实现。 |
| P1 | 报名初始 1-on-1 课次只做老师冲突、不做学生冲突。 | partial | medium | 同一学生可能在初始课次阶段被安排到重叠时间。 | 初始课次生成时纳入学生冲突校验，并返回可选择的下一步。 |
| P1 | 冲突详情不是结构化对象。 | partial | high | 前端只能展示泛化错误，不能定位冲突资源和已有课次。 | 定义 `ScheduleConflict` 结构，包含 resourceType、resourceId、resourceName、start/end、occurrenceId。 |
| P1 | 教室 backfill 没有独立闭环。 | missing | medium | 报名/班课缺教室后，用户缺少系统化补录和复查入口。 | 建立 Classrooms 待补录列表、保存前冲突校验、保存后占用复查。 |
| P1 | Reports 缺业务单测。 | missing | high | 报表宽表字段、汇总金额和告警口径容易回归。 | 新增 `DataSummaryServiceImplTest`，覆盖取消订单、快照优先、金额汇总、低课时、到期、欠费和一致性异常。 |
| P1 | 完整价格矩阵未定义。 | needs product decision | high | 缺价格、0 元、折扣和手动 override 的边界不清，会影响报名阻断和金额统计。 | 产品确认各课程/校区/时段/优惠组合的权威价格矩阵及 0 元规则。 |
| P2 | renewal 失败后的用户可见修复入口不明确。 | partial | low | 固定规则续期被冲突阻断后，用户不一定知道需要处理哪个规则。 | 在排课规则列表暴露续期失败原因、最近失败时间和重试入口。 |
| P2 | credit / 预存款规则未定义。 | needs product decision | medium | 付款超过应付目前被阻断，未来若支持 credit 需单独建模。 | 产品确认 credit 是否进入本期范围，避免静默当作付款。 |
| P2 | Excel export 范围和字段未定义。 | needs product decision | medium | 直接实现导出容易固化错误字段或暴露不该导出的信息。 | 产品确认导出对象、字段、权限、脱敏、文件格式和排序规则。 |
| P2 | 线上课税号规则未定义。 | needs product decision | low | 收据税号如果按校区推断，线上课可能展示错误 EIN。 | 产品确认线上课 EIN 来源，以及 Show EIN 在收据打印中的展示规则。 |
| P2 | 支票 check number 规则未定义。 | needs product decision | low | 若作为备注临时保存，后续收据、查询和导出难以稳定使用。 | 产品确认 check number 是否必填、是否可后补、展示位置和查询需求。 |

### 10. 现有测试 / E2E 覆盖边界和缺口

现有后端单测覆盖了报名生成订单/课包/付款/收据、付款超额拦截、取消/退款、课包余额、固定排课规则、本周调整、消课二阶段确认、课时台账、教师付款和基础资料聚合。现有 E2E 覆盖了 1-on-1 从报名、初始课次、老师初确认、管理员终确认、余额/台账、教师付款的闭环，也覆盖班课流程、排课调整、驳回重确认、重复确认、余额不足、重复教师付款、订单取消/退款和若干 UI 设置流程。

当前覆盖缺口是：Reports 缺业务单测；缺 payment_record vs order amount、package balance vs ledger 的一致性 E2E；缺结构化冲突详情 UI 验证；缺教室 backfill 独立闭环 E2E；缺报名初始 1-on-1 学生冲突用例；缺 Students 与 Reports 对 CANCELLED 订单统一口径的回归测试。现有 E2E 更多覆盖核心业务成功链路和部分边界，尚未把 Reports 作为运营数据可信度的最终验收面。

### 9. 最小回归命令与人工验收路径

最小自动回归应包含：

- 后端核心链路测试：`cd backend && mvn -Dtest=StudentServiceImplTest,EnrollmentOrderServiceImplTest,PaymentRecordServiceImplTest,RegistrationIntakeServiceImplTest,ReceiptServiceImplTest,LessonRecordServiceImplTest,LessonOccurrenceServiceImplTest test`
- 后端编译兜底：`cd backend && mvn -DskipTests compile`
- 前端类型和构建：`cd frontend && npm run typecheck && npm run build`

人工验收路径应覆盖：

- Registration：提交一张 1-on-1 报名，确认生成学生、订单、课程明细、课包、付款、收据、账号和初始课次；再提交一张班课报名，确认生成班课报名且重复报名被拒绝。
- Student history：从报名上下文打开学生历史，检查报名表快照、付款时间线和补记付款余额更新。
- Scheduling：创建固定排课规则，检查生成课次；尝试制造学生、老师、教室冲突，确认被阻止；使用 Adjust This Week 验证本周新增、修改、取消和失败回滚。
- 1-on-1 Lessons：老师初确认后检查 Pending Admin、余额未扣、工资未生成；管理员终确认后检查课包余额、hour ledger、teacher payment eligibility；驳回后检查不扣课不计薪。
- Reports：检查报名金额、付款、余额、课时、欠费高亮、到期高亮和低课时高亮是否可追溯到订单、明细、课包、班课报名或消课记录。

## ADDED Requirements

### Requirement: 系统必须定义端到端操作链路

The system SHALL document and enforce a clear end-to-end flow from registration to data summary.

#### Scenario: 用户完成报名

- **WHEN** 员工在 Registration 提交报名表
- **THEN** 系统在一个事务中创建或复用学生
- **AND** 创建报名订单和多行课程明细
- **AND** 按课程明细生成 1-on-1 课包或班课报名记录
- **AND** 创建付款流水和收据快照
- **AND** 对 1-on-1 报名生成学生账号、老师账号确认、初始课次或后续排课入口
- **AND** Reports 可从这些源数据推导出报名、费用、付款、余额、课时和提醒

#### Scenario: 任一关键步骤失败

- **WHEN** 学生复用、课程明细、课包生成、班课报名、付款、收据或 1-on-1 账号任一步失败
- **THEN** 系统回滚整次报名事务
- **AND** 前端展示明确错误和下一步，例如补课程价格、选择老师、修正付款金额、选择班级

### Requirement: 系统必须区分主数据、快照数据和派生数据

The system SHALL keep source-of-truth data, historical snapshots, and derived summary data separate.

#### Scenario: 学生资料后续变化

- **WHEN** 学生当前年级、学校、电话或接送信息被修改
- **THEN** 学生当前档案更新
- **AND** 历史报名表仍使用 enrollment order snapshot
- **AND** Reports 历史字段应优先使用订单快照，避免历史报名被当前资料覆盖

#### Scenario: 课程价格后续变化

- **WHEN** 管理员修改课程价格配置
- **THEN** 新报名使用新价格配置
- **AND** 历史报名明细继续使用订单明细中的价格、Detail 和 manual override 标记

### Requirement: 报名必须保护数据准确性

The system SHALL prevent inaccurate registration data from entering downstream reports.

#### Scenario: 重复学生识别

- **WHEN** 用户输入学生姓名和主家长电话
- **THEN** 系统按姓名加家长电话查找可复用学生
- **AND** 前端提示 existing student 或 new student
- **AND** 用户仍可保存本次报名快照，不覆盖历史报名

#### Scenario: 课程配置缺失

- **WHEN** 课程明细缺少 Register Class、Detail、Price、active course product 或 classId
- **THEN** 系统阻止提交
- **AND** 前端提示用户去 Course Pricing 或选择有效班级

#### Scenario: 付款金额异常

- **WHEN** 报名付款或后续补款超过 Amount due 或当前 Balance
- **THEN** 系统阻止提交并保持订单金额不变
- **AND** 如未来支持预存 credit，必须单独定义 credit 规则，不得静默当作付款

### Requirement: 课程记录必须可追溯

The system SHALL make every course-related record traceable back to its registration source.

#### Scenario: 1-on-1 课程明细

- **WHEN** 报名明细为 1-on-1
- **THEN** 系统生成 Course Package
- **AND** Course Package 关联 enrollment order 和 order item
- **AND** 课包记录总课时、已用课时、剩余课时、老师分配和金额快照

#### Scenario: 班课课程明细

- **WHEN** 报名明细为班课
- **THEN** 系统生成 Class Enrollment
- **AND** Class Enrollment 关联 enrollment order item
- **AND** 系统阻止同一学生重复加入同一个 active class

### Requirement: 排课表生成必须有明确来源

The system SHALL define where lesson occurrences come from and how they are updated.

#### Scenario: 1-on-1 报名后初始课次

- **WHEN** 1-on-1 报名包含老师和可解析时段
- **THEN** 系统生成初始 Lesson Occurrence
- **AND** 如果时段不可解析，系统使用默认时间并写入说明
- **AND** 前端应提示用户可后续通过 1-on-1 Lessons 或 schedule adjustment 修正时间

#### Scenario: 固定排课规则生成课次

- **WHEN** 管理员创建 recurring schedule rule
- **THEN** 系统按 slot、start/end date、generation horizon 生成 lesson occurrences
- **AND** 生成数量受课包余额、已确认课次和已保留课次约束
- **AND** 更新规则时保留已确认、已调整和已取消课次，不物理覆盖历史

#### Scenario: 本周临时调整

- **WHEN** 用户调整本周课次
- **THEN** 系统只作用于当前自然周
- **AND** 支持新增、修改、取消本周未核销课次
- **AND** 不修改 recurring schedule rule
- **AND** 写入调整日志并提示对课包余额和后续排课预测的影响

### Requirement: 冲突校验必须覆盖学生、老师和教室

The system SHALL reject schedule changes that create resource conflicts.

#### Scenario: 排课生成发生冲突

- **WHEN** 生成或调整课次与同一学生、同一老师或同一教室的有效课次时间重叠
- **THEN** 系统拒绝保存
- **AND** 返回冲突资源和时间
- **AND** 前端展示可执行方案：换时间、换老师、换教室、取消本次调整

#### Scenario: 教室未在报名阶段填写

- **WHEN** 报名阶段没有教室
- **THEN** 系统允许报名继续
- **AND** Classrooms 页面负责后续补录教室时段
- **AND** 一旦补录教室，系统必须校验同一教室时间冲突

### Requirement: 消课必须保护课时、工资和统计一致性

The system SHALL only deduct hours and make teacher payment eligible after the correct confirmation stage.

#### Scenario: 老师初确认

- **WHEN** 老师确认自己的课次
- **THEN** 系统进入 Pending Admin
- **AND** 不扣课时
- **AND** 不进入老师待支付列表

#### Scenario: 管理员终确认

- **WHEN** 管理员确认 Pending Admin 课次
- **THEN** 系统写消课记录、扣减课包余额、写课时台账
- **AND** 该课次进入老师待支付范围
- **AND** Reports 和低课时提醒使用更新后的余额

#### Scenario: 管理员驳回

- **WHEN** 管理员驳回老师初确认
- **THEN** 系统不扣课、不计薪
- **AND** 前端展示驳回原因和重新处理入口

### Requirement: Reports 必须只展示可解释的统计结果

The system SHALL derive reports from authoritative records and expose enough detail to explain every number.

#### Scenario: 用户查看 Reports

- **WHEN** 用户查看报名和经营数据
- **THEN** 每一行可追溯到 enrollment order、order item、course package 或 class enrollment
- **AND** 金额来自订单和付款流水
- **AND** 课时来自 course package、lesson occurrence 和 lesson record
- **AND** 欠费、到期、低课时高亮有明确规则和可解释字段

#### Scenario: 统计值不一致

- **WHEN** 汇总金额、订单余额、课包余额或课时台账不一致
- **THEN** 系统应暴露可检查入口或一致性校验结果
- **AND** 前端不得静默展示无法解释的数字

### Requirement: 用户交互必须把错误变成下一步

The system SHALL turn data validation failures into clear user decisions.

#### Scenario: 用户遇到冲突或缺配置

- **WHEN** 用户保存时遇到 schedule conflict、missing price、missing teacher、duplicate enrollment、overpayment 或 inactive class
- **THEN** 前端展示明确错误
- **AND** 提供下一步，例如打开 Course Pricing、选择老师、选择其他时间、打开 student history、回到 Classrooms 后补教室

## MODIFIED Requirements

### Requirement: 报名不是单纯录入表单

The system SHALL treat registration as the source event that initializes downstream operational records and reports.

#### Scenario: 报名成功后

- **WHEN** 报名成功
- **THEN** 系统必须能解释生成了哪些后续对象：学生、订单、课程明细、课包/班课、付款、收据、账号、初始课次

### Requirement: 排课不是独立日历功能

The system SHALL treat scheduling as a controlled projection from course packages, class enrollments, recurring rules, and weekly adjustments.

#### Scenario: 用户修改排课

- **WHEN** 用户修改固定规则或本周临时课次
- **THEN** 系统明确说明影响范围：长期规则、本周课次、余额预测、历史记录

## REMOVED Requirements

### Requirement: 静默接受不完整数据

**Reason**: 系统承担数据统计职责，不完整或冲突数据会污染报表、余额、课时和工资。
**Migration**: 缺课程配置、缺老师、冲突排课、超额付款、重复报名等情况必须阻止保存并给出下一步。
