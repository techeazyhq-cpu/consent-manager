plugins {
    id("cm.spring-service")
    id("cm.clean-arch")
}

dependencies {
    implementation(project(":cm-cns-cmd-dom"))
    implementation(project(":cm-cns-cmd-app"))
    implementation(project(":cm-cns-cmd-infra"))
    implementation(project(":cm-cns-cmd-if-rest"))
    implementation(project(":cm-sec-kit"))
    implementation(project(":cm-web-kit"))

    implementation("org.springframework.boot:spring-boot-starter-webflux")
    implementation("org.springframework.boot:spring-boot-starter-data-r2dbc")
    implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.cloud:spring-cloud-starter-circuitbreaker-reactor-resilience4j:3.1.2")
    implementation("org.springdoc:springdoc-openapi-starter-webflux-ui:2.6.0")
    implementation("org.postgresql:r2dbc-postgresql:1.0.5.RELEASE")
    implementation("org.postgresql:postgresql:42.7.4") // Flyway needs a blocking JDBC driver even though runtime access stays R2DBC
    implementation("org.flywaydb:flyway-core:10.17.3")
    implementation("org.flywaydb:flyway-database-postgresql:10.17.3")
    implementation("org.apache.pulsar:pulsar-client-reactive-adapter:0.5.2")
    implementation("org.apache.pulsar:pulsar-client:3.3.1")

    testImplementation(project(":cm-test-kit"))
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("io.projectreactor:reactor-test")
}
