# Research: Data Summary Product Tabs

## Decision 1: Reuse the Product display-order field

- **Decision**: Store the shared Product Tab order in the existing `course_product.sort_order` column.
- **Rationale**: The schema and `CourseProductEntity` already define this field as the Product display order. It is system-wide, audited through the existing entity fields, and already used when listing Products. Reusing it avoids a redundant preference table and keeps Product management and Data Summary ordering consistent.
- **Alternatives considered**:
  - A new system-preference table was rejected because there is only one shared order and an existing Product-level order already covers the need.
  - Browser storage was rejected because it would not be shared between staff or survive a different browser/device and login session.

## Decision 2: Add a dedicated all-Product read and bulk order write

- **Decision**: Add a non-paginated Product list operation for the Data Summary tab strip and one administrator-only bulk reorder operation.
- **Rationale**: The existing paged Product endpoint is optimized for the Product management table. Data Summary needs every non-deleted Product, including inactive Products with historical records, and must avoid silently truncating the tab list at a page boundary. A bulk write keeps one drag operation atomic from the caller's perspective.
- **Alternatives considered**:
  - Calling the paged endpoint with a page size of 200 was rejected because it imposes an unnecessary hard limit on the number of Products visible as tabs.
  - Sending one Product update request per drag was rejected because partial failures could create an order that is neither the old order nor the requested order.

## Decision 3: Keep row filtering and view state local to the Data Summary page

- **Decision**: Load the existing summary dataset once, filter by Product in the browser, and maintain one view-state object per Product Tab for month, column filters, pagination, and sort.
- **Rationale**: The current Data Summary already performs column filtering and sorting locally. Keeping the same pipeline makes tab switching immediate, avoids re-fetching data for every tab, and makes it possible to restore each tab's state without serializing transient UI state to the server.
- **Alternatives considered**:
  - A server request on every Tab switch was rejected for this MVP because it would add latency and require a new paged summary contract without a current need.
  - A single shared view state was rejected because switching Products would overwrite the user's work on the previous Product.

## Decision 4: Use native drag events for the tab strip

- **Decision**: Use the browser's native drag-and-drop events on the existing Vue page, with a clear visual drag affordance.
- **Rationale**: The project has no drag-and-drop dependency, the interaction is limited to a horizontal list of tabs, and native events are sufficient for desktop-first staff use. Horizontal overflow remains available on narrow screens.
- **Alternatives considered**:
  - Adding a third-party sortable dependency was rejected to keep the change small and avoid a new production dependency for one list.

## Decision 5: Reconcile order writes defensively

- **Decision**: The backend accepts the complete Product ID order from the client, validates all submitted IDs, and appends current Product IDs omitted by a stale client in their existing order.
- **Rationale**: Product creation or deletion may happen between loading the tabs and saving a drag. Appending omitted current Products prevents a stale browser from deleting a newly created Product from the shared order.
- **Alternatives considered**:
  - Rejecting every stale order was rejected because a normal administrator drag should not fail merely because an unrelated Product was added.
  - Blindly accepting arbitrary IDs was rejected because it could persist deleted or unauthorized Product references.
