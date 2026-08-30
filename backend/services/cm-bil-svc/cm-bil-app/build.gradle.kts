plugins {
    id("java-library")
}

dependencies {
    api(project(":cm-bil-dom"))
    api(project(":cm-web-kit"))
    compileOnly("org.springframework:spring-tx:6.1.13")

    testImplementation(platform("org.junit:junit-bom:5.10.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
