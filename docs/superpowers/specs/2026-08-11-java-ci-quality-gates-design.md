# Java CI Quality Gates Design

## Goal

Add blocking CI checks for Checkstyle, PMD, SpotBugs, and Google Java Format to the existing Java 21 Gradle build.

## Design

Use Gradle plugins and make every quality task part of the standard `check` lifecycle. This gives developers and CI the same entry point (`./gradlew check`) and keeps tool versions reproducible in the build configuration.

- **Checkstyle:** use a repository-owned `config/checkstyle/checkstyle.xml` and apply it to main and test Java sources.
- **PMD:** use a repository-owned `config/pmd/pmd.xml`, run against main and test sources, and fail on violations.
- **SpotBugs:** analyze compiled main classes and fail on the configured high-confidence findings. Keep generated/build output outside analysis.
- **Google Java Format:** add a verification-only Gradle task using a pinned formatter version. The task checks formatting and never rewrites files in CI.

The existing workflow's `check` job will invoke `./gradlew check` before its current test, build, and publication-structure commands. The publish job remains dependent on that check job and therefore cannot publish if a quality gate fails.

## Error handling and rollout

All four checks are blocking from their first introduction. Existing source must pass the selected baseline rules; do not weaken rules by excluding production code. Test fixtures and generated resources may use narrowly scoped exclusions only when the tool cannot analyze them meaningfully.

## Verification

Verify locally with:

```text
./gradlew check
```

Also run the same publication-structure command sequence used by CI to ensure the new `check` dependency does not interfere with packaging or publication validation.
