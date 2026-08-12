# Java CI Quality Gates Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make Checkstyle, PMD, SpotBugs, and Google Java Format blocking gates in the Java 21 Gradle build and existing GitHub Actions check job.

**Architecture:** Add pinned Gradle quality plugins through the version catalog and configure their tasks in `build.gradle.kts`. Keep repository-owned Checkstyle and PMD rules under `config/`; define Google Java Format as a verification-only task; attach all quality tasks to Gradle `check`. Update the existing workflow to call `check` explicitly before packaging/publication validation.

**Tech Stack:** Gradle Kotlin DSL, Java 21, Checkstyle, PMD, SpotBugs, google-java-format, GitHub Actions.

## Global Constraints

- All quality checks are blocking gates.
- Tool versions are pinned in `gradle/libs.versions.toml`.
- Google Java Format must verify and never rewrite CI files.
- Existing test, build, publication, and signing behavior remains intact.
- Do not expose credentials in logs.

### Task 1: Pin quality plugins

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `build.gradle.kts`

**Interfaces:**
- Produces Gradle plugin aliases for Checkstyle, PMD, SpotBugs, and a formatter plugin.

- [ ] Add plugin versions and aliases to the existing `[versions]` and `[plugins]` sections, using versions compatible with Gradle 9.6.1 and Java 21.
- [ ] Apply aliases in the root `plugins` block without changing existing plugins.
- [ ] Run `./gradlew tasks --all` and verify the quality task groups are registered.

### Task 2: Add repository-owned rule configuration

**Files:**
- Create: `config/checkstyle/checkstyle.xml`
- Create: `config/pmd/pmd.xml`

**Interfaces:**
- Checkstyle and PMD tasks consume these files from the project root.

- [ ] Add a maintainable baseline ruleset that checks production and test Java without generated/build directories.
- [ ] Configure rule violations to fail the task.
- [ ] Run the individual Checkstyle and PMD tasks and correct only source violations required by the chosen baseline.

### Task 3: Wire all checks into Gradle lifecycle

**Files:**
- Modify: `build.gradle.kts`

**Interfaces:**
- `./gradlew check` runs Checkstyle, PMD, SpotBugs, and Google Java Format verification.

- [ ] Configure Checkstyle and PMD source sets and reports.
- [ ] Configure SpotBugs to analyze main compiled classes and fail on findings.
- [ ] Configure the formatter task as verification-only and make it depend on Java compilation where required.
- [ ] Make `check` depend on every quality verification task.
- [ ] Run `./gradlew check` and fix configuration/source findings until it passes.

### Task 4: Add CI invocation

**Files:**
- Modify: `.github/workflows/sonatype.yml`

**Interfaces:**
- The existing `check` job blocks `publish` when any quality gate fails.

- [ ] Add an explicit `./gradlew check` invocation before the existing clean test/build/publication checks, or fold it into the same command without removing existing tasks.
- [ ] Keep Java 21 setup and Gradle setup unchanged.
- [ ] Validate YAML syntax and inspect the final command ordering.

### Task 5: Verify end-to-end gates

**Files:**
- No source changes expected.

- [ ] Run `./gradlew clean check`.
- [ ] Run the existing structural publication command sequence with `-PskipSigning`.
- [ ] Confirm task output includes all four quality checks and existing tests/build/publication checks.
- [ ] Inspect `git diff --check` and repository status before committing the implementation as one atomic change.
