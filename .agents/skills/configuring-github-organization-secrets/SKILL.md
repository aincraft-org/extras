---
name: configuring-github-organization-secrets
description: Use when a GitHub Actions workflow needs organization-level secrets, repository access scoping, secret precedence, or a manually triggered release workflow.
---

# Configuring GitHub Organization Secrets

Use organization-level Actions secrets when the repository belongs to the organization that owns the workflow and the same credentials may be managed centrally. Keep secret names identical to the workflow's `${{ secrets.NAME }}` references.

## Required secrets

Create these organization secrets:

```text
CENTRAL_PORTAL_USERNAME
CENTRAL_PORTAL_PASSWORD
SIGNING_KEY
SIGNING_PASSWORD
```

The Sonatype workflow maps them into Gradle properties:

```yaml
env:
  ORG_GRADLE_PROJECT_centralPortalUsername: ${{ secrets.CENTRAL_PORTAL_USERNAME }}
  ORG_GRADLE_PROJECT_centralPortalPassword: ${{ secrets.CENTRAL_PORTAL_PASSWORD }}
  ORG_GRADLE_PROJECT_signingKey: ${{ secrets.SIGNING_KEY }}
  ORG_GRADLE_PROJECT_signingPassword: ${{ secrets.SIGNING_PASSWORD }}
```

## Setup

1. Open **GitHub organization → Settings → Secrets and variables → Actions**.
2. Select **New organization secret**.
3. Enter one required name and its sensitive value.
4. Set **Repository access** to **Selected repositories**, then select `modularjobs` (recommended), or choose **All repositories** when deliberately sharing the credential.
5. Repeat for all four secrets.
6. Confirm the repository belongs to the organization where the secrets were created.

## Precedence and release gate

If a repository or environment defines a secret with the same name, the more specific value takes precedence over the organization secret. Check the `maven-central` environment as well: approval rules there can pause publishing until an authorized reviewer approves it.

To manually trigger the release workflow:

```text
Actions → Sonatype Central Portal → Run workflow → Branch: master
```

Do not print secret values or place them in command output. A workflow run only proves the configured steps executed; independently verify publication state before claiming a release.

## Quick reference

| Need | Location/action |
|---|---|
| Shared credentials | Organization Settings → Secrets and variables → Actions |
| Limit exposure | Repository access → Selected repositories → `modularjobs` |
| Manual run | Actions → Sonatype Central Portal → Run workflow → `master` |
| Publishing approval | `maven-central` environment rules |

## Sources

- [GitHub Actions secrets](https://docs.github.com/en/actions/how-tos/write-workflows/choose-what-workflows-do/use-secrets)
- [GitHub Actions security and secret precedence](https://docs.github.com/en/actions/reference/security/secrets)
