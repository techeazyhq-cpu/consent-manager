plugins {
    id("java-library")
}

dependencies {
    compileOnly("org.springframework.boot:spring-boot-starter-webflux:3.3.4")
    compileOnly("org.springframework.boot:spring-boot-autoconfigure:3.3.4")

    testImplementation(platform("org.junit:junit-bom:5.10.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
