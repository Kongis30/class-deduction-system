# JIN V2 一对一排课周期课时修复

- 部署日期：2026-10-17（Asia/Shanghai）。
- 前端来源：`main_v2` 提交 `0ad69c0`；发布目录 `/home/class-deduction-system/releases/v2-20261017-0ad69c0-period-audit/front`，`index.html` SHA-256 `377a16787be0da436dcc5769103755dfcf38016264d4b5311986a86ea8590323`。
- 后端来源：`main_v2` 提交 `b4e9c79`；发布目录 `/home/class-deduction-system/releases/v2-20261017-b4e9c79-period-correction/backend`，JAR SHA-256 `b1da84ad44dac647391af1c580b5e84e460ba1dc969e379f5fe863d6c1598d97`。
- 启动时新增 `recurring_schedule_rule.allocation_period_started_at`；随后执行 `deploy/database/migrations-v2/20261017_01_restore_one_on_one_period_boundaries.sql`，仅更新三条规则的周期起点。
- 规则修复前快照：服务器 `/opt/jin-db-backup/one-on-one-20261017-0ad69c0/recurring_schedule_rule.sql`。之前的 systemd 配置保存在 `/etc/systemd/system/class-deduction.service.d/v2.conf.pre-0ad69c0`，第一次后端切换配置保存在 `v2.conf.pre-b4e9c79`。

## 验证

- 干净源码构建通过；前端 typecheck 与 build 通过；旧课位等价判断和同周期课位重建的针对性测试通过。全量后端测试编译仍受既有、不相关的测试代码签名错误阻断。
- 公网 `/api/actuator/health` 返回 `UP`，首页 HTTP 200；`class-deduction.service` 为 active，启动日志显示后端在 8084 端口正常启动。
- 三条规则的课次数量、有效课次数量、计划课时总量、最后更新时间及行指纹在修复前后完全一致；课位版本数量和有效课位数量也未变化。
- 57 个有效一对一课包的 `total_units - consumed_units = remaining_units` 均成立。
- Ethan Gao：当前老师 Allocated 40h / Deducted 4h / Remaining 36h；课包余额 36h。
- Beau Hayworth：28h / 2h / 26h；课包余额 26h。
- Yinuo Zhou／Xintong Zou 共享课：每位成员当前老师 34h / 2h / 32h；各自课包余额 32h。
- Erika Yu 按用户确认保留最近周期 16h 分配；当前老师 16h / 0h / 16h，课包余额 10h。6h 超分配以异常提示展示，现有未来课次保持原样。
- 非共享课包审计：49 个有有效规则，36 个未检出超分配，13 个仍需逐包核对原分配或旧周期未处理课次；未自动改写这些课包。

## 回滚边界

如需回滚应用，恢复上述 systemd 配置及原前端链接后重启服务。周期元数据仅涉及三条规则；是否回滚这些值应先复核当时课位版本，不能连带回滚课次或课时流水。
