plugins {
    id("java-library")
}

dependencies {
    api("com.tngtech.archunit:archunit-junit5:1.3.0")
    api(platform("org.testcontainers:testcontainers-bom:1.20.1"))
    api("org.testcontainers:junit-jupiter")
    api("org.testcontainers:postgresql")
    api("org.testcontainers:pulsar")

    api(platform("org.junit:junit-bom:5.10.3"))
    api("org.junit.jupiter:junit-jupiter")
}
