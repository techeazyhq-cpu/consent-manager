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

// Keycloak theme JAR: cm-realm's login pages, one theme per audience (data principal, data fiduciary, platform
// administrator) on a shared cm-base. Resources only; Keycloak finds the themes through META-INF/keycloak-themes.json.
// The JAR lands next to cm-idn-otp-spi's in the providers directory infra/docker-compose.yml mounts into Keycloak.
plugins {
    id("java-library")
}

val realmExport = rootProject.file("infra/keycloak/realm-export/cm-realm.json")

dependencies {
    testImplementation(platform("org.junit:junit-bom:5.14.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.junit.jupiter:junit-jupiter-params")
    testImplementation("com.fasterxml.jackson.core:jackson-databind:2.18.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<JavaCompile> {
    options.release.set(21)
}

tasks.jar {
    destinationDirectory.set(layout.projectDirectory.dir("../build/providers"))
}

tasks.test {
    useJUnitPlatform()
    // The realm export decides which client shows which theme, so the tests check the two together.
    inputs.file(realmExport)
    systemProperty("cm.realmExport", realmExport.absolutePath)
}
