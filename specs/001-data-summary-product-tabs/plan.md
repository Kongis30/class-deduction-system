# Implementation Plan: Data Summary Product Tabs

**Branch**: `main` | **Date**: 2026-08-31 | **Spec**: [spec.md](./spec.md)  
**Input**: Feature specification from `specs/001-data-summary-product-tabs/spec.md`

## Summary

Add a Product-based tab strip below the existing Data Summary workbook. Load all non-deleted course products in the shared product order, filter the existing summary rows by the selected Product, and keep each Product's month/filter/paging/sort state independently in the current page session. Reuse `course_product.sort_order` as the system-wide order and expose an administrator-only bulk reorder operation so the order survives refresh and later login sessions.

## Technical Context

**Language/Version**: Vue 3 + TypeScript, Spring Boot 3.5 + Java 17  
**Primary Dependencies**: Element Plus, Axios, MyBatis-Plus, MySQL  
**Storage**: Existing `course_product.sort_order` column; no new table or column  
**Testing**: `vue-tsc`, Vite production build, Maven unit tests, Playwright acceptance test updates  
**Target Platform**: Desktop-first authenticated admin web application, with usable narrow-screen horizontal scrolling  
**Project Type**: Full-stack internal web application  
**Performance Goals**: Product tabs and the selected table become usable after the existing summary/product reads complete; local tab switching does not issue a full-page reload  
**Constraints**: Preserve existing Data Summary financial and alert calculations; use English visible UI text; order writes must be administrator-only and must not silently lose concurrent/new Product IDs  
**Scale/Scope**: The current small tutoring operation with a course-product master list and a single shared order across authorized staff  

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- **Spec-first workflow**: PASS. The approved feature specification exists before implementation.
- **Minimal schema change**: PASS. The existing Product `sort_order` column already represents display order, so no DDL is needed.
- **Existing architecture**: PASS. The change stays within the existing Base Data Product API and Data Summary view/API layers.
- **Business data safety**: PASS. Product order updates only affect display ordering; enrollment snapshots and financial values remain untouched.
- **Authorization**: PASS. Reading follows the existing Data Summary access boundary; changing the shared order is restricted to administrators.
- **Project UI rules**: PASS. Visible labels remain English and the existing Excel-like workbook style is preserved.

## Project Structure

### Documentation (this feature)

```text
specs/001-data-summary-product-tabs/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   └── course-product-tabs.md
└── tasks.md
```

### Source Code (repository root)

```text
backend/
├── src/main/java/com/classdeduction/system/modules/base/
│   ├── controller/
│   ├── dto/
│   ├── service/
│   └── vo/
└── src/test/java/com/classdeduction/system/modules/base/

frontend/
├── src/api/
├── src/types/
├── src/views/data-summary/
└── e2e/acceptance/
```

**Structure Decision**: This is a full-stack web change. The backend adds Product listing and administrator-only order persistence within the existing Base Data Product module. The frontend keeps the existing Data Summary workbook in `DataSummaryView.vue`, adds Product Tab state and pagination there, and updates the existing API adapter and acceptance coverage.

## Complexity Tracking

No constitution violations.
