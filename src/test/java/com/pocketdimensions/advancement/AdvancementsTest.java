package com.pocketdimensions.advancement;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/** The advancement tab's files agree with each other, with the language file and with the events the code fires. */
class AdvancementsTest {

    private static final Path DIR = Path.of("src/main/resources/data/pocketdimensions/advancement/main");
    private static final Path LANG = Path.of("src/main/resources/assets/pocketdimensions/lang/en_us.json");

    private static Map<String, JsonObject> load() throws IOException {
        Map<String, JsonObject> all = new HashMap<>();
        try (Stream<Path> files = Files.list(DIR)) {
            for (Path f : files.toList()) {
                String id = "pocketdimensions:main/" + f.getFileName().toString().replace(".json", "");
                all.put(id, JsonParser.parseString(Files.readString(f, StandardCharsets.UTF_8)).getAsJsonObject());
            }
        }
        return all;
    }

    @Test
    void everyParentExistsAndThereIsOneRoot() throws IOException {
        Map<String, JsonObject> all = load();
        int roots = 0;
        for (var e : all.entrySet()) {
            if (!e.getValue().has("parent")) { roots++; continue; }
            assertTrue(all.containsKey(e.getValue().get("parent").getAsString()), e.getKey() + " has a missing parent");
        }
        assertEquals(1, roots);
    }

    @Test
    void customEventsMatchTheOnesTheCodeFires() throws IOException {
        Set<String> used = new HashSet<>();
        for (var e : load().entrySet()) {
            for (var c : e.getValue().getAsJsonObject("criteria").entrySet()) {
                JsonObject criterion = c.getValue().getAsJsonObject();
                if (!criterion.get("trigger").getAsString().equals("pocketdimensions:event")) continue;
                String event = criterion.getAsJsonObject("conditions").get("event").getAsString();
                assertTrue(Milestones.ALL.contains(event), e.getKey() + " listens for an event nothing fires: " + event);
                used.add(event);
            }
        }
        assertEquals(Set.copyOf(Milestones.ALL), used, "every event the code fires earns something");
    }

    @Test
    void everyTitleAndDescriptionHasText() throws IOException {
        JsonObject lang = JsonParser.parseString(Files.readString(LANG, StandardCharsets.UTF_8)).getAsJsonObject();
        for (var e : load().entrySet()) {
            JsonObject display = e.getValue().getAsJsonObject("display");
            for (String part : new String[]{"title", "description"}) {
                String key = display.getAsJsonObject(part).get("translate").getAsString();
                assertTrue(lang.has(key), e.getKey() + " is missing " + key);
                assertFalse(lang.get(key).getAsString().contains("—"), key + " uses an em dash");
            }
        }
    }
}
