# GitHub Packages Publication Design

## Goal

Remove Sonatype Central publication from `extras` and publish the existing Maven artifacts to GitHub Packages using the current UTC date-and-run version scheme.

## Design

Keep the two existing Maven publications, `extrasApi` and `extrasPaper`, including their main artifacts, sources, and Javadocs. Replace the Sonatype NMCP repository configuration with a Maven repository at `https://maven.pkg.github.com/aincraft-org/extras`.

Gradle publication credentials come from `GITHUB_ACTOR` and `GITHUB_TOKEN`. The workflow grants `packages: write` and uses the built-in `GITHUB_TOKEN`; no long-lived Sonatype or signing secrets remain in the release workflow. The package version remains `yy.month.day.run_number`, generated in UTC, preserving the existing `year.month.day.version` intent without creating immutable-version collisions between workflow runs.

The quality/test/build job remains a blocking prerequisite. A main-branch push or manual workflow dispatch publishes both Maven publications after checks pass. Pull requests run checks only. The `maven-central` environment and Sonatype deployment-status polling are removed.

## Cleanup

Remove the NMCP settings plugin and `nmcpSettings` block, Sonatype-specific Gradle task dependencies, Sonatype workflow naming, secret mappings, deployment log/status handling, and generated local build artifacts. Build outputs remain ignored and are not committed.

## Verification

Run the complete local check/build and generate the GitHub Packages publication POMs without credentials:

```text
./gradlew clean check build generatePomFileForExtrasApiPublication generatePomFileForExtrasPaperPublication
```

Inspect the generated POM repository-independent metadata and verify the workflow YAML parses. Do not attempt a real package upload from local verification; publishing requires GitHub Actions credentials and repository package permission.
