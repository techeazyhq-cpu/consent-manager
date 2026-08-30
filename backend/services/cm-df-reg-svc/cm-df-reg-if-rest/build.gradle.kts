plugins {
    id("java-library")
}

dependencies {
    api(project(":cm-df-reg-app"))
    implementation("org.springframework.boot:spring-boot-starter-webflux:3.3.4")

    testImplementation(platform("org.junit:junit-bom:5.10.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
