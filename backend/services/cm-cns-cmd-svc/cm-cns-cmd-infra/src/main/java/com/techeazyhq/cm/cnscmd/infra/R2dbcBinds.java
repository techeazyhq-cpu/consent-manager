package com.techeazyhq.cm.cnscmd.infra;

import org.springframework.r2dbc.core.DatabaseClient;

/**
 * {@code DatabaseClient.GenericExecuteSpec#bind} throws on a null value — R2DBC needs
 * {@code bindNull(name, type)} instead so the driver knows the column's type. Every
 * nullable column in this service (the {@code dp_ref_typ}/{@code dp_ref_val}/{@code dp_id}
 * triplet, plan §7a) goes through this helper instead of repeating the null check inline.
 */
final class R2dbcBinds {

    private R2dbcBinds() {
    }

    static DatabaseClient.GenericExecuteSpec bindNullable(
            DatabaseClient.GenericExecuteSpec spec, String name, Object value, Class<?> type) {
        return value == null ? spec.bindNull(name, type) : spec.bind(name, value);
    }
}
