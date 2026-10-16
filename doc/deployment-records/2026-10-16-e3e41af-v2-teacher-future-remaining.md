# JIN V2 部署：老师未来剩余课时与课时口径提示

- 代码来源：`main_v2` 提交 `e3e41af`。
- 部署日期：2026-10-16（Asia/Shanghai）。
- 部署范围：V2 后端 JAR 与完整前端静态资源；无数据库结构变更或数据迁移。
- 变更：1-on-1 Lessons 页眉右侧增加 `Remaining with [teacher]`，按该老师未来有效排课记录的 `plannedUnits` 汇总；过去、已确认、待管理员确认、正式请假、取消及已合并课次不计入。课包级 `Remaining Hours` 保持原账本口径。Total、Starting、Remaining、Allocated、Deducted、Pending 和老师 Remaining 均提供简短英文悬浮说明。
- 构建：前端 `npm run build` 成功（包含 Vue 类型检查，88 个文件）；后端 Java 17 `mvn -Dmaven.test.skip=true package` 成功；测试未运行。
- 后端 JAR：`/home/class-deduction-system/releases/v2-20261016-e3e41af/backend/v2-backend-e3e41af.jar`；SHA-256：`fbd026ac38486793602c0b797a225ae23443abbeccea12039e934a25a0a99f1c`。
- 前端发布目录：`/home/class-deduction-system/releases/v2-20261016-e3e41af/front/dist`；线上由 `/home/class-deduction-system/front/dist` 指向该目录。
- 前端 `index.html` SHA-256：`e7292188029e8e8fdeaf5d83b69a0019a10b12e510538e13ff520d5cbe652a0c`。
- 关键 JS `OneOnOneDeductionView-DpbnO4h6.js` SHA-256：`cec2b1f12be8d7d6db54e68080b21fad2e535854867fd10d4532b804bf6585ac`；CSS `OneOnOneDeductionView-ubdXyY0O.css` SHA-256：`8d0555595f7f392f50981e13a98d34fc6a9dd12f1ad7b784184c83b249910908`。
- 回滚：前端备份 `/home/class-deduction-system/front/dist.bak_e3e41af_20261016T032700Z`；上一版 systemd 配置和版本标记备份位于发布目录 `backend/v2.conf.before_e3e41af_20261016T032700Z`、`backend/DEPLOY_VERSION.before_e3e41af_20261016T032700Z`；原前端目录保留为 `front/dist.pre_e3e41af_20261016T032700Z`。

## 生产验证

- `class-deduction.service` 与 Nginx 均为 active；线上后端 Java 17，`http://127.0.0.1:8084/api/actuator/health` 返回 `{"status":"UP"}`。
- `nginx -t` 成功；公网首页和健康接口返回 HTTP 200 / `UP`。
- 公网课消页 JS 与 CSS 的 SHA-256 与本地构建完全一致，JS 包含各字段英文提示及未来排课 Remaining 说明。
- 部署后 5 分钟内 `class-deduction.service` 无 error-priority 日志。
