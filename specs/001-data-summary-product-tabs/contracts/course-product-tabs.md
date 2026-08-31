# Course Product Tab API Contract

The endpoint paths below are relative to `/api` and use the project's standard response wrapper.

## List Data Summary Products

`GET /v1/base/course-products/all`

Returns every non-deleted course Product in the persisted shared display order. Inactive Products are included so historical Data Summary records remain reachable.

### Response data

```json
[
  {
    "id": "2080000000000000001",
    "productName": "Saturday Math",
    "sortOrder": 1,
    "status": "ACTIVE"
  }
]
```

The response may include the existing Product metadata fields, but `id`, `productName`, `sortOrder`, and `status` are required by the Data Summary tab strip.

## Save Shared Tab Order

`PUT /v1/base/course-products/tab-order`

Authorization: `ADMIN` role required.

### Request body

```json
{
  "productIds": [
    "2080000000000000002",
    "2080000000000000001"
  ]
}
```

### Rules

- `productIds` must contain no duplicate IDs.
- Every submitted ID must identify a current non-deleted Product.
- Current Products omitted because the caller has a stale list are appended in their existing order.
- The resulting order is persisted atomically as one system-wide order.
- An empty list is valid only when there are no current Products.

### Response data

```json
null
```

The standard `success`, `code`, `message`, and `timestamp` fields communicate the result. A rejected or failed write must leave the previous persisted order unchanged.
