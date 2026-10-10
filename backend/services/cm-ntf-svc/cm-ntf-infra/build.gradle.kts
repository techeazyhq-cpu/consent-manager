plugins {
    id("java-library")
}

dependencies {
    api(project(":cm-ntf-dom"))
    implementation(project(":cm-ntf-app"))
    implementation("org.springframework.boot:spring-boot-starter-mail:3.3.4")
    implementation("org.springframework.boot:spring-boot-starter-webflux:3.3.4")
    implementation("org.springframework.boot:spring-boot-starter-data-r2dbc:3.3.4")
    implementation("org.apache.pulsar:pulsar-client-reactive-api:0.5.2")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.17.2")
    implementation("org.slf4j:slf4j-api:2.0.16")

    testImplementation(platform("org.junit:junit-bom:5.10.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
