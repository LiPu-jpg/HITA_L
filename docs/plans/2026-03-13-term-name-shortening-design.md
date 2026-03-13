# Term Name Shortening Design

**Goal**: Show concise term labels everywhere in the app (e.g. `2026春季`), removing the long `2025-2026 2026春季` style.

**Scope**
- All UI surfaces that display a term name: import timetable, exam, score inquiry, empty classroom, timetable top title.
- Timetable names generated during EAS import.

**Non-goals**
- No API changes.
- No data migration.
- No change to term ordering or selection logic.

**Design Summary**
- Introduce a single formatter utility used by all term display paths.
- Prefer `term.termName` if present, otherwise fall back to `term.name`.
- Strip any leading academic-year prefix from the fallback label if needed.

**Formatting Rules**
1. If `term.termName` is non-empty after trim, return it as-is.
2. Else, take `term.name` and remove a leading year-range prefix:
- Match `YYYY-YYYY` optionally followed by `学年` and whitespace.
- Example input: `2025-2026 2026春季` → output `2026春季`.
3. If no prefix match, return trimmed `term.name`.

**Implementation Notes**
- New utility: `TermNameFormatter` in `app/src/main/java/com/stupidtree/hitax/utils/`.
- Update display helpers:
- `app/src/main/java/com/stupidtree/hitax/ui/eas/imp/ImportTimetableActivity.kt`.
- `app/src/main/java/com/stupidtree/hitax/ui/eas/exam/ExamActivity.kt`.
- `app/src/main/java/com/stupidtree/hitax/ui/eas/score/ScoreInquiryActivity.kt`.
- `app/src/main/java/com/stupidtree/hitax/ui/eas/classroom/EmptyClassroomActivity.kt`.
- Update timetable name generation:
- `app/src/main/java/com/stupidtree/hitax/data/repository/EASRepository.kt` (`buildTimetableName`).

**Error Handling**
- If all term strings are blank, return an empty string and keep existing caller fallbacks.

**Testing**
- Add a small unit test for the formatter with representative inputs.

