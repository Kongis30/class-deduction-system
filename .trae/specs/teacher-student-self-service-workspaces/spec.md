# 老师与学生自助工作台 Spec

## Why
当前系统已有老师和学生账号以及部分受限菜单，但老师无法在同一入口处理自己的一对一和班课出勤，学生也无法按“未来 30 天 + 历史状态”查看自己的课表。角色职责与入口不完整，导致老师、学生仍需依赖管理员页面完成本应自助完成的操作。

## What Changes
- 建立老师自助工作台：老师仅能查看自己被安排的一对一和班课课次，并可对符合时间窗口的课次提交出勤。
- 建立学生自助课表：学生仅能查看自己的课表，默认展示未来 30 天，并可切换查看历史课次及其状态。
- 班课老师确认与一对一老师确认采用一致的二阶段语义：老师提交出勤后进入待管理员审核；只有管理员审核后才完成最终消课。
- 教师端保留 `My Calendar` 和 `My Lessons` 两个入口；`My Lessons` 统一展示一对一与班课记录及待办。
- 学生端只保留 `My Calendar`，不展示课程余额、其他学生信息、内部审核信息或任何消课操作。
- 后端接口按教师/学生绑定关系强制数据隔离，前端隐藏菜单不作为权限边界。

## Impact
- Affected specs: 角色权限、老师初确认、班课二阶段确认、学生课表、自助服务导航。
- Affected code:
  - `frontend/src/utils/menu.ts`
  - `frontend/src/router/index.ts`
  - `frontend/src/types/auth.ts`
  - `frontend/src/views/schedule/CalendarView.vue`
  - `frontend/src/views/schedule/TeacherScheduleView.vue`
  - `frontend/src/views/schedule/StudentScheduleView.vue`
  - 新增或改造老师课程记录视图及相关 API 封装
  - `backend/.../modules/schedule/controller/ClassGroupController.java`
  - `backend/.../modules/schedule/service/impl/ClassGroupServiceImpl.java`
  - 一对一、班课课次查询与确认接口的角色过滤逻辑

## ADDED Requirements

### Requirement: 老师自助工作台
系统 SHALL 为已绑定老师档案的教师账号提供仅属于该老师的课表和课程记录入口。

#### Scenario: 老师登录
- **WHEN** 老师账号登录系统
- **THEN** 系统默认进入 `My Calendar`
- **AND** 左侧菜单仅展示 `My Calendar` 和 `My Lessons`
- **AND** 系统不展示报名、学生档案、财务、教室管理、其他老师或其他班级的数据入口

#### Scenario: 老师查看自己的课程记录
- **WHEN** 老师进入 `My Lessons`
- **THEN** 系统展示该老师被安排的一对一和班课课次
- **AND** 每行至少展示课程或班级、学生或班级名称、上课时间、教室、出勤状态和可执行操作
- **AND** 系统默认展示未来 30 天及所有待老师处理的课次
- **AND** 老师可切换查看历史课次和历史状态

### Requirement: 老师只能提交本人课次的出勤
老师 SHALL 仅能对自己被安排的一对一或班课课次提交出勤；提交出勤不应完成最终消课。

#### Scenario: 老师提交一对一出勤
- **WHEN** 老师对符合确认时间窗口的本人一对一课次点击 `Submit attendance`
- **THEN** 系统允许填写结果、实际开始时间、实际课时和备注
- **AND** 系统创建或更新 `PENDING_ADMIN` 记录
- **AND** 系统不扣减课包余额、不写最终课时台账、不进入老师工资结算

#### Scenario: 老师提交班课出勤
- **WHEN** 老师对符合确认时间窗口的本人班课课次点击 `Submit attendance`
- **THEN** 系统为当次符合资格的在册学生创建或更新 `PENDING_ADMIN` 记录
- **AND** 班课课次显示为 `Awaiting admin review`
- **AND** 系统不在老师提交时完成最终确认

#### Scenario: 老师尝试访问非本人课次
- **WHEN** 老师通过 URL、请求参数或篡改前端数据访问非本人安排的课次或班级
- **THEN** 后端拒绝请求
- **AND** 不返回其他老师、学生或班级的业务数据

### Requirement: 管理员审核班课与一对一出勤
管理员 SHALL 保持对老师已提交出勤的终审核职责；终审核才产生最终消课结果。

#### Scenario: 管理员审核老师已提交的班课出勤
- **WHEN** 管理员打开待审核的班课课次并点击 `Review & confirm`
- **THEN** 系统完成该班课当次符合资格学生的最终确认
- **AND** 记录管理员审核人和时间
- **AND** 后续报表、课次状态与老师结算仅使用最终确认后的记录

#### Scenario: 管理员驳回班课或一对一出勤
- **WHEN** 管理员驳回待审核记录并填写原因
- **THEN** 系统保留驳回原因
- **AND** 课次回到可由对应老师重新提交出勤的状态
- **AND** 不产生最终消课或工资结算影响

### Requirement: 学生只读课表
系统 SHALL 为已绑定学生档案的学生账号提供仅属于该学生的只读课表。

#### Scenario: 学生登录
- **WHEN** 学生账号登录系统
- **THEN** 系统默认进入 `My Calendar`
- **AND** 左侧菜单仅展示 `My Calendar`
- **AND** 学生不可看到消课按钮、老师审核状态、课程余额、其他学生信息或管理入口

#### Scenario: 默认查看未来课表
- **WHEN** 学生进入 `My Calendar`
- **THEN** 系统默认展示从当天开始的未来 30 天课次
- **AND** 每条课次展示日期时间、课程或班级、老师、教室和面向学生的课次状态
- **AND** 无课次时展示明确空状态

#### Scenario: 查看历史课次
- **WHEN** 学生切换到历史课次视图
- **THEN** 系统展示已结束课次及其面向学生的状态
- **AND** 历史视图不得暴露内部确认阶段、驳回原因、课时台账、老师工资或其他学生资料

### Requirement: 自助接口必须实施服务端数据隔离
系统 SHALL 以当前登录用户绑定的 `teacherId` 或 `studentId` 作为自助查询和操作的事实边界。

#### Scenario: 缺少角色绑定
- **WHEN** 老师或学生账号没有有效的老师或学生绑定关系
- **THEN** 系统不返回任意业务数据
- **AND** 前端展示“账号尚未关联档案，请联系管理员”的明确提示

#### Scenario: 普通管理账号继续使用管理入口
- **WHEN** 管理员或员工使用现有管理页面
- **THEN** 系统继续保留跨老师、跨学生、跨班级的查询与审核能力
- **AND** 自助端的数据隔离规则不影响管理端业务能力

## MODIFIED Requirements

### Requirement: 班课二阶段确认可由班课老师自助发起
班课确认接口和课程记录查询 SHALL 同时支持管理员与已绑定的班课老师。老师只能读取和提交本人计划授课的班课课次；管理员仍负责终审核或驳回。

#### Scenario: 班课老师访问自己班级
- **WHEN** 老师进入 `My Lessons`
- **THEN** 系统只返回该老师作为计划老师的班课
- **AND** 老师可对符合窗口的课次提交出勤

### Requirement: 学生课表默认范围
学生课表 SHALL 从当前按周浏览的默认行为调整为未来 30 天列表或日历视图，并提供历史课次切换。

#### Scenario: 学生进入课表
- **WHEN** 学生首次进入 `My Calendar`
- **THEN** 系统默认查询并显示未来 30 天
- **AND** 用户可主动切换至历史课次

## REMOVED Requirements

### Requirement: 学生端展示课程余额与消课记录
**Reason**: 本次角色职责限定学生端只负责课表查询，余额与内部消课记录会增加不必要的解释成本并暴露管理口径。
**Migration**: 将学生菜单收敛为 `My Calendar`；如未来需要对学生开放余额或请假能力，应作为独立需求定义字段、口径与权限。
