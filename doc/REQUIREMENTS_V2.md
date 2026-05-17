# 需求文档 V2

## 1. 概述

本系统 V2 版本包含三大改动：

1. **消课页面重构** — 从三表设计改为以学生/班课为中心的核销工作台
2. **新增班课管理** — 支持 1vN 班课的统一核销和到期管理
3. **Dashboard 按校区统计** — 从系统级统计改为按 campus 分组展示

---

## Part A: 消课页面重构 + 班课管理

## 2. 页面布局（总览）

```
┌──────────────────────────────────────────────────────────┐
│  [1v1 消课]  [班课管理]         ← Tab 切换              │
├──────────────────────────────────────────────────────────┤
│  (Tab 内内容见下方对应章节)                               │
└──────────────────────────────────────────────────────────┘
```

---

## 3. Tab 1：1v1 消课

### 3.1 页面布局

```
┌──────────────────────────────────────────────────────────┐
│  左侧栏（学生列表）         │  右侧内容区                  │
│  宽度: 280px               │                             │
│                             │                             │
│  ┌───────────────────────┐  │  ┌─────────────────────┐   │
│  │ 🔍 Search student    │  │  │ Student Name         │   │
│  ├───────────────────────┤  │  │                      │   │
│  │ ○ Alice Johnson      │  │  │  ┌─ Package A ─────┐ │   │
│  │   Last: 05/10         │  │  │  │ ⚡ 8 units left │ │   │
│  │ ○ Bob Smith           │  │  │  │ Last: 05/24     │ │   │
│  │   Last: 04/28         │  │  │  ├─────────────────┤ │   │
│  │ ○ Carol Lee           │  │  │  │ [today] 05/16   │ │   │
│  │   Last: 05/14         │  │  │  │ 10:00-11:00 ✓   │ │   │
│  │ ○ ...                 │  │  │  │ [next]  05/23   │ │   │
│  │                       │  │  │  │ 10:00-11:00     │ │   │
│  │                       │  │  │  │ [next]  05/30   │ │   │
│  │                       │  │  │  │ 10:00-11:00     │ │   │
│  │                       │  │  │  └─────────────────┘   │
│  │                       │  │  │                         │
│  │                       │  │  │  ┌─ Package B ─────┐   │
│  │                       │  │  │  │ ⚡ 12 units left│   │
│  │                       │  │  │  │ Last: 05/10     │   │
│  │                       │  │  │  ├─────────────────┤   │
│  │                       │  │  │  │ [prev] 05/03    │   │
│  │                       │  │  │  │ 14:00-15:30    │   │
│  │                       │  │  │  │ [prev] 05/10    │   │
│  │                       │  │  │  │ 14:00-15:30    │   │
│  │                       │  │  │  │ [next] 05/17    │   │
│  │                       │  │  │  │ 14:00-15:30    │   │
│  │                       │  │  │  └─────────────────┘   │
│  └───────────────────────┘  └─────────────────────────────┘
```

### 3.2 左侧学生列表

- **数据来源**：`fetchAllStudents()`（所有学生）
- **搜索**：顶部搜索框，按学生姓名过滤
- **选中**：点击学生条目高亮，右侧区切换到该学生的课包视图
- **学生条目显示**：
  - 学生姓名
  - 该学生所有课包中最后一次课的日期（若无课程记录则不显示）
- **默认选中**：页面加载后默认选中第一个学生

### 3.3 右侧 — 学生信息及课包列表

选中学生后，右侧展示：
- **学生姓名**：作为区头
- **课包列表**：每个课包独立卡片，按创建时间倒序排列（最新的在上面）

#### 每张课包卡片包含：

##### 头部信息
- 课包名称（packageName）
- 剩余课时数（剩余 units）
- 本课包最后一次课的日期（该课包所有课程记录中日期最大的那次）

##### 课程记录列表
- 展示该课包下**所有**排课记录（lesson_occurrence），按日期排序
- 每条记录显示：
  - 日期标记（`[prev]`、`[today]`、`[next]`）：
    - `[prev]`：日期在今天之前的记录
    - `[today]`：日期等于今天的记录（高亮显示）
    - `[next]`：日期在今天之后的记录
  - 时间范围（如 `10:00 – 11:00`）
  - 教师姓名
  - 教室
  - 时长（hours，如 `1.0h`）
  - 状态标签：
    - 未核销（planningStatus = SCHEDULED/ADJUSTED）：显示 `Pending` 标签
    - 已核销（有 resultStatus）：显示结果状态标签
  - 操作按钮：
    - 未核销记录 → 「核销」按钮
    - 已核销记录 → 「详情」按钮（查看本次核销详情）
    - 已取消记录（resultStatus = CANCELLED）→ 「补课」按钮

##### 核销弹窗
- **完全复用当前 Confirm 弹窗的设计**，包含：
  - Result Status（下拉选择：Completed / Formal Leave / No Show / Cancelled）
  - Actual Teacher（可选，默认排课教师）
  - Actual Classroom（可选，默认排课教室）
  - Actual Start Time（可选，默认排课时间）
  - Actual End Time（可选，默认排课时间）
  - Units（输入框 + 小时换算提示）
  - Deduction Preview（扣课预览）
  - Notes（备注）
- **请假自动补排课规则**：
  - 当核销状态选为 `Formal Leave` 时，点击确认后，后端自动为该课包追加一节课
  - 补课时间规则：根据该课包的**排课规律**（recurring rule），在原定日期后的**下一个可用排课日期**，使用**相同的时间段**和**相同的教师/教室**创建一条新的 lesson_occurrence（occurrenceType = MAKEUP）
  - 补课记录自动出现在课包列表末尾（标记为 `[next]`）

---

## 4. Tab 2：班课管理

### 4.1 概述

班课（1vN）是固定时间、固定地点的班级式课程（如 SAT 冲刺班、数学提高班）。学生按**时间周期**报班（1 个月 / 2 个月等），到期需续费。同一班课的所有排课记录**统一核销**，一次操作全班生效。

### 4.2 页面布局

```
┌──────────────────────────────────────────────────────────┐
│  左侧栏（班课列表）         │  右侧内容区                  │
│  宽度: 280px               │                             │
│                             │                             │
│  ┌───────────────────────┐  │  ┌─────────────────────┐   │
│  │ 🔍 Search class      │  │  │ 📖 SAT Math          │   │
│  ├───────────────────────┤  │  │ 每周六 10:00-12:00   │   │
│  │ ○ SAT Math           │  │  │ 教师: Mr. Smith      │   │
│  │   Active: 12         │  │  │ 教室: 205             │   │
│  │ ○ English Writing    │  │  ├─────────────────────┤   │
│  │   Active: 8          │  │  │ 📋 在读学生 (12人)    │   │
│  │ ○ Physics 101        │  │  │ ┌──────────────────┐ │   │
│  │   Active: 6          │  │  │ │ 姓名  │ 到期日   │ │   │
│  │ ○ ...                │  │  │ │ Alice │ 06/01 ✅ │ │   │
│  │                       │  │  │ │ Bob   │ 05/25 ⚠️ │ │   │
│  │                       │  │  │ │ Carol │ 07/01 ✅ │ │   │
│  │                       │  │  │ └──────────────────┘ │   │
│  │                       │  │  │ 📬 [发送到期提醒]    │   │
│  │                       │  │  ├─────────────────────┤   │
│  │                       │  │  │ 📅 排课记录            │   │
│  │                       │  │  │ ┌──────────────────┐ │   │
│  │                       │  │  │ │ [prev] 05/10 ✅ │ │   │
│  │                       │  │  │ │ [today]05/31 ⏳ │ │   │
│  │                       │  │  │ │ [next] 06/07    │ │   │
│  │                       │  │  │ └──────────────────┘ │   │
│  └───────────────────────┘  └─────────────────────────────┘
```

### 4.3 左侧班课列表

- **数据来源**：获取所有班课（class/course group 列表），含各班在读人数
- **搜索**：顶部搜索框，按班课名称过滤
- **选中**：点击班课条目高亮，右侧切换到该班课详情
- **班课条目显示**：
  - 班课名称
  - 在读学生数
- **默认选中**：页面加载后默认选中第一个班课

### 4.4 右侧 — 班课详情

选中班课后，右侧从上到下分为三个区域：

#### 4.4.1 班课头部信息
- 班课名称
- 排课时间描述（如 "每周六 10:00-12:00"）
- 任教教师
- 教室

#### 4.4.2 在读学生列表（含到期管理）

- **展示该班课所有在册学生**，每行显示：
  - 学生姓名
  - 报名日期（enrollment date）
  - 到期日期（expiry date）
  - 状态标签：
    - ✅ 正常：到期日 > 30 天
    - ⚠️ 即将到期：到期日在未来 30 天内
    - ❌ 已到期：到期日 ≤ 今天
- **排序规则**：到期日期升序排列（即将到期的排最前）
- **操作入口**：「发送到期提醒」按钮
  - 按钮上显示即将到期人数（如 `📬 发送到期提醒 (2人)`）
  - 点击后，系统向所有「即将到期」和「已到期」的学生发送续费提醒邮件
  - 邮件内容：学生姓名、班课名称、到期日期（复用通知模板）
  - 发送完成后弹出提示 "Expiry reminders sent to N students."

#### 4.4.3 排课记录列表

- 展示该班课下**所有**排课记录，按日期排序
- 每条记录显示内容与 1v1 课包一致（日期标记、时间、教师、教室、状态）
- **核销操作**：
  - 未核销记录 → 「核销」按钮
  - 点击核销，弹窗与 1v1 核销弹窗**完全一致**
  - 点击确认后，后端自动对该时间点**所有在册学生**统一写入 lesson_record（resultStatus = COMPLETED），扣课逻辑按该班课的配置执行
  - 班课核销时，核销弹窗中的 Units 字段固定为该班课的标准课时数（不可编辑 / 显示为只读）

### 4.5 班课核销特殊规则

- **统一核销**：老师点一次「确认」，该排课时间点的**所有在册学生**自动完成核销，无需逐个操作
- **请假处理**：班课不支持个别学生请假核销。请假需单独在 1v1 侧处理（若班课学生有个别请假需求，属于后续迭代范围）
- **核销时只允许选择 COMPLETED 状态**（班课不需要 Leave / No Show / Cancelled 等状态）

---

## 5. 数据接口

### 5.1 1v1 相关 API

现有接口 `GET /api/v1/lesson-occurrences` 需确认是否支持按 `studentId` + `packageId` 查询。

若现有接口不支持，需新增：
```
GET /api/v1/schedule/students/{studentId}/packages
→ 返回该学生的所有课包（含剩余课时）

GET /api/v1/schedule/students/{studentId}/packages/{packageId}/occurrences
→ 返回该课包的所有排课记录（含核销状态）
```

### 5.2 班课相关 API（需新增）

```
GET /api/v1/classes
→ 返回所有班课列表（含 id, name, teacherName, classroomName, scheduleDesc, activeStudentCount）

GET /api/v1/classes/{classId}
→ 返回班课详情（含头部信息 + 排课信息）

GET /api/v1/classes/{classId}/students
→ 返回该班课在读学生列表（含 studentName, enrollmentDate, expiryDate, status）

GET /api/v1/classes/{classId}/occurrences
→ 返回该班课所有排课记录（含核销状态）

POST /api/v1/classes/{classId}/occurrences/{occurrenceId}/confirm
→ 统一核销：为该班课当前所有在册学生核销该次课
→ body: { actualTeacherId?, actualClassroomId?, actualStartAt?, actualEndAt?, notes? }
→ 注：班课核销不需要传 resultStatus（固定 COMPLETED）和 actualUnits（固定班课标准课时）

POST /api/v1/classes/{classId}/send-expiry-reminders
→ 向该班课中即将到期和已到期的学生发送续费提醒邮件
→ 返回: { sentCount: number }
```

### 5.3 核销接口（1v1）

复用现有：
```
POST /api/v1/lessons/confirm
body: { occurrenceId, resultStatus, actualTeacherId, actualClassroomId, actualStartAt, actualEndAt, actualUnits, notes }
```

### 5.4 请假自动补课

核销接口内部处理：当 `resultStatus = FORMAL_LEAVE` 时，后端在同一事务中：
1. 写入 `lesson_record`（核销记录）
2. 写入 `hour_ledger`（课时扣减——请假不扣课时）
3. 根据该课包的 recurring rule 推算下一个排课日期
4. 创建新的 `lesson_occurrence`（occurrenceType = MAKEUP, planningStatus = SCHEDULED）

## 6. 交互说明

- 页面顶部 Tab 切换：点击 Tab 名称切换 1v1 / 班课模式，切换时保持左侧栏筛选状态
- 左侧列表均支持键盘上下选择
- 核销成功后不刷新页面，而是更新当前列表（前端移除该条记录的 Pending 状态 + 若为请假则追加新记录）
- 页面顶部不需要日期导航控件

## 7. 边界情况

### 7.1 1v1 消课

- 学生无课包：右侧显示 "No active packages for this student."
- 课包无排课记录：卡片内显示 "No lesson records in this package."
- 课包已耗尽（剩余 0 units）：卡片头部显示 "0 units left"，课程列表正常展示
- 请假触发补课时，若该课包无 recurring rule → 前端提示 "No recurring rule found, please manually schedule a makeup."

### 7.2 班课管理

- 无班课数据：左侧显示 "No group classes available."
- 班课无在读学生：右侧学生区域显示 "No enrolled students in this class."
- 班课无排课记录：记录列表显示 "No lesson records in this class."
- 所有学生均未到期（均 > 30 天）：「发送到期提醒」按钮置灰，显示 "No students expiring soon"
- 班课核销时若当前无在册学生 → 提示 "No active students to confirm for this lesson."

---

## Part B: Dashboard 按校区统计

### 8. 概述

当前 dashboard 展示系统级统计数据（全校总学生数、总教师数等）。改为**顶部展示系统级总览，下方按校区（campus）分组展示各校区明细**。

### 9. 页面设计

```
┌──────────────────────────────────────────────────────────┐
│  📊 运营总览                                              │
│  ┌──────┬──────┬──────┬──────┬──────┬──────┬──────────┐  │
│  │学生  │教师  │教室  │校区  │课程  │今日课 │低余量提醒│  │
│  │ 86   │ 12   │ 15   │  3   │ 28   │  6   │   3     │  │
│  └──────┴──────┴──────┴──────┴──────┴──────┴──────────┘  │
│                                                           │
│  🏫 各校区明细                                             │
│  ┌────────────────────┬────────────────────┬────────────┐ │
│  │ 🟢 主校区          │ 🔵 东校区          │ 🟠 西校区  │ │
│  │                    │                    │             │ │
│  │ 教室        8      │ 教室        5      │ 教室    2   │ │
│  │ 今日课      4      │ 今日课      2      │ 今日课  0   │ │
│  │ 教师        7      │ 教师        4      │ 教师    2   │ │
│  │ 学生       42      │ 学生       28      │ 学生   16   │ │
│  │ 低余量课包  2      │ 低余量课包  1      │ 低余量课包 0│ │
│  └────────────────────┴────────────────────┴────────────┘│
└──────────────────────────────────────────────────────────┘
```

### 10. API 变更

```
GET /api/v1/dashboard/summary
→ 原接口，返回格式改为:
{
  // 系统总览（不变）
  totalStudents, totalTeachers, totalClassrooms,
  totalCampuses, totalCourseProducts,
  todayLessonsCount, lowBalancePackages,

  // 新增：各校区明细
  campusDetails: [
    {
      campusId: 1,
      campusName: "主校区",
      students: 42,
      teachers: 7,
      classrooms: 8,
      todayLessons: 4,
      lowBalancePackages: 2,
    },
    ...
  ]
}
```

### 11. 统计口径

| 指标 | 统计方式 |
|------|----------|
| 系统级总数 | 与原实现一致 |
| 各校区教室数 | classroom.campus_id 分组 count |
| 各校区学生数 | student.campus_id 分组 count |
| 各校区教师数 | teacher.campus_id 分组 count |
| 各校区今日课数 | lesson_occurrence → planned_classroom_id → classroom.campus_id 分组 |
| 各校区低余量课包 | course_package.student_id → student.campus_id 分组（按 LOW_BALANCE 阈值过滤） |

---

## Part C: DB Schema 变更

### 12. 表结构修改

#### 12.1 student 表 — 新增 campus_id

```sql
ALTER TABLE `student`
  ADD COLUMN `campus_id` BIGINT DEFAULT NULL COMMENT 'Campus ID',
  ADD KEY `idx_student_campus` (`campus_id`),
  ADD CONSTRAINT `fk_student_campus` FOREIGN KEY (`campus_id`) REFERENCES `campus` (`id`);
```

#### 12.2 teacher 表 — 新增 campus_id

```sql
ALTER TABLE `teacher`
  ADD COLUMN `campus_id` BIGINT DEFAULT NULL COMMENT 'Campus ID',
  ADD KEY `idx_teacher_campus` (`campus_id`),
  ADD CONSTRAINT `fk_teacher_campus` FOREIGN KEY (`campus_id`) REFERENCES `campus` (`id`);
```

#### 12.3 classroom 表 — campus VARCHAR → campus_id BIGINT

```sql
-- 新增 campus_id 列
ALTER TABLE `classroom`
  ADD COLUMN `campus_id` BIGINT DEFAULT NULL COMMENT 'Campus ID',
  ADD KEY `idx_classroom_campus_id` (`campus_id`),
  ADD CONSTRAINT `fk_classroom_campus` FOREIGN KEY (`campus_id`) REFERENCES `campus` (`id`);

-- 迁移数据：根据 campus 名称匹配 campus 表
UPDATE `classroom` c
  JOIN `campus` cp ON c.`campus` = cp.`campus_name`
  SET c.`campus_id` = cp.`id`;

-- 后续可删除旧 campus 列（可选，本版本保留向下兼容）
```

### 13. 实体类变更

#### StudentEntity
```java
@TableField("campus_id")
private Long campusId;
```

#### TeacherEntity
```java
@TableField("campus_id")
private Long campusId;
```

#### ClassroomEntity
```java
// campus VARCHAR → campus_id BIGINT
@TableField("campus_id")
private Long campusId;
```

### 14. 前端 TypeScript 类型变更

#### DashboardSummary
```typescript
export interface DashboardSummary {
  totalStudents: number;
  totalTeachers: number;
  totalClassrooms: number;
  totalCampuses: number;
  totalCourseProducts: number;
  todayLessonsCount: number;
  lowBalancePackages: number;
  campusDetails: CampusDetail[];  // 新增
}

export interface CampusDetail {
  campusId: number;
  campusName: string;
  students: number;
  teachers: number;
  classrooms: number;
  todayLessons: number;
  lowBalancePackages: number;
}
```

#### StudentItem / TeacherItem / ClassroomItem
```typescript
export interface StudentItem {
  // ... 现有字段
  campusId?: number;  // 新增
}

export interface TeacherItem {
  // ... 现有字段
  campusId?: number;  // 新增
}

export interface ClassroomItem {
  // ... 现有字段
  campusId?: number;  // 新增
  campus?: string;    // 保留（旧字段）
}
```
