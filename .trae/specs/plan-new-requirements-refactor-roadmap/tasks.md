# Tasks

- [x] Task 1: 数据库结构评审与迁移设计
  - [x] SubTask 1.1: 盘点现有 student、enrollment_order、payment_record、receipt_record、course_package、class_group、class_enrollment、lesson_record 结构与新需求差距。
  - [x] SubTask 1.2: 设计 student 新增字段：grade、school、guardian_phone_2、pickup/dropoff 勾选与地址。
  - [x] SubTask 1.3: 设计 enrollment_order 报名快照字段：校区、报名日期、周期起止、学生资料快照、接送快照、费用快照、余额。
  - [x] SubTask 1.4: 设计 enrollment_order_item 多课程明细表，支持 Register Class、Detail、Price、课程结构选项、1 对 1 课时和老师。
  - [x] SubTask 1.5: 设计课程价格选项表或扩展现有 course_product 结构，支撑课程、年级/档位、周期/时长/时段、价格、启用状态。
  - [x] SubTask 1.6: 输出迁移脚本顺序，确保可回滚、可重复验证，不破坏现有订单和课包数据。

- [x] Task 2: 后端领域模型与基础接口改造
  - [x] SubTask 2.1: 同步实体、DTO、VO、Mapper，覆盖新增学生字段、订单快照、订单明细和课程价格选项。
  - [x] SubTask 2.2: 新增课程价格选项查询接口，供前端结构化课程选择器使用。
  - [x] SubTask 2.3: 复用并整理支付方式字典接口，确保 Others 作为字典项配置和选择。
  - [x] SubTask 2.4: 明确学生复用规则：按“学生姓名 + 家长手机号”查找，未命中则创建。

- [x] Task 3: 新报名 intake 后端闭环
  - [x] SubTask 3.1: 新增报名 intake API，支持一次提交学生信息、多个课程明细、费用、折扣、其他费用、付款明细和收据选项。
  - [x] SubTask 3.2: 在同一事务内创建或复用学生，创建报名订单和多行订单明细。
  - [x] SubTask 3.3: 按课程明细创建 1 对 1 课包或班课报名记录。
  - [x] SubTask 3.4: 创建多笔付款记录并计算 paid、balance、payment status。
  - [x] SubTask 3.5: 创建收据记录并保存完整打印快照。
  - [x] SubTask 3.6: 对包含 1 对 1 的报名，强制校验老师，并自动创建或复用学生账号。
  - [x] SubTask 3.7: 默认密码优先使用手机号，无手机号时使用 9297019872。
  - [x] SubTask 3.8: API 返回报名结果、学生号、账号信息、收据号和收据预览数据。

- [x] Task 4: 前端信息架构重排
  - [x] SubTask 4.1: 调整路由和菜单，管理员/员工主入口只展示报名、1 对 1 消课、教室管理、后台数据整理。
  - [x] SubTask 4.2: 将学生、老师、课程价格、支付方式、收据配置等配置页收纳到管理员配置入口。
  - [x] SubTask 4.3: 老师登录默认进入自己的 1 对 1 消课页，学生登录默认进入自己的课程和剩余课时页。

- [x] Task 5: 前端报名页主流程
  - [x] SubTask 5.1: 新增完整报名表页面，按客户表单组织校区、学生、接送、付款、课程明细和费用汇总。
  - [x] SubTask 5.2: 实现课程明细多行编辑，支持添加、删除、每行独立选择课程结构。
  - [x] SubTask 5.3: 实现课程结构化选择器：课程类型、年级/档位、周期/时长/时段、老师、价格。
  - [x] SubTask 5.4: 实现费用实时计算：课程小计、接送费、其他费用、折扣、已付、余额。
  - [x] SubTask 5.5: 实现多笔付款编辑，每笔包含付款方式、金额和日期。
  - [x] SubTask 5.6: 对 1 对 1 明细未选择老师、价格缺失、姓名/手机号缺失等情况给出明确错误。
  - [x] SubTask 5.7: 提交成功后展示报名结果、账号信息和收据打印入口。

- [x] Task 6: 收据预览与打印
  - [x] SubTask 6.1: 新增收据预览组件，尽量还原客户表单样式。
  - [x] SubTask 6.2: 支持 Show EIN 开关，同一收据记录切换显示对应校区税号。
  - [x] SubTask 6.3: 编写打印样式，打印时隐藏导航、按钮和系统框架。
  - [x] SubTask 6.4: 支持历史收据查看和再次打印。

- [x] Task 7: 1 对 1 消课前端与后端适配
  - [x] SubTask 7.1: 后端补齐按老师、学生、课包维度查询 1 对 1 消课表所需的数据接口。
  - [x] SubTask 7.2: 前端实现管理员视角：按老师查看学生和消课表，可二次确认和打印。
  - [x] SubTask 7.3: 前端实现老师视角：仅查看自己的学生和待确认课程。
  - [x] SubTask 7.4: 前端实现学生视角：查看自己的排课、剩余课时和消课记录。
  - [x] SubTask 7.5: 默认 2 小时，允许改 1.5 小时等非标准时长。
  - [x] SubTask 7.6: 剩余 2 节课时显示续费提醒。

- [x] Task 8: 教室管理后补流程
  - [x] SubTask 8.1: 保留报名阶段无教室必填的规则。
  - [x] SubTask 8.2: 提供教室列表、时段占用录入和占用日历/表格。
  - [x] SubTask 8.3: 录入或调整教室时段时检查冲突并明确提示。

- [x] Task 9: 后台数据整理
  - [x] SubTask 9.1: 后端提供按校区、课程类型、班级/课程、周期、付款状态聚合的数据接口。
  - [x] SubTask 9.2: 前端实现接近 Excel 的汇总表，展示学生、学校、年级、报名日期、电话、接送、付款、费用、余额。
  - [x] SubTask 9.3: 1 对 1 记录额外展示总课时、已用课时、剩余课时和老师。
  - [x] SubTask 9.4: 欠费、即将到期、1 对 1 低课时记录高亮。
  - [x] SubTask 9.5: 展示总报名人数、总收入、欠费总额、各校区和各课程类型汇总。

- [x] Task 10: 回归测试与验收
  - [x] SubTask 10.1: 编写后端单元/集成测试覆盖报名 intake 事务、学生复用、多课程明细、付款、收据、账号创建。
  - [x] SubTask 10.2: 编写前端或 E2E 测试覆盖报名表提交、收据 EIN 切换、1 对 1 必选老师、支付方式字典。
  - [x] SubTask 10.3: 回归现有消课双确认、老师工资、低余额提醒、班课报名链路。
  - [x] SubTask 10.4: 验证数据库迁移可在现有数据上执行，历史订单、课包、消课、付款不丢失。

- [x] Task 11: 修复管理员配置入口未落地的配置能力
  - [x] SubTask 11.1: 将 `/admin/config/payment-methods` 从占位页替换为可用支付方式字典管理页面，支持查看、新增、编辑、停用/删除。
  - [x] SubTask 11.2: 将 `/admin/config/receipt` 从占位页替换为可用收据配置页面，覆盖 EIN、政策文本、页脚和打印配置入口。
  - [x] SubTask 11.3: 在管理员配置入口提供课程价格选项管理能力，覆盖结构化报名选项的课程类型、年级/档位、周期/时长/时段、Detail 和 Price。

- [x] Task 12: 修复最终业务缺口
  - [x] SubTask 12.1: 1 对 1 报名 intake 创建课包后自动生成可在 1 对 1 消课页展示和确认的课次记录。
  - [x] SubTask 12.2: 1 对 1 自动账号用户名按客户规则生成：`first name + last name first letter + @jin`，重名追加数字。
  - [x] SubTask 12.3: 历史收据接口返回 `receiptPayload`，历史收据页可完整再次预览和打印。

## Task Dependencies

- Task 2 depends on Task 1
- Task 3 depends on Task 2
- Task 4 can start after Task 1 design is stable
- Task 5 depends on Task 3 and Task 4
- Task 6 depends on Task 3 and Task 5
- Task 7 depends on Task 2 and can run partly in parallel with Task 5
- Task 8 depends on Task 4 and can run in parallel with Task 7
- Task 9 depends on Task 3 and Task 5
- Task 10 depends on Tasks 3 through 9
- Task 11 depends on Task 4
- Task 12 depends on Task 3, Task 6, and Task 7
