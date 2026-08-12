# GitHub Packages Publication Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace Sonatype Central publication with GitHub Packages publication while preserving both Maven artifacts and UTC date-and-run versions.

**Architecture:** Keep `maven-publish` and the existing `extrasApi`/`extrasPaper` publications. Remove NMCP settings and Sonatype-specific task wiring. Configure one GitHub Packages Maven repository using `GITHUB_ACTOR` and `GITHUB_TOKEN`, then make the existing main-branch workflow publish both publications after the blocking quality/build job.

**Tech Stack:** Gradle Kotlin DSL, Maven Publish, GitHub Actions, GitHub Packages, Java 21.

## Global Constraints

- Publish to `https://maven.pkg.github.com/aincraft-org/extras`.
- Use the built-in `GITHUB_TOKEN`; do not retain Sonatype credentials or signing requirements.
- Preserve UTC version format `yy.month.day.run_number`.
- Pull requests run checks but do not publish.
- Main pushes and manual dispatch publish after checks pass.
- Keep `extrasApi` and `extrasPaper`, including sources and Javadocs.
- Generated build artifacts remain ignored and must not be committed.
- Delete `.agents/skills/configuring-github-organization-secrets/SKILL.md`, because it documents Sonatype Central credentials and is no longer applicable.

### Task 1: Remove Sonatype/artefact configuration

**Files:**
- Modify: `settings.gradle.kts`
- Modify: `build.gradle.kts`
- Modify: `.gitignore` if obsolete Sonatype-only entries remain
- Delete: `.agents/skills/configuring-github-organization-secrets/SKILL.md`

**Interfaces:**
- Gradle retains `maven-publish` publications but no NMCP plugin, settings block, or Sonatype task dependencies.

- [ ] Remove the `com.gradleup.nmcp.settings` plugin and `nmcpSettings` block from `settings.gradle.kts`.
- [ ] Remove publication-time `gradle.taskGraph` signing/version guard that only protected Central releases.
- [ ] Remove `signing` plugin, signing configuration, and `sign*` publication task dependencies.
- [ ] Remove `nmcp*` and Sonatype-only task references from Gradle configuration.
- [ ] Preserve artifact IDs, POM metadata, sources, Javadocs, and build task dependencies.
- [ ] Delete the tracked local Sonatype credentials skill and verify it no longer exists.
- [ ] Run `./gradlew tasks --all` and confirm no `nmcp` or Sonatype tasks remain.

### Task 2: Configure GitHub Packages repository

**Files:**
- Modify: `build.gradle.kts`

**Interfaces:**
- Gradle publishing resolves credentials from `GITHUB_ACTOR` and `GITHUB_TOKEN` and targets the GitHub Packages Maven URL.

- [ ] Add a Maven repository under `publishing.repositories` with URL `https://maven.pkg.github.com/aincraft-org/extras`.
- [ ] Set `credentials { username = System.getenv("GITHUB_ACTOR"); password = System.getenv("GITHUB_TOKEN") }`.
- [ ] Keep the repository declaration usable for publication configuration without requiring credentials during local checks.
- [ ] Run `./gradlew generatePomFileForExtrasApiPublication generatePomFileForExtrasPaperPublication`.

### Task 3: Replace Sonatype workflow publication

**Files:**
- Rename: `.github/workflows/sonatype.yml` → `.github/workflows/github-packages.yml`

**Interfaces:**
- Workflow publishes both Maven publications to GitHub Packages only after the check job passes.

- [ ] Rename the workflow display name to `CI and GitHub Packages`.
- [ ] Change permissions to `contents: read` and `packages: write`.
- [ ] Keep checkout, Java 21, Gradle setup, and the quality/test/build check command.
- [ ] Keep UTC version generation as `date -u +'%y.%-m.%-d'` plus `${GITHUB_RUN_NUMBER}`.
- [ ] Replace Sonatype environment/secrets with `GITHUB_ACTOR` and `GITHUB_TOKEN` environment variables.
- [ ] Run `./gradlew publishExtrasApiPublicationToGitHubPackages publishExtrasPaperPublicationToGitHubPackages -PreleaseVersion=...` or the exact generated repository task names.
- [ ] Remove rerun rejection only if it is Sonatype-immutability-specific; retain protection against accidental duplicate publication when GitHub Packages permits overwrites only if the chosen task behavior requires it.
- [ ] Remove deployment log parsing and Central status polling.
- [ ] Validate YAML syntax.

### Task 4: Remove generated artifacts and verify

**Files:**
- No committed generated files expected.

- [ ] Run `./gradlew clean` to remove local build artifacts.
- [ ] Run `./gradlew clean check build generatePomFileForExtrasApiPublication generatePomFileForExtrasPaperPublication`.
- [ ] Inspect task output for successful quality checks, tests, build, and POM generation.
- [ ] Confirm `git status --short` contains only intentional source/config/docs changes and no `build/` or `.gradle/` artifacts.
- [ ] Run `git diff --check` and inspect the final diff before atomic commits.
