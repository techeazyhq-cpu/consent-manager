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

package com.techeazyhq.cm.dfreg.dom;

/**
 * A person at a fiduciary who signs in to the DF Console as themselves (ADR-033). Keycloak is the system of record:
 * the member is a realm user with a {@code df_id} attribute and one {@link DfRole}, so this is a snapshot read from
 * it. {@code id} is the Keycloak user id, the {@code sub} of their tokens.
 */
public record DfMember(String id, String dfId, String email, String name, DfRole role) {
}
