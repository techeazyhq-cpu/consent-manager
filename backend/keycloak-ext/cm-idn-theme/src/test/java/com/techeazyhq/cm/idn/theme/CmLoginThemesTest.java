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
 * The login themes this JAR ships, and cm-realm's use of them. Keycloak resolves a missing theme, parent or message
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

    @Test
    void theRealmDefaultsToTheBaseTheme() throws IOException {
        assertEquals(BASE_THEME, realmExport().path("loginTheme").asText());
    }

    @ParameterizedTest
    @CsvSource({"cm-dp-portal, cm-dp", "cm-df-app, cm-df", "cm-cm-admin, cm-admin"})
    void eachFrontendHasItsOwnClientShowingItsAudienceTheme(String clientId, String theme) throws IOException {
        JsonNode client = client(clientId);

        assertTrue(client.path("publicClient").asBoolean(), clientId + " must be public (PKCE, no secret)");
        assertEquals(theme, client.path("attributes").path("login_theme").asText());
    }

    @Test
    void noBrowserClientUsesAThemeThisJarDoesNotShip() throws IOException {
        Set<String> shippedThemes = new HashSet<>(AUDIENCE_THEMES);
        shippedThemes.add(BASE_THEME);

        for (JsonNode client : realmExport().get("clients")) {
            String theme = client.path("attributes").path("login_theme").asText("");
            if (!theme.isEmpty()) {
                assertTrue(shippedThemes.contains(theme), client.get("clientId").asText() + " uses " + theme);
            }
        }
    }

    @Test
    void theSharedBrowserClientIsGone() throws IOException {
        for (JsonNode client : realmExport().get("clients")) {
            assertFalse("cm-shell".equals(client.get("clientId").asText()), "cm-shell still in cm-realm.json");
        }
    }

    private JsonNode client(String clientId) throws IOException {
        for (JsonNode client : realmExport().get("clients")) {
            if (clientId.equals(client.get("clientId").asText())) {
                return client;
            }
        }
        throw new AssertionError(clientId + " missing from cm-realm.json");
    }

    private JsonNode realmExport() throws IOException {
        String realmExportPath = System.getProperty("cm.realmExport");
        assertNotNull(realmExportPath, "cm.realmExport system property not set by the build");
        return json.readTree(Path.of(realmExportPath).toFile());
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
