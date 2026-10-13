# JIN V2 部署：Starting Hours 与课时字段显示对齐

- 部署对象：Class Deduction V2 前端静态资源与后端 JAR；未修改数据库结构或业务数据。
- 代码版本：`main_v2` 提交 `d3c32ab3df6e29f7090bd1f8dc8be405ca6b2330`。
- 部署日期：2026-10-13（Asia/Shanghai）。
- 前端构建：`npm run build` 成功，发布 87 个文件。
- 后端构建：JDK 24，`mvn -Dmaven.test.skip=true package` 成功；按项目既有发布约束跳过测试源码编译。
- 后端 JAR SHA-256：`fcd5db336e7a36ad26197364357447350fa3b136316ad3f8d03eca435286047d`。
- 1-on-1 页面 JS：`assets/OneOnOneDeductionView-MPhIvIxn.js`，SHA-256 `e5b25c4c90d73cafe283ccd95237448c0369acc2cb21311bd9c3ae56e3acc6d5`。
- Schedule 页面 JS：`assets/RecurringScheduleView-CJOhnXwR.js`，SHA-256 `582e8926074d52a393bfaf2c046f9516e757fe3619f6bc9ecd3365c3b7f08f27`。
- 线上发布目录：`/home/class-deduction-system/releases/v2-20261013-d3c32ab`。
- 前端回滚备份：`/home/class-deduction-system/front/dist.bak_d3c32ab_20261013T042643Z`。
- systemd 配置备份：`/home/class-deduction-system/releases/v2-20261013-d3c32ab/v2.conf.before_d3c32ab_20261013T042643Z`。
- 版本标记备份：`/home/class-deduction-system/releases/v2-20261013-d3c32ab/DEPLOY_VERSION.before_d3c32ab_20261013T042643Z`。
- 后端基于上一版 JAR 保持 Boot Loader 与依赖归档格式，只替换本次变更涉及的 25 个编译类（含内部类）；完成 ZIP 完整性与替换类内容校验。首次暂存切换因后端包结构问题未通过启动检查，自动回滚至 `f092bdb`；修正后重新部署并通过检查。

## 变更

- Schedule 的 Edit Weekly Schedule 汇总区将 Allocated 与 Remaining 的显示顺序互换。
- 1-on-1 消课页面在 Total 与 Allocated 之间显示 Starting，取自课包的 `startingHours`。
- 课包列表接口补充 `startingHours`，Schedule Package Hours 窗口复用同一保存值。

## 生产验证

- `class-deduction.service` 为 active，systemd 命令指向本次发布 JAR。
- `https://class.jinbesttutoring.com/api/actuator/health` 返回 `{"status":"UP"}`；首页 HTTP 200。
- Nginx 配置检查通过。
- 线上 1-on-1 与 Schedule 页面 JS 的 SHA-256 均与本地构建一致。
