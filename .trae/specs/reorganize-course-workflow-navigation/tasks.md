# Tasks

- [ ] Task 1: 建立统一的可点击上下文路径
  - [ ] 为路由 meta 定义稳定的业务区域和对象上下文规则。
  - [ ] 将顶部静态路径替换为可点击 breadcrumb。
  - [ ] 支持 Course Product、Grade Class、Student、Course Package 名称异步解析，并在缺少对象时安全降级。

- [ ] Task 2: 重组 Course Products 的两类产品创建入口
  - [ ] 将 tab 固定在 Course Products 容器顶部。
  - [ ] 为 `1:1 Private` 和 `Group Class` 分别提供创建按钮、标题、表单和保存后下一步。
  - [ ] 移除不属于 Product 层的班级、教室和排课字段。

- [ ] Task 3: 修正 Course Products 的后续操作
  - [ ] 1 对 1产品只提供 Prices、Edit、Deactivate/Delete，不提供班课排课入口。
  - [ ] 班课产品提供 Grade classes / Prices；仅在存在 Grade Class 后启用 Class schedules。
  - [ ] 班课排课跳转保持 productId，价格行排课跳转保持 classId。

- [ ] Task 4: 建立 1 对 1课包到排课的入口
  - [ ] 在 Course Packages 或报名成功后的可见上下文中为未排课的 1 对 1课包提供 `Create weekly schedule`。
  - [ ] Weekly Schedule 接收并应用 studentId/packageId 查询参数，预选对应对象。
  - [ ] 已有排课时显示 `Edit weekly schedule`，不再要求用户重新查找学生和课包。

- [ ] Task 5: 在价格矩阵和班课排课页补齐下一步
  - [ ] 班课价格矩阵保存后显示 Grade Class 状态与 `Set class schedule` 或 `Ready for registration`。
  - [ ] 1 对 1价格矩阵只显示 `Ready for registration`，不显示班课操作。
  - [ ] Class Schedules 显示当前 Product 和 Grade Class 上下文，并提供返回价格矩阵的入口。

- [ ] Task 6: 更新测试和验证
  - [ ] 更新导航 E2E，覆盖可点击 breadcrumb、两类产品创建按钮和正确的排课入口。
  - [ ] 运行前端类型检查、构建和受影响的 E2E。

## Task Dependencies

- Task 2 depends on Task 1.
- Task 3 depends on Tasks 1 and 2.
- Task 4 depends on Task 1.
- Task 5 depends on Tasks 1 and 3.
- Task 6 depends on Tasks 1 through 5.
