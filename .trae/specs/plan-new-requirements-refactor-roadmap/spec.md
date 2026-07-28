# 新需求重构路线 Spec

## Why

客户的新需求把系统从“后台管理式功能集合”改为“按现有表格习惯操作的极简教务系统”。在进入实现前，需要先明确数据库、后端、前端的改造顺序，避免前端先做完但缺少数据结构支撑，或数据库改动无法兼容现有业务链路。

## What Changes

- 新增一条围绕 4 个主入口的重构路线：报名、1 对 1 消课、教室管理、后台数据整理。
- 明确数据库优先级：先补稳定快照和明细结构，再改后端事务链路，最后接前端页面。
- 明确后端优先级：先提供新报名 intake API 和课程价格结构，再改账号自动创建、收据数据、数据汇总和消课接口。
- 明确前端优先级：先重排菜单与报名主页面，再接课程选择器、费用付款、收据打印、1 对 1 消课和后台整理。
- 明确每阶段可验证结果，避免一次性大重构不可验收。
- **BREAKING**：现有“先创建/选择学生，再在 Enrollment 弹窗创建订单”的前端主流程将不再作为员工日常报名入口。
- **BREAKING**：现有 6 类后台式一级菜单将调整为 4 个业务主入口，部分配置页面转入管理员配置入口。

## Impact

- Affected specs:
  - 报名表单主流程
  - 课程价格配置
  - 多课程报名明细
  - 学生与账号自动创建
  - 收据打印与税号切换
  - 1 对 1 消课双确认
  - 教室占用管理
  - 后台数据整理与高亮提醒
- Affected code:
  - `doc/migrations/`
  - `doc/mysql_schema_from_db.sql`
  - `backend/src/main/java/com/classdeduction/system/modules/base`
  - `backend/src/main/java/com/classdeduction/system/modules/enrollment`
  - `backend/src/main/java/com/classdeduction/system/modules/auth`
  - `backend/src/main/java/com/classdeduction/system/modules/schedule`
  - `backend/src/main/java/com/classdeduction/system/modules/lesson`
  - `backend/src/main/java/com/classdeduction/system/modules/finance`
  - `frontend/src/router/index.ts`
  - `frontend/src/utils/menu.ts`
  - `frontend/src/api`
  - `frontend/src/views/enrollment`
  - `frontend/src/views/schedule`
  - 新增 `frontend/src/views/registration`
  - 新增 `frontend/src/views/one-on-one`
  - 新增 `frontend/src/views/data-summary`

## ADDED Requirements

### Requirement: 重构路线必须按依赖顺序推进

The system SHALL use a phased implementation roadmap that starts from database schema, then backend domain/API changes, then frontend workflow changes, then cross-flow verification.

#### Scenario: 数据结构先于接口和页面

- **WHEN** 开始实现新报名表单、多课程明细、接送字段、订单快照、账号自动创建
- **THEN** 数据库迁移和实体字段必须先完成
- **AND** 后端接口和前端页面不得依赖不存在的字段或临时字符串拼接结构

#### Scenario: 后端接口先于前端主流程切换

- **WHEN** 前端要把员工报名入口切换到新报名表
- **THEN** 后端必须已经提供新报名 intake API、课程价格选项 API、付款方式字典 API、收据打印数据 API
- **AND** API 失败必须返回明确错误，前端不得静默降级为旧报名流程

### Requirement: 新报名流程必须作为第一条端到端主线

The system SHALL prioritize the complete registration intake flow before lower-priority refactors.

#### Scenario: 员工完成报名表提交

- **WHEN** 员工填写报名表并提交
- **THEN** 系统创建或复用学生
- **AND** 系统创建报名订单和多行课程明细
- **AND** 系统创建付款记录和收据记录
- **AND** 如果包含 1 对 1 课程，系统创建或复用学生账号并要求选择老师
- **AND** 前端展示报名结果和收据打印入口

### Requirement: 课程价格结构必须支撑结构化选择

The system SHALL model course options as structured selections rather than free-form text.

#### Scenario: 员工选择课程明细

- **WHEN** 员工新增一行课程明细
- **THEN** 前端按课程类型、年级/档位、时长/周期/时段逐步选择
- **AND** 系统自动生成 Detail 和 Price
- **AND** 允许员工对特殊报名手动覆盖 Price，但保留覆盖标记

### Requirement: 菜单与权限必须贴合新四入口

The system SHALL present the simplified navigation based on user role.

#### Scenario: 管理员或员工登录

- **WHEN** 管理员或员工登录
- **THEN** 默认进入报名页
- **AND** 主菜单展示报名、1 对 1 消课、教室管理、后台数据整理
- **AND** 配置类页面放入管理员配置入口

#### Scenario: 老师或学生登录

- **WHEN** 1 对 1 老师登录
- **THEN** 只展示自己的 1 对 1 消课相关页面
- **WHEN** 1 对 1 学生登录
- **THEN** 只展示自己的课程、剩余课时和消课记录

### Requirement: 后台数据整理必须在报名主线后实现

The system SHALL derive summary data from registration, payment, refund, course item, and lesson deduction records.

#### Scenario: 报名后查看后台整理

- **WHEN** 新报名提交成功
- **THEN** 后台数据整理页能按校区、课程类型、班级/课程展示该学生
- **AND** 欠费、即将到期、1 对 1 低课时状态可高亮

## MODIFIED Requirements

### Requirement: 报名入口

The system SHALL replace the employee-facing enrollment creation dialog with a full-page registration intake form that matches the customer-provided form layout.

#### Scenario: 员工报名

- **WHEN** 员工进入报名入口
- **THEN** 页面直接展示完整报名表
- **AND** 员工无需先去学生管理页创建学生

### Requirement: 学生识别

The system SHALL identify repeat registrations by student name plus primary parent phone.

#### Scenario: 已有学生再次报名

- **WHEN** 员工输入的学生姓名和家长手机号匹配已有学生
- **THEN** 系统复用该学生记录
- **AND** 新报名仍保存本次报名快照

### Requirement: 收据税号

The system SHALL support showing EIN on the same receipt record without creating another receipt history record.

#### Scenario: 切换带税号收据

- **WHEN** 员工在收据预览中打开 Show EIN
- **THEN** 当前收据预览显示对应校区税号
- **AND** 系统不创建新的 receipt record

## REMOVED Requirements

### Requirement: 员工日常必须先维护学生资料再报名

**Reason**: 客户要求报名表提交即自动完成学生信息录入，旧流程学习成本高。

**Migration**: 保留学生管理页作为管理员资料维护入口，但不再作为员工报名的前置步骤。

### Requirement: 报名阶段必须选择教室

**Reason**: 客户明确报名时无需排教室，教室运营后续补录。

**Migration**: 报名表不展示必填教室字段；教室管理入口后续支持补录课程教室与占用时段。
