# JIN V2：按当前周期续排及重复规则修复

- 后端来源：`main_v2` 提交 `877a9ce`；部署目录 `/home/class-deduction-system/releases/v2-20261018-877a9ce-period-renewal/backend`。
- JAR SHA-256：`274623f482ce61159c0fe719e8d48da57551d60925fa646c8df2ef97e614c1b5`。
- 前端保持既有版本；systemd 原配置备份在发布目录的 `v2.conf.before`。
- 本次只修续排时的排课周期归属和同一课包重复创建 ACTIVE 规则的入口校验；`Allocated`、`Deducted`、`Remaining with` 仍按 `docs/one-on-one-hours-business-rules.md` 计算。

## 生产数据与验证

- 发布前已备份共享课组两条规则到本地 `/tmp/jin_v2_duplicate_group_rules_before_20261018.sql`，将没有任何课次、与原规则课位完全相同的重复规则 `2103117340461289474` 标记为 ENDED；组内两位成员各自的当前周期老师课时为 Angel 16/2/14h、Henry 30/8/22h。
- 用现有冲突及余额条件保护的脚本补入 Muze Song 2026-12-12 09:00–11:00 的 2h；Ms. Claudia 当前有效课次由 18h 补齐为 20h。
- 部署后首次自动续排仅新增 Nathan Xie 2027-01-09 18:00–20:00 的 2h；Ms. Lau 当前有效课次由 54h 补齐为 56h。课包剩余 56h，课位分配 56h。
- 发布前后有效课次快照对比：新增 1 条，原有 12,496 条无删除、无内容变更。快照分别在本地 `/tmp/jin_v2_occurrences_before_877a9ce.tsv` 和 `/tmp/jin_v2_occurrences_after_877a9ce.tsv`。
- 公网和本机 `/api/actuator/health` 均为 `UP`，`class-deduction.service` 为 active。自动续排日志 `oneOnOneInserted=1, classInserted=0`。
- Java 17 主源码构建通过；两项新增针对性测试通过。仓库原有无关测试存在接口签名过时，阻断全量测试编译；单独执行本测试类时仍有六项原有断言失败。

## 回滚

恢复发布目录 `backend/v2.conf.before` 到 `/etc/systemd/system/class-deduction.service.d/v2.conf`，`systemctl daemon-reload` 后重启服务。数据修复与发布相互独立；不得通过回滚 JAR 自动撤销已经补入的有效课次。
