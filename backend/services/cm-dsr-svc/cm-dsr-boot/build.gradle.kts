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
    id("cm.spring-service")
    id("cm.clean-arch")
}

dependencies {
    implementation(project(":cm-dsr-dom"))
    implementation(project(":cm-dsr-app"))
    implementation(project(":cm-dsr-infra"))
    implementation(project(":cm-dsr-if-rest"))
    implementation(project(":cm-web-kit"))
    implementation(project(":cm-sec-kit"))
    implementation(project(":cm-aud-kit"))
    implementation(project(":cm-kc-kit")) // deletes the principal's account at the end of an erasure (ADR-059)

    implementation("org.springframework.boot:spring-boot-starter-webflux")
    implementation("org.springframework.boot:spring-boot-starter-data-r2dbc")
    implementation(project(":cm-res-kit"))
    implementation("org.springframework.cloud:spring-cloud-starter-circuitbreaker-reactor-resilience4j:5.0.3")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("io.micrometer:micrometer-registry-prometheus") // /actuator/prometheus, exposed only when CM_METRICS_EXPOSURE says so (ADR-070)
    implementation("io.micrometer:micrometer-tracing-bridge-otel") // traces, sampled at 0 unless CM_TRACING_SAMPLING says so (ADR-093)
    implementation("org.springframework.boot:spring-boot-micrometer-tracing-opentelemetry")
    implementation("org.springframework.boot:spring-boot-opentelemetry")
    implementation("io.opentelemetry:opentelemetry-exporter-otlp")
    implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")
    implementation("org.postgresql:r2dbc-postgresql:1.1.3.RELEASE")
    implementation("org.postgresql:postgresql:42.7.14")
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")
    implementation("org.apache.pulsar:pulsar-client-reactive-adapter:0.5.2")
    implementation("org.apache.pulsar:pulsar-client:3.3.9")

    testImplementation(project(":cm-test-kit"))
    testImplementation(project(":cm-evt-kit"))
    testImplementation("org.springframework.boot:spring-boot-starter-test")
}
