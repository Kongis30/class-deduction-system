# 代码结构清理与死代码删除 Spec

## Why

近期系统完成了报名、学生历史、分期付款、表格化低学习成本体验等多轮改造，代码库中可能残留旧入口、占位页、未引用组件、旧 workspace schema、临时文件和过期 API 类型。需要在不改变业务行为的前提下清理死代码，让结构更贴合当前 4 个主入口和表格化工作流。

## What Changes

- 系统性盘点前端、后端、测试和文档中的未使用代码、旧入口、临时文件和过期结构。
- 删除可证明未被引用的前端页面、模块 schema、组件、类型、API 包装和样式。
- 删除或合并后端中可证明未被引用的 DTO、VO、Mapper 方法、服务方法和测试辅助代码。
- 清理本地运行产物、临时目录、无关缓存文件和过期文档残留，但不删除仍作为需求基准的 `doc/新需求改动`。
- 整理当前源码目录结构，使保留文件对应当前业务入口：Registration、1-on-1 Lessons、Classrooms、Reports、Settings。
- 不修改数据库结构、不重写业务流程、不改变已验证接口语义。
- **BREAKING**：无。任何删除都必须先有引用检查和构建/测试验证。

## Impact

- Affected specs: 菜单权限、报名主流程、学生报名历史、1 对 1 消课、教室管理、后台数据整理、系统配置。
- Affected code:
  - `frontend/src/views`
  - `frontend/src/modules`
  - `frontend/src/components`
  - `frontend/src/api`
  - `frontend/src/types`
  - `frontend/src/router/index.ts`
  - `frontend/src/utils/menu.ts`
  - `backend/src/main/java/com/classdeduction/system/modules`
  - `backend/src/test`
  - `doc`
  - `.run` and other local generated artifacts when safe

## ADDED Requirements

### Requirement: 删除前必须证明未使用

The system SHALL only remove code after checking references through static search, build tools, and route/API usage.

#### Scenario: 删除前端文件

- **WHEN** 清理某个 Vue view、component、module schema、API wrapper 或 type
- **THEN** 必须检查 router、imports、dynamic imports、template usage、tests 和 string-based references
- **AND** 只有确认不再被当前业务入口使用时才允许删除

#### Scenario: 删除后端文件或方法

- **WHEN** 清理某个 controller、service、mapper、DTO、VO、entity helper 或 test helper
- **THEN** 必须检查 Spring injection、Mapper 引用、test 引用、OpenAPI exposure 和前端 API 调用
- **AND** 只有确认不影响当前接口契约时才允许删除

### Requirement: 清理范围必须贴合当前产品方向

The system SHALL align retained code with the current four-entry product direction and keep low-frequency admin configuration isolated.

#### Scenario: 整理前端入口

- **WHEN** 清理旧页面或旧模块
- **THEN** 保留 Registration、1-on-1 Lessons、Classrooms、Reports、Settings 相关代码
- **AND** 删除已被四入口替代且没有路由/API/测试引用的旧后台式页面或 schema

### Requirement: 保留用户和需求资料

The system SHALL not delete user-provided requirement documents or unrelated user changes.

#### Scenario: 清理文档或临时文件

- **WHEN** 清理 `doc`、`.run`、缓存或导出文件
- **THEN** 不得删除 `doc/新需求改动` 下的需求资料
- **AND** 对不确定来源的文件必须先标记为候选，不能直接删除

### Requirement: 清理后必须保持构建和测试通过

The system SHALL verify that cleanup does not break frontend or backend behavior.

#### Scenario: 完成清理

- **WHEN** 完成一批死代码删除或结构整理
- **THEN** 至少运行前端 typecheck/build
- **AND** 运行与受影响后端模块相关的 Maven 测试或编译
- **AND** 如删除路由、API 或共享类型，必须额外验证关键业务入口仍可解析

### Requirement: 清理过程必须保持最小 Diff

The system SHALL avoid broad rewrites, formatting churn, or unrelated refactors.

#### Scenario: 发现可读性问题

- **WHEN** 代码存在可读性问题但仍被使用
- **THEN** 只做局部整理
- **AND** 不为了清理而改变业务逻辑、接口字段或数据库语义

## MODIFIED Requirements

### Requirement: 项目结构应围绕当前业务入口组织

The system SHALL keep source directories understandable around active modules and remove legacy organization layers that no longer have runtime usage.

#### Scenario: 整理结构

- **WHEN** 旧 workspace schema、旧 placeholder 页面或旧后台菜单结构已经不再被引用
- **THEN** 系统应删除这些文件
- **AND** 保留必要的 shared type、layout、auth、print、receipt 和 table utilities

## REMOVED Requirements

### Requirement: 无保留价值的旧功能占位

**Reason**: 当前产品方向已经收敛为 4 个主入口，旧占位页、旧 schema 和旧模块包装会增加维护成本。
**Migration**: 删除前必须确认没有路由、菜单、测试或动态引用；如仍有引用，先迁移引用到当前入口后再删除。
