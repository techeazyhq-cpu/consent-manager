// Convention plugin applied to every "-boot" module: pulls in cm-test-kit's shared
// ArchUnit base test so each service enforces the Clean Architecture dependency rule
// (Domain -> nothing internal, Application -> Domain, Infra/If-Rest -> App+Domain, never
// Infra <-> If-Rest) as a normal `./gradlew test` failure, not a manual review step.

plugins {
    id("java")
}

dependencies {
    "testImplementation"("com.tngtech.archunit:archunit-junit5:1.3.0")
    "testImplementation"(project(":cm-test-kit"))
}
