/*
 * Copyright 2026 Vasantha Kumar
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * @author Vasantha Kumar <vasantha.kumar@hotmail.com>
 */

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
