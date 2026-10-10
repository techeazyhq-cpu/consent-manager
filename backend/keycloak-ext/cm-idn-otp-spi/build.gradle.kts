// Keycloak provider JAR (plan §9a), not a Spring Boot service: Keycloak supplies its own SPI classes at runtime,
// so they are compileOnly and the JAR carries only this module's classes, templates and messages.
// infra/docker-compose.yml mounts build/libs into Keycloak's providers directory.
plugins {
    id("java-library")
}

val keycloakVersion = "25.0.6"

dependencies {
    compileOnly("org.keycloak:keycloak-server-spi:$keycloakVersion")
    compileOnly("org.keycloak:keycloak-server-spi-private:$keycloakVersion")
    compileOnly("org.keycloak:keycloak-services:$keycloakVersion")
    compileOnly("jakarta.ws.rs:jakarta.ws.rs-api:3.1.0")
    compileOnly("org.jboss.logging:jboss-logging:3.6.0.Final")
    testImplementation("org.jboss.logging:jboss-logging:3.6.0.Final")

    testImplementation(platform("org.junit:junit-bom:5.10.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.junit.jupiter:junit-jupiter-params")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<JavaCompile> {
    options.release.set(21)
}
