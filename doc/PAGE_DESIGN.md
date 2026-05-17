# Page Design Notes

## Product Context

This admin system is built for `JIN BEST TUTORING`, a small tutoring business in the United States. The interface should feel practical, clear, and operationally calm rather than flashy.

## Global UI Principles

- Use English only across all visible UI text.
- Preserve the brand name `JIN BEST TUTORING` exactly as written.
- Display user-facing dates and times in the US local timezone, currently defaulting to `America/New_York`.
- Prefer sentence case for labels, action names, and helper text.
- Keep workflows obvious for staff who spend long hours in scheduling and record maintenance.

## Core Layout Areas

### Header

The header should include:

- brand name
- current page title
- global search or quick actions if needed later
- user entry point for account actions

### Sidebar

The sidebar should organize modules in this order:

1. Dashboard
2. Base data
3. Enrollment
4. Scheduling
5. Lessons
6. System settings

### Content Area

The content area should support three common page types:

- list-driven management pages
- form-driven create and edit pages
- schedule and occupancy pages with calendar emphasis

## Key Page Types

### Dashboard

Should surface:

- today's lessons
- pending lesson confirmations
- classrooms in use today
- packages close to depletion
- quick links into high-frequency tasks

### Base Data Pages

Should provide:

- filters at the top
- a clear table or card list
- stable primary actions such as create, edit, disable, and view detail
- room for notes and contact fields without crowding the page

### Enrollment Pages

Should prioritize:

- student selection
- product selection
- pricing and discount snapshot
- payment status and receipt actions
- course package creation results

### Scheduling Pages

Should emphasize:

- teacher, student, and classroom conflicts
- recurring rule setup
- visibility into planned vs adjusted lessons
- room occupancy awareness

### Lesson Pages

Should make it easy to:

- confirm actual lesson outcomes
- apply status rules
- explain deduction decisions
- review remaining units and ledger history

### System Pages

Should include:

- lesson status rules
- notification templates
- brand settings
- account and permission management

## Copy Guidelines for Pages

- Prefer direct labels such as `Create student`, `Save changes`, and `Send receipt`.
- Avoid untranslated placeholders and bilingual copy.
- Use operational wording rather than technical wording where possible.
- Make empty states actionable, for example `No classrooms found. Add a classroom to start scheduling.`

## Responsive Guidance

- Desktop is the primary target.
- Tablet support should remain usable for quick operations.
- Mobile should not be the main workflow target, but pages must still load cleanly.
