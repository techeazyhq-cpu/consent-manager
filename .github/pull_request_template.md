<!-- Title: Conventional Commits form, e.g. "fix: refuse a consent request for an inactive fiduciary". See CONTRIBUTING.md. -->

## Summary

<!-- What changes and why, in a few sentences. Link the issue it closes. -->

Closes #

## Changes

<!-- Notable changes grouped by area. Call out migrations, new events or topics, new configuration properties, changed defaults and new dependencies. -->

-

## Test plan

<!-- What you ran and what you checked by hand. Add screenshots for UI changes. -->

- [ ] `./gradlew build`
- [ ] `pnpm build` in `frontend/`, if a frontend changed
- [ ] Checked by hand:

## Risk and rollback

<!-- What could break, who is affected, and how to undo it. Required for migrations, event contracts, authentication, authorisation, the consent lifecycle and the audit ledger. -->

## Checklist

- [ ] One focused change, rebased on or merged with the latest `main`
- [ ] Tests added or updated; a fixed bug has a test that fails without the fix
- [ ] Layering respected (the service's ArchUnit test passes); no service reads another's schema
- [ ] No new CodeQL or Trivy finding, and every suppression states its reason
- [ ] Database changes are a new Flyway migration; no merged migration was edited
- [ ] No secrets, consent tokens or personal data in code, tests or logs
- [ ] New source files carry the license header
- [ ] README, ADRs and notes updated where behaviour or a decision changed
