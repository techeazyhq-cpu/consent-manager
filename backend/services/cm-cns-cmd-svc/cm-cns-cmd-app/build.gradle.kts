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

plugins {
    id("java-library")
}

dependencies {
    api(project(":cm-cns-cmd-dom"))
    // cm-sec-kit is a horizontal "kit" (CnsTknSigner), not this service's -infra layer —
    // depending on it does not cross the dom/app/infra/ifc boundary the ArchUnit test checks,
    // since that test only scans this service's own com.techeazyhq.cm.cnscmd package tree.
    implementation(project(":cm-sec-kit"))
    api(project(":cm-web-kit")) // DomRuleViolation is part of every handler's public error contract
    // @Transactional at the use-case boundary is a common, pragmatic pattern even in
    // otherwise-framework-light application layers; spring-tx alone (not all of Spring)
    // keeps this a narrow addition.
    compileOnly("org.springframework:spring-tx:6.1.13")
    compileOnly("org.springframework:spring-context:6.1.13") // @Scheduled on CnsExpirySweepJob
    implementation("org.slf4j:slf4j-api:2.0.16")

    testImplementation(platform("org.junit:junit-bom:5.10.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.mockito:mockito-core:5.13.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
