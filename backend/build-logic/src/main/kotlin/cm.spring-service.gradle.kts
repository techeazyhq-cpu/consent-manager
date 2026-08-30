import org.gradle.api.tasks.testing.Test

plugins {
    id("java")
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

dependencies {
    "testImplementation"("org.springframework.boot:spring-boot-starter-test")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

// Container image name/tag every docker-compose.yml service reference expects (plan §6:
// Buildpacks, not a hand-written Dockerfile) — one place to keep this consistent instead of
// repeating it per service.
tasks.named<org.springframework.boot.gradle.tasks.bundling.BootBuildImage>("bootBuildImage") {
    imageName.set("techeazyhq/${project.name}:${project.version}")
}
