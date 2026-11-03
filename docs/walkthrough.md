# Manual walkthrough

A check by hand, in a browser, of what `scripts/verify-phase1.sh` checks through the API: the three web apps, from
onboarding a fiduciary to a principal withdrawing consent. Run it before a demo or a release, after
`./gradlew build` and the integration tests pass.

Start everything as in the [README](../README.md#run-it), and each web app in its own terminal:

```bash
cd frontend && pnpm dev:cm-admin
cd frontend && pnpm dev:df-app
cd frontend && pnpm dev:dp-portal
```

Every code and notice arrives in Mailpit, http://localhost:8025. Use a new address for the fiduciary's contact and
for the principal on each run, for example with the date in it.

## 1. Sign in to CM Admin

- [ ] http://localhost:5176 asks for an email address, not a password.
- [ ] `admin@cm.local` gets a 6-digit code in Mailpit; a wrong code is refused, the right one signs in.
- [ ] The dashboard opens; Billing, Staff, Security and Audit are in the menu.
- [ ] Signed in as `operator@cm.local` instead, the pages that change things are read-only.

## 2. Onboard a fiduciary

- [ ] **Onboard tenant**: a name, a contact email (`owner-<date>@example.com`) and the STANDARD plan. It is listed as
      Registered.
- [ ] **Activate** it. It shows as Activated, and Mailpit has an invitation to the contact address as its Owner.
- [ ] Its **Subscription** tab shows STANDARD, ACTIVE, and this month's usage at 0.

## 3. Sign in to the DF Console and raise a request

- [ ] http://localhost:5175 signs in the Owner from step 2 with a code from Mailpit (their own account, not the API
      client secret).
- [ ] **Purposes**: create a purpose with EMAIL needed and NAME optional, and publish version 1.
- [ ] **Raise**: ask `dp-<date>@example.com` for that purpose, then **Send request**. It is listed as Raised.
- [ ] Mailpit has the notice to `dp-<date>@example.com`, with the purpose's title and text.

## 4. Sign up as the principal and decide

- [ ] http://localhost:5174 with `dp-<date>@example.com`: there is no account yet, and the code from Mailpit creates
      one.
- [ ] The request from step 3 is already under **Awaiting your decision**: it was linked to the new account when
      they signed up.
- [ ] The fiduciary shows with its verified name. The decision screen shows the notice, EMAIL as needed and NAME as
      optional.
- [ ] Untick NAME, choose a shorter period, then **Give consent to 1 of 2**. The consent moves to **Consents you've
      given**, with EMAIL only and the chosen end date.
- [ ] Its history shows a receipt, and the export downloads.

## 5. See it from the fiduciary's side

- [ ] In the DF Console the request now shows as given, for EMAIL only. The dashboard counts it.
- [ ] The **Playground** verifies the consent for its purpose (allowed), and is refused for another purpose.

## 6. Withdraw

- [ ] In the DP portal, **Withdraw consent**. It moves to **Declined, withdrawn or expired**.
- [ ] In the Playground, verifying it again is refused at once, though its token has not expired.
- [ ] If the fiduciary has a webhook endpoint, its delivery log shows the withdrawal.

## 7. Plans, usage and suspension in CM Admin

- [ ] The fiduciary's **Subscription** tab: usage has gone up by the requests and verifications above.
- [ ] **Change plan** to FREE. The new plan and its limits show at once.
- [ ] **Suspend** the subscription. In the DF Console, raising a request is now refused, saying the subscription is
      not active.
- [ ] **Reactivate** it, and a request goes through again.
- [ ] **Audit** lists each of these changes with who made it.

## 8. Sign out

- [ ] Signing out of each app returns to its sign-in page, and Back does not show the signed-in pages.

Record the date, the commit (`git rev-parse --short HEAD`) and anything that failed in the pull request or release
that the walkthrough was for.
