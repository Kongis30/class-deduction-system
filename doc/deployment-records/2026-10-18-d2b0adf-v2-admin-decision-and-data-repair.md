# JIN V2：1-on-1 管理员最终决定与排课数据修复

- 后端来源：`main_v2` 提交 `d2b0adf`；部署目录 `/home/class-deduction-system/releases/v2-20261018-d2b0adf-pending-admin/backend/backend.jar`。
- JAR SHA-256：`b568cd979eb561a1d85aa786472115ce34a6b530ae62b732930326ad2a804401`。
- 前端未变；systemd 覆盖配置原件备份在发布目录 `v2.conf.before`。`class-deduction.service` 为 active，本机 `/api/actuator/health` 返回 `UP`。
- 老师在 `PENDING_ADMIN` 的消课或请假意见只预留原计划课时；只有管理员最终确认才扣课或生成正式请假补课。详见 `docs/one-on-one-hours-business-rules.md`。

## 线上数据处理

- 修正 10 个课包的 13 条历史 slot 分配快照；不改课次、课包余额或消课记录。
- 以有效 Create/Edit Schedule 和管理员最终请假补课为依据，技术取消 16 条误追加且无消课记录的普通课次，共 32h。课次保留在数据库中并标记 `planningStatus=CANCELLED`，不计入收费取消或 Deducted。
- Zhen Shen Zou 的 Ms. Lau 当前 slot 分配由 14h 更正为 12h；旧周期已消课时不再混入当前周期。Mr. Derek 的课时未改。
- Terry Huang 已确认的 Mr. Wu 10/09 正式请假补课，原先误排到 Mr. Tony 的 2027-03-23 周二。把已有的 Mr. Wu 2027-02-26 周五课次关联成补课，技术取消错误周二补课；两个老师的 slot 分配、周五课程时间及所有已确认记录未改。课包预计结束日期更正为 2027-03-18。
- 另将 Katherine Chen、Kylie Yan、Liekkas Lin 的预计结束日期更新到有效课次尾部。
- 各操作先备份原值，再用精确 ID、版本、状态、来源课次和「无消课记录」等条件作事务校验；修复 SQL 和复核查询保存在 `doc/data-repair/2026-10-18/`。本地原值快照在 `/tmp/jin-one-on-one-*-before-20261018.tsv`。

## 验证与边界

- 57 个有效 1-on-1 课包中，55 个有有效排课规则；其余 2 个无规则也无有效课次，未擅自创建排课。
- 对全部 55 个已排课课包，按当前周期复核课包及老师分配、最终已消课、有效课次，均满足 `Allocated − Deducted = Remaining with = 当前周期有效课次时长`；老师剩余总额均不超过课包余额。课包 `Total − Consumed = Remaining` 也无差额。
- 六处超过一周的排课间隔对应已确认正式请假或之前的排课调整；两条时间与常规 slot 不同的课次为 `ADJUSTED`，本次均不自动补排或改动。
- Java 17 主源码构建成功，五项新增针对性测试通过。现有完整相关测试类仍有七项旧断言／旧夹具失败，未把这项验证写成全套通过。

## 回滚

可用发布目录的 `v2.conf.before` 恢复先前后端，再执行 `systemctl daemon-reload` 并重启服务。数据库更正独立于 JAR 发布，回滚程序不会自动撤销数据；如需撤销，先依据原值快照、现有课次状态及后续用户操作做新的事务校验。
