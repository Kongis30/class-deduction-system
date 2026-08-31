# Feature Specification: 报名一对一赠送课时 Bonus Hours

**Feature Branch**: `002-registration-bonus-hours`
**Created**: 2026-08-31
**Status**: Draft
**Input**: 用户在「报名」页的一对一课程行加入一个 Bonus hours 输入框，允许录入赠送课时；赠送课时并入课时包总课时（totalUnits），不新增数据库列、不新增课时流水明细。

## 背景与目标

### 希望解决的问题

一对一报名目前只能填「购买小时数（Package hours）」。运营有时需要给学员额外赠送课时（例如试听补偿、老生回馈），现行做法需报名完成后再手工调课时包，流程割裂且易漏操作。

### 目标

在报名页的一对一课程行加入「Bonus hours」输入框，报名时即可直接录入赠送课时：
- 赠送课时作为课时包 `totalUnits` 的一部分，直接放大可消费总课时；
- 赠送课时不影响课程行单价计算（仅影响课时总量与剩余课时）；
- 不新增表、不新增字段、不新增课时流水明细（沿用现有 make 总课时逻辑）。

## 核心决策（已确认）

1. **上限**：赠送小时数上限与购买同，为 `0–400` 小时，`0.5` 小时步进。
2. **账务方式**：并入 `totalUnits`（`1 unit = 0.5h`），**不**新增数据库列、**不**新增流水行、**不**做迁移。赠送产生的总课时直接回落，后续任一课时扣减均落在课包余额上。
3. **校验**：赠送小时数必须为非负、0–400、0.5 步进，否则拒绝提交。

## 数据模型

无变更。课时包 `totalUnits` 的计算由「仅购买」调整为「购买 + 赠送」：

```
totalUnits   = purchasedUnits(oneOnOneHours) + bonusUnits(oneOnOneBonusHours)
consumedUnits = openingUsedUnits(openingUsedHours)
remainingUnits = totalUnits - consumedUnits
```

一处 `总课时` 的计算即可贯穿创建课包、快照、预览剩余课时。`EnrollmentOrderItemDTO.oneOnOneBonusHours` 为请求体可选字段；是否持久化到 `enrollment_order_item` 表不强制（现有 oneOnOneUnits 仍按总课时写）。

## API 契约变更

`POST /api/v1/enrollment/registrations`（报名 intake）的 `items[]` 增加可选字段：

| 字段 | 类型 | 说明 | 示例 |
------|------|------|------|
| `oneOnOneBonusHours` | number | 赠送小时数，0–400，0.5 步进 | `2` |

响应与预览（`preview`/`openingRemainingHours`）同步反映总课时与剩余课时；预览新增 `oneOnOneBonusHours` 回传。

## 边界情况

| 场景 | 行为 |
|------|------|
| 赠送为空/0 | 与原逻辑一致，等价于无赠送 |
| 赠送为负 | 拒绝，返回 `ONE_ON_ONE_BONUS_HOURS_INVALID` |
| 赠送 >400 或非 0.5 步进 | 拒绝，返回 `ONE_ON_ONE_BONUS_HOURS_INVALID` |
| 已用小时 > 含赠送的总课时 | 拒绝，报错文案改为「total package hours (purchased + bonus)」 |
| 仅赠送、无购买 | 购买小时必须 ≥0.5，因此不允许「纯赠送无购买」 |
| 前端 UI | Bonus hours 输入达到上限时，Already used 上界自动扩大为「购买+赠送」 |

## 受影响模块

- 后端：`RegistrationIntakeServiceImpl`（校验、totalUnits 计算、预览回传）、`EnrollmentOrderItemDTO`（新字段）。
- 前端：`RegistrationIntakeView.vue`（新增 Bonus hours 输入框、接口字段、校验、payload、打印/汇总标签展示总课时）。
- 测试：`RegistrationIntakeServiceImplTest` 新增「totalUnits 含赠送」「已用小时对总课时上界」「负赠送拒绝」「非 0.5 步进拒绝」。
- 文档：`doc/新需求改动/课程报名与收费机制设计.md` §5.5 已确认该方案。

## 验收标准

1. 报名页一对一课程行出现「Bonus hours」输入框，默认 0，0–400，0.5 步进。
2. 提交后课包 `totalUnits = (购买+赠送)*2`，`remainingUnits` 相应放大。
3. 已用小时上界随购买+赠送扩大；非法赠送被拒绝。
4. 打印/汇总标签显示「购买 + 赠送」每小时数；预览剩余课时随赠送变大。
5. 既有一对一报名（无赠送）行为完全不变。
