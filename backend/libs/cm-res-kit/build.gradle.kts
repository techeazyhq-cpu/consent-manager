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

// What every service needs to call another one through a circuit breaker (ADR-016): which failures count against
// the breaker, and how an unavailable dependency surfaces to the caller (503 problem+json).
dependencies {
    compileOnly("org.springframework.boot:spring-boot-starter-webflux:3.5.16")
    compileOnly("org.springframework.boot:spring-boot-autoconfigure:3.5.16")
    compileOnly("io.github.resilience4j:resilience4j-circuitbreaker:2.4.0")

    testImplementation(platform("org.junit:junit-bom:5.14.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.springframework.boot:spring-boot-starter-webflux:3.5.16")
    testImplementation("io.github.resilience4j:resilience4j-circuitbreaker:2.4.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
