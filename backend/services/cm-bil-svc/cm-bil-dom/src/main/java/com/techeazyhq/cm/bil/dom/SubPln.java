package com.techeazyhq.cm.bil.dom;

/** Rate-tier reference data (plan §11) — seeded, CM_ADMIN-managed CRUD. No lifecycle beyond simple field updates, so a plain record, not an {@code Agg}. */
public record SubPln(String id, String name, long reqPerSecQta, long reqPerMonthQta) {
}
