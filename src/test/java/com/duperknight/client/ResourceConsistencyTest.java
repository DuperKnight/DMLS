package com.duperknight.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResourceConsistencyTest {
    private static final String LANGUAGE_RESOURCE = "/assets/dmls/lang/en_us.json";
    private static final Pattern TRANSLATION_LITERAL = Pattern.compile(
            "\"((?:dmls\\.(?:button|chat|dropdown|field|help|message|module|option|placeholder|prefix|screen|staff_rank|title|update|validation)|key\\.dmls)\\.[A-Za-z0-9_.-]+)\"");
    private static final Set<String> CONSTRUCTED_NAMESPACES = Set.of(
            "dmls.chat.promo.",
            "dmls.chat.demo.",
            "dmls.chat.containers",
            "dmls.chat.griefs"
    );

    @Test
    void languageResourceIsFlatStrictJsonWithUniqueKeys() throws Exception {
        String json = languageJson();
        try (JsonReader reader = new JsonReader(new StringReader(json))) {
            readUniqueValue(reader, "$");
            assertEquals(JsonToken.END_DOCUMENT, reader.peek(), "language JSON has trailing content");
        }

        JsonObject language = JsonParser.parseString(json).getAsJsonObject();
        assertFalse(language.isEmpty(), "language resource is empty");
        language.entrySet().forEach(entry -> {
            assertFalse(entry.getKey().isBlank(), "language resource contains a blank key");
            assertTrue(entry.getValue().isJsonPrimitive()
                            && entry.getValue().getAsJsonPrimitive().isString(),
                    () -> "translation must be a string: " + entry.getKey());
        });
    }

    @Test
    void everyLiteralAndConstructedTranslationReferenceExists() throws Exception {
        Set<String> languageKeys = JsonParser.parseString(languageJson()).getAsJsonObject().keySet();
        Set<String> references = sourceTranslationLiterals();
        Set<String> unresolved = new TreeSet<>();
        for (String reference : references) {
            if (!CONSTRUCTED_NAMESPACES.contains(reference) && !languageKeys.contains(reference)) {
                unresolved.add(reference);
            }
        }

        Set<String> dynamicKeys = new TreeSet<>();
        addWaveKeys(dynamicKeys, "promo");
        addWaveKeys(dynamicKeys, "demo");
        addScanKeys(dynamicKeys, "containers", List.of("removed", "added"));
        addScanKeys(dynamicKeys, "griefs", List.of("broke", "placed"));
        dynamicKeys.stream().filter(key -> !languageKeys.contains(key)).forEach(unresolved::add);

        assertTrue(unresolved.isEmpty(), () -> "missing en_us translations: " + unresolved);
    }

    private static String languageJson() throws IOException {
        try (InputStream input = ResourceConsistencyTest.class.getResourceAsStream(LANGUAGE_RESOURCE)) {
            assertNotNull(input, "missing " + LANGUAGE_RESOURCE);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static Set<String> sourceTranslationLiterals() throws IOException {
        Path sourceRoot = Path.of("src/main/java");
        assertTrue(Files.isDirectory(sourceRoot), "tests must run from the project root");
        Set<String> result = new TreeSet<>();
        try (Stream<Path> paths = Files.walk(sourceRoot)) {
            for (Path path : paths.filter(file -> file.toString().endsWith(".java")).toList()) {
                Matcher matcher = TRANSLATION_LITERAL.matcher(Files.readString(path));
                while (matcher.find()) {
                    result.add(matcher.group(1));
                }
            }
        }
        return result;
    }

    private static void addWaveKeys(Set<String> keys, String wave) {
        String prefix = "dmls.chat." + wave + ".";
        for (String outcome : List.of("confirmed", "simulated")) {
            keys.add(prefix + outcome + ".one");
            keys.add(prefix + outcome + ".many");
        }
        for (String outcome : List.of(
                "rejected", "timed_out", "blocked", "failed",
                "cancelled_connection", "cancelled", "partial")) {
            keys.add(prefix + outcome);
        }
    }

    private static void addScanKeys(Set<String> keys, String scan, List<String> actions) {
        String prefix = "dmls.chat." + scan + ".";
        for (String suffix : List.of(
                "nothing", "header", "no_results", "no_response",
                "pages", "pages.capped", "start", "cancelled", "progress")) {
            keys.add(prefix + suffix);
        }
        actions.forEach(action -> keys.add(prefix + action));
    }

    private static void readUniqueValue(JsonReader reader, String path) throws IOException {
        switch (reader.peek()) {
            case BEGIN_OBJECT -> {
                reader.beginObject();
                Set<String> objectNames = new HashSet<>();
                while (reader.hasNext()) {
                    String name = reader.nextName();
                    assertTrue(objectNames.add(name), () -> "duplicate JSON key at " + path + "." + name);
                    readUniqueValue(reader, path + "." + name);
                }
                reader.endObject();
            }
            case BEGIN_ARRAY -> {
                reader.beginArray();
                int index = 0;
                while (reader.hasNext()) {
                    readUniqueValue(reader, path + "[" + index++ + "]");
                }
                reader.endArray();
            }
            case STRING, NUMBER -> reader.nextString();
            case BOOLEAN -> reader.nextBoolean();
            case NULL -> reader.nextNull();
            default -> throw new IOException("unexpected JSON token at " + path + ": " + reader.peek());
        }
    }
}
