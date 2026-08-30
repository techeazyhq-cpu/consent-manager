plugins {
    id("java-library")
}

dependencies {
    api("com.nimbusds:nimbus-jose-jwt:9.40")
    compileOnly("org.springframework.boot:spring-boot-starter-webflux:3.3.4")
    compileOnly("org.springframework.boot:spring-boot-starter-oauth2-resource-server:3.3.4")
    compileOnly("org.springframework.security:spring-security-oauth2-jose:6.3.3")

    testImplementation(platform("org.junit:junit-bom:5.10.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
