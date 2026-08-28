# 课包状态生命周期修复方案（1-on-1 学生列表过滤）

> 状态：待用户确认（v2，已按用户反馈改为"事件驱动、即时转换"，移除定时任务方案）
> 关联需求：1-on-1 Lessons 左侧学生列表只应显示"有进行中课程"的学生

---

## 一、背景与目标

1-on-1 Lessons 页面（`OneOnOneDeductionView.vue`）左侧学生列表，其数据源 `listOneOnOneDeductions`
（`LessonOccurrenceServiceImpl:579`）**已经在按 `packageStatus = "ACTIVE"` 过滤**（第 601 行），过滤逻辑本身正确。

问题出在 **`course_package.package_status` 数据没有可靠维护**，导致已停课/完结的课包仍是 `ACTIVE`，
从而出现在左侧列表里。用户目标：**`ACTIVE` 必须准确代表"进行中的课程"**。

用户已明确两条设计决策：
1. 停课的课包确认遗留课次时，**允许确认但保持 PAUSED**；
2. 课时完结应**自动即时**转为非 `ACTIVE`，**不用定时任务**（"定时任务不够及时"）。

现状（本地库 12 个课包全部为 ACTIVE）：

| 类别 | 数量 | 说明 |
|---|---|---|
| 2024 年的 6 个课包（PKG-2024-001~006） | 6 | `estimated_end_date` 均在 2024 年（2 年前），课程早已完结 |
| 2026-05-23 新建 4 个测试课包 | 4 | 0 消课、0 课次，从未启用 |
| 正常进行中 | 2 | Math 1:1（已消 44 课时）、pack11（已排课） |

## 二、问题分析

### 2.1 停课（PAUSED）的课包可被"悄悄复活" —— 真实逻辑缺陷

- 停课操作本身正确：`pausePackage`（`CoursePackageServiceImpl:680`）→ PAUSED，并取消未来课次、暂停排课规则。
- **但存在复活路径**：
  1. `requirePackage`（`LessonRecordServiceImpl:1301`）只拦截 `DRAFT`、`CLOSED`，**未拦截 `PAUSED`**；
  2. `cancelFutureUnrecordedOccurrences`（`CoursePackageServiceImpl:844`）只取消"未来"课次（`planned_start_at >= now`），
     过去未确认的课次仍保留为可确认状态；
  3. 在 1-on-1 页面确认这些遗留课次时，`applyPackageAndLedgerChanges`（`LessonRecordServiceImpl:784`）
     **无条件把状态改回 `ACTIVE`**。
- 结果：停课的课包一经确认遗留课次即变回 ACTIVE，重新出现在左侧列表。

### 2.2 完结的课包无即时自动转换 —— 设计缺口

- 课时扣完（`remaining_units` 归零）时确认课次 → 置为 `COMPLETED` ✅（此路径已即时生效）
- **但课程完结、课时未用完的课包没有任何即时转换机制**，永远停在 `ACTIVE`：
  - 一个课包按部就班上完所有已排课次后（`estimated_end_date` 随之失效），若课时未扣完（如请假、缺课、
    剩余课时），状态仍是 `ACTIVE`；
  - 课包没有任何"排课耗尽即视为完结"的收敛逻辑，只能等管理员手动去 CoursePackages 页点 Close。
- 这就是 6 个 2024 年课包长期停留在 ACTIVE 的根本原因（历史遗留 + 无收敛逻辑）。
- 定时任务方案已被用户否决：完结应发生在"课程结束"事件发生的**同一时刻**，而不是每日轮询。

### 2.3 取消（CANCELLED）—— 已正确，无需改动

- 报名单 Cancel / Delete / Force-Void → `cancelOneOnOneEnrollment`（`EnrollmentOrderServiceImpl:1215`）→ 课包置为 `CLOSED` ✅

## 三、修复方案（事件驱动，无定时任务）

### 3.1 停课保持：确认课次不改变 PAUSED 状态

**文件**：`backend/.../modules/lesson/service/impl/LessonRecordServiceImpl.java`
**位置**：`applyPackageAndLedgerChanges` 第 784 行

现状：
```java
coursePackage.setPackageStatus(packageCompleted ? "COMPLETED" : "ACTIVE");
```

改为：
```java
String currentStatus = coursePackage.getPackageStatus();
if (packageCompleted) {
    coursePackage.setPackageStatus("COMPLETED");
} else if ("ACTIVE".equals(currentStatus) && isCourseScheduleExhausted(coursePackage.getId())) {
    // 课程排课已耗尽（无未来课次、无活跃排课规则、且已上过课）→ 课程完结，即时转 COMPLETED
    coursePackage.setPackageStatus("COMPLETED");
}
// 其余状态（PAUSED 等）保持不变：确认遗留课次不再把 PAUSED 改回 ACTIVE
```

说明：
- `remaining_units` 归零 → 仍置为 `COMPLETED`（课时耗尽，优先级最高）；
- 原状态为 `PAUSED` → 保持 `PAUSED`（**PAUSED 优先于"排课耗尽"判断**，避免停课课包被误判为完结）；
- 原状态为 `ACTIVE` 且排课耗尽 → 即时置为 `COMPLETED`（解决 2.2 的设计缺口）；
- 超扣防护已存在（第 321 行 `INSUFFICIENT_REMAINING_UNITS`），`newRemainingUnits` 不会为负，无需改动；
- `requirePackage` 保持不变：仍允许 PAUSED 课包确认课次（过去的课确实上过，需要记录）。

**新增私有辅助方法** `isCourseScheduleExhausted(Long packageId)`：
```text
1. 存在未来非取消课次（planned_start_at >= now 且 planning_status != 'CANCELLED'）→ 返回 false（课程仍在排课）
2. 存在 rule_status = 'ACTIVE' 的重复排课规则 → 返回 false（规则会继续生成课次，课程仍在进行）
3. 无任何 confirm_stage = 'CONFIRMED' 的消课记录 → 返回 false（从未上过课，属于"新报名尚未排课"，不自动完结）
4. 否则 → 返回 true（课程排课已耗尽，且确实上过课，判定完结）
```

> 第 3 条用于区分"课程上完了"与"新报名还没排课"，避免误完结从未启用的课包。

### 3.2 课时调整到 0：即时转 COMPLETED

**文件**：`backend/.../modules/enrollment/service/impl/CoursePackageServiceImpl.java`
**位置**：`adjustPackageHours` 第 656-659 行

现状：只有 `newRemainingUnits > 0` 且原状态为 `COMPLETED` 时才回转为 ACTIVE；`newRemainingUnits == 0` 时**不动状态**，
导致课时被调整为 0 的课包仍是 ACTIVE。

改为：
```java
if (newRemainingUnits == 0) {
    coursePackage.setPackageStatus("COMPLETED");
    coursePackage.setCompletedAt(AppTime.nowLocalDateTime());
} else if (newRemainingUnits > 0 && "COMPLETED".equals(coursePackage.getPackageStatus())) {
    coursePackage.setPackageStatus("ACTIVE");
    coursePackage.setCompletedAt(null);
}
```

说明：`newRemainingUnits` 已由第 651 行保证非负，此处 `== 0` 即转 COMPLETED。

### 3.3 本地旧数据手动改正

对 6 个 2024 年完结课包执行一次性数据修正：

```sql
UPDATE course_package
SET package_status = 'COMPLETED', updated_at = NOW()
WHERE id IN (12001, 12002, 12003, 12004, 12005, 12006)
  AND package_status = 'ACTIVE';
```

> 说明：
> - 选择 `COMPLETED` 而非 `CLOSED`：与 3.1 自动完结的语义一致（课程自然完结，可恢复）；`CLOSED` 保留给
>   订单取消、手动 Close 等"管理性终止"场景。如需终态不可恢复，可改为 `CLOSED`，仅一处改动。
> - 这 6 个课包若在 3.1 上线后重新触发确认/调整事件，也会被同一逻辑收敛；手动改是为了立即生效。
> - 4 个 2026 年测试课包（从未启用、`estimated_end_date` 为 NULL）不在本次范围，由管理员按需手动关闭。

## 四、验收标准

1. **停课保持**：课包 PAUSED 后，在 1-on-1 页面确认遗留的过去课次，课包仍为 PAUSED，不再出现在左侧列表；
   课时恰好扣完时置为 COMPLETED。
2. **排课耗尽即时完结**：ACTIVE 课包在确认最后一节已排课次（无未来课次、无活跃排课规则、已上过课）后，
   **立即**置为 COMPLETED，从左侧列表消失，无需等待任何定时任务。
3. **课时调整到 0**：`adjust-hours` 把课时调整为 0 → 立即置为 COMPLETED。
4. **旧数据**：6 个 2024 年课包手动置为 COMPLETED 后，其学生从 1-on-1 左侧列表消失。
5. **正常课包不受影响**：Math 1:1、pack11 等仍有未来课次或活跃排课规则的课包保持 ACTIVE 并继续显示；
   4 个从未启用的测试课包保持 ACTIVE（不会被误判完结）。
6. **回归**：`mvn test` 全部通过；前端 1-on-1 页面加载正常。

## 五、影响范围与边界

- **影响文件**：
  - `LessonRecordServiceImpl.java`（改 1 处状态逻辑 + 新增 1 个私有辅助方法 + 注入 `RecurringScheduleRuleMapper`）
  - `CoursePackageServiceImpl.java`（`adjustPackageHours` 补 0 课时完结逻辑）
  - 本地库执行 1 条 UPDATE（旧数据修正）
- **不影响**：报名/排课/消课主流程、取消/退款逻辑、前端页面结构；**不新增定时任务**。
- **风险**：
  - 自动完结为 `COMPLETED`，**可恢复**：若误判，管理员通过"调整课时"（`adjustPackageHours` 会把
    COMPLETED 且有剩余课时者转回 ACTIVE）即可恢复，不会造成不可逆后果。
  - 排课耗尽判断含"已上过课"前置条件，新报名尚未排课的课包不会被误完结。
- **已知边界**（不纳入本次改动）：
  - 通过"排课调整"直接取消最后一节课次、而非走消课确认的路径，不触发 3.1 的完结判断；
    此类课包由管理员在 CoursePackages 页手动关闭（现有功能），后续如需可再加收敛点。
