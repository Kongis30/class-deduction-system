# 表格化低学习成本体验优化 Spec

## Why

需求材料明确指出，新系统的核心不是增加更多后台能力，而是贴合机构当前 Excel、纸质表格和“填一张表即可完成报名”的操作习惯。当前计划需要从泛化的 UX 优化调整为更具体的方向：少入口、少隐藏、表格化、打印稳定、日常操作路径短。

## What Changes

- 管理员和员工侧信息架构回到 4 个一级入口：Registration、1-on-1 Lessons、Classrooms、Reports。
- 不把 Dashboard 作为日常主入口；管理员和员工登录后应默认进入 Registration。
- Students 不作为新的一级菜单打散 4 个入口原则，学生查找、报名历史和补记付款应从 Registration 或 Reports 的上下文进入。
- 页面视觉优先贴近 Excel/纸质表格，而不是增加复杂卡片、折叠和多层导航。
- 报名页保持“信息录入，打印收据”的纸质表单心智，减少弹窗和隐藏步骤。
- 1-on-1 Lessons 以“每个老师的每个学生一张消课表”的方式组织，突出老师确认、管理员确认、剩余课时和打印。
- Reports 以后台数据整理表为核心，接近 Excel 汇总表，突出欠费、到期、低课时高亮。
- Classrooms 维持后补运营定位，不出现在报名必填链路中。
- Settings 只保留管理员低频配置入口，不干扰日常操作。
- 不改变数据库结构、核心业务规则和已确认的报名/付款/收据/消课语义。
- **BREAKING**：无。

## Impact

- Affected specs: 报名主流程、学生报名历史、分期付款、1 对 1 消课、教室管理、后台数据整理、菜单权限。
- Affected code:
  - `frontend/src/utils/menu.ts`
  - `frontend/src/router/index.ts`
  - `frontend/src/stores/auth.ts`
  - `frontend/src/components/layout/AppSidebar.vue`
  - `frontend/src/assets/styles/index.css`
  - `frontend/src/views/registration/RegistrationIntakeView.vue`
  - `frontend/src/views/base-data/StudentsView.vue`
  - `frontend/src/views/one-on-one/OneOnOneDeductionView.vue`
  - `frontend/src/views/schedule/ClassroomOccupancyView.vue`
  - `frontend/src/views/data-summary/DataSummaryView.vue`
  - `frontend/src/views/enrollment/ReceiptRecordsView.vue`
  - `frontend/src/views/system/*`

## ADDED Requirements

### Requirement: 主入口必须保持极简

The system SHALL keep daily navigation focused on the four business entries confirmed in the requirement documents.

#### Scenario: 管理员或员工登录

- **WHEN** 管理员或员工登录系统
- **THEN** 系统默认进入 `Registration`
- **AND** 侧边栏第一层只展示 `Registration`、`1-on-1 Lessons`、`Classrooms`、`Reports`
- **AND** 管理员低频配置通过 `Settings` 入口访问，但不与日常入口混在一起

#### Scenario: 查找已报名学生

- **WHEN** 用户需要查找学生、回看报名表或补记付款
- **THEN** 系统从 `Registration` 或 `Reports` 提供上下文入口
- **AND** 系统不应为了查学生额外增加新的一级菜单

### Requirement: 视觉形态应贴近表格化工作流

The system SHALL favor spreadsheet-like tables and paper-form layouts over card-heavy or deeply nested interactions.

#### Scenario: 用户处理日常业务

- **WHEN** 用户进入报名、消课、教室或报表页面
- **THEN** 页面优先展示表单或表格主体
- **AND** 关键字段直接可见，不应要求用户反复打开多层弹窗才能理解当前数据

#### Scenario: 页面需要辅助说明

- **WHEN** 页面存在筛选、状态或下一步操作
- **THEN** 系统使用简短英文说明直接解释用途
- **AND** 说明不应占用过多空间或遮挡表格主体

### Requirement: 报名页应维持纸质表单心智

The system SHALL keep registration as the fastest path to enter information, calculate fees, record payments, and print receipt.

#### Scenario: 员工进入报名页

- **WHEN** 员工进入 `Registration`
- **THEN** 页面展示接近客户提供截图的完整报名表
- **AND** 校区、学生信息、接送、课程明细、费用、付款和打印入口在同一主页面完成
- **AND** `Save` 只负责保存报名信息，`Print` 只负责打印

#### Scenario: 用户填写费用和付款

- **WHEN** 用户选择课程、调整价格、录入接送费、其他费用、折扣或多笔付款
- **THEN** 系统实时展示 Amount due、Paid、Balance
- **AND** 欠费状态必须显著但不干扰继续录入

### Requirement: 学生历史应作为报名运营上下文

The system SHALL make enrolled student lookup and historical registration review accessible without breaking the four-entry navigation model.

#### Scenario: 用户在报名运营中查学生

- **WHEN** 用户需要查找已报名学生
- **THEN** `Registration` 或 `Reports` 页面提供明显的 student lookup/history 入口
- **AND** 用户可以查看学生报名历史、历史报名表、付款时间线和补记付款入口

### Requirement: 1-on-1 消课应像老师文件夹里的学生表

The system SHALL present one-on-one deduction around teacher-student tables with clear confirmation and balance states.

#### Scenario: 管理员查看 1 对 1 消课

- **WHEN** 管理员进入 `1-on-1 Lessons`
- **THEN** 系统按老师组织学生列表和消课表
- **AND** 每个学生表清楚展示 total hours、used/assigned hours、remaining hours、teacher confirmation、admin confirmation
- **AND** 页面提供打印单个学生消课表的入口

#### Scenario: 老师或学生登录

- **WHEN** 老师登录
- **THEN** 老师只看到自己的学生、排课和待确认记录
- **WHEN** 学生登录
- **THEN** 学生只看到自己的课程、剩余课时和消课记录

### Requirement: Reports 应接近 Excel 汇总表

The system SHALL make reports work like the customer’s existing backend data整理 spreadsheet.

#### Scenario: 用户查看后台数据整理

- **WHEN** 用户进入 `Reports`
- **THEN** 系统展示以学生/报名为行的宽表
- **AND** 字段覆盖 class、student、school、grade、phone、pickup/dropoff、period、tuition、fees、discount、paid、balance
- **AND** 1 对 1 行额外展示 total hours、used/assigned hours、remaining hours、teacher

#### Scenario: 存在风险数据

- **WHEN** 学生欠费、报名周期临近结束或 1 对 1 剩余 2 节课
- **THEN** 系统在表格中高亮该行或关键字段
- **AND** 状态清除后高亮自动消失

### Requirement: 打印体验必须稳定

The system SHALL preserve clean print output for registration receipts and one-on-one deduction sheets.

#### Scenario: 用户打印

- **WHEN** 用户打印报名表、历史收据或 1 对 1 消课表
- **THEN** 打印内容不显示侧边栏、导航、系统按钮和编辑控件
- **AND** 表格边框、课程明细和政策内容清晰可读

### Requirement: UI 文案和状态应一致

The system SHALL use consistent English UI labels for operations while preserving the bilingual paper-form content required by the customer.

#### Scenario: 用户跨页面查看状态

- **WHEN** 用户在报名、学生历史、消课或报表页面看到付款、余额、确认或续费状态
- **THEN** 相同业务状态使用一致英文文案和颜色
- **AND** 新增系统 UI 文案不得引入中文

## MODIFIED Requirements

### Requirement: Dashboard 不作为管理员员工主工作台

The system SHALL not introduce Dashboard as the primary daily workflow for admin or staff users.

#### Scenario: 登录后默认工作区

- **WHEN** 用户登录成功
- **THEN** 系统优先进入 Registration
- **AND** 如 Dashboard 页面仍保留，应作为低优先级辅助入口，不替代四个主入口

### Requirement: Students 不作为一级入口

The system SHALL keep student management accessible through registration/report workflows rather than adding it as a fifth daily navigation entry.

#### Scenario: 用户查学生

- **WHEN** 用户从报名或报表页面查找学生
- **THEN** 系统可以打开学生列表、详情或历史抽屉
- **AND** 侧边栏仍保持四个日常主入口

## REMOVED Requirements

### Requirement: 任务型 Dashboard 主导体验

**Reason**: 需求资料明确强调登录后路径极简和 4 个一级功能，泛化 Dashboard 会增加一个新的学习入口。
**Migration**: 将原计划中的 New registration、Find student、Record payment、Confirm lessons、View reports 等快捷能力转移到 Registration、1-on-1 Lessons 和 Reports 页面上下文中。
