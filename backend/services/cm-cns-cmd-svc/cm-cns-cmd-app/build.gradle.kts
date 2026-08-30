plugins {
    id("java-library")
}

dependencies {
    api(project(":cm-cns-cmd-dom"))
    // cm-sec-kit is a horizontal "kit" (CnsTknSigner), not this service's -infra layer —
    // depending on it does not cross the dom/app/infra/ifc boundary the ArchUnit test checks,
    // since that test only scans this service's own com.techeazyhq.cm.cnscmd package tree.
    implementation(project(":cm-sec-kit"))
    api(project(":cm-web-kit")) // DomRuleViolation is part of every handler's public error contract
    // @Transactional at the use-case boundary is a common, pragmatic pattern even in
    // otherwise-framework-light application layers; spring-tx alone (not all of Spring)
    // keeps this a narrow addition.
    compileOnly("org.springframework:spring-tx:6.1.13")
    compileOnly("org.springframework:spring-context:6.1.13") // @Scheduled on CnsExpirySweepJob
    implementation("org.slf4j:slf4j-api:2.0.16")

    testImplementation(platform("org.junit:junit-bom:5.10.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.mockito:mockito-core:5.13.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
