# Feature Specification: Data Summary Product Tabs

**Feature Branch**: `001-data-summary-product-tabs`  
**Created**: 2026-08-31  
**Status**: Draft  
**Input**: User description: 在 Data Summary 页面表格底部增加 Product Tab；一个 Product 对应一个 Tab；Tab 支持拖拽排序；切换 Tab 时保留筛选、分页、排序状态；顺序保存为所有工作人员共享配置，退出并重新登录后仍保持。

## User Scenarios & Testing

### User Story 1 - 按 Product 查看汇总表 (Priority: P1)

管理员进入 Data Summary 页面后，可以通过表格底部的 Product Tab 快速查看某一个产品对应的报名和费用汇总数据，而不需要在一张混合表格中手动筛选产品。

**Why this priority**: Product 是后台数据整理的核心业务维度，按 Product 拆分后更接近现有 Excel 工作表的使用方式，也是其他 Tab 交互的基础。

**Independent Test**: 使用至少两个 Product 和各自的报名记录进入 Data Summary，确认每个 Product 都有一个 Tab，切换后表格只显示当前 Product 的记录。

**Acceptance Scenarios**:

1. **Given** 系统中存在多个未删除的 Product，**When** 管理员打开 Data Summary，**Then** 表格底部按既定顺序显示每个 Product 一个 Tab。
2. **Given** 当前选中某个 Product Tab，**When** 页面加载汇总数据，**Then** 表格、月份选项和汇总数字只基于当前 Product 的记录计算，并继续遵循现有的分组、余额和提醒规则。
3. **Given** 某个 Product 暂无报名记录，**When** 管理员选中该 Product Tab，**Then** 页面保留该 Tab 并显示可识别的空状态，不影响其他 Product 的数据。

---

### User Story 2 - 切换 Product 时保留工作状态 (Priority: P1)

管理员在不同 Product 之间核对数据时，可以回到原 Product 继续查看，不会丢失已经设置的筛选条件、分页位置和排序方式。

**Why this priority**: 数据整理通常需要在多个产品之间来回比对，状态丢失会造成重复操作并增加误读风险。

**Independent Test**: 在 Product A 设置筛选、分页和排序，切换到 Product B 后再返回 Product A，确认 Product A 恢复之前的全部状态。

**Acceptance Scenarios**:

1. **Given** Product A 已设置月份筛选、一个或多个列筛选、分页页码和排序方向，**When** 管理员切换到 Product B 再切回 Product A，**Then** Product A 恢复原来的筛选、分页和排序状态。
2. **Given** 当前 Product 的筛选结果不足以支撑原分页页码，**When** 筛选条件发生变化或数据重新加载，**Then** 页面将页码调整到有效范围，同时保留仍然有效的筛选和排序条件。
3. **Given** 管理员在当前 Product 修改筛选或排序，**When** 查看表格和汇总信息，**Then** 变更立即作用于当前 Product，不改变其他 Product 已保存的页面状态。

---

### User Story 3 - 调整并共享 Product Tab 顺序 (Priority: P1)

管理员可以直接拖拽表格底部的 Product Tab 调整工作表顺序。调整结果立即反映在当前页面，并保存为系统级共享顺序；重新打开页面或重新登录后，所有有权限的工作人员看到相同顺序。

**Why this priority**: 机构需要按照当前业务重点安排产品顺序，持久化共享顺序可以避免每次进入页面重新整理。

**Independent Test**: 拖拽两个 Product Tab 改变顺序，刷新页面并重新建立登录会话，确认顺序仍保持且其他有权限账号看到同一顺序。

**Acceptance Scenarios**:

1. **Given** 页面显示至少两个 Product Tab，**When** 管理员将一个 Tab 拖到另一个 Tab 前面，**Then** 页面不刷新并立即显示新的顺序。
2. **Given** 管理员已完成拖拽排序，**When** 页面刷新、离开后重新进入或退出后重新登录，**Then** Product Tab 按最后保存的系统级顺序显示。
3. **Given** Tab 顺序保存请求失败，**When** 管理员完成拖拽，**Then** 页面提示保存失败并恢复到拖拽前的顺序，避免显示与系统配置不一致的顺序。
4. **Given** Product 被新增、停用或删除，**When** Data Summary 再次加载，**Then** 新增 Product 出现在现有顺序末尾，停用 Product 仍可查看历史数据，已删除 Product 不再显示，其余 Product 的相对顺序不变。

---

### Edge Cases

- 没有 Product 时，页面不显示空白 Tab 区域，并以现有空数据状态提示管理员。
- Product 数量超过一行可容纳的宽度时，Tab 区域支持横向滚动，拖拽后顺序仍准确。
- 历史记录未关联 Product 时，不应被错误归入其他 Product；页面需要保留现有的未分配数据可见性，或以明确的 `Unassigned` 归类展示。
- Product 列表加载成功但汇总数据加载失败时，Tab 仍可显示，并给出汇总数据加载失败提示；不应覆盖已保存的 Tab 顺序。
- 快速连续拖拽时，最终一次有效顺序应成为保存结果；保存失败时恢复到最后一次已确认的顺序。
- 当前筛选结果为空时，分页控件和空状态应保持一致，不显示无效页码。

## Requirements

### Functional Requirements

- **FR-001**: System MUST display one Data Summary Tab for every non-deleted Product available to the authorized workspace.
- **FR-002**: Each Product Tab MUST be identified by a stable Product identity rather than its display name, so renaming a Product does not lose its Tab state or order.
- **FR-003**: Product Tabs MUST be rendered immediately below the Data Summary table and remain available while the table content is vertically scrolled.
- **FR-004**: Selecting a Product Tab MUST restrict the displayed rows and existing summary calculations to that Product, without navigating away from Data Summary.
- **FR-005**: The Data Summary table MUST continue to support the existing business fields, class grouping, balance display, unpaid highlighting, expiry highlighting, and one-on-one low-hour highlighting within the selected Product.
- **FR-006**: The page MUST support paging for the selected Product's filtered and sorted records, and the active page must be visible to the administrator.
- **FR-007**: The system MUST maintain an independent view state for each Product Tab, including month filter, column filters, page number, page size, sort field, and sort direction.
- **FR-008**: Switching between Product Tabs MUST restore the destination Product's last view state during the current page session.
- **FR-009**: Product Tab order MUST be draggable by an administrator, with no full-page refresh required during the interaction.
- **FR-010**: The system MUST persist the Product Tab order as one shared system configuration, not as a per-user preference.
- **FR-011**: The saved shared order MUST be restored after page refresh, page re-entry, and a later login session.
- **FR-012**: Only an authorized administrator may change the shared Product Tab order; authorized viewers may read the resulting order.
- **FR-013**: A failed order save MUST provide a visible error message and leave the UI consistent with the last successfully saved order.
- **FR-014**: Product lifecycle changes MUST reconcile with the saved order without renumbering or reordering unaffected Products unnecessarily.
- **FR-015**: The feature MUST use the existing Product display-order business concept and MUST NOT change historical enrollment snapshots or financial calculations.

### Key Entities

- **Product Tab**: A page-level representation of one course Product, identified by Product ID and labeled with the current Product name.
- **Shared Product Tab Order**: The system-wide ordered list of Product IDs used by all authorized staff when rendering Data Summary Tabs.
- **Product View State**: The current-page display state for one Product Tab, including filters, pagination, and sorting; it is independent per Product and is not required to survive logout.
- **Data Summary Row**: The existing registration/order snapshot row associated with a Product and used for the current table and business summaries.

## Success Criteria

### Measurable Outcomes

- **SC-001**: For every non-deleted Product returned by the system, an administrator can find exactly one corresponding Tab after the page finishes loading.
- **SC-002**: After selecting a Product Tab, 100% of displayed data rows belong to that Product, and no row from another Product is displayed.
- **SC-003**: In a manual test with at least five Product Tabs, switching away and back restores the selected Product's filter, page, page size, and sort state in one interaction without re-entering values.
- **SC-004**: After a successful reorder, the new order is visible immediately without a full-page refresh and remains unchanged across at least one page refresh and one subsequent login session.
- **SC-005**: When order persistence fails, the administrator receives an error message and the visible Tab order matches the last successfully saved order within 2 seconds of the failure response.
- **SC-006**: Existing Data Summary balance, refund, payment, and reminder display rules produce the same results when viewing the all-data set or an equivalent Product-specific subset.

## Assumptions

- The existing `course_product.sort_order` concept is the source of the shared Product order; a separate preference table is not required unless later requirements introduce multiple independent order sets.
- All non-deleted Products, including inactive Products with historical registrations, remain eligible for a Data Summary Tab.
- The current Data Summary page's existing English UI language and Excel-like visual style remain unchanged.
- Product view state is retained while the page component remains active; only the shared Tab order is required to persist across logout and login.
- The current Data Summary authorization model remains in place. The current deployment has one administrator with access, while the shared order is designed to be visible to future authorized staff accounts.
- Rows without a Product association are legacy or incomplete data. They will not be silently assigned to a real Product; an explicit `Unassigned` presentation may be used to preserve visibility when such rows exist.
- Existing backend response wrappers, authentication, audit fields, and soft-delete behavior are reused.
