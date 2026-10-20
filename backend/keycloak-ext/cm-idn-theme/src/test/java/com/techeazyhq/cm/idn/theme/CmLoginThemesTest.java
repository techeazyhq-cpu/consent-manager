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

package com.techeazyhq.cm.idn.theme;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The login themes this JAR ships, and the three realms' use of them (ADR-035). Keycloak resolves a missing theme, parent or message
 * key silently (falling back to its default theme or printing the key), so these are checked here rather than found
 * on a sign-in page.
 */
class CmLoginThemesTest {

    private static final String BASE_THEME = "cm-base";
    private static final List<String> AUDIENCE_THEMES = List.of("cm-dp", "cm-df", "cm-admin");
    private static final List<String> AUDIENCE_MESSAGE_KEYS =
            List.of("cmAudienceName", "cmAudienceTagline", "cmAudienceIntro");
    private static final Pattern CM_MESSAGE_KEY_IN_TEMPLATE = Pattern.compile("msg\\(\"(cm[A-Za-z]+)\"");

    private final ObjectMapper json = new ObjectMapper();

    @Test
    void declaresTheBaseThemeAndOneLoginThemePerAudience() throws IOException {
        JsonNode declaration = json.readTree(resource("META-INF/keycloak-themes.json"));

        Map<String, Set<String>> typesByTheme = new HashMap<>();
        declaration.get("themes").forEach(theme -> {
            Set<String> types = new HashSet<>();
            theme.get("types").forEach(type -> types.add(type.asText()));
            typesByTheme.put(theme.get("name").asText(), types);
        });

        assertEquals(Set.of(BASE_THEME, "cm-dp", "cm-df", "cm-admin"), typesByTheme.keySet());
        typesByTheme.forEach((theme, types) -> assertEquals(Set.of("login"), types, theme));
    }

    @ParameterizedTest
    @CsvSource({"cm-dp, dp", "cm-df, df", "cm-admin, admin"})
    void eachAudienceThemeExtendsTheBaseThemeAndNamesItsAudience(String theme, String audience) throws IOException {
        Properties properties = loginProperties(theme, "theme.properties");

        assertEquals(BASE_THEME, properties.getProperty("parent"));
        assertEquals(audience, properties.getProperty("cmAudience"));
    }

    @Test
    void theBaseThemeBuildsOnKeycloaksUnstyledBaseTheme() throws IOException {
        Properties properties = loginProperties(BASE_THEME, "theme.properties");

        assertEquals("base", properties.getProperty("parent"));
        assertEquals("css/cm-login.css", properties.getProperty("styles"));
    }

    @Test
    void everyCmMessageTheBaseTemplateUsesIsDefined() throws IOException {
        String template = text("theme/" + BASE_THEME + "/login/template.ftl");
        Properties messages = loginProperties(BASE_THEME, "messages/messages_en.properties");

        Matcher usage = CM_MESSAGE_KEY_IN_TEMPLATE.matcher(template);
        Set<String> usedKeys = new HashSet<>();
        while (usage.find()) {
            usedKeys.add(usage.group(1));
        }

        assertFalse(usedKeys.isEmpty(), "template.ftl uses no cm messages");
        usedKeys.forEach(key -> assertNotNull(messages.getProperty(key), key + " missing from cm-base messages"));
    }

    @Test
    void everyAudienceThemeWordsItsOwnAudienceMessages() throws IOException {
        for (String theme : AUDIENCE_THEMES) {
            Properties messages = loginProperties(theme, "messages/messages_en.properties");
            for (String key : AUDIENCE_MESSAGE_KEYS) {
                assertNotNull(messages.getProperty(key), key + " missing from " + theme);
            }
        }
    }

    /** A realm per audience (ADR-035) holds only that audience's apps: for principals, the web portal and the mobile app (ADR-032). */
    @ParameterizedTest
    @CsvSource({"cm-dp, cm-dp-portal|cm-dp-mobile, cm-dp", "cm-df, cm-df-app, cm-df", "cm-staff, cm-cm-admin, cm-admin"})
    void eachAudienceHasItsOwnRealmWithOnlyItsFrontendsShowingItsTheme(String realm, String clientIds, String theme)
            throws IOException {
        JsonNode export = realmExport(realm);
        assertEquals(realm, export.path("realm").asText());
        assertEquals(theme, export.path("loginTheme").asText(), realm + " defaults to its audience's theme");

        List<String> clients = new java.util.ArrayList<>();
        export.get("clients").forEach(c -> clients.add(c.get("clientId").asText()));
        assertEquals(List.of(clientIds.split("[|]")), clients, realm + " holds only its own frontends");

        for (JsonNode client : export.get("clients")) {
            String clientId = client.path("clientId").asText();
            assertTrue(client.path("publicClient").asBoolean(), clientId + " must be public (PKCE, no secret)");
            assertEquals("S256", client.path("attributes").path("pkce.code.challenge.method").asText(), clientId);
            assertEquals(theme, client.path("attributes").path("login_theme").asText());
        }
    }

    @ParameterizedTest
    @CsvSource({"cm-dp, DP", "cm-df, ''", "cm-staff, ''"})
    void onlyDataPrincipalsCanSignThemselvesUp(String realm, String autoRegisterRole) throws IOException {
        JsonNode configs = realmExport(realm).get("authenticatorConfig");
        assertEquals(autoRegisterRole, configs.get(0).path("config").path("autoRegisterRole").asText(), realm);
    }

    @ParameterizedTest
    @CsvSource({"cm-dp, DP", "cm-df, DF", "cm-staff, CM_"})
    void eachRealmDefinesOnlyItsAudiencesRoles(String realm, String prefix) throws IOException {
        realmExport(realm).path("roles").path("realm").forEach(role ->
                assertTrue(role.get("name").asText().startsWith(prefix), realm + " defines " + role.get("name")));
    }

    @Test
    void theOldSharedRealmIsGone() {
        assertFalse(Path.of(exportsDir(), "cm-realm.json").toFile().exists(), "cm-realm.json is back");
    }

    private JsonNode realmExport(String realm) throws IOException {
        return json.readTree(Path.of(exportsDir(), realm + ".json").toFile());
    }

    private static String exportsDir() {
        String dir = System.getProperty("cm.realmExports");
        assertNotNull(dir, "cm.realmExports system property not set by the build");
        return dir;
    }

    private Properties loginProperties(String theme, String file) throws IOException {
        Properties properties = new Properties();
        try (Reader reader = new InputStreamReader(resource("theme/" + theme + "/login/" + file),
                StandardCharsets.UTF_8)) {
            properties.load(reader);
        }
        return properties;
    }

    private String text(String path) throws IOException {
        try (InputStream stream = resource(path)) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private InputStream resource(String path) {
        InputStream stream = getClass().getClassLoader().getResourceAsStream(path);
        assertNotNull(stream, path + " not on the classpath");
        return stream;
    }
}
