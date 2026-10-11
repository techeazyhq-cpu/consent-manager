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

rootProject.name = "consent-manager"

pluginManagement {
    includeBuild("backend/build-logic")
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

// ---- shared libs ----
include(":cm-dom-kit")
include(":cm-evt-kit")
include(":cm-web-kit")
include(":cm-sec-kit")
include(":cm-test-kit")
include(":cm-res-kit")
include(":cm-kc-kit")
project(":cm-dom-kit").projectDir = file("backend/libs/cm-dom-kit")
project(":cm-evt-kit").projectDir = file("backend/libs/cm-evt-kit")
project(":cm-web-kit").projectDir = file("backend/libs/cm-web-kit")
project(":cm-sec-kit").projectDir = file("backend/libs/cm-sec-kit")
project(":cm-test-kit").projectDir = file("backend/libs/cm-test-kit")
project(":cm-res-kit").projectDir = file("backend/libs/cm-res-kit")
project(":cm-kc-kit").projectDir = file("backend/libs/cm-kc-kit")

// ---- gateway (single module) ----
include(":cm-gw")
project(":cm-gw").projectDir = file("backend/services/cm-gw")
// ---- cm-stf-svc ----
include(":cm-stf-dom", ":cm-stf-app", ":cm-stf-infra", ":cm-stf-if-rest", ":cm-stf-boot")
project(":cm-stf-dom").projectDir = file("backend/services/cm-stf-svc/cm-stf-dom")
project(":cm-stf-app").projectDir = file("backend/services/cm-stf-svc/cm-stf-app")
project(":cm-stf-infra").projectDir = file("backend/services/cm-stf-svc/cm-stf-infra")
project(":cm-stf-if-rest").projectDir = file("backend/services/cm-stf-svc/cm-stf-if-rest")
project(":cm-stf-boot").projectDir = file("backend/services/cm-stf-svc/cm-stf-boot")

// ---- cm-df-reg-svc ----
include(":cm-df-reg-dom", ":cm-df-reg-app", ":cm-df-reg-infra", ":cm-df-reg-if-rest", ":cm-df-reg-boot")
project(":cm-df-reg-dom").projectDir = file("backend/services/cm-df-reg-svc/cm-df-reg-dom")
project(":cm-df-reg-app").projectDir = file("backend/services/cm-df-reg-svc/cm-df-reg-app")
project(":cm-df-reg-infra").projectDir = file("backend/services/cm-df-reg-svc/cm-df-reg-infra")
project(":cm-df-reg-if-rest").projectDir = file("backend/services/cm-df-reg-svc/cm-df-reg-if-rest")
project(":cm-df-reg-boot").projectDir = file("backend/services/cm-df-reg-svc/cm-df-reg-boot")

// ---- cm-cns-cmd-svc ----
include(":cm-cns-cmd-dom", ":cm-cns-cmd-app", ":cm-cns-cmd-infra", ":cm-cns-cmd-if-rest", ":cm-cns-cmd-boot")
project(":cm-cns-cmd-dom").projectDir = file("backend/services/cm-cns-cmd-svc/cm-cns-cmd-dom")
project(":cm-cns-cmd-app").projectDir = file("backend/services/cm-cns-cmd-svc/cm-cns-cmd-app")
project(":cm-cns-cmd-infra").projectDir = file("backend/services/cm-cns-cmd-svc/cm-cns-cmd-infra")
project(":cm-cns-cmd-if-rest").projectDir = file("backend/services/cm-cns-cmd-svc/cm-cns-cmd-if-rest")
project(":cm-cns-cmd-boot").projectDir = file("backend/services/cm-cns-cmd-svc/cm-cns-cmd-boot")

// ---- cm-cns-qry-svc ----
include(":cm-cns-qry-dom", ":cm-cns-qry-app", ":cm-cns-qry-infra", ":cm-cns-qry-if-rest", ":cm-cns-qry-boot")
project(":cm-cns-qry-dom").projectDir = file("backend/services/cm-cns-qry-svc/cm-cns-qry-dom")
project(":cm-cns-qry-app").projectDir = file("backend/services/cm-cns-qry-svc/cm-cns-qry-app")
project(":cm-cns-qry-infra").projectDir = file("backend/services/cm-cns-qry-svc/cm-cns-qry-infra")
project(":cm-cns-qry-if-rest").projectDir = file("backend/services/cm-cns-qry-svc/cm-cns-qry-if-rest")
project(":cm-cns-qry-boot").projectDir = file("backend/services/cm-cns-qry-svc/cm-cns-qry-boot")

// ---- cm-ntf-svc ----
include(":cm-ntf-dom", ":cm-ntf-app", ":cm-ntf-infra", ":cm-ntf-if-rest", ":cm-ntf-boot")
project(":cm-ntf-dom").projectDir = file("backend/services/cm-ntf-svc/cm-ntf-dom")
project(":cm-ntf-app").projectDir = file("backend/services/cm-ntf-svc/cm-ntf-app")
project(":cm-ntf-infra").projectDir = file("backend/services/cm-ntf-svc/cm-ntf-infra")
project(":cm-ntf-if-rest").projectDir = file("backend/services/cm-ntf-svc/cm-ntf-if-rest")
project(":cm-ntf-boot").projectDir = file("backend/services/cm-ntf-svc/cm-ntf-boot")

// ---- cm-bil-svc ----
include(":cm-bil-dom", ":cm-bil-app", ":cm-bil-infra", ":cm-bil-if-rest", ":cm-bil-boot")
project(":cm-bil-dom").projectDir = file("backend/services/cm-bil-svc/cm-bil-dom")
project(":cm-bil-app").projectDir = file("backend/services/cm-bil-svc/cm-bil-app")
project(":cm-bil-infra").projectDir = file("backend/services/cm-bil-svc/cm-bil-infra")
project(":cm-bil-if-rest").projectDir = file("backend/services/cm-bil-svc/cm-bil-if-rest")
project(":cm-bil-boot").projectDir = file("backend/services/cm-bil-svc/cm-bil-boot")

// ---- cm-aud-svc: tamper-evident audit ledger (ADR-014) ----
include(":cm-aud-dom", ":cm-aud-app", ":cm-aud-infra", ":cm-aud-if-rest", ":cm-aud-boot")
project(":cm-aud-dom").projectDir = file("backend/services/cm-aud-svc/cm-aud-dom")
project(":cm-aud-app").projectDir = file("backend/services/cm-aud-svc/cm-aud-app")
project(":cm-aud-infra").projectDir = file("backend/services/cm-aud-svc/cm-aud-infra")
project(":cm-aud-if-rest").projectDir = file("backend/services/cm-aud-svc/cm-aud-if-rest")
project(":cm-aud-boot").projectDir = file("backend/services/cm-aud-svc/cm-aud-boot")

// ---- keycloak extensions (plain Java provider JARs, not Spring Boot services) ----
include(":cm-idn-otp-spi")
project(":cm-idn-otp-spi").projectDir = file("backend/keycloak-ext/cm-idn-otp-spi")
include(":cm-idn-theme")
project(":cm-idn-theme").projectDir = file("backend/keycloak-ext/cm-idn-theme")
