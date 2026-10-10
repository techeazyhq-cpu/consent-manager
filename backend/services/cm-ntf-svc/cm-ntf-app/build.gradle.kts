plugins {
    id("java-library")
}

dependencies {
    api(project(":cm-ntf-dom"))
    api(project(":cm-web-kit")) // DomRuleViolation is part of every handler's public error contract
    implementation("org.slf4j:slf4j-api:2.0.16")

    testImplementation(platform("org.junit:junit-bom:5.10.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
