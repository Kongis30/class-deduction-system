# Data Model: Data Summary Product Tabs

## Existing Product

The existing `course_product` entity remains the source of Product identity and display metadata.

| Field | Type | Role | Rules |
| --- | --- | --- | --- |
| `id` | `Long` | Stable Product identity and Tab key | Must be a current non-deleted Product ID when submitted for ordering |
| `product_name` | `String` | Current Tab label | Renaming changes the label but not the Tab identity or state key |
| `sort_order` | `Integer` | Shared Product and Tab display order | Lower values display first; rewritten as a contiguous order after a successful reorder |
| `status` | `String` | Product lifecycle state | `ACTIVE` and `INACTIVE` Products remain eligible for Data Summary Tabs while not deleted |

## Shared Product Tab Order

This is a logical view of the ordered non-deleted Product IDs persisted through `course_product.sort_order`.

- **Identity**: Product IDs are unique and stable.
- **Scope**: One system-wide order shared by all authorized Data Summary viewers.
- **Write authority**: Administrator only.
- **Read behavior**: Product list reads the persisted order and appends any current Product not yet ordered.
- **Reconciliation**: Deleted Products disappear; new Products are appended; unaffected Product relative order is retained.

## Product View State

This is transient page state held independently for each Product Tab.

| Field | Description |
| --- | --- |
| `selectedMonth` | Current sign-up month selection, including `ALL` |
| `columnFilters` | Text, select, date-range, and numeric filters for each workbook column |
| `page` | Current page number after filtering and sorting |
| `pageSize` | Current number of records per page |
| `sortState` | One selected column and ascending/descending direction |

View state is keyed by Product ID, restored on Tab switching, and discarded when the page session is destroyed. It is not persisted as a user preference.

## Data Summary Row

The existing `DataSummaryRow` remains unchanged as the registration/order snapshot used by the workbook. A row belongs to a Product when `courseProductId` matches the selected Product Tab. Rows without a current Product association are kept separate in an explicit `Unassigned` fallback presentation when present.

## Invariants

- A rendered Product Tab has exactly one stable Product ID.
- A successful saved order contains each current non-deleted Product ID exactly once.
- Product order changes never update enrollment order snapshots, course packages, payment records, ledger entries, or lesson records.
- A Product-specific table contains no row belonging to a different Product.
