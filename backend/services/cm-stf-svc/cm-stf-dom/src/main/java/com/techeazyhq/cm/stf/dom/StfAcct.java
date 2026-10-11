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

package com.techeazyhq.cm.stf.dom;

import java.time.Instant;

/**
 * A member of platform staff. Keycloak is the system of record (the account is a Keycloak user), so this is a
 * snapshot read from it, not an aggregate this service stores. {@code id} is the Keycloak user id, the {@code sub} of
 * their tokens.
 */
public record StfAcct(String id, String email, String name, StfRole role, boolean enabled, Instant createdAt) {
}
