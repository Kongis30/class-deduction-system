# 课程工作流与页面路径重组 Spec

## Why

当前左上角路径由路由名称静态拼接，不能表达用户当前正在处理的课程产品、年级班或学生课包，也不能作为可靠的返回和继续操作入口。课程产品、价格矩阵、班课排课和 1 对 1 排课的对象边界不清，导致用户从错误页面跳转，尤其是 1 对 1 产品被错误地引导到班课排课。

## What Changes

- 将全局路径改为可点击的上下文导航，不再仅展示静态文字。
- 明确两条独立工作流：
  - `1-on-1 Product -> Price Matrix -> Registration -> Student Course Package -> Weekly Schedule -> 1-on-1 Lessons`
  - `Group Product -> Grade Class and Price Matrix -> Class Schedule -> Registration -> Class Operations`
- 在 Course Products 页面按当前 tab 提供不同的创建入口和后续动作。
- 1 对 1 产品不提供班课 `Class schedules` 入口；其排课入口只在具体学生课包产生后出现。
- 班课产品只在已有 Grade Class 后提供 `Class schedules`；排课对象是 Grade Class，不是 Product。
- 在相关页面展示当前对象名称和下一步操作，保留查询参数以维持产品、Grade Class、学生和课包上下文。
- **BREAKING**：移除 Course Products 页面中将 1 对 1 产品跳转至 `Class Schedules` 的行为。

## Impact

- Affected specs: 课程产品、价格矩阵、班课排课、1 对 1 排课、报名、全局导航。
- Affected code:
  - `frontend/src/components/layout/AppHeader.vue`
  - `frontend/src/router/index.ts`
  - `frontend/src/views/base-data/CourseProductsView.vue`
  - `frontend/src/views/system/CoursePriceOptionsView.vue`
  - `frontend/src/views/schedule/ClassScheduleView.vue`
  - `frontend/src/views/schedule/RecurringScheduleView.vue`
  - `frontend/src/views/enrollment/CoursePackagesView.vue`
  - `frontend/src/views/registration/RegistrationIntakeView.vue`
  - `frontend/src/utils/menu.ts`

## ADDED Requirements

### Requirement: 路径必须反映真实业务对象

The system SHALL render a contextual, clickable page path based on the active route and its selected business object.

#### Scenario: 用户查看课程产品

- **WHEN** 用户进入 Course Products
- **THEN** 路径显示 `Back office / Course setup / Course Products`
- **AND** `Back office` 与 `Course setup` 可点击返回对应入口

#### Scenario: 用户从产品进入价格矩阵

- **WHEN** 用户从某个课程产品点击 `Prices` 或 `Grade classes / Prices`
- **THEN** 路径显示 `Back office / Course setup / Course Products / [Product name] / Price Matrix`
- **AND** 点击 `[Product name]` 返回带该产品筛选条件的 Course Products
- **AND** 页面保持 `courseProductId` 和课程类型上下文

#### Scenario: 用户从价格行进入班课排课

- **WHEN** 用户从某个 Grade Class 的价格行点击 `Class schedules`
- **THEN** 路径显示 `Back office / Course setup / [Product name] / [Grade Class name] / Class Schedule`
- **AND** 页面只聚焦该 Grade Class

#### Scenario: 用户进入 1 对 1 学生排课

- **WHEN** 用户从一个 1 对 1学生课包点击排课
- **THEN** 路径显示 `Back office / 1-on-1 / [Student name] / [Package name] / Weekly Schedule`
- **AND** 页面保留 `studentId` 和 `packageId`

### Requirement: 1 对 1 产品和班课产品必须分开创建

The system SHALL expose separate product creation actions according to the active product tab.

#### Scenario: 创建 1 对 1产品

- **WHEN** 用户处于 `1:1 Private` tab 并点击 `Create 1-on-1 product`
- **THEN** 创建表单固定教学模式为 `ONE_ON_ONE`
- **AND** 表单只展示产品名称、报名显示名、科目、默认单次课时、状态和备注
- **AND** 保存后引导用户进入该产品的 1 对 1价格矩阵

#### Scenario: 创建班课产品

- **WHEN** 用户处于 `Group Class` tab 并点击 `Create group class product`
- **THEN** 创建表单固定教学模式为 `GROUP`
- **AND** 表单只展示产品名称、报名显示名、科目、状态和备注
- **AND** 不要求老师、教室、班级日期、课次或周课表
- **AND** 保存后引导用户进入该产品的 Grade Class and Price Matrix

### Requirement: 排课入口必须绑定正确对象

The system SHALL only show schedule actions for the operational object that can be scheduled.

#### Scenario: 1 对 1产品列表

- **WHEN** 用户查看一个 1 对 1产品
- **THEN** 行操作只提供 `Prices`、`Edit`、`Deactivate/Delete`
- **AND** 不显示 `Class schedules`
- **AND** 页面说明学生报名后在 Course Packages 或学生上下文中排课

#### Scenario: 班课产品列表没有 Grade Class

- **WHEN** 用户查看一个尚未建立 Grade Class 的班课产品
- **THEN** 行操作提供 `Grade classes / Prices`
- **AND** `Class schedules` 不可用
- **AND** 提示用户先创建 Grade Class

#### Scenario: 班课产品列表已有 Grade Class

- **WHEN** 用户查看一个已有 Grade Class 的班课产品
- **THEN** 行操作提供 `Grade classes / Prices` 和 `Class schedules`
- **AND** `Class schedules` 跳转到该产品下的 Grade Class 列表

#### Scenario: 1 对 1学生课包未排课

- **WHEN** 管理员从 Course Packages 或报名成功后的下一步查看 1 对 1课包
- **THEN** 系统显示 `Create weekly schedule`
- **AND** 排课页面预选该学生和课包

### Requirement: 每个工作流页面必须提供下一步和返回上游

The system SHALL make workflow progression explicit without relying on browser back behavior.

#### Scenario: 用户完成班课价格行

- **WHEN** 用户保存一个班课 Grade Class and Price Matrix row
- **THEN** 页面显示下一步 `Set class schedule` 或 `Ready for registration`
- **AND** 已排课的班级显示老师、教室和周课表摘要

#### Scenario: 用户完成 1 对 1价格行

- **WHEN** 用户保存一个 1 对 1价格行
- **THEN** 页面显示下一步 `Ready for registration`
- **AND** 不显示班级、教室或 Class Schedule 操作

#### Scenario: 用户完成 1 对 1报名

- **WHEN** 1 对 1报名成功并创建课包
- **THEN** 成功页或后续页面提供 `Create weekly schedule`
- **AND** 跳转后预选对应学生和课包

## MODIFIED Requirements

### Requirement: 全局顶部导航

The system SHALL replace non-interactive static path text with a contextual breadcrumb that represents actual workflow hierarchy and provides usable navigation links.

#### Scenario: 无对象上下文的日常页面

- **WHEN** 用户进入 Registration、Classrooms、1-on-1 Lessons 或 Data Summary
- **THEN** 路径显示为 `Daily work / [Page name]`
- **AND** 不显示不存在的产品、班级或课包层级

### Requirement: Course Products 页面

The system SHALL organize Course Products around two product modes with distinct creation and action flows.

#### Scenario: 切换产品 tab

- **WHEN** 用户切换 `1:1 Private` 与 `Group Class`
- **THEN** tab 位于 Course Products 容器顶部
- **AND** 创建按钮、列表列、行操作、说明文案同步反映当前模式

## REMOVED Requirements

### Requirement: 产品级统一 Schedules 操作

**Reason**: 1 对 1与班课的排课对象不同。产品级统一排课会把 1 对 1错误带到班课 Grade Class 排课页面。

**Migration**: 班课继续通过 Grade Class 进入 Class Schedules；1 对 1通过学生 Course Package 进入 Weekly Schedule。
