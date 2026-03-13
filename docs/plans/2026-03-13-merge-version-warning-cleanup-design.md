# Merge, Version Bump, and Warning Cleanup Design

**Date:** 2026-03-13

## Goal
Rebase the ICS share fix branch onto the latest `origin/master`, resolve conflicts, bump version to `1.4.8` with `versionCode 2026031301`, and reduce compiler warnings using only behavior-preserving edits.

## Scope
- Rebase current branch onto `origin/master`.
- Resolve conflicts (expected in `EASRepository.kt`/`EASource.kt` if any).
- Update `app/build.gradle` version fields.
- Clean up warnings only when changes are behavior-preserving (unused parameter renames, redundant safe calls, selective `@Suppress`).
- Avoid deprecated API migrations or logic changes.

## Architecture
The branch is rebased onto upstream to incorporate remote changes before any warning cleanup. Version bump and warning cleanup are applied after rebase to minimize conflict surface. Warning changes are limited to semantic no-ops to avoid functional regressions.

## Components
- Git: `rebase origin/master` and resolve conflicts.
- Versioning: `app/build.gradle` updates.
- Warning cleanup: primarily within `app/` module files listed by compiler output; only safe, no-op edits.

## Data Flow
1. Fetch and rebase onto `origin/master`.
2. Resolve conflicts and verify build compiles.
3. Update version fields.
4. Apply warning cleanups (safe only).
5. Run unit tests.

## Error Handling
- If a change risks behavior, skip it.
- If rebase conflicts are complex, favor remote changes and reapply local fixes carefully.
- If compilation fails, back out the most recent warning cleanup and re-evaluate.

## Testing
- Run `./gradlew :app:testDebugUnitTest` using JDK 17 options.
- Instrumentation test remains optional and is not required for this pass.
