---
name: user perspective
description: Always design and build from the end-user's vantage point, not the developer's
type: feedback
---

When building frontend features, always think from the user's perspective — what information does the user actually need to see? System IDs like package numbers are meaningless to users. Show human-readable names (course names, student names, teacher names) instead.

**Why:** User explicitly called this out — showing internal IDs like "CP-20260408-0001" is useless to end users who think in terms of course names like "SAT Math".

**How to apply:** Before displaying any data field, ask: would a non-technical user understand this? Prefer names over codes, labels over IDs. When mapping API data, reach for the human-readable field.
