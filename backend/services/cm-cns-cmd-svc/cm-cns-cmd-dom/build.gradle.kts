plugins {
    id("java-library")
}

dependencies {
    api(project(":cm-dom-kit"))
    // Reactor Core is a standalone async-value-type library, not Spring-specific — the
    // Clean Architecture rule this project follows is "no Spring Framework imports in
    // -dom", not "no reactive types"; repository/eligibility ports need Mono/Flux
    // signatures to be implementable by the reactive -infra adapters.
    api("io.projectreactor:reactor-core:3.6.10")

    testImplementation(platform("org.junit:junit-bom:5.10.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
