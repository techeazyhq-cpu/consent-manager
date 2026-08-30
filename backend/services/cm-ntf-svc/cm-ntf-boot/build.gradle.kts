plugins {
    id("cm.spring-service")
    id("cm.clean-arch")
}

dependencies {
    implementation(project(":cm-ntf-dom"))
    implementation(project(":cm-ntf-app"))
    implementation(project(":cm-ntf-infra"))
    implementation(project(":cm-ntf-if-rest"))

    implementation("org.springframework.boot:spring-boot-starter-webflux")
    implementation("org.springframework.boot:spring-boot-starter-mail")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.apache.pulsar:pulsar-client-reactive-adapter:0.5.2")
    implementation("org.apache.pulsar:pulsar-client:3.3.1")

    testImplementation(project(":cm-test-kit"))
    testImplementation("org.springframework.boot:spring-boot-starter-test")
}
