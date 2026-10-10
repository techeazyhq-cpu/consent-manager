# Branch protection for `main`

The rules live in [`.github/branch-protection.json`](../.github/branch-protection.json) and are applied with one API
call, so they are reviewed like code and can be re-applied after a change. Branch protection is a repository setting:
a pull request cannot change it, someone with **admin** rights runs the steps below.

## What gets enforced

| Rule | Setting | Why |
|---|---|---|
| Pull request required | on, 0 approvals | Nothing lands on `main` without going through CI. Approvals are 0 while the repository has a single maintainer, who cannot approve their own PR; raise to 1 when a second reviewer joins |
| Required status checks | 6 checks, below | A failing build, test, coverage ratchet, frontend build or audit, image build, dependency or secret scan, or CodeQL run blocks the merge |
| Branch must be up to date (`strict`) | on | Checks run against what `main` will become, not a stale base |
| Include administrators (`enforce_admins`) | on | The repository owner cannot bypass the rules either |
| Stale approvals dismissed on new commits | on | Takes effect once approvals are above 0 |
| Conversations resolved before merge | on | Review comments cannot be silently left open |
| Force pushes and deletion of `main` | off | History on `main` cannot be rewritten or lost |

Required checks, by name exactly as GitHub reports them (`app_id` 15368 is GitHub Actions, so only the workflow can
satisfy a check):

| Workflow | Check | What it verifies |
|---|---|---|
| CI | `Backend build and tests` | License headers; `./gradlew build` (compile, unit and PostgreSQL integration tests, each service's ArchUnit layering test); `coverageVerification` (line coverage across all modules at or above the ratchet in `build.gradle.kts`) |
| CI | `Frontend build and audit` | `pnpm install --frozen-lockfile`, all three micro-frontends build, no HIGH or CRITICAL advisory in runtime dependencies |
| CI | `Service images build` | Every service image builds. Its Trivy scan is reported but not yet blocking; it becomes a gate on CRITICAL findings with the dependency upgrade |
| Security | `Dependency and secret scan` | Trivy on the repository: no HIGH or CRITICAL fixable vulnerability, committed secret or misconfiguration |
| Security | `CodeQL (java-kotlin)`, `CodeQL (javascript-typescript)` | No new code-scanning alert |

## Steps

1. Confirm you have admin rights and that every check above is green on `main`:

   ```bash
   gh repo view techeazyhq-cpu/consent-manager --json viewerPermission --jq .viewerPermission
   gh api "repos/techeazyhq-cpu/consent-manager/commits/main/check-runs?per_page=100" --jq '.check_runs[] | "\(.conclusion)\t\(.name)"'
   ```

   If a check name differs from the table (a job was renamed), update `.github/branch-protection.json` first: a
   required check that never reports blocks every merge.

2. Apply the protection from the repository root:

   ```bash
   gh api -X PUT repos/techeazyhq-cpu/consent-manager/branches/main/protection --input .github/branch-protection.json
   ```

3. Check it took effect:

   ```bash
   gh api repos/techeazyhq-cpu/consent-manager/branches/main/protection --jq '.required_status_checks.checks[].context'
   ```

To change a rule, edit the JSON in a pull request and re-run step 2 once it is merged.
