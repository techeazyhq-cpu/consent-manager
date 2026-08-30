plugins {
    id("cm.spring-service")
    id("cm.clean-arch")
}

dependencies {
    implementation(project(":cm-df-reg-dom"))
    implementation(project(":cm-df-reg-app"))
    implementation(project(":cm-df-reg-infra"))
    implementation(project(":cm-df-reg-if-rest"))
    implementation(project(":cm-web-kit"))

    implementation("org.springframework.boot:spring-boot-starter-webflux")
    implementation("org.springframework.boot:spring-boot-starter-data-r2dbc")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.postgresql:r2dbc-postgresql:1.0.5.RELEASE")
    implementation("org.postgresql:postgresql:42.7.4")
    implementation("org.flywaydb:flyway-core:10.17.3")
    implementation("org.flywaydb:flyway-database-postgresql:10.17.3")
    implementation("org.apache.pulsar:pulsar-client-reactive-adapter:0.5.2")
    implementation("org.apache.pulsar:pulsar-client:3.3.1")

    testImplementation(project(":cm-test-kit"))
    testImplementation("org.springframework.boot:spring-boot-starter-test")
}
