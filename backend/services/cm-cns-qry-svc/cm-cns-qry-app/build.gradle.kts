plugins {
    id("java-library")
}

dependencies {
    api(project(":cm-cns-qry-dom"))

    testImplementation(platform("org.junit:junit-bom:5.10.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
