# 技术实施方案：Data Summary 撤销退款

**工作基线**：当前检出 `e45ddf7`，detached HEAD；规划日期：2026-11-09  
**需求**：[spec.md](spec.md) · **研究**：[research.md](research.md) · **模型**：[data-model.md](data-model.md) · **接口**：[contracts/undo-refund.md](contracts/undo-refund.md) · **验证**：[quickstart.md](quickstart.md)

## 概要

按行级退款撤销：定位订单、学生/参与人和课程购买项，以及该行选定的一次退款流水；同一显示行包含多条购买记录或多笔退款时先明确选择。只逆转该次行级退款造成的变化，保留其他行和退款。历史整单或跨行退款不得伪装为行级并部分撤销。服务端校验收据、流水、负向分摊及完整快照，在单事务内验证后续事实、Credit 和排课冲突；无法证明安全则拒绝，禁止财务-only 的 legacy 回退。外部资金不由系统追回。

本计划依据当前主检出目录中的代码，而非代理所用的干净 `origin/main` worktree：后者基于不同提交，缺少当前目录已有的退款区域和撤销逻辑，其移植建议不适用于本基线。实施前须确认集成基线；不得从其他分支或 worktree 整体移植，也不得覆盖现有未提交改动。

## 技术背景

- Java 17 / Spring Boot 3.5 / MyBatis-Plus，MySQL；Vue 3 / TypeScript / Element Plus / Vite；沿用现有依赖及统一 API 包装。
- 数据源为订单、付款及分摊、收据快照、课程和排课表；无需预设 DDL。实际 schema 与索引必须先核对目标环境 `information_schema`，文档可能漂移；本阶段未连接数据库。
- JUnit/Mockito、数据库集成并发测试及前端类型检查和 Playwright 验收。正常环境目标为需求 SC-003 的 3 秒；关联查询按订单批量，避免逐行 N+1，不以牺牲安全换取性能。
- 对用户日期采用 America/New_York；快照中规范化金额与时间精度。外部资金操作不在范围内。

## Constitution Check

`.specify/memory/constitution.md` 仍为占位模板，不能作为已批准的具体原则；以下依据实际全局/项目规则检查：中文文档先行及确认、最小变更与既有依赖、后端授权、事务一致性、数据库变更先审查、保护其他工作区改动、类型检查与测试。设计后复核：无预设 schema 变更或新依赖；并发安全和持久失败审计在实现验证通过前均为上线阻断项。当前只提交方案供确认，不改业务代码或执行 DDL。

## 当前实施状态（2026-11-09）

后端已完成**可执行的行级退款撤销**，legacy 财务-only 回退（`deleteLegacyRefundReceipt`）已删除，快照缺失、损坏、归属不明或状态已变化时一律拒绝。

已实现内容：

- `EnrollmentOrderServiceImpl.deleteRefundReceipt`：收据 → 订单行锁 → 订单项/班级锁 → 课包/规则/时段/课次行锁 → 重新采集当前状态并与快照 `after` 全量比较 → 退款后课时活动检查 → 复活课次解析与教师/学生/教室重叠检查 → Credit 冲正 → 恢复 → 作废并软删退款流水与收据 → 审计。锁顺序与 `refundOrder` 一致，按主键升序。
- `EnrollmentOrderServiceImpl.evaluateRefundUndo`：Data Summary 预览复用同一套校验（只读，不加锁、不写库）。
- `RefundUndoAuditService`：失败审计用 `REQUIRES_NEW` 独立事务，业务回滚后仍留痕；审计自身失败只记日志，不吞原异常。
- `DataSummaryServiceImpl.getRefundUndoCandidates`：结构校验通过后调用 `evaluateRefundUndo`，保证候选预览与实际执行是同一判定标准。
- 前端 `DataSummaryView.vue` 的行内 `Undo refund` 入口、候选对话框与确认流程已接入。

测试结果（2026-11-09）：

- `EnrollmentOrderServiceImplTest`：57 个用例，51 通过、6 失败。6 个失败在 `e45ddf7` 干净 worktree 上同样失败（V2 班课计费、收据更正、充值收据三类，与本次改动无关），已用 HEAD 基线逐条核对。新增 6 个撤销用例全部通过：成功撤销、状态变更拒绝、跨行退款拒绝、退款后课时活动拒绝、排课冲突拒绝、预览只读。
- 为绕过其他会话未提交改动导致的 `test-compile` 失败，本次用独立 classpath 单独编译并运行上述测试类，未修改其他会话的测试文件。
- `DataSummaryServiceImplTest`：34 个用例全部通过。
- 前端 `npm run typecheck` 通过；`doc/API_CONTRACT.md` 已补充 `GET /api/v1/data-summary/refund-candidates`、`DELETE /api/v1/enrollment/receipts/{id}` 及撤销错误码；`ReceiptRecordsView.vue` 的按钮与确认文案、`DataSummaryView.vue` 的确认文案已改为撤销语义（恢复课程/课包/未来课次、冲正 Credit、退款后事实则拒绝、外部资金不追回）。

尚未验证（上线阻断项）：

- 真实 MySQL 并发/集成测试未执行。当前仅靠行锁 + 快照重采 + 冲突探测降低竞争窗口；`LessonRecordServiceImpl.confirmLesson`、`CoursePackageServiceImpl` 的课包维护、`RecurringScheduleRuleServiceImpl.updateRuleInternal`/`renewOneRule`、`ClassGroupServiceImpl.listClassOccurrences` 等写入方仍为“先普通查询后写入”，不参与同一锁协议，残余 TOCTOU 未被证明消除。
- `LessonRecordServiceImpl.revokeLesson` 的锁顺序是 occurrence → package，与本撤销的 package → occurrence 相反，同一课包+课次并发时存在互斥死锁可能（InnoDB 会回滚其中一方，不会损坏数据）。
- Playwright 验收、浏览器实际交互和真实数据（含旧快照）验证均未执行；`npm run typecheck` 已通过。
- 工作区含其他会话的未提交改动，本次改动未提交，不得从该工作区部署。

## 代码结构及现状

- `backend/.../enrollment/service/impl/EnrollmentOrderServiceImpl.java`：退款、before/after 快照、`refundOrder`、行级撤销服务与 `evaluateRefundUndo`；legacy 财务-only 回退已移除。
- `DataSummaryServiceImpl.java` / `DataSummaryResponseVO.java` / `DataSummaryController.java`：现有退款标记仅为展示推导，不等同于可撤销交易；MANAGER 投影不得泄漏标识。
- `ReceiptController.java`、`ReceiptServiceImpl.java`：已有收据撤销入口和快照脱敏，须统一安全服务并覆盖所有返回路径。
- `frontend/src/views/data-summary/DataSummaryView.vue`：Refund Student 行新增桌面、键盘及移动端可操作入口；当前文件有其他未提交改动，逐块集成。
- `frontend/src/views/enrollment/ReceiptRecordsView.vue`、`frontend/src/api/{data-summary,enrollment}.ts`：同一确认体验及 API 类型。
- `backend/src/test/.../enrollment/service/`、`frontend/e2e/acceptance/`：增量测试；实际执行前核实基线与测试配置。

## 设计与实施步骤

1. **基线与写入清单**：核对目标分支、dirty 文件及其归属，和集成方协调后在符合项目纪律的隔离环境实施；列出退款路径所有实际写入与前后快照差异，包括 `replayAllocations` 是否在 before 快照前改写字段。确保新退款快照在首次副作用前捕获；保护退款收据不被订单更正覆盖。对旧快照只在证明完整且满足本方案时可用，否则人工处理。
2. **行级候选与影响**：单独提供 ADMIN/STAFF 候选查询；核验唯一收据、退款流水、负分摊与订单/学生/课程购买项归属。无分摊、多重收据、参与人不明、跨行退款或数据矛盾时返回不可自动撤销原因，不以展示用主学生回退猜测。显示行合并多次购买时明确选择购买项；一项多次退款时明确选择流水。候选不返回快照。
3. **快照验证及差异恢复**：为新快照明确版本和完整性，按 ID、显式字段和状态做规范化比较；处理金额 scale、时间精度、排序、null、逻辑删除及 version。锁定后检查退款后的相关记录及新增事实；只恢复 before/after 真正发生的退款差异，保留退款前已有停课和其他退款。按逻辑删除表提供受版本保护的专用恢复 SQL，正确写回 null；不重置已发生的上课/消课/课时历史。若无法证明，则全部拒绝；不保证较早退款在存在后续交易时可撤销。
4. **排课与共享资源**：仅恢复退款实际取消的、提交时仍属未来且快照中存在的课次及规则/时段；已经过去的课次不复活为待上，也不重新生成缺失课次。共享班课仅恢复该学生权益，绝不批量覆盖共享课次。检查教师、学生、教室、共享资源冲突，任何冲突整笔回滚。规则的后续生成需经既有排课冲突路径且不得重建历史。
5. **并发和 Credit**：在事务中按确定顺序锁定退款收据/流水、订单、受影响课程/报名/课包及 Credit 账户，并将受影响的排课资源纳入与排课写入方共同遵守的锁协议；重新查询范围和新增事实后校验。乐观版本检查和受影响行数必须作为二道防线。只有证明所有相关写入方（排课、确认上课、订单修正、退款、Credit 等）参与相同互斥或等效可串行化机制，且用真实数据库并发测试证明无幻读/TOCTOU 才允许上线；无法证明则拒绝该类自动撤销，不能宣称只锁订单足够。Credit 先核验原流水及余额、后续使用、身份与可逆性，同事务追加反向流水。
6. **统一服务与审计**：既有 DELETE 与新入口均走同一服务；不可信请求不可绕过确认所依据的范围检查。成功审计与业务事务同提交，保留退款与冲正的可追溯状态；失败审计通过项目既有可靠审计机制在业务事务回滚后独立持久化，验证失败时不会出现虚假成功或吞异常。若现有审计机制不能满足持久要求，先补充经审查的设计，不擅自新增表或降低要求。
7. **前端与文档**：退款行打开候选选择及整笔确认，注明外部资金不会追回；提交中禁用重复操作，错误引导刷新或人工核查，刷新保留 tab、月份及筛选。同一行仍有有效退款时根据最新数据继续留在退款区。旧收据入口同样提供完整影响和警告；服务端权限为最终依据。更新 `doc/API_CONTRACT.md`，若 schema 确认需要变更先补设计、审查迁移及回滚方案。
8. **验证及集成**：按 [quickstart](quickstart.md) 完成单元、真实 MySQL 并发集成和前端验收；运行类型检查。核实所有证据与限制后由集成方处理既有工作区修改和目标分支，不从 dirty worktree 部署。

## 关键门禁和风险

- 缺少快照/关联、快照受篡改、状态变化或数据恢复失败：拒绝且事务回滚；不采用 legacy 财务-only 分支。
- 快照包含受保护信息；所有列表和详情响应均剥离内部快照。MANAGER 不得访问候选 API、退款标识或执行接口。
- 仅检查已有课次不够：新排课、确认课时和 Credit 使用可能构成幻读；必须实证跨服务互斥方案。无法证明就不发布。
- 失败审计必须在回滚后仍存在；操作失败也不能泄露内部快照或个人敏感信息。
- 代理只读报告基于不同的 `origin/main` worktree，其行号和“没有已有撤销”结论不适用于当前检出；仍可作为风险提示，不作为事实基线。

## 任务拆分与依赖

T1 基线/写入及并发协议审计 → T2 快照完整性与安全比较、legacy 禁用 → T3 锁协议和恢复/冲突/Credit 原子事务 → T4 候选与影响 API → T5 两个前端入口及权限 → T6 后端/前端/真实数据库验收 → T7 合同文档与集成。T4 可与 T2/T3 的设计并行，但 T5 提交和 T6 必须依赖统一服务。正式可执行任务清单另由 `/speckit-tasks` 生成。

## Complexity Tracking

无已批准 constitution 条款可供判断例外；排课跨域锁与独立失败审计是满足原子性、并发与追溯的必要复杂性，不引入未经审查的新架构或数据库结构。
