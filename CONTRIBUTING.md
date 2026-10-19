# Contributing to consent-manager

Thank you for helping improve consent-manager. This guide explains how to report problems, set up a development
environment, and get a change merged. Read it once before your first pull request; it is short on purpose.

## Contents

- [Ground rules](#ground-rules)
- [Reporting a security vulnerability](#reporting-a-security-vulnerability)
- [Reporting a bug or asking for a feature](#reporting-a-bug-or-asking-for-a-feature)
- [Setting up a development environment](#setting-up-a-development-environment)
- [Making a change](#making-a-change)
- [Coding standards](#coding-standards)
- [Pull request guidelines](#pull-request-guidelines)
- [Review and merge](#review-and-merge)
- [License](#license)

## Ground rules

- **Be respectful.** Assume good intent, keep feedback about the code, and help newcomers.
- **Nothing lands on `main` directly.** Every change, however small, goes through a branch and a pull request, and
  every required check must be green before it merges.
- **One change per pull request.** A bug fix, a feature or a refactoring, not all three.
- **Discuss large changes first.** Open an issue before you start on anything that changes the architecture, a
  public API, a database schema, the event contracts on Pulsar or the deployment model, so nobody spends a week on a
  direction that will not merge.

## Reporting a security vulnerability

**Do not open a public issue for a security problem.** Report it privately by email to
[vasantha.kumar@hotmail.com](mailto:vasantha.kumar@hotmail.com). Include:

- the affected version or commit;
- the steps to reproduce, or a proof of concept;
- what an attacker gains.

You will get an acknowledgement within a few working days. Please give a fix the chance to ship before you disclose the
problem publicly.

## Reporting a bug or asking for a feature

Search the existing issues first; if one already covers it, add your details there.

A useful **bug report** says:

- what you did, what you expected and what happened;
- the version or commit, and how you run it (Docker Compose, from the IDE);
- relevant logs, with tokens, passwords, consent tokens and personal data (email addresses, phone numbers, names)
  removed;
- the problem type (`urn:cm:problem:...`) and correlation id from the error response, if you have them.

A useful **feature request** describes the problem you are trying to solve before the solution you have in mind.

## Setting up a development environment

You need:

- **JDK 21** (the Gradle wrapper downloads Gradle itself);
- **Node 20** or newer and **pnpm**;
- **Docker** with Docker Compose. Docker runs the local stack and the PostgreSQL integration tests
  (Testcontainers).

```bash
git clone https://github.com/techeazyhq-cpu/consent-manager.git
cd consent-manager
./gradlew build                                  # every service, the Keycloak providers and their tests
docker compose -f infra/docker-compose.yml up -d --build
(cd frontend && pnpm install && pnpm dev:dp-portal)
```

The [README](README.md) lists the services, ports and demo sign-ins.

## Making a change

1. **Branch** from an up-to-date `main`:

   ```bash
   git fetch origin
   git checkout -b fix/short-description origin/main
   ```

   Name the branch after the kind of change: `feat/`, `fix/`, `docs/`, `refactor/`, `test/`, `perf/`, `build/`,
   `ci/` or `chore/`, followed by a few words in kebab case.

2. **Write a failing test first**, then make it pass, then tidy up. Every bug fix comes with a test that fails without
   it.

3. **Run the same checks CI runs** before you push:

   ```bash
   node scripts/license-headers.mjs              # every source file carries the license header (--fix adds it)
   ./gradlew build coverageVerification          # compile, unit and integration tests, architecture rules, coverage ratchet
   (cd frontend && pnpm install --frozen-lockfile && pnpm -r build && pnpm audit --prod --audit-level high)
   ```

4. **Keep static analysis at zero.** Fix what a scan reports (CodeQL, Trivy). Suppress a finding only when it does
   not apply, and then state why next to the suppression.

5. **Commit** in small, logical steps using [Conventional Commits](https://www.conventionalcommits.org/):

   ```text
   fix: keep the consent token valid until the artefact's own expiry
   feat: let a data principal revoke every consent given to one fiduciary
   ```

   - **Type:** one of `feat`, `fix`, `docs`, `refactor`, `test`, `perf`, `build`, `ci` or `chore`.
   - **Summary:** imperative and lower case, with no full stop, describing the effect rather than the code.
   - **Body:** add one when the reason is not obvious. Explain *why*; the diff already shows *what*.

6. **Push and open a pull request** against `main`, following the guidelines below.

## Coding standards

**Architecture**

- **Respect the layering.** Each service is split into `-dom` (domain model, ports), `-app` (use cases), `-infra`
  (adapters: R2DBC, Pulsar, HTTP clients) and `-if-rest` (HTTP interface), wired together in `-boot`. Domain depends on
  nothing internal; application depends on domain only; infrastructure and interfaces never depend on each other. An
  ArchUnit test in every service enforces this. See the [architecture decision records](docs/adr).
- **Keep bounded contexts apart.** Services never read each other's schemas. They talk through Pulsar events written
  by the transactional outbox, or through an explicit `/internal/**` endpoint.
- **Commands and queries stay separate.** State changes go through `cm-cns-cmd-svc`; reads come from
  `cm-cns-qry-svc`'s projections.
- **Follow the naming convention.** Domain types use the abbreviated ubiquitous language (`CnsReq`, `CnsArt`, `Df`,
  `Dp`, `Ntf`); keep new names consistent with the glossary in the [design](docs/design.md).

**Code**

- **Clean code.** Use small methods, intention-revealing names and no dead code. Explain intent in Javadoc (or TSDoc)
  on the type or method; keep inline comments for what the code cannot say.
- **Reactive all the way.** Services are WebFlux and R2DBC. Never block an event-loop thread; isolate unavoidable
  blocking calls on `Schedulers.boundedElastic()` and say why.
- **License header.** Every new source file starts with the Apache 2.0 header and an author line. Copy it from any
  existing file of the same type.

**Data and secrets**

- **Database changes** go in a new Flyway migration (`V<n>__description.sql`) in the owning service's `-boot` module.
  - Never edit a migration that has already been merged.
  - Keep each step backward compatible with the running version.
- **Event contracts** on Pulsar are versioned in the topic name (`...-v1`). A breaking change gets a new version and a
  period in which both are published.
- **No secrets in the repository.** Configuration comes from environment variables or the deployment's secret store.
- **Personal data** (email addresses, phone numbers, names, notice text) and consent tokens never go into logs,
  metrics, traces or error messages.

**Decisions and documentation**

- **Significant decisions get an ADR.** Add `docs/adr/adr-NNN-short-title.md` with the next free number, following
  the existing ones (context, decision, consequences). Amend an existing ADR instead of contradicting it silently.
- **Docs follow behaviour.** When you change what a user or operator sees, update the [README](README.md), the
  relevant ADR and the service's own notes in the same pull request.

## Pull request guidelines

A pull request is easy to review, and therefore quick to merge, when it is:

- **Focused.** One purpose. If you find an unrelated problem on the way, open an issue or a separate pull request.
- **Small.** Aim for a diff a reviewer can read in one sitting. Split large work into a series of pull requests that
  each leave `main` working.
- **Up to date.** Rebase on, or merge, the latest `main` before asking for review, and resolve conflicts yourself.
- **Green.** Every required check passes. If a check fails for a reason unrelated to your change, say so in the
  pull request rather than re-running it until it passes.

### Title

Use the same Conventional Commits form as a commit summary, for example
`fix: refuse a consent request for an inactive fiduciary`. The title becomes the merge commit's summary.

### Description

The [pull request template](.github/pull_request_template.md) is filled in automatically. Complete every section:

- **Summary:** what changes and why, in a few sentences a reviewer can read before the diff. Link the issue it closes
  (`Closes #123`).
- **Changes:** the notable changes, grouped by area, including anything a reviewer might miss: a migration, a new
  event or topic, a new configuration property, a changed default, a new dependency.
- **Test plan:** what you ran and what you checked by hand, as a checklist. Show evidence for behaviour the tests
  cannot see, such as a screenshot of a UI change.
- **Risk and rollback:** what could break, who is affected, and how to undo it. This is required for migrations, event
  contract changes and changes to authentication, authorisation, consent lifecycle or the audit ledger.

### Required checks

These must all pass before a pull request can merge. They are defined in `.github/workflows` and enforced by the
branch protection described in [docs/branch-protection.md](docs/branch-protection.md).

| Check | What it verifies |
|---|---|
| Backend build and tests | License headers, the OPA policies (`opa check`, `opa test`), `./gradlew build` (unit and PostgreSQL integration tests, ArchUnit layering), and line coverage at or above the ratchet in `build.gradle.kts` |
| Frontend build and audit | The three micro-frontends build from the locked dependencies, with no HIGH or CRITICAL advisory in runtime dependencies |
| Service images build | Every service image builds (its vulnerability scan is reported, and becomes blocking after the dependency upgrade) |
| Dependency and secret scan | No HIGH or CRITICAL vulnerable dependency, committed secret or misconfiguration (Trivy) |
| CodeQL (Java, TypeScript) | No CodeQL finding (results are checked in the job: the private repository has no code scanning) |

The coverage minimum only goes up: when a pull request raises coverage, raise `coverageMinimum` with it.

## Review and merge

- A maintainer reviews within a few working days. Answer every comment, either with a change or with a reason, and
  resolve the conversation once it is settled.
- Pull requests are merged with a merge commit once the checks are green and conversations are resolved.
- Dependabot's pull requests follow the same rules; a major version upgrade is a deliberate change with its own
  pull request.

## License

By contributing, you agree that your contributions are licensed under the [Apache License 2.0](LICENSE).
