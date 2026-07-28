# Tasks

- [x] Task 1: 需求方向复核与 UI 差距盘点
  - [x] SubTask 1.1: 对照 `doc/新需求改动` 复核四个主入口、表格化工作流、打印和角色权限要求。
  - [x] SubTask 1.2: 盘点当前侧边栏、默认路由、报名、学生历史、1 对 1 消课、教室和报表页面与需求材料的差距。
  - [x] SubTask 1.3: 标记需要保留的现有能力：学生历史、补记付款、历史收据、配置入口。
  - [x] SubTask 1.4: 明确本次只做前端信息架构和视觉体验优化，不改数据库结构。

- [x] Task 2: 收敛信息架构为四个日常入口
  - [x] SubTask 2.1: 管理员和员工侧边栏第一层只保留 Registration、1-on-1 Lessons、Classrooms、Reports。
  - [x] SubTask 2.2: 管理员 Settings 作为低频配置入口保留，不干扰四个日常入口。
  - [x] SubTask 2.3: Students 不作为第五个一级入口，学生查找、报名历史和补记付款从 Registration 或 Reports 进入。
  - [x] SubTask 2.4: 管理员和员工登录默认进入 Registration；老师和学生默认页保持各自受限入口。

- [x] Task 3: 建立表格化低干扰视觉规范
  - [x] SubTask 3.1: 在全局样式中补齐表格化页面标题、紧凑筛选区、金额、状态标签、风险高亮和空状态样式。
  - [x] SubTask 3.2: 降低卡片堆叠和装饰性视觉，保证表格主体优先。
  - [x] SubTask 3.3: 统一主要操作、次要操作、打印操作和危险操作的视觉权重。
  - [x] SubTask 3.4: 确保新增系统 UI 文案为英文，报名纸质表单既有中英双语内容不被破坏。

- [x] Task 4: 优化 Registration 作为主工作台
  - [x] SubTask 4.1: 保持报名纸质表单布局和打印样式，补充轻量说明而不改变客户熟悉的表格形态。
  - [x] SubTask 4.2: 在报名页提供查找已报名学生、查看报名历史、补记付款的上下文入口。
  - [x] SubTask 4.3: 强化费用区 Amount due、Paid、Balance 和欠费提示，保持 Save 与 Print 职责分离。
  - [x] SubTask 4.4: 成功提交后明确下一步：Print receipt、View student history、Create another registration。

- [x] Task 5: 优化 1-on-1 Lessons 表格化消课体验
  - [x] SubTask 5.1: 按老师和学生组织消课视图，贴近“每个老师每个学生一张表”的工作方式。
  - [x] SubTask 5.2: 在消课表中清晰区分待老师确认、待管理员确认、已完成、异常或驳回状态。
  - [x] SubTask 5.3: 突出 total hours、used/assigned hours、remaining hours 和剩余 2 节课提醒。
  - [x] SubTask 5.4: 保留并优化单个学生消课表打印入口。

- [x] Task 6: 优化 Classrooms 和 Reports 表格化运营页
  - [x] SubTask 6.1: Classrooms 明确“报名阶段无需教室，后续运营补录”的页面说明。
  - [x] SubTask 6.2: Classrooms 在无占用或冲突时提供明确空状态和错误提示。
  - [x] SubTask 6.3: Reports 保持接近 Excel 的宽表，突出欠费、到期、低课时高亮。
  - [x] SubTask 6.4: Reports 保留导出入口位置，但不新增未确认的导出实现。

- [x] Task 7: 回归验证
  - [x] SubTask 7.1: 运行前端类型检查和构建。
  - [x] SubTask 7.2: 验证管理员/员工、老师、学生菜单入口和默认页符合权限要求。
  - [x] SubTask 7.3: 验证报名表、历史收据和 1 对 1 消课表打印时隐藏导航、按钮和系统 UI。
  - [x] SubTask 7.4: 回归新报名保存、学生历史回看、补记付款、1 对 1 消课、教室占用和 Reports 入口。

- [x] Task 8: 修复验证发现的信息架构和报表视觉缺口
  - [x] SubTask 8.1: 将 Settings 从四个日常入口同层菜单中分离为低频配置入口。
  - [x] SubTask 8.2: 保持 Settings 对管理员可访问，且不影响员工、老师、学生的日常入口。
  - [x] SubTask 8.3: 降低 Reports 汇总卡片对 Excel 宽表主体的视觉干扰。
  - [x] SubTask 8.4: 重新运行前端类型检查和构建，并复验失败检查项。

## Task Dependencies

- Task 2 depends on Task 1
- Task 3 depends on Task 1
- Task 4 depends on Task 2 and Task 3
- Task 5 depends on Task 2 and Task 3
- Task 6 depends on Task 2 and Task 3
- Task 7 depends on Tasks 4 through 6
- Task 8 depends on Task 7 verification findings
