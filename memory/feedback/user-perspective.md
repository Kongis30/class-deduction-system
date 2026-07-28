---
name: user perspective
description: Highest priority UI rule: keep pages simple, design from the user's workflow, and minimize learning cost
type: feedback
---

# Highest Priority

Page design must be simple by default. Always design from the user's real workflow and minimize the learning cost of using the system. Prefer obvious layouts, direct wording, table-like operational views, and visible actions over decorative, complex, or clever UI.

When building frontend features, always think from the user's perspective — what information does the user actually need to see? System IDs like package numbers are meaningless to users. Show human-readable names (course names, student names, teacher names) instead.

**Why:** User explicitly called this out — showing internal IDs like "CP-20260408-0001" is useless to end users who think in terms of course names like "SAT Math".

**How to apply:** Before displaying any data field or adding any interaction, ask: would a front-desk user immediately understand what to do? Prefer names over codes, labels over IDs, and one clear action path over multiple clever shortcuts. Remove visual noise and avoid layouts that require training to operate.
