# Tasks: Data Summary Product Tabs

**Input**: Design documents from `specs/001-data-summary-product-tabs/`  
**Prerequisites**: `plan.md`, `spec.md`, `research.md`, `data-model.md`, `contracts/course-product-tabs.md`, `quickstart.md`

## Phase 1: Setup

- [x] T001 Record the approved feature specification in `specs/001-data-summary-product-tabs/spec.md`
- [x] T002 Record the implementation plan and API contract in `specs/001-data-summary-product-tabs/plan.md` and `specs/001-data-summary-product-tabs/contracts/course-product-tabs.md`

## Phase 2: Foundational

- [ ] T003 Add the all-Product and shared tab-order API declarations in `frontend/src/api/base-data.ts`
- [ ] T004 Add backend request/response types and service signatures in `backend/src/main/java/com/classdeduction/system/modules/base/dto/`, `service/`, and `vo/`

## Phase 3: User Story 1 - 按 Product 查看汇总表 (Priority: P1)

**Goal**: Show one Product Tab per current Product and display only the selected Product's workbook rows.

**Independent Test**: Load multiple Products with separate summary rows and verify each Product Tab filters the table correctly, including an empty Product.

- [ ] T005 [US1] Implement all non-deleted Product listing in `backend/src/main/java/com/classdeduction/system/modules/base/service/impl/CourseProductServiceImpl.java` and expose it from `CourseProductController.java`
- [ ] T006 [US1] Add Product Tab loading, active Product selection, Product-specific row filtering, and empty/unassigned handling in `frontend/src/views/data-summary/DataSummaryView.vue`
- [ ] T007 [US1] Add the bottom-of-workbook Product Tab strip styling and narrow-screen overflow behavior in `frontend/src/views/data-summary/DataSummaryView.vue`

## Phase 4: User Story 2 - 切换 Product 时保留工作状态 (Priority: P1)

**Goal**: Preserve each Product's month filter, column filters, pagination, and sort independently during page navigation.

**Independent Test**: Set state on Product A, switch to Product B, then return to Product A and verify all state is restored.

- [ ] T008 [US2] Add per-Product view-state snapshots and restoration in `frontend/src/views/data-summary/DataSummaryView.vue`
- [ ] T009 [US2] Add local filtered/sorted pagination and visible pagination controls in `frontend/src/views/data-summary/DataSummaryView.vue`
- [ ] T010 [US2] Update `frontend/e2e/acceptance/data-summary-workbook.spec.ts` to cover Product filtering, state restoration, pagination, and the bottom tab location

## Phase 5: User Story 3 - 调整并共享 Product Tab 顺序 (Priority: P1)

**Goal**: Allow an administrator to drag Product Tabs, save one shared order, and restore it across reload and login.

**Independent Test**: Drag two Product Tabs, verify no full-page reload, reload and sign in again, and verify the same order.

- [ ] T011 [US3] Implement administrator-only bulk Product order validation and transactional persistence in `backend/src/main/java/com/classdeduction/system/modules/base/service/impl/CourseProductServiceImpl.java`
- [ ] T012 [US3] Expose `PUT /v1/base/course-products/tab-order` with `ADMIN` authorization in `backend/src/main/java/com/classdeduction/system/modules/base/controller/CourseProductController.java`
- [ ] T013 [US3] Implement native drag/drop reorder, queued saves, failure rollback, and shared-order reload in `frontend/src/views/data-summary/DataSummaryView.vue`
- [ ] T014 [US3] Add backend unit coverage for order validation, stale Product reconciliation, and persistence in `backend/src/test/java/com/classdeduction/system/modules/base/service/CourseProductServiceImplTest.java`
- [ ] T015 [US3] Add Playwright coverage for drag order persistence and failed-save rollback in `frontend/e2e/acceptance/data-summary-workbook.spec.ts`

## Phase 6: Polish and Verification

- [ ] T016 Update endpoint documentation and feature quickstart in `doc/API_CONTRACT.md` and `specs/001-data-summary-product-tabs/quickstart.md`
- [ ] T017 Run `npm run typecheck` and `npm run build` from `frontend/`
- [ ] T018 Run the relevant Maven unit tests from `backend/`
- [ ] T019 Review the final diff for unchanged financial calculations, authorization boundaries, and accidental unrelated edits

## Dependencies and Execution Order

- T003 and T004 establish the API types and service signatures before story implementation.
- User Story 1 must load Products before User Story 2 can scope per-Product state.
- User Story 3 depends on the Product list from User Story 1 but does not change Data Summary financial calculations.
- T010, T014, and T015 can be prepared in parallel after the corresponding implementation contracts exist.

## Implementation Strategy

1. Complete the Product list and Product-specific table view first.
2. Add per-Product pagination and view-state restoration.
3. Add transactional shared reorder persistence and failure handling.
4. Run type checks, backend tests, build, and focused acceptance coverage before final review.
