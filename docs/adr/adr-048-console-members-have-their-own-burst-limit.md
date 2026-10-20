# ADR-048: People in the DF Console have a per-second limit of their own; the plan's is for API clients

- **Status:** Accepted
- **Date:** 2026-10-20

## Context

The gateway limits each fiduciary's requests a second to its plan's quota (`bil.sub_pln.req_per_sec_qta`; FREE is 5),
counting in Redis under `ratelimit:<dfId>:<epochSecond>`. It counted every call carrying the fiduciary's identity, so
its people in the DF Console shared the bucket with its systems.

- One console page load makes about 5 calls: the shell's `df-reg dfs/{id}`, `cns-cmd prps-defs` and `cns-qry cns-arts`,
  plus the page's own. React StrictMode doubles that in development. A FREE fiduciary's pages failed with 429
  (`urn:cm:problem:rate-limit-exceeded`) just from being opened.
- A person opening a page could also use up the share its systems' API calls rely on, and the other way round.
- The per-second quota protects capacity (ADR-016: it fails open), it isn't an authorisation. A plan sells API
  throughput; people clicking through the console aren't what it is meant to meter.
- Exempting people outright would leave any member token unlimited, a script holding one included.

## Decision

1. **A fiduciary's systems keep the plan's quota.** A token from the `cm-df` realm whose only fiduciary role is `DF`
   (an API client, or an API key, which the gateway exchanges for the API client's token, ADR-041) counts in
   `ratelimit:<dfId>:<epochSecond>` against `reqPerSecQta`, as before.
2. **Each person has a bucket of their own.** A token from the `cm-df` realm with a member role (`DF_*`, ADR-033;
   `AuthCtx.isDfMember()`) counts in `ratelimit:member:<dfId>:<sub>:<epochSecond>` against
   `cm.rate-limit.member-req-per-sec`, 20 by default, the same on every plan. It doesn't touch the systems' bucket or
   ask cm-bil-svc for the quota.
3. **Monthly usage is unchanged.** Every call with the fiduciary's identity, people's included, still counts in
   `usg:<dfId>:<yyyy-MM>` (ADR-039). Counting only API clients is still a separate decision.
4. A refusal is the same for both: 429, `Retry-After: 1`, `urn:cm:problem:rate-limit-exceeded`.

## Consequences

- The console loads on every plan, and its people can't starve their own systems' API calls.
- A fiduciary with many people signed in at once can send more than its plan's per-second quota in total. Capacity is
  bounded per person instead; the monthly allowance still covers everything.
- A member token used from a script gets 20 a second, not the plan's rate. Members are the fiduciary's own people,
  invited by an Owner or Administrator; that is the trade for not refusing the console.
- The DF Console's Billing page says the per-second limit is for the fiduciary's systems.
- Found while making this change: the filter forwarded every fiduciary request twice (`switchIfEmpty` after a
  `Mono<Void>` that always completes empty), and still forwarded one it had refused with 429. It now decides before
  forwarding, once.

## Compliance impact

None: the counters hold fiduciary ids, member subject ids and counts, for two seconds.
