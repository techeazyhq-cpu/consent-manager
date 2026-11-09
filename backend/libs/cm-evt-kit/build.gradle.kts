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
    api(project(":cm-dom-kit"))
    api("com.fasterxml.jackson.core:jackson-databind:2.22.3")
    // the erasure saga's step consumer (ADR-059)
    api("org.apache.pulsar:pulsar-client-reactive-api:0.5.2")
    api("org.apache.pulsar:pulsar-client-api:3.3.9")
    implementation("org.slf4j:slf4j-api:2.0.20")

    testImplementation(platform("org.junit:junit-bom:5.14.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.apache.pulsar:pulsar-client:3.3.9")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
