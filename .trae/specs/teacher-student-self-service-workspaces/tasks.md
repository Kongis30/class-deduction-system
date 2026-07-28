# Tasks

- [x] Task 1: 建立自助角色导航与路由边界。
  - [x] SubTask 1.1: 为教师新增 `My Lessons` 入口，并将教师默认页调整为 `My Calendar`。
  - [x] SubTask 1.2: 将学生菜单收敛为仅 `My Calendar`，并将学生默认页调整为该页面。
  - [x] SubTask 1.3: 更新前端路由守卫，使教师仅能访问 `My Calendar` 和 `My Lessons`，学生仅能访问 `My Calendar`。

- [x] Task 2: 实现教师自助课程记录工作台。
  - [x] SubTask 2.1: 提供教师本人一对一与班课课次的统一查询，默认返回未来 30 天和待处理课次，并支持历史课次状态查询。
  - [x] SubTask 2.2: 实现教师端 `My Lessons` 表格化页面，按课程类型展示课程或班级、时间、学生或班级、教室、状态和可执行操作。
  - [x] SubTask 2.3: 在教师端复用出勤录入表单，仅允许对本人且处于确认窗口内的课次提交出勤。
  - [x] SubTask 2.4: 对一对一和班课分别展示 `Submit attendance`、`Awaiting admin review`、`Rejected`、`Confirmed` 等用户可理解状态。

- [x] Task 3: 完成班课教师初确认的服务端权限与二阶段流程。
  - [x] SubTask 3.1: 调整班课列表、班级详情和班课课次查询，使已绑定老师只能读取本人班级和课次。
  - [x] SubTask 3.2: 保持班课老师提交后所有符合资格学生记录均为 `PENDING_ADMIN`，且不产生最终消课或计薪。
  - [x] SubTask 3.3: 保持管理员对班课和一对一记录的 `Review & confirm`、驳回和单方兜底确认权限。
  - [x] SubTask 3.4: 为缺少老师或学生绑定关系的自助请求返回明确且无数据泄露的错误。

- [x] Task 4: 改造学生只读课表。
  - [x] SubTask 4.1: 学生登录后默认显示从当天开始的未来 30 天课次。
  - [x] SubTask 4.2: 增加历史课次切换，展示学生可理解的课程状态。
  - [x] SubTask 4.3: 移除学生端课程余额、内部审核、消课记录与所有管理操作的展示。
  - [x] SubTask 4.4: 后端按当前学生绑定关系过滤课表和详情请求，阻止通过参数读取其他学生数据。

- [ ] Task 5: 补充角色自助流程验证。
  - [ ] SubTask 5.1: 后端测试覆盖老师读取本人/非本人一对一和班课、班课初确认待审核、管理员终审核与驳回。
  - [ ] SubTask 5.2: 前端或 E2E 验证教师菜单、教师一对一与班课出勤、学生未来 30 天和历史课次切换、路由越权拦截。
  - [ ] SubTask 5.3: 运行前端类型检查和生产构建，并运行受影响后端测试。

## Task Dependencies

- Task 2 depends on Task 1 and Task 3.
- Task 4 depends on Task 1.
- Task 5 depends on Tasks 2 through 4.
