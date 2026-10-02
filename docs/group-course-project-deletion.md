# 教学项目删除与归档规则

## 当前入口与调用链

Prices 的 Group Class 项目卡片调用 `deleteGroupCourseProject(projectId)`，请求 `DELETE /v2/group-course-model/projects/{projectId}`，由 `GroupCourseModelServiceImpl.deleteProject` 决定删除还是停用。每个项目出现在当前 Prices 列表后，Delete 按钮先显示 10 秒倒计时；倒计时结束后才可打开确认框。冷静时间只在前端生效，服务端仍执行关联检查。

## 当前服务端行为

服务端检查同一项目下未软删除的报名方案、订单项目、实际班级和假期：

- 只要任一类关联记录存在，项目的 `status` 设为 `INACTIVE`。不删除这些关联记录，也不改变它们自己的状态。
- 没有上述关联记录时，调用 MyBatis `deleteById`。项目实体启用了逻辑删除，因此实际效果是将项目设为 `is_deleted = 1`，不是从数据库物理删除。
- 两条路径都不会级联删除报名方案、实际班级、班级周排课、假期、订单、报名权益、收据或课次记录。

项目停用后，报名服务会拒绝该项目的新报名，因为它要求项目和方案都为 `ACTIVE`。现有订单和财务历史保留。实际班级和已有排课/课次也保持原状态；删除项目不会替用户取消课程或释放老师、教室时段。

## 删除为何不能直接级联

订单项目和收据是财务事实；报名权益、已确认课次和消课记录是学生上课与扣课依据。物理清除这些数据会破坏退款、收据查询、课时核对和审计链路。若项目仍有学生在读，直接取消班级排课还会影响已购买的服务。因此项目删除不能默认为删除所有下游数据。

## 完整性评估

目前“保留历史、有引用就停用”的数据保护规则是安全的，但 UI 的 Delete 容易被理解为把项目及课程一并清除。确认框会明确显示：有任何关联时只停用项目，班级和排课仍保留，不会自动取消；无关联时只是软删除项目本身。

尚未实现“结束项目并取消课程”的业务流程。若需要这种操作，应另行设计终止流程：先列出受影响的在读学生、未确认未来课次和资源占用；明确退款/转班处理；再取消指定范围内的未来未确认课次并保留已确认/完成课次、订单收据和消课历史。不能把该流程混入当前 Delete。

## 涉及代码

- 前端按钮、冷静时间、确认提示：`frontend/src/views/system/GroupCourseModelPanel.vue`
- 删除/停用规则：`backend/src/main/java/com/classdeduction/system/modules/base/service/impl/GroupCourseModelServiceImpl.java`
- API：`backend/src/main/java/com/classdeduction/system/modules/base/controller/GroupCourseModelController.java`
- 报名对停用项目的校验：`backend/src/main/java/com/classdeduction/system/modules/enrollment/service/impl/RegistrationIntakeServiceImpl.java`
