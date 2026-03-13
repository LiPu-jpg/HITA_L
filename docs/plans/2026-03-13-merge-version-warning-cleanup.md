# Merge, Version Bump, and Warning Cleanup Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Rebase onto `origin/master`, resolve conflicts, bump version to 1.4.8 (code 2026031301), and reduce compiler warnings using behavior-preserving edits.

**Architecture:** Rebase first to minimize conflicts, then update version fields and perform safe warning cleanups. Avoid deprecated API migrations and logic changes to reduce risk.

**Tech Stack:** Git, Android (Kotlin), Gradle.

---

### Task 1: Rebase onto `origin/master`

**Files:**
- Modify: (git history)

**Step 1: Rebase**

Run: `git fetch origin`
Expected: remote updates fetched

Run: `git rebase origin/master`
Expected: either clean rebase or conflict markers

**Step 2: Resolve conflicts (if any)**

If conflicts:
- Open conflicted files (expected: `app/src/main/java/com/stupidtree/hitax/data/repository/EASRepository.kt`, `app/src/main/java/com/stupidtree/hitax/data/source/web/eas/EASource.kt`)
- Keep upstream changes and re-apply local ICS/share fixes if needed

**Step 3: Continue rebase**

Run: `git add <conflict-files>`
Run: `git rebase --continue`
Expected: rebase completes

**Step 4: Commit (if rebase required manual resolution)**

No new commit is required unless rebase was aborted and rerun.

---

### Task 2: Version Bump

**Files:**
- Modify: `app/build.gradle`

**Step 1: Write failing test**

N/A (version bump)

**Step 2: Apply change**

Set:
- `versionName "1.4.8"`
- `versionCode 2026031301`

**Step 3: Commit**

```bash
git add app/build.gradle
git commit -m "chore: bump version to 1.4.8"
```

---

### Task 3: Warning Cleanup (Behavior-Preserving Only)

**Files:**
- Modify: (target warnings listed by compiler)

**Step 1: Identify warnings to fix safely**

Focus on:
- Unused parameters -> rename to `_` or remove if unused and not overriding
- Redundant safe calls -> remove `?.` where receiver is non-null
- Unchecked casts -> add `@Suppress("UNCHECKED_CAST")` locally

Avoid:
- Deprecated API migrations
- Signature or logic changes

**Step 2: Apply minimal edits**

Make small, isolated edits per file.

**Step 3: Commit**

```bash
git add <touched-files>
git commit -m "chore: reduce compiler warnings"
```

---

### Task 4: Verification

**Files:**
- Test: N/A

**Step 1: Run tests**

Run:
`./gradlew -Dorg.gradle.java.home=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home -Dorg.gradle.jvmargs="--add-exports=jdk.compiler/com.sun.tools.javac.main=ALL-UNNAMED" :app:testDebugUnitTest`
Expected: PASS (warnings may remain but should be reduced)

**Step 2: Document any remaining warnings**

Note warnings that require API changes and were intentionally skipped.
