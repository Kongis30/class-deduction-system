# 报名单删除功能规格（Enrollment Order Delete）

> 状态：**已确认**（用户确认"全量级联软删，按规格"）
> 范围：Enrollments 页面（`EnrollmentOrdersView.vue`）表格删除按钮 + 后端 `deleteOrder` 完整实现
> 依据：用户已确认三项决策 —— ① 软删除；② 已核销数据一并软删；③ 班课报名也支持删除。在"全量级联软删 vs 仅未付款可删"之间，用户选择**全量级联软删**。
> 对齐现状：origin/online 已有保守版 `deleteOrder`（仅未付款可删）+ 前端删除按钮（10s 倒计时确认）。本规格将其升级为全量级联软删，并补充 `enrollment_order_item`、班课课时核销数据的级联。

## 1. 需求背景

origin/online 的 `DELETE /api/v1/enrollment/orders/{id}` 为保守实现：`validateOrderCanBeDeleted`
限制仅 `UNPAID + 无收款` 订单可删，且仅"取消未来排课 + 撤班/关包 + 软删订单"，**不级联删除
已核销数据**（`lesson_record`、`hour_ledger`、`payment_record`、`receipt_record` 保留为孤儿）。
前端已具备删除入口（1:1 与班课两个表格 + 10s 倒计时确认框），但 `canDeleteOrder` 同样限制
仅未付款订单可点。

本功能按已确认的**全量级联软删**语义重写后端 `deleteOrder`，并放开前端删除限制与确认文案。

## 2. 业务语义（已确认）

| 决策项 | 结论 |
|---|---|
| 整体语义 | **软删除**：所有关联记录标记 `is_deleted=1`，页面不再显示，数据保留可审计/可恢复 |
| 删除范围 | **全量级联**：任意状态订单（CONFIRMED / CANCELLED / 已付款）均可删，子表数据一并软删 |
| 已核销数据 | **一并软删**：`lesson_record`、`hour_ledger` 随报名一起软删（1v1 与班课学生个人课时均适用） |
| 班课报名 | **支持删除**：撤销该学生的 `class_enrollment` 在册记录，软删该学生个人的课时核销记录，**不删班级共享排课**（其他学生不受影响） |
| 教师工资 | **不动**：`teacher_payment` / `teacher_payment_item` 无软删字段且被已结算工资引用，保留；`lesson_record` 软删后行仍在，外键不破 |
| 班级共享数据 | **不动**：`lesson_occurrence`（挂 `class_group_id`）、`class_group` 及其排课槽共享给全班级，不随单删 |

## 3. 连带删除范围

### 3.1 1v1 报名单（`class_id IS NULL`）

| 表 | 处理方式 | 删除键 |
|---|---|---|
| `enrollment_order` | 软删 | id |
| `enrollment_order_item` | 软删 | enrollment_order_id |
| `course_package` | 软删 | enrollment_order_id（兼容 legacy 直连与 item 模型） |
| `package_teacher_assignment` | 软删 | package_id（上一步收集） |
| `recurring_schedule_rule` | 软删 | package_id |
| `recurring_schedule_slot` | 软删 | rule_id（上一步收集） |
| `lesson_occurrence` | 软删 | package_id |
| `lesson_adjustment_log` | **物理删**（表无 is_deleted） | occurrence_id（上一步收集） |
| `lesson_record` | 软删 | package_id |
| `hour_ledger` | 软删 | package_id OR lesson_record_id（上一步收集） |
| `package_balance_snapshot` | **物理删**（表无 is_deleted，PK = package_id） | package_id |
| `notification_log` | 软删 | package_id OR lesson_record_id（上一步收集） |
| `payment_record` | 软删 | enrollment_order_id |
| `receipt_record` | 软删 | enrollment_order_id |
| `teacher_payment` / `teacher_payment_item` | **不处理** | — |

### 3.2 班课报名单（`class_id IS NOT NULL`）

| 表 | 处理方式 | 删除键 |
|---|---|---|
| `enrollment_order` | 软删 | id |
| `enrollment_order_item` | 软删 | enrollment_order_id |
| `class_enrollment` | 软删（status→WITHDRAWN, is_deleted=1） | enrollment_order_item_id（item 模型）**或** class_id + student_id（legacy 直连），不限定 status，去重 |
| `lesson_record`（学生个人） | 软删 | occurrence_id IN（class_group_id = order.class_id 的 occurrence）AND student_id = order.student_id |
| `hour_ledger` | 软删 | class_enrollment_id OR lesson_record_id（上一步收集；当前数据无此类行，为将来 class 课时接线预留） |
| `notification_log` | 软删 | lesson_record_id（上一步收集） |
| `course_package` | 软删（当前班课订单为 0，item 模型未来可能产生） | enrollment_order_id |
| `payment_record` | 软删 | enrollment_order_id |
| `receipt_record` | 软删 | enrollment_order_id |

班课共享的 `lesson_occurrence`（挂 `class_group_id`）与 `recurring_schedule_rule/slot` **不删除**；
只删该学生个人的 `lesson_record` / `hour_ledger`。当前数据存在 49 条班课学生 lesson_record（package_id 为空，
可经 occurrence→class_group_id + student_id 归因），删除后一并软删，符合"已核销数据一并软删"决策。

## 4. 删除顺序与事务

整个删除在单个 `@Transactional(rollbackFor = Exception.class)` 方法内完成，顺序为
"子表 → 父表"（先删叶子数据，最后软删 `enrollment_order`）。软删不破坏外键（行保留），
两处物理删（`lesson_adjustment_log`、`package_balance_snapshot`）均为叶子表，无下游引用。
任一环节抛异常整体回滚。

## 5. 保护与边界情况

- 订单不存在 / 已软删 → 抛 `ENROLLMENT_ORDER_NOT_FOUND`（MyBatis-Plus 逻辑删除自动过滤 is_deleted=1）
- 重复删除同一单 → 第二次查不到，抛 not found，幂等安全
- 有核销流水 / 教师工资引用的订单 → 正常软删，外键均有效；教师工资历史保留
- 班课订单删除只软删该学生个人的课时核销记录，不触碰班级其他学生的排课与在册记录
- 乐观锁 `@Version` 冲突时由 MyBatis-Plus 抛异常，事务回滚
- `enrollment_order_item` 当前为 0 行（legacy 数据），级联逻辑对 item 模型做防御式兼容，未来数据自然覆盖

## 6. 后端改动清单

`EnrollmentOrderServiceImpl.java`：

1. 重写 `deleteOrder(Long id)`：
   - 分支：`order.classId == null` → 1v1 级联；否则班课级联
   - 最后统一 `enrollmentOrderMapper.deleteById(id)` 软删父表
   - **删除** `validateOrderCanBeDeleted` 及其三个校验方法（不再限制仅未付款可删）
2. 注入新依赖（lesson / schedule / systemconfig 模块）：
   - `HourLedgerMapper`（lesson）
   - `PackageBalanceSnapshotMapper`（lesson）
   - `LessonAdjustmentLogMapper`（schedule）
   - `NotificationLogMapper`（systemconfig）
   - `LessonRecordMapper` 已注入，无需新增
3. 新增私有辅助方法：
   - `deleteOneOnOneOrderCascade(order, itemIds)` — 按 §3.1 级联
   - `deleteOneOnOnePackageCascade(packages)` — 包及其下游（teacher assignment / 排课 / 课时 / 流水 / 快照 / 通知）
   - `deleteClassOrderCascade(order, itemIds)` — 按 §3.2 级联
   - `deleteOrderFinancialArtifacts(order, itemIds)` — items / 支付 / 收据（两分支共用）
   - `collectClassEnrollments(order, itemIds, onlyActive)` — 收集班课在册（兼容两模型，去重）
   - `markClassEnrollmentsWithdrawn(enrollments)` — 置 WITHDRAWN + is_deleted=1
4. 重构 `cancelClassEnrollment` 复用上述收集/撤销辅助（`onlyActive=true`），供 Cancel / ForceVoid 流程沿用（行为不变）

## 7. 前端改动清单

`EnrollmentOrdersView.vue`（删除 UI 已存在，仅改限制与文案）：

1. `canDeleteOrder(row)`：由 `paymentStatus==='UNPAID' && receivedAmount===0` 改为 **恒 true**（任意订单可删）
2. `canForceVoidOrder(row)`：由 `isAdmin && !canDeleteOrder && !canCancelOrder` 改为 `isAdmin && !canCancelOrder`，
   避免全量删除放开后 ForceVoid 入口消失；仍仅管理员可见，用于"保留财务/消课历史"的清理
3. 删除确认框文案更新，说明全量级联删除范围（保留 10s 倒计时防误触）
4. 确认删除成功后刷新列表逻辑不变（1v1 → `loadData`；班课 → `loadExpandedClassOrders` + `loadClassEnrollments`）

### 确认框文案

```
Delete enrollment order {enrollmentNo}?

This removes the order from the list together with its course package,
scheduled lessons, confirmed lesson records, hour ledger, and payment/receipt
records. For class orders the student's seat and personal lesson records are
also removed; the shared class schedule is untouched.
Data is soft-deleted and retained for audit, but no longer displayed.
This action cannot be recovered through the UI.
```

## 8. 验证清单

**后端（编译 + 单测或接口调用）**
- [ ] `mvn -q compile` 通过，类型检查无警告
- [ ] 1v1 订单删除后：enrollment_order / enrollment_order_item / course_package / payment_record /
      receipt_record / package_teacher_assignment / recurring_schedule_rule / recurring_schedule_slot /
      lesson_occurrence / lesson_record / hour_ledger / notification_log 的 `is_deleted=1`
- [ ] `package_balance_snapshot`、`lesson_adjustment_log` 对应行物理消失
- [ ] 班课订单删除后：`class_enrollment` status=WITHDRAWN + is_deleted=1，该学生个人 lesson_record 软删，
      班级共享 lesson_occurrence 数量不变，其他学生 lesson_record 不变
- [ ] 删除不存在的订单抛 not found；重复删除幂等安全
- [ ] 有核销流水/教师工资引用的订单删除后，外键无残留破坏

**前端**
- [ ] 1:1 与班课两个表格均出现 Delete 按钮（任意状态订单）
- [ ] 确认框文案、10s 倒计时、取消、确认、失败提示均正常
- [ ] 删除成功刷新列表，被删订单不再显示

## 9. 不做的事（Out of Scope）

- 不做物理删除 / 不可恢复删除
- 不改 `teacher_payment` / `teacher_payment_item`（教师工资历史）
- 不动 `student`、`class_group` 主数据，不删班级共享 `lesson_occurrence` / 排课
- 不改 API 契约（`DELETE /api/v1/enrollment/orders/{id}` 已存在）
- 不改 `forceVoidOrder` 的业务语义（保留财务/消课历史），仅调整其前端可见性条件
