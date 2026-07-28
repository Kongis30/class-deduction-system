# Tasks

- [x] Task 1: 建立清理候选清单
  - [x] SubTask 1.1: 盘点当前 git 工作区，区分本轮可处理文件、已有用户改动、生成产物和需求资料。
  - [x] SubTask 1.2: 使用 `rg --files`、imports、router、menu、test references 识别前端未引用页面、组件、模块 schema、API 和类型。
  - [x] SubTask 1.3: 使用 Java 编译引用、Spring 注入、Mapper 引用和测试引用识别后端未使用 DTO、VO、Mapper 方法、service 方法和测试辅助代码。
  - [x] SubTask 1.4: 输出候选分级：safe delete、needs migration、keep。

- [x] Task 2: 清理前端旧结构与死代码
  - [x] SubTask 2.1: 删除已无路由、菜单、import 或测试引用的旧 Vue 页面、占位页和 workspace schema。
  - [x] SubTask 2.2: 删除或合并未使用的 API wrapper、domain type、utility 和样式片段。
  - [x] SubTask 2.3: 保留当前业务入口和配置入口所需代码：Registration、1-on-1 Lessons、Classrooms、Reports、Settings。
  - [x] SubTask 2.4: 运行前端 typecheck，确认删除没有破坏导入和类型。

- [x] Task 3: 清理后端未使用代码
  - [x] SubTask 3.1: 删除可证明未被 controller/service/mapper/test 引用的 DTO、VO、service 方法、mapper 方法或 helper。
  - [x] SubTask 3.2: 清理过期注释、空注释块和重复测试辅助代码。
  - [x] SubTask 3.3: 保留当前接口契约和数据库实体字段，不改接口语义。
  - [x] SubTask 3.4: 运行受影响模块的 Maven 测试或编译。

- [x] Task 4: 清理非源码产物与文档残留
  - [x] SubTask 4.1: 识别 `.run`、缓存、临时导出、过期 dump 等非源码产物是否应移除或加入忽略。
  - [x] SubTask 4.2: 清理明显过期且不作为需求基准的文档残留。
  - [x] SubTask 4.3: 保留 `doc/新需求改动` 和当前 spec 文档，不删除需求来源。
  - 说明: repo root 的 `dump.rdb` 为本地 Redis dump，可能包含调试/临时状态，未确认前不删除，已加入忽略。

- [x] Task 5: 结构整理与一致性校验
  - [x] SubTask 5.1: 检查前端目录结构是否围绕当前入口和 shared utilities 组织，移除空目录。
  - [x] SubTask 5.2: 检查后端模块结构是否仍按 auth、base、enrollment、lesson、schedule、finance、systemconfig 等边界组织。
  - [x] SubTask 5.3: 检查新增或保留代码没有引入中文 UI 文案，报名纸质表单例外。

- [x] Task 6: 回归验证
  - [x] SubTask 6.1: 运行前端 typecheck 和 build。
  - [x] SubTask 6.2: 运行后端编译或受影响模块测试。
  - [x] SubTask 6.3: 验证核心入口仍可解析：Registration、1-on-1 Lessons、Classrooms、Reports、Settings。
  - [x] SubTask 6.4: 验证新报名保存、学生历史回看、补记付款、历史收据、1 对 1 消课、Reports 不因清理回归。

- [x] Task 7: 明确保留高风险本地产物
  - [x] SubTask 7.1: 将 `.run/`、`backend/.run/` 和 `dump.rdb` 标记为本地运行产物并加入忽略。
  - [x] SubTask 7.2: 保留现有 `dump.rdb` 文件，不在未确认内容的情况下删除潜在本地数据。
  - [x] SubTask 7.3: 复验非源码产物检查项。

## Task Dependencies

- Task 2 depends on Task 1
- Task 3 depends on Task 1
- Task 4 depends on Task 1
- Task 5 depends on Tasks 2 through 4
- Task 6 depends on Task 5
- Task 7 depends on Task 6 verification findings
