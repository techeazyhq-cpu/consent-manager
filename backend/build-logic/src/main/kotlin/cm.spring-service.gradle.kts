/*
 * Copyright 2026 Vasantha Kumar
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * @author Vasantha Kumar <vasantha.kumar@hotmail.com>
 */

import org.gradle.api.tasks.testing.Test

plugins {
    id("java")
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

dependencies {
    "testImplementation"("org.springframework.boot:spring-boot-starter-test")
}

tasks.withType<Test> {
    useJUnitPlatform()
    // Testcontainers 1.20's docker-java asks for Docker API 1.32 by default; Docker Engine 29+ refuses anything
    // below 1.40 (400 on /info: "Could not find a valid Docker environment"). 1.44 works with Engine 25 and newer.
    systemProperty("api.version", "1.44")
}

// Container image name/tag every docker-compose.yml service reference expects (plan §6:
// Buildpacks, not a hand-written Dockerfile) — one place to keep this consistent instead of
// repeating it per service.
tasks.named<org.springframework.boot.gradle.tasks.bundling.BootBuildImage>("bootBuildImage") {
    imageName.set("techeazyhq/${project.name}:${project.version}")
}
