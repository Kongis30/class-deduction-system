# Checklist

- [x] 已完成清理候选清单，并区分 safe delete、needs migration、keep。

- [x] 删除的前端文件均确认无 router、menu、import、template、test 或字符串动态引用。

- [x] 删除的后端代码均确认无 controller、service、mapper、Spring injection、test 或前端 API 依赖。

- [x] 当前业务入口 Registration、1-on-1 Lessons、Classrooms、Reports、Settings 所需代码均保留。

- [x] 学生历史、补记付款、历史收据、课程价格配置、支付方式配置、账号配置等现有能力没有被误删。

- [x] `.run`、缓存、临时导出或 dump 等非源码产物已被清理或明确保留理由。

- [x] `doc/新需求改动` 需求资料未被删除或改写。

- [x] 前端目录中不存在已删除路由仍引用的 view/component/api/type。

- [x] 后端不存在编译失败的删除残留 import、Bean 注入或 Mapper 引用。

- [x] 清理没有引入业务接口字段变化、数据库结构变化或权限语义变化。

- [x] 新增或保留 UI 文案不引入中文，报名纸质表单既有中英双语例外不被破坏。

- [x] 前端 typecheck 和 build 通过。

- [x] 后端编译或受影响模块测试通过。

- [x] 核心链路回归通过：新报名保存、学生历史回看、补记付款、历史收据、1 对 1 消课、Reports。
