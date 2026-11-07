# Runbook: a stuck account erasure

When a data principal closes their account, `cm-dsr-svc` runs the erasure saga ([ADR-059](../adr/adr-059-closing-an-account-erasure-saga.md)).
A step that gets no answer is sent again every two minutes. After 8 attempts the erasure is **STUCK**:

- `cm-dsr-svc` logs `ERROR Erasure <id> is STUCK at <step>`; alert on it;
- the audit ledger records `dp-erasure.stuck`;
- CM Admin's **Account erasures** page lists it under *Stuck*, with the step and the last error.

The principal sees "Closing your account is delayed" if they are still signed in. Their data is part-way erased: nothing
is lost by waiting, and every step is safe to run again.

## Find the cause

| Stuck at | Owned by | Usual cause | Check |
|---|---|---|---|
| `NOTICE_AND_DEVICES` | cm-ntf-svc | the service down, or not subscribed to `dsr/cmd-erasure-step` | `docker logs infra-cm-ntf-svc-1 \| grep -i erasure` |
| `CONSENTS` | cm-cns-cmd-svc | the service or its database down | `docker logs infra-cm-cns-cmd-svc-1 \| grep -i erasure` |
| `AUDIT` | cm-aud-svc | the service down | `docker logs infra-cm-aud-svc-1 \| grep -i erasure` |
| `REQUESTS` | cm-dsr-svc | its database | the last error on the erasure |
| `IDENTITY` | cm-dsr-svc → Keycloak | Keycloak down, or the admin credentials wrong (`CM_KEYCLOAK_ADMIN-*`) | the last error on the erasure |

For a remote step (the first three), also check Pulsar:

```bash
docker exec common-pulsar-1 bin/pulsar-admin topics stats persistent://cm/dsr/cmd-erasure-step-v1
```

Each service has its own subscription (`ntf-erasure-notice-and-devices-sub`, `cnscmd-erasure-consents-sub`,
`aud-erasure-audit-sub`). A large backlog on one means that service isn't consuming.

## Fix it, then retry

Once the cause is fixed, an **administrator** presses **Retry** on the erasure in CM Admin, or calls:

```bash
curl -X POST -H "Authorization: Bearer $ADMIN_TOKEN" https://api.example.com/api/dsr/erasures/<id>/retry
```

The step is sent again at once and the saga carries on. A step done twice changes nothing.

## Don't

- Don't delete the principal's Keycloak user by hand before the other steps are done: the principal would lose the
  portal while their consents are still in force. Identity is deliberately the last step.
- Don't edit `dsr.dp_erasure` by hand. Retry is the only supported way to restart a step.
