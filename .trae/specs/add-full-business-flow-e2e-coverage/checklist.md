# Checklist

* [x] 已确认现有 course product、enrollment、course packages、schedule/confirm、teacher payments E2E 覆盖边界

* [x] 已提供跨模块 E2E 数据准备、查询、清理 helper，支持订单、课包、课次、消课记录、老师付款数据校验

* [x] 已支持管理员与老师角色在 E2E 中登录和切换

* [x] 1对1完整链路覆盖：创建课程产品、报名、排课、生成课包和课次

* [x] 1对1完整链路覆盖：老师初确认后进入 PENDING\_ADMIN，且课包余额未扣减、未进入老师待支付列表

* [x] 1对1完整链路覆盖：管理员终确认后进入 CONFIRMED，且扣课、写台账、进入老师待支付

* [x] 1对1完整链路覆盖：Payments 页面完成老师付款，付款后待支付列表更新

* [x] 班课完整链路覆盖：创建班课产品、报名、生成班级、班课进度和学生购买课次

* [x] 班课完整链路覆盖：班课消课确认后学生进度、课次状态、计薪资格一致

* [x] 已覆盖正常完成、请假、No Show 类结果中当前系统支持的关键分支

* [x] 已覆盖管理员驳回老师初确认，校验不扣课、不计薪；重新处理入口已发现并锁定 UI 风险

* [x] 已覆盖重复确认、重复付款、关闭课包后继续操作、排课冲突、余额不足等非法操作中的主要场景

* [x] 所有新增跨页 E2E 都包含 UI 与 API/DB 双重数据校验

* [x] 新增跨页 E2E 能稳定清理测试数据，不污染后续运行

* [x] 新增全链路 E2E spec 单独运行通过

* [x] course product、enrollment、schedule/confirm、payments 相关 E2E 全量运行通过

* [x] `frontend/e2e/ui/README.md` 已说明全链路业务覆盖范围

* [x] 管理员驳回老师初确认后，用例会尝试老师从 UI 重新初确认；若当前 UI 入口缺失，则记录 known-ui-gap，并通过老师 API 推进后续状态，继续校验管理员终确认、扣课与计薪闭环

* [x] 本周临时调课 E2E 覆盖调整时间、老师、教室或课时，且校验 recurring rule 不被修改

* [x] 本周临时调课 E2E 覆盖多上、少上、课时不变提示和 adjustment log

* [x] 补课/顺延 E2E 覆盖请假或取消后的补课创建、补课完成、课包预计结束或余额变化

* [x] 调课/补课冲突与不可修改状态被拒绝，且后端数据不变

* [x] Pay Rate E2E 覆盖设置、修改费率并影响后续待支付金额

* [x] 零费率教师付款 E2E 覆盖 `Zero Rate Warning` 和继续/阻止后的数据结果

* [x] 跨周期教师付款 E2E 覆盖按周期筛选课次和创建付款

* [x] Payment History E2E 覆盖 Pending/Paid 筛选、Detail 明细、Confirm 后状态变化

* [x] 历史付款不被 Pay Rate 后续修改反写，已付款课次不会重复进入待支付

* [x] 新增增量 E2E 单独运行通过，现有 business-flow、enrollment、course product 回归通过
