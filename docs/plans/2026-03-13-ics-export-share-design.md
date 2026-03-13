# ICS Export Share Fix Design

**Date:** 2026-03-13

## Goal
Ensure exported .ics files are recognized by calendar apps on Android by fixing the share intent (URI + MIME + permissions) while keeping ical4j generation unchanged.

## Scope
- Keep existing ical4j export logic intact.
- Fix only the share/export intent so calendar apps can receive the file.
- No behavioral changes to event content or recurrence rules.

## Architecture
The app continues to generate .ics using ical4j into app external files. The UI layer will share the file using a FileProvider-generated content URI, MIME `text/calendar`, and read permissions. This aligns with Android intent resolution rules for calendar apps.

## Components
- `TimetableDetailActivity`: update share intent to use FileProvider and correct MIME.
- `TimetableManagerActivity`: same change to keep behavior consistent.
- (Optional) small helper in `FileProviderUtils` or a new `ShareUtils` to avoid duplicate intent wiring.

## Data Flow
1. `exportToICS` writes `.ics` to `getExternalFilesDir("ics")`.
2. UI receives the file path from `DataState`.
3. Convert `File` -> `content://` URI via `FileProviderUtils.getUriForFile`.
4. Create `ACTION_SEND` intent with `text/calendar`, `EXTRA_STREAM` set to URI, and `FLAG_GRANT_READ_URI_PERMISSION`.
5. System share sheet surfaces calendar apps.

## Error Handling
- Export failure remains unchanged (existing `DataState` failure toast).
- If no apps match the intent, the system will show default “no apps available” UI.

## Testing
- Manual: export and verify share sheet includes calendar apps (system calendar, Google Calendar, Outlook).
- Validate intent MIME `text/calendar` and URI scheme `content://`.
- No automated tests added for intent routing.
