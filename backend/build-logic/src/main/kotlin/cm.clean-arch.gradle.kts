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

// Convention plugin applied to every "-boot" module: pulls in cm-test-kit's shared
// ArchUnit base test so each service enforces the Clean Architecture dependency rule
// (Domain -> nothing internal, Application -> Domain, Infra/If-Rest -> App+Domain, never
// Infra <-> If-Rest) as a normal `./gradlew test` failure, not a manual review step.

plugins {
    id("java")
}

dependencies {
    "testImplementation"("com.tngtech.archunit:archunit-junit5:1.3.0")
    "testImplementation"(project(":cm-test-kit"))
}
