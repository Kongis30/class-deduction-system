# 班课与 1 对 1 假期调整方案及代码修改计划

> 基线：`main_v2`（`1bb9f2e`）
>
> 状态：班课 P0 已实施；1 对 1 P1 暂缓（当前没有 1 对 1 假期入口，先不改私教排课）。
>
> 范围：班课项目假期、1 对 1 私教假期。

## 1. 结论

班课和 1 对 1 必须使用两套完全不同的假期规则：

| 项目 | 班课 | 1 对 1 |
|---|---|---|
| 假期内课次 | 直接取消 | 取消并跳过 |
| 假期外课次 | **完全不动** | **完全不动** |
| 老师轮换 | **不重算** | 不涉及 |
| 是否顺延 | **否** | **是** |
| 班级/课包结束日 | **不变** | 课包结束日顺延 |
| 报名有效期 | 不变 | 课包结束日顺延 |
| 是否补课 | 否 | 尾部补课 |

一句话：

> **班课假期只做删除；1 对 1 假期做跳过 + 尾部补课。只有 1 对 1 需要顺延。**

### P0 已实施内容

- `ClassHolidayServiceImpl` 新增/修改/删除假期只调用 `reconcileAffectedDates`，不再调用 `synchronizeProjectClassSchedules` / `recalculateSessionEnrollments`。
- 删除 `synchronizeProjectClassSchedules`、`removeFutureRegularOccurrencesAfter`、`resolveScheduleEnd`、`ensureNoStudentConflict` 等假期延长/重排逻辑。
- `ClassSlotRotation` 不再把假期日期从轮换计数中排除，保证假期外课次老师/科目/教室不变。
- `ClassGroupServiceImpl.generateFutureClassOccurrences` 不再通过 `endDateAfterHolidays` 修改班级结束日。
- `ClassScheduleDates` 删除 `endDateAfterHolidays`。
- Prices 假期弹窗文案改为“只取消，不顺延，不补课”。
- 新增/更新 `ClassHolidayServiceImplTest`、`ClassScheduleDatesTest`、`ClassSlotRotationTest`。

### P1 暂缓内容

当前系统没有 1 对 1 假期设置入口，因此先不落地私教假期排课调整。后续需要时再按本文第 6 节实现：

- 私教假期跳过 + 尾部补课；
- 课包结束日顺延；
- 假期补课撤销与来源记录。

---

## 2. 班课业务规则

### 2.1 示例

用户设置：

```text
1 号 ～ 30 号，每天一节课
```

假期：

```text
1 号 ～ 15 号
```

正确结果：

```text
1-15 号：课次取消
16-30 号：日期、老师、科目、教室、时间全部保持原样
30 号：仍然是班级结束日
30 号之后：不生成任何新课次
```

错误结果：

```text
1-15 号取消后，把 16-30 号的老师轮换重新计算
或者把结束日延长到 31 号之后
或者补造 15 节新课
```

### 2.2 禁止事项

班课假期处理中禁止：

1. 修改 `class_group.end_date`；
2. 修改 `class_group.schedule_base_end_date`；
3. 修改 `class_enrollment.expiry_date`；
4. 在班级原结束日之后生成课次；
5. 重新计算假期外课次的老师/科目/教室；
6. 因为假期日期而改变老师 A/B 轮换次序。

### 2.3 允许事项

1. 将假期范围内的课次标记为 `CANCELLED`；
2. 记录 `course_holiday_id`；
3. 记录 `holiday_previous_status`；
4. 删除/缩短假期时恢复对应课次；
5. 未来生成课次时跳过假期日期。

---

## 3. 1 对 1 业务规则

### 3.1 示例

原排课：

```text
1 号 ～ 30 号，每天一节课
```

假期：

```text
1 号 ～ 15 号
```

正确结果：

```text
1-15 号：私教课次取消，不扣课时
16-30 号：保持原样
30 号之后：补 15 节
课包预计结束日：顺延到补课结束
```

### 3.2 补课规则

1. 每个被取消的私教课次生成一条 `MAKEUP`；
2. 沿用原课次的：
   - 老师
   - 科目
   - 教室
   - weekday
   - 开始/结束时间
3. 补课排在该课位当前最后一节课之后；
4. 补课之间也要做老师、教室、学生冲突检查；
5. 不改变课包总课时、已消课时、剩余课时；
6. 更新 `course_package.estimated_end_date`。

### 3.3 与 `FORMAL_LEAVE` 的关系

保留现有 `FORMAL_LEAVE` 单课请假逻辑，但假期补课必须单独标记来源，不能和老师请假生成的补课混在一起。

建议新增：

```text
lesson_holiday_makeup_effect
```

记录：

```text
holiday_id
cancelled_occurrence_id
makeup_occurrence_id
generated_version
created_at
```

这样删除/修改假期时可以准确找到并撤销对应补课。

---

## 4. 当前代码问题定位

### 4.1 班课当前会延长结束日

`backend/src/main/java/com/classdeduction/system/modules/schedule/service/impl/ClassHolidayServiceImpl.java`

当前 `createHoliday` / `updateHoliday` / `deleteHoliday` 都会调用：

```java
synchronizeProjectClassSchedules(groupProjectId);
recalculateSessionEnrollments(groupProjectId, ...);
```

`synchronizeProjectClassSchedules()` 中：

```java
LocalDate targetEnd = ClassScheduleDates.endDateAfterHolidays(...);
group.setEndDate(targetEnd);
```

`ClassScheduleDates.endDateAfterHolidays()` 会跳过假期日期，再找第 N 个上课日，因此结束日必然往后推。

这导致班课出现：

- 结束日延长；
- 尾部补造课次；
- 老师轮换重算；
- 报名有效期延长。

### 4.2 班课轮换会被假期改变

`backend/src/main/java/com/classdeduction/system/modules/schedule/util/ClassSlotRotation.java`

当前逻辑：

```java
if (activeLineage && !isHoliday.test(date)) {
    classIndex++;
}
```

假期日期不参与轮换计数，导致 1-15 号放假设定后，16-30 号的老师 A/B 次序翻转。

按新规则，班课轮换必须按“用户配置的日期序列”计数，假期日期也要计数，只是当天不生成课次。

### 4.3 班课排课编辑也会延长结束日

`backend/src/main/java/com/classdeduction/system/modules/schedule/service/impl/ClassGroupServiceImpl.java`

`generateFutureClassOccurrences()` 当前也会调用：

```java
ClassScheduleDates.endDateAfterHolidays(...)
```

并更新 `classGroup.endDate`。

这个入口也必须改为固定使用 `class_group.end_date`。

### 4.4 1 对 1 当前完全不受假期影响

`backend/src/main/java/com/classdeduction/system/modules/schedule/service/impl/RecurringScheduleRuleServiceImpl.java`

`buildOccurrences()` 只按 `rule.startDate`、`rule.endDate`、weekday 生成，没有任何假期判断。

当前 1 对 1 只有 `FORMAL_LEAVE` 单课请假会生成尾部补课：

`backend/src/main/java/com/classdeduction/system/modules/lesson/service/impl/LessonRecordServiceImpl.java`

`buildMakeupIfNeeded()` 会创建 `MAKEUP` 课次，但不会由项目假期自动触发。

---

## 5. 班课代码修改计划（P0）

### 5.1 `ClassHolidayServiceImpl`

文件：

```text
backend/src/main/java/com/classdeduction/system/modules/schedule/service/impl/ClassHolidayServiceImpl.java
```

修改：

1. `createHoliday`
   - 删除 `synchronizeProjectClassSchedules(groupProjectId)`；
   - 删除 `recalculateSessionEnrollments(...)`；
   - 保留 `reconcileAffectedDates(...)`，只做取消。

2. `updateHoliday`
   - 删除 `synchronizeProjectClassSchedules(groupProjectId)`；
   - 删除 `recalculateSessionEnrollments(...)`；
   - 只对旧范围和新范围的差异执行取消/恢复。

3. `deleteHoliday`
   - 删除 `synchronizeProjectClassSchedules(groupProjectId)`；
   - 删除 `recalculateSessionEnrollments(...)`；
   - 只恢复被该假期取消的课次。

4. 删除以下不再使用的方法：
   - `synchronizeProjectClassSchedules`
   - `removeFutureRegularOccurrencesAfter`
   - `resolveScheduleEnd`
   - `isSessionBilling`
   - `occurrenceKey`
   - `ensureNoStudentConflict`
   - `recalculateSessionEnrollments`（私有方法）

5. 保留：
   - `previewHoliday`
   - `reconcileAffectedDates`
   - `ensureNoLessonRecord`
   - `ensureNoResourceConflict`（恢复课次时仍需要）
   - `recalculateClassSessionEnrollments`（班课编辑排课时仍使用）
   - `projectedExpiry`

6. 清理不再需要的依赖：
   - `ClassSlotRotation`
   - `ScheduleSlotTime`
   - `BusinessNoGenerator`
   - `PrivateLessonGroupMemberMapper`
   - `ClassAdjustedSlotReservations`

7. 构造器注入变化后，同步修改 `ClassHolidayServiceImplTest`。

### 5.2 `ClassSlotRotation`

文件：

```text
backend/src/main/java/com/classdeduction/system/modules/schedule/util/ClassSlotRotation.java
```

修改：

1. 移除 `isHoliday` 参数；
2. 轮换计数不再排除假期日期：

```java
if (activeLineage) {
    classIndex++;
}
```

3. 更新调用方：
   - `ClassGroupServiceImpl.generateFutureClassOccurrences`
   - `EnrollmentOrderServiceImpl.ensureClassOccurrencesThrough`
   - `ClassHolidayServiceImpl`（如果仍有残留调用）

4. 更新测试：
   - `ClassSlotRotationTest`
   - 原测试 `alternatesTeacherSubjectAndClassroomByActualLessonAfterHoliday` 改为验证“假期不改变配置轮换”。

### 5.3 `ClassGroupServiceImpl`

文件：

```text
backend/src/main/java/com/classdeduction/system/modules/schedule/service/impl/ClassGroupServiceImpl.java
```

方法：

```java
generateFutureClassOccurrences(...)
```

修改：

1. 删除 `ClassScheduleDates.endDateAfterHolidays(...)` 计算；
2. 删除对 `classGroup.setEndDate(requiredEnd)` 的更新；
3. 删除对 `classGroup.setScheduleBaseEndDate(baseEnd)` 的更新；
4. 结束日固定使用：

```java
LocalDate endDate = configuredEnd != null
        ? configuredEnd
        : resolveClassScheduleHorizon(classGroup.getId(), startDate);
```

5. 保留假期日期跳过：

```java
if (classHolidayService.isHoliday(date, holidays)) {
    continue;
}
```

6. 更新 `ClassSlotRotation.resolve(...)` 调用，移除 `isHoliday` 参数。

7. `saveClassSchedule` 仍调用 `recalculateClassSessionEnrollments(classId)`，用于 SESSION 课包覆盖校验；不允许通过假期延长结束日。

### 5.4 `ClassScheduleDates`

文件：

```text
backend/src/main/java/com/classdeduction/system/modules/schedule/util/ClassScheduleDates.java
```

修改：

1. 删除 `endDateAfterHolidays(...)`；
2. `countScheduledDays(...)` 如果只被上述方法使用，可一并删除；
3. 保留：
   - `scheduledDays`
   - `nthScheduledDay`
   - `slotAppliesOn`
   - `hasClassOn`

4. 更新测试：
   - 删除 `endDateAfterHolidays` 相关用例；
   - 保留“假期日期不生成课次”的用例。

### 5.5 SESSION 课包覆盖校验

文件：

```text
backend/src/main/java/com/classdeduction/system/modules/enrollment/service/impl/ClassBillingServiceImpl.java
```

当前 `holidayCausedSessionOverflow()` 用于报名时校验。

建议抽取一个共享工具：

```text
ClassSessionCoverage
```

提供：

```java
int availableSessionDays(ClassGroupEntity group, LocalDate start, LocalDate end,
                         List<ClassGroupScheduleSlotEntity> slots,
                         List<ClassHolidayVO> holidays);

boolean wouldShortenPurchasedSessions(...);
```

使用方：

1. `ClassBillingServiceImpl` 报名校验；
2. `ClassHolidayServiceImpl.previewHoliday` 假期预览；
3. 可选：假期保存时阻止会让在读 SESSION 学生少课的假期。

建议先做“预览警告”，是否阻止保存由业务确认。

### 5.6 班课预览

`ClassHolidayPreviewVO` 建议增加：

```text
cancelledOccurrenceCount
affectedStudentCount
sessionShortfallWarnings
```

前端提示改为：

```text
将取消 N 节课
班级结束日期不变
不会在假期后补课
```

---

## 6. 1 对 1 代码修改计划（P1，暂缓）

### 6.1 假期适用范围

当前 `course_holiday` 只绑定 `group_project_id`，私教课包无法命中。

建议扩展现有表：

```sql
ALTER TABLE course_holiday
  ADD COLUMN scope VARCHAR(24) NOT NULL DEFAULT 'GROUP_PROJECT';
```

范围值：

```text
GROUP_PROJECT
PRIVATE
ALL
```

可选匹配字段：

```text
course_product_id
grade_band
campus_id
```

现有数据默认 `GROUP_PROJECT`。

实体：

```text
backend/src/main/java/com/classdeduction/system/modules/schedule/entity/ClassHolidayEntity.java
```

增加：

```java
private String scope;
private Long courseProductId;
private String gradeBand;
private Long campusId;
```

如果暂时不做精细匹配，可以先支持：

```text
scope = ALL
```

即所有 active 私教课包都受假期影响。

### 6.2 新增私教假期服务

建议新增：

```text
backend/src/main/java/com/classdeduction/system/modules/schedule/service/PrivateHolidayAdjustmentService.java
backend/src/main/java/com/classdeduction/system/modules/schedule/service/impl/PrivateHolidayAdjustmentServiceImpl.java
```

接口：

```java
PrivateHolidayPreview previewHoliday(ClassHolidayEntity holiday);

void applyHoliday(ClassHolidayEntity holiday);

void revertHoliday(ClassHolidayEntity holiday);
```

`applyHoliday` 流程：

```text
1. 查询匹配的 active course_package
2. 查询每个课包的 active recurring_schedule_rule
3. 查询假期范围内的 REGULAR 课次
4. 跳过已开始、已有 lesson record 的课次
5. 标记取消：
   planning_status = CANCELLED
   course_holiday_id = holiday.id
   holiday_previous_status = 原状态
6. 为每个取消课次创建尾部 MAKEUP
7. 更新 course_package.estimated_end_date
```

`revertHoliday` 流程：

```text
1. 查询该假期生成的 lesson_holiday_makeup_effect
2. 删除未开始、无记录的补课
3. 恢复原课次
4. 已有记录/已开始的补课不允许自动删除
5. 重算 course_package.estimated_end_date
```

### 6.3 补课生成复用

当前 `LessonRecordServiceImpl.buildMakeupIfNeeded()` 已经实现了：

- 找当前课位尾部；
- 同 weekday、同时间；
- 跳过冲突；
- 生成 `MAKEUP`；
- 更新课包结束日。

建议抽取为共享组件：

```text
PrivateMakeupScheduler
```

方法：

```java
LessonOccurrenceEntity createTailMakeup(
        CoursePackageEntity coursePackage,
        LessonOccurrenceEntity sourceOccurrence,
        RecurringScheduleSlotEntity targetSlot,
        MakeupReason reason,
        Long holidayId);
```

`LessonRecordServiceImpl` 和 `PrivateHolidayAdjustmentServiceImpl` 共用。

### 6.4 私教排课生成跳过假期

文件：

```text
backend/src/main/java/com/classdeduction/system/modules/schedule/service/impl/RecurringScheduleRuleServiceImpl.java
```

方法：

```java
buildOccurrences(...)
```

修改：

1. 注入 `ClassHolidayService` 或 `HolidayCalendarService`；
2. 在日期循环中增加：

```java
if (isHoliday(packageId, date)) {
    continue;
}
```

3. 所有调用 `buildOccurrences` 的入口保持一致：
   - 创建规则；
   - 更新规则；
   - 续排任务；
   - 课包重算。

4. 假期取消的普通课次不能在下一次续排时被重新生成。

### 6.5 数据库迁移

新增：

```sql
CREATE TABLE lesson_holiday_makeup_effect (
  id BIGINT PRIMARY KEY,
  holiday_id BIGINT NOT NULL,
  cancelled_occurrence_id BIGINT NOT NULL,
  makeup_occurrence_id BIGINT NOT NULL,
  generated_version INT NOT NULL DEFAULT 0,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  is_deleted TINYINT(1) NOT NULL DEFAULT 0,
  UNIQUE KEY uk_holiday_cancelled (holiday_id, cancelled_occurrence_id),
  KEY idx_holiday_id (holiday_id),
  KEY idx_makeup_occurrence (makeup_occurrence_id)
);
```

可选：给 `lesson_occurrence` 增加 `makeup_reason`：

```sql
ALTER TABLE lesson_occurrence
  ADD COLUMN makeup_reason VARCHAR(32) NULL
  COMMENT 'FORMAL_LEAVE / HOLIDAY / MANUAL';
```

### 6.6 1 对 1 预览

预览返回：

```text
受影响课包数
受影响课次数
尾部补课数
预计顺延结束日
冲突列表
```

前端 1 对 1 排课页展示：

```text
1-15 号停课
16-30 号正常
30 号后补 15 节
```

---

## 7. 数据修复计划

当前线上可能存在旧逻辑造成的班课延长：

```text
class_group.end_date > class_group.schedule_base_end_date
```

修复步骤：

1. 生成审计报告：

```sql
SELECT id, class_name, group_project_id, start_date, end_date, schedule_base_end_date
FROM class_group
WHERE is_deleted = 0
  AND schedule_base_end_date IS NOT NULL
  AND end_date > schedule_base_end_date;
```

2. 检查 `schedule_base_end_date` 之后的课次：

```sql
SELECT *
FROM lesson_occurrence
WHERE class_group_id = ?
  AND planned_start_at >= ?
  AND is_deleted = 0;
```

3. 只删除满足以下条件的课次：
   - 未来课次；
   - `occurrence_type = 'REGULAR'`；
   - 无 lesson record；
   - 无人工调课记录；
   - 由旧假期延长生成。

4. 恢复：

```text
class_group.end_date = class_group.schedule_base_end_date
```

5. 重新按原配置生成周期内课次；
6. 老师轮换按“配置日期序列”计算，不排除假期；
7. 输出修复前后对比；
8. 保留回滚 SQL。

不确定的班级不要自动删除，先人工确认。

---

## 8. 测试计划

### 8.1 班课

1. 中间一天假期：
   - 当天课次取消；
   - 前后课次日期、老师、科目、教室不变；
   - 结束日不变；
   - 不生成新课次。

2. 1-15 号全假：
   - 1-15 号全部取消；
   - 16-30 号完全不变；
   - 30 号仍是结束日。

3. 删除假期：
   - 1-15 号恢复；
   - 16-30 号不变；
   - 不产生额外课次。

4. 修改假期范围：
   - 只取消新增日期；
   - 只恢复移除日期。

5. 已开始/已有记录：
   - 不允许取消。

6. 老师轮换：
   - 假期不改变 16-30 号老师 A/B 顺序。

7. 多校区、多班级：
   - 全部按各自周课表跳过假期。

8. MONTH 报名：
   - 有效期不变。

9. SESSION 报名：
   - 预览正确提示是否少课；
   - 按确认后的策略阻止或警告。

10. 历史延长班级：
    - 修复后结束日回到原周期。

### 8.2 1 对 1

1. 单日假期：
   - 当天课取消；
   - 尾部补一节；
   - 课包结束日顺延。

2. 1-15 号全假：
   - 1-15 号取消；
   - 16-30 号不变；
   - 30 号后补 15 节。

3. 共享私教：
   - 只取消一次、补一次；
   - 所有学生冲突检查通过。

4. 删除假期：
   - 补课删除；
   - 原课次恢复；
   - 课包结束日重算。

5. 补课冲突：
   - 自动找下一个可用周次；
   - 找不到则报错。

6. 已上课/已有记录：
   - 禁止自动调整。

7. 续排任务：
   - 不会重复生成假期取消的课次或补课。

---

## 9. 上线顺序

### 第一步：班课修复（P0）

1. 修改 `ClassHolidayServiceImpl`；
2. 修改 `ClassSlotRotation`；
3. 修改 `ClassGroupServiceImpl`；
4. 清理 `ClassScheduleDates`；
5. 更新班课测试；
6. 前端假期提示改为“只取消，不顺延”；
7. 生产数据审计；
8. 执行修复脚本；
9. 部署验证。

### 第二步：1 对 1 假期（P1，暂缓）

1. 增加 `course_holiday.scope`；
2. 新增 `lesson_holiday_makeup_effect`；
3. 新增 `PrivateHolidayAdjustmentService`；
4. 抽取 `PrivateMakeupScheduler`；
5. 私教生成逻辑跳过假期；
6. 前端私教假期/补课展示；
7. 测试与部署。

### 第三步：SESSION 策略（可选）

根据业务确认：

- 阻止会让在读 SESSION 学生少课的假期；或
- 允许假期并记录未消耗节数，提供退费/结转/补偿入口。

---

## 10. 验收标准

### 班课

- [ ] 假期只取消假期范围内课次。
- [ ] 假期外课次日期、老师、科目、教室、时间完全不变。
- [ ] 班级结束日不变。
- [ ] 报名有效期不变。
- [ ] 原结束日之后不生成课次。
- [ ] 删除假期只恢复原课次，不重排其他课次。

### 1 对 1

- [ ] 假期范围内课次取消且不扣课时。
- [ ] 假期外课次不变。
- [ ] 每个取消课次在尾部生成一条补课。
- [ ] 课包预计结束日顺延。
- [ ] 删除假期可撤销补课并恢复原课次。
- [ ] 补课冲突检查完整。

---

## 11. 班课假期场景与测试覆盖

已新增 MySQL 集成测试：

`backend/src/test/java/com/classdeduction/system/modules/schedule/service/ClassHolidayMySqlIntegrationTest.java`

默认不运行，执行方式：

```bash
cd backend
CLASS_HOLIDAY_MYSQL_TEST=1 mvn test -Dtest=ClassHolidayMySqlIntegrationTest
```

覆盖场景：

| 场景 | 验证点 |
|---|---|
| 单日假期 | 只取消当天，前后课次和结束日不变 |
| 多日假期 | 取消范围内每个上课日 |
| 整月假期 | 整月课次取消，不延长结束日，不生成尾部课次 |
| 删除假期 | 原课次恢复，假期引用清空，结束日不变 |
| 延长假期 | 新增日期被取消 |
| 缩短假期 | 移出范围的日期恢复 |
| 非上课日假期 | 不产生任何课次变化 |
| 项目多班级/多校区 | 项目内所有班级都取消 |
| 报名有效期 | SESSION 报名 `expiry_date/status` 不变 |
| 预览 | 正确统计受影响课次数，无冲突 |
| 已开始课次 | 拒绝保存假期 |
| 已有消课记录 | 拒绝保存假期 |
| 假期外课次 | 老师、科目、教室保持原样 |
| 多个不重叠假期 | 两个假期都生效 |
| 重叠假期 | 拒绝保存 |
| 过去日期 | 拒绝新建假期 |
| 班级结束日之后 | 不产生课次变化 |
| 手工取消课次 | 删除假期时不恢复手工取消 |
| ADJUSTED 课次 | 假期取消后恢复为 ADJUSTED |

测试结果：19 个集成测试全部通过。

### 线上快照复现

使用本地 `jin_prod_snapshot_20261029_1625` 的临时副本，复现 G6-G8 项目添加 `Election Day / 2026-11-03`：

- 假期当天 5 个课次被取消；
- 5 个课次都正确写入 `course_holiday_id`；
- 6 个班级的 `end_date` 和 `schedule_base_end_date` 全部保持不变；
- 原结束日之后没有新增课次；
- 没有再出现 `Restoring or extending this class schedule would conflict with another lesson`。

复现测试：

`backend/src/test/java/com/classdeduction/system/modules/schedule/service/ClassHolidaySnapshotReproTest.java`

默认不运行；需要先把线上快照复制到本机临时库，再通过环境变量指定临时库连接。

测试过程中发现并修复了一个实际缺陷：

> 删除假期恢复课次时，`updateById` 默认忽略 null 字段，导致 `course_holiday_id` 和 `holiday_previous_status` 没有被清空。

已改为使用 `LambdaUpdateWrapper` 显式 `set(..., null)`，确保删除/缩短假期后课次真正恢复。

---

## 12. 已确认业务点

1. **班课 SESSION 课包遇到假期少课时：允许假期，不阻止。**  
   已购 SESSION 可能出现未使用节数，由管理员按退款/补偿流程人工处理；系统不自动延长班课结束日。

2. **1 对 1 假期功能暂缓。**  
   当前没有 1 对 1 假期入口，私教排课暂不接入假期；后续实现时采用“跳过 + 尾部补课 + 课包结束日顺延”。

3. **删除已经过去的假期：只恢复未来课次。**  
   已过去的课次不自动恢复为待上课状态，避免制造历史异常。
