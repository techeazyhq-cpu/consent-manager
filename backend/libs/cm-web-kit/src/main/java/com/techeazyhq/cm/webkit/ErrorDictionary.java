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

package com.techeazyhq.cm.webkit;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * The platform's one list of errors, warnings and events (ADR-062), read from {@code cm-error-dictionary.json}: for each
 * API problem its stable code, status, human title, what it means, what the caller should do, and whether trying again
 * can help. Every service answers problems from it ({@link Problems}), so the same code always carries the same title
 * and status, whichever service raises it.
 *
 * <p>The file is also what {@code scripts/error-dictionary.mjs} checks the source against in CI: a code raised but not
 * listed, or listed but not raised, or raised with another status, fails the build. At runtime an unlisted code still
 * gets an answer (a title made from its name), is logged once, and never breaks a request.
 */
public final class ErrorDictionary {

    /** One line of the dictionary. {@code status} is null for a log code or an event; {@code level} the other way round. */
    public record Entry(String code, String kind, Integer status, String category, String title, String description,
                        String action, boolean retryable, String level, List<String> raisedBy) {
    }

    private static final Logger LOG = LoggerFactory.getLogger(ErrorDictionary.class);
    static final String RESOURCE = "cm-error-dictionary.json";
    private static final ErrorDictionary INSTANCE = load(RESOURCE);

    private final Map<String, Entry> entries;
    private final Set<String> reportedUnknown = ConcurrentHashMap.newKeySet();

    private ErrorDictionary(Map<String, Entry> entries) {
        this.entries = entries;
    }

    /** The platform's dictionary, loaded once. */
    public static ErrorDictionary instance() {
        return INSTANCE;
    }

    static ErrorDictionary load(String resource) {
        try (InputStream in = ErrorDictionary.class.getClassLoader().getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException(resource + " is missing from the class path");
            }
            return parse(new ObjectMapper().readTree(in));
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read " + resource, e);
        }
    }

    static ErrorDictionary parse(JsonNode root) {
        Map<String, Entry> entries = new LinkedHashMap<>();
        for (JsonNode n : root.path("entries")) {
            List<String> raisedBy = new ArrayList<>();
            n.path("raisedBy").forEach(s -> raisedBy.add(s.asText()));
            Entry entry = new Entry(n.path("code").asText(), n.path("kind").asText(),
                    n.hasNonNull("status") ? n.get("status").asInt() : null, n.path("category").asText(),
                    n.path("title").asText(), n.path("description").asText(), n.path("action").asText(),
                    n.path("retryable").asBoolean(false), n.hasNonNull("level") ? n.get("level").asText() : null,
                    List.copyOf(raisedBy));
            if (entries.put(entry.code(), entry) != null) {
                throw new IllegalStateException("The error dictionary lists " + entry.code() + " twice");
            }
        }
        return new ErrorDictionary(Map.copyOf(entries));
    }

    public Optional<Entry> find(String code) {
        return Optional.ofNullable(entries.get(code));
    }

    public Collection<Entry> entries() {
        return entries.values();
    }

    /** The entry, or an {@link IllegalArgumentException} for a code nobody has registered. */
    public Entry require(String code) {
        return find(code).orElseThrow(() -> new IllegalArgumentException("Not in the error dictionary: " + code));
    }

    /**
     * The title for an API problem. A code that is not listed (the CI check stops that reaching a release) is logged once
     * and gets a title made from its name, so the answer is still a proper problem.
     */
    public String title(String code) {
        Entry entry = entries.get(code);
        if (entry != null) {
            return entry.title();
        }
        if (reportedUnknown.add(code)) {
            LOG.error("[CM-ERRDICT-UNLISTED] The problem '{}' is raised but not in the error dictionary", code);
        }
        String words = code.replace('-', ' ');
        return words.substring(0, 1).toUpperCase(Locale.ROOT) + words.substring(1);
    }

    /** Whether trying the same request again later can work. Unlisted: only when the status says so. */
    public boolean retryable(String code, int status) {
        Entry entry = entries.get(code);
        return entry != null ? entry.retryable() : status == 429 || status == 503;
    }

    /** The status the dictionary gives a problem, or the one passed when it is unlisted. */
    public int status(String code, int fallback) {
        Entry entry = entries.get(code);
        return entry != null && entry.status() != null ? entry.status() : fallback;
    }
}
