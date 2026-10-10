plugins {
    id("java-library")
}

dependencies {
    api(project(":cm-ntf-app"))
    implementation(project(":cm-sec-kit"))
    implementation("org.springframework.boot:spring-boot-starter-webflux:3.3.4")
    implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server:3.3.4")

    testImplementation(platform("org.junit:junit-bom:5.10.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
