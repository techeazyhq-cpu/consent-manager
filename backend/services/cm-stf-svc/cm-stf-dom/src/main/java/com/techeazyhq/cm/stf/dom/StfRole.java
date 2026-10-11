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

/**
 * What a member of platform staff may do, as the Keycloak realm role of the same name.
 * <ul>
 *   <li>{@link #CM_ADMIN}: everything, including managing staff, changing a tenant's plan and suspending it;</li>
 *   <li>{@link #CM_OPERATOR}: views tenants and their consent figures, onboards and activates tenants.</li>
 * </ul>
 */
public enum StfRole {
    CM_ADMIN,
    CM_OPERATOR
}
