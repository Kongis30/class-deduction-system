# JIN V2：排课历史继承与周期数据审计

- 部署源码：`main_v2` 的 `04bdf98`，其中排课功能为 `0dc328b`。前后端均从同一提交的源码归档重新编译。
- 发布目录：`/home/class-deduction-system/releases/v2-20261030-04bdf98-schedule-history`。
- 后端 JAR SHA-256：`debd798df9e522866da668895342500fec688bb9919b214341539bc1958daddc`；前端首页 SHA-256：`36ebfab72201d69a9a3fa599b3b84a14f3bfa8515fa79777bd96916507bd679b`。服务器及公网首页哈希核对一致。
- 部署前备份：`/root/jin-v2-backups/pre-04bdf98-20261029T165508Z.sql.gz`，SHA-256 `29efeac4a99832dd93d2dbb7f21d9d3452c1b9e5d70ea74ebeecee71e1fe6194`。
- 部署后、数据修复前备份：`/root/jin-v2-backups/post-deploy-pre-period-repair-20261029T171025Z.sql.gz`，SHA-256 `dfb10c7a0b69c7b2182543716a36e532054221d0a491bf1ec9630e34e9b9380b`。两份均完成 gzip 完整性检查。
- 启动兼容迁移添加 `lesson_occurrence.history_owner_lineage_id`，不改变既有记录的历史归属。

## 在线周期审计和修复

实时审计 59 条 ACTIVE 私教规则，45 条显式周期起点为空；与 10 月 29 日快照中同一批规则的关键证据一致。空值按代码回退到规则创建时间，不自动表明数据错误。

对本地已演练且线上重新满足前后版本双向关联、未重建 slot、起始余额存在、版本及时间戳一致等前置条件的四条规则，分别使用独立事务回填周期起点为原有效读取值：`2111920968370274305`、`2111935751962279937`、`2113455381851725825`、`2115630796150251521`。各更新均为 1 行；前后六张相关业务表 checksum 一致，审计输出的其余字段逐条相同。日志在 `/root/jin-v2-backups/period-repair-04bdf98-20261030.log`。

剩余 41 条未做推测性回填：30 条无前序版本链接且没有旧 slot 重建证据，7 条无链接但有旧 slot，2 条有链接且有旧 slot，2 条有链接且无旧 slot。41 条均有创建时间可供现行逻辑回退；这不证明回退值一定是正确的历史周期起点。逐条证据分类清单位于本机私有目录 `~/.local/share/jin-v2/period-review-remaining-20261030.tsv`，仍需进一步溯源。

## 验证及限制

- 前端构建、146 项排课相关后端测试及两个相关 Playwright 用例通过；真实数据的本地隔离回归详见 `docs/edit-schedule-history-inheritance-rehearsal-20261030.md`。
- `LessonRecordServiceImplTest` 有 10 项失败或报错，与未修改 HEAD 的同名失败完全一致；这项既有消课测试缺口仍未解决，不能声称全部消课场景验证通过。
- 上线后服务及 Nginx 均 active，本机和公网 health 为 UP，公网首页哈希与发布包一致，服务错误级日志无新条目。未对线上进行会改变排课或消课的数据写入测试。

## 回滚

前一前后端发布目录为 `/home/class-deduction-system/releases/v2-20261029-2cd295f-legacy-slot-handoff`。发布目录保存了前端及 current 的原目标和 `backend/v2.conf.before`；恢复后执行 `systemctl daemon-reload && systemctl restart class-deduction.service && nginx -t && systemctl reload nginx`。新增列为可空字段，代码回滚不需要删除。周期回填为原读取值的显式化，勿无依据回滚其他数据；需要完整数据库恢复时使用上述备份并先评估之后产生的线上数据。
