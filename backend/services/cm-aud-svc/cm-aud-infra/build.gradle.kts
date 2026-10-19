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
    api(project(":cm-aud-app"))
    implementation(project(":cm-evt-kit"))
    implementation(project(":cm-aud-kit"))
    implementation(project(":cm-res-kit"))
    implementation("org.springframework.cloud:spring-cloud-starter-circuitbreaker-reactor-resilience4j:3.3.3")
    implementation("org.springframework.boot:spring-boot-starter-data-r2dbc:3.5.16")
    implementation("org.postgresql:r2dbc-postgresql:1.1.3.RELEASE")
    implementation("org.apache.pulsar:pulsar-client-reactive-api:0.5.2")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.22.3")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.22.3")
    implementation("org.slf4j:slf4j-api:2.0.20")
    implementation("org.bouncycastle:bcpkix-jdk18on:1.86")

    testImplementation(platform("org.junit:junit-bom:5.14.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
