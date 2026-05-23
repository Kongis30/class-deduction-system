# Copy Guidelines

## Product Context

This system is for `JIN BEST TUTORING`, a small training center in the United States. The UI should feel simple, trustworthy, and easy for staff, students, and parents or guardians to use without explanation.

## Brand Usage

- Use `JIN BEST TUTORING` as the primary business name in visible product copy.
- Keep the capitalization exactly as `JIN BEST TUTORING` unless a design task explicitly introduces a different approved lockup.
- Do not translate, localize, or partially shorten the business name in user-facing text.
- When a shorter label is necessary because of space limits, confirm the shorter form first or leave the full name.

## Time And Timezone

- Treat all user-facing schedules, reminders, attendance records, and due dates as US-local product data.
- Do not show China time, server-local time, or ambiguous timestamps to users.
- Prefer an explicit configured IANA timezone for the training center, such as `America/New_York`.
- If the exact campus timezone is still unknown, use `America/New_York` as the temporary default in mockups and call out that assumption during handoff.
- When timezone context matters, show it in the UI, for example `Starts at 4:00 PM ET`.
- Convert stored timestamps before display instead of assuming the runtime machine timezone is correct.

## Voice And Tone

- Clear and direct
- Warm but not casual
- Helpful without sounding robotic
- Short enough to scan quickly on desktop and mobile

## Preferred Writing Style

- Use plain US English
- Prefer common words over formal or translated wording
- Write short sentences
- Use active voice
- Make actions specific

## Preferred Terms

- `Student`
- `Parent` or `Guardian`
- `Instructor`
- `Course`
- `Class session`
- `Enrollment`
- `Attendance`
- `Schedule`
- `Payment`
- `Invoice`

Choose one primary term per concept and stay consistent across the product.

## Recommended UI Patterns

- Action buttons: `Save changes`, `Create course`, `Send invoice`
- Empty states: `No students found.`, `No upcoming sessions yet.`
- Search and filters: `Search students`, `Filter by course`
- Validation: `Please enter an email address.`, `Select a course before continuing.`
- Success messages: `Changes saved.`, `Invoice sent successfully.`

## Avoid These Patterns

- `Please input`
- `Operate`
- `Kindly`
- `Open class`
- `System exception`
- `Data abnormal`
- `Training organization`

Replace them with natural product wording such as `Enter`, `Manage`, `Class session`, `Something went wrong`, `Unexpected data`, or `Training center`, depending on context.

## Formatting Rules

- Use US spelling and phrasing
- Prefer `MM/DD/YYYY` or a written month format when shown to users
- Prefer 12-hour time with `AM` or `PM` unless the product later standardizes on 24-hour time
- For date and time together, prefer formats like `04/05/2026, 4:00 PM` or `April 5, 2026 at 4:00 PM`
- When a timezone label is visible, prefer common US labels such as `ET`, `CT`, `MT`, or `PT` only if they match the configured timezone
- Do not use full-width punctuation
- Do not mix English with Chinese in the same string

## Microcopy Principles

- Buttons should start with a clear action
- Empty states should explain what is missing and what the user can do next
- Error messages should tell the user how to recover
- Helper text should reduce hesitation, not restate the field label
