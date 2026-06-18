# Course Package 学生进度筛选与排序 Spec

## Why
Course Package 页面中课程产品展开后的学生进度列表目前会混合显示有效和已取消/停止的记录，用户需要默认聚焦仍有效的学生进度。同时，用户需要能按进度和周期快速排序，便于查看使用情况和报名先后。

## What Changes
- 1 on 1 课程产品展开后的学生进度列表增加状态筛选按钮。
- 默认只显示 `ACTIVE` 学生进度记录。
- 用户可以切换筛选，查看包含 `INACTIVE` 在内的其他记录。
- 展开列表的 `Progress` 表头支持点击排序。
- 展开列表的 `Period` 表头支持按开始时间或结束时间排序。
- 展开列表默认按创建时间倒序显示，新报名记录在最上面。

## Impact
- Affected specs: Course Package 列表、1 on 1 产品展开学生进度列表
- Affected code: `frontend/src/views/enrollment/CoursePackagesView.vue`，必要时涉及 `frontend/src/types/domain.ts` 和 Course Package 查询返回字段

## ADDED Requirements
### Requirement: 展开学生进度状态筛选
The system SHALL provide status filter controls in the 1 on 1 course product expanded student progress list.

#### Scenario: 默认只显示有效记录
- **WHEN** 用户展开某个 1 on 1 课程产品
- **THEN** 展开列表默认只显示状态为 `ACTIVE` 的学生进度记录

#### Scenario: 用户查看非有效记录
- **WHEN** 用户切换筛选条件为包含非有效记录
- **THEN** 展开列表显示匹配筛选条件的学生进度记录，包括 `INACTIVE` 记录

### Requirement: 展开学生进度排序
The system SHALL allow sorting the 1 on 1 expanded student progress list by progress and period.

#### Scenario: 按进度排序
- **WHEN** 用户点击 `Progress` 表头
- **THEN** 展开列表按进度百分比排序，并支持升序/降序切换

#### Scenario: 按周期排序
- **WHEN** 用户点击 `Period` 表头并选择或触发开始时间/结束时间排序
- **THEN** 展开列表按对应日期排序，并支持升序/降序切换

#### Scenario: 默认排序
- **WHEN** 用户首次展开课程产品
- **THEN** 展开列表按创建时间倒序排序，新报名记录显示在最上面

## MODIFIED Requirements
### Requirement: 1 on 1 课程产品展开列表
The system SHALL display student-level progress under each 1 on 1 course product, with default filtering to active progress records and user-controllable sorting by progress, start period, end period, and created time.

## REMOVED Requirements
无。
