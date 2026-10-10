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
    api(project(":cm-ntf-dom"))
    implementation(project(":cm-ntf-app"))
    implementation("org.springframework.boot:spring-boot-starter-mail:3.5.16")
    implementation("org.springframework.boot:spring-boot-starter-webflux:3.5.16")
    implementation("org.springframework.boot:spring-boot-starter-data-r2dbc:3.5.16")
    implementation("org.apache.pulsar:pulsar-client-reactive-api:0.5.2")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.17.2")
    implementation("org.slf4j:slf4j-api:2.0.16")

    testImplementation(platform("org.junit:junit-bom:5.14.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
