# ADR-067: A public status page, measured by the gateway

- **Status:** Accepted
- **Date:** 2026-11-08
- **Issue:** #115

## Context

When something is wrong, customers (fiduciaries' people and principals) need to know whether it is the platform, and staff need a
place to say what is happening. There was no public statement of the platform's health at all, and nothing that measured it over
time: each service's health was visible only to whoever could reach its port.

## Decision

1. **The gateway measures it.** Every 30 seconds `cm-gw` asks each service behind a route for its health, and sign-in (a
   realm's published keys). It already knows every route's address, so a new service that gets a route and a label is watched.
   A probe that does not answer 2xx within 3 seconds has failed.
2. **States people can read.** One failed probe is a blip (*having problems*); two in a row, about a minute, is *down*; a
   success clears it at once. The platform as a whole is *operational*, *degraded* (something is off) or a *major outage* (a
   critical part is down: organisation accounts and API keys, consent requests and decisions, consent records and verification,
   sign-in).
3. **Uptime is counted, never claimed.** Each probe adds to an hour bucket and a day bucket in Redis (so every gateway replica
   adds to one history). The page shows the share of probes that succeeded over the last 24 hours and 30 days, or a dash for a
   component not watched yet. Buckets expire (hours after 2 days, days after 35).
4. **Public, with no sign-in** (`GET /api/status`): a status page is read when signing in is what is broken. It is cached for 15
   seconds, so a crowd reading it costs nothing. It says what a customer can act on: a name for each part ("Consent requests and
   decisions"), its state and its uptime, and the notices below. It says nothing of how the platform is built: no address,
   port, version, endpoint or error text.
5. **Staff post notices** (`POST /api/status/notices`, an operator or an administrator): an *incident*, planned *maintenance*, or
   *information*, with a title, optional plain-text details and the parts it affects. A notice stays up until it is resolved and for
   a week after, so people can see what happened. Status and uptime are measured, and notices are written: the two are never
   mixed, so a notice can't claim everything is fine while the probes say otherwise.
6. **Where it is shown.** The DF Console serves it at `#/status`, with no sign-in (like the sign-up page), and links to it from the
   sign-up page; CM Admin has a *Platform status* page for posting and resolving notices.

## What it found

The first minutes of probing showed `cns-qry` failing probes while a person was using the console: the service was at 85% of its 512 MB
memory limit and slow to answer under load. That is exactly what the page is for.

## Not yet

- **Notices live in Redis**, not a database: they are lost if Redis is. They are short-lived, but a notice about an outage that
  took Redis with it is the one case that matters. They should move to a database table.
- **No subscriptions** (email or webhook when the status changes), no RSS, and no history of past incidents beyond a week.
- **The probe asks "is it up", not "does it work"**: a service whose health answers but whose consent flow is broken still shows
  as operational. A synthetic check that raises and decides a test consent request every few minutes would be the honest measure.
- **Each gateway replica keeps its own failure count** (a replica that cannot reach a service says *down* while another says
  *operational*); the uptime history is shared, the state is not.
- **The principals' portal does not link to it yet**, only the DF Console and CM Admin do.
- **The gateway being down shows nothing at all**: the page is served by the gateway's own API. A status page that survives the
  platform has to be served and measured from somewhere else, which is a deployment decision (a static page on a separate
  host, fed by the gateway while it is up and by an external probe when it is not).

## Options considered

- **A third-party uptime service** (Statuspage, Better Stack): the right answer for the external view and the gateway-down case,
  and a choice about a vendor and a bill, which is the owner's. This is the internal measure that feeds one.
- **Prometheus and Alertmanager** (#98): the full observability stack is its own piece of work; the status page needs
  only a yes or no per component and a counter.
- **Each service reporting its own status:** the service that is down cannot say so. The gateway can.
