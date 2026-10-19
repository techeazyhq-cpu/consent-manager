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

allprojects {
    group = "com.techeazyhq.cm"
    version = "0.1.0-SNAPSHOT"

    repositories {
        mavenCentral()
    }
}

subprojects {
    apply(plugin = "java")
    apply(plugin = "jacoco")

    configure<JavaPluginExtension> {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(21))
        }
    }

    tasks.withType<Test> {
        useJUnitPlatform()
    }

    // Patched releases of libraries that Spring Boot 3.5 brings in, within the lines it uses, until its own managed
    // versions catch up. Trivy found HIGH and CRITICAL vulnerabilities in the versions before these (VAPT, docs/security).
    // Spring's dependency-management plugin sets Netty and Jackson from its BOM after any rule below; these are the
    // BOM's own overrides.
    extra["netty.version"] = "4.1.137.Final"
    extra["jackson-bom.version"] = "2.21.7"
    configurations.configureEach {
        resolutionStrategy.eachDependency {
            val v = requested.version ?: return@eachDependency
            val patched = when {
                requested.group == "io.netty" && v.startsWith("4.1.") -> "4.1.137.Final"
                requested.group == "com.fasterxml.jackson.core" && requested.name != "jackson-annotations"
                        && v.startsWith("2.21.") -> "2.21.7"
                requested.group == "org.bouncycastle"
                        && requested.name in setOf("bcprov-jdk18on", "bcpkix-jdk18on", "bcutil-jdk18on") -> "1.85"
                requested.group == "com.ongres.scram" && v.startsWith("3.") -> "3.3"
                else -> null
            }
            if (patched != null && patched != v) {
                useVersion(patched)
                because("patched release (VAPT dependency findings)")
            }
        }
    }
}

// Quality gate (CONTRIBUTING.md, CI): one coverage figure across every module, since most modules are tested
// through their service's -boot module or a neighbour rather than by their own tests. `coverageReport` writes
// build/reports/jacoco/coverageReport (HTML and XML); `coverageVerification` fails the build below the minimum.
// The minimum is a ratchet: raise it as coverage grows, never lower it.
apply(plugin = "jacoco")

val coverageMinimum = "0.19".toBigDecimal()

fun JacocoReportBase.coverAllModules() {
    subprojects.forEach { module ->
        module.plugins.withType<JavaPlugin> {
            val main = module.the<SourceSetContainer>()["main"]
            sourceDirectories.from(main.allJava.srcDirs)
            classDirectories.from(main.output.classesDirs)
            executionData.from(module.fileTree(module.layout.buildDirectory.dir("jacoco")) { include("*.exec") })
            dependsOn(module.tasks.withType<Test>())
        }
    }
}

tasks.register<JacocoReport>("coverageReport") {
    group = "verification"
    description = "Line and branch coverage of all modules' tests, in one report."
    coverAllModules()
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}

tasks.register<JacocoCoverageVerification>("coverageVerification") {
    group = "verification"
    description = "Fails when line coverage across all modules drops below the ratchet."
    coverAllModules()
    dependsOn("coverageReport")
    violationRules {
        rule {
            limit {
                counter = "LINE"
                value = "COVEREDRATIO"
                minimum = coverageMinimum
            }
        }
    }
}
