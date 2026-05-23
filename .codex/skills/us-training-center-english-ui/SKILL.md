---
name: us-training-center-english-ui
description: Use for any task in this project that creates or edits user-facing pages, frontend copy, navigation, forms, dashboards, emails, notifications, seeded UI data, or localization files. Enforce US English only for all visible strings. Do not add Chinese characters, bilingual copy, or untranslated placeholders. Write concise, friendly wording suitable for a small training center in the United States, and run scripts/check_no_chinese.py before finishing UI work.
---

# US Training Center English UI

## Goal

Keep every customer-facing surface in this project fully English, natural for US users, and free of Chinese text. The business name is `JIN BEST TUTORING`, and user-facing copy should preserve that exact brand name unless a task explicitly defines a shorter approved variant.

## Use This Skill When

- Building or editing pages, layouts, or reusable UI components
- Adding or revising labels, buttons, helper text, validation copy, toast messages, or empty states
- Creating user-facing emails, exported headers, template content, seeded demo data, or locale files
- Reviewing frontend work for language quality before handoff

## Hard Rules

- All visible text must be English only.
- Do not ship Chinese characters, bilingual copy, or untranslated placeholders.
- Use natural US English wording instead of literal translation patterns.
- Keep the tone clear, friendly, and practical for a small training business.
- Prefer sentence case for most UI labels unless a brand rule says otherwise.
- Keep the brand name as `JIN BEST TUTORING` in headings, emails, and marketing-facing UI unless a narrower context calls for `JBT` or another explicitly approved short form.
- Display user-facing dates and times in the training center's US local timezone, not server-local time and not China time.

## Workflow

1. Identify every user-facing string touched by the task.
2. Rewrite new or changed copy in plain US English.
3. If terminology choices are unclear, read `references/copy-guidelines.md`.
4. Check every new or edited date, time, schedule, and reminder against the US timezone rule.
5. Run `python .codex/skills/us-training-center-english-ui/scripts/check_no_chinese.py <affected-paths>` before finishing.
6. Fix every reported issue before handoff.

If the project structure is still evolving and there is no clear frontend folder yet, run the check script from the repository root and then narrow it once `src/`, `app/`, or similar folders exist.

## Treat These As User-Facing

- Page titles and navigation labels
- Forms, placeholders, hints, and validation messages
- Dialogs, tables, filters, badges, and status labels
- Empty states, success states, and error messages shown in the UI
- Email templates and exported column headers
- Seeded content or mock data that can appear on a page
- Schedules, calendars, reminders, booking times, payment due dates, and attendance timestamps

## Out Of Scope

Internal implementation details such as variable names, test names, or private developer notes do not need product copy review unless they are rendered to users.

## Handoff Expectations

- Mention any terminology decisions that affect consistency
- Mention the timezone assumption used for user-facing time displays if the real campus timezone is still unknown
- Call out any text that must remain unchanged because it comes from a third-party system
- Note whether the no-Chinese check was run and on which paths
