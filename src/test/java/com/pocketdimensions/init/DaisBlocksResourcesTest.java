package com.pocketdimensions.init;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** The Stone Dais's two blocks have everything the game needs: names, models, items, drops, and the pickaxe (RL-024). */
class DaisBlocksResourcesTest {

    private static final Path ASSETS = Path.of("src/main/resources/assets/pocketdimensions");
    private static final Path DATA = Path.of("src/main/resources/data");
    private static final String[] BLOCKS = {"heartstone", "aurora_stone"};

    private static JsonObject read(Path p) throws IOException {
        assertTrue(Files.exists(p), "missing " + p);
        return JsonParser.parseString(Files.readString(p, StandardCharsets.UTF_8)).getAsJsonObject();
    }

    @Test
    void eachHasANameABlockstateAndAnItem() throws IOException {
        JsonObject lang = read(ASSETS.resolve("lang/en_us.json"));
        assertEquals("Heartstone", lang.get("block.pocketdimensions.heartstone").getAsString());
        assertEquals("Aurora Stone", lang.get("block.pocketdimensions.aurora_stone").getAsString());
        for (String b : BLOCKS) {
            read(ASSETS.resolve("blockstates/" + b + ".json"));
            read(ASSETS.resolve("models/block/" + b + ".json"));
            read(ASSETS.resolve("items/" + b + ".json"));
        }
    }

    @Test
    void eachDropsItselfAndNeedsAPickaxe() throws IOException {
        JsonArray pickaxe = read(DATA.resolve("minecraft/tags/block/mineable/pickaxe.json")).getAsJsonArray("values");
        for (String b : BLOCKS) {
            assertTrue(pickaxe.contains(new com.google.gson.JsonPrimitive("pocketdimensions:" + b)), b + " in mineable/pickaxe");
            JsonObject loot = read(DATA.resolve("pocketdimensions/loot_table/blocks/" + b + ".json"));
            assertEquals("pocketdimensions:" + b, loot.getAsJsonArray("pools").get(0).getAsJsonObject()
                    .getAsJsonArray("entries").get(0).getAsJsonObject().get("name").getAsString());
        }
    }

    @Test
    void auroraStoneIsTheHeartstoneWithGlowingVeinsOnTop() throws IOException {
        JsonObject m = read(ASSETS.resolve("models/block/aurora_stone.json"));
        assertEquals("minecraft:cutout", m.get("render_type").getAsString());
        assertEquals("pocketdimensions:block/heartstone", m.getAsJsonObject("textures").get("rock").getAsString(),
                "the rock is the heartstone texture itself, so the two always match");
        JsonArray el = m.getAsJsonArray("elements");
        assertEquals(3, el.size());
        assertFalse(el.get(0).getAsJsonObject().has("light_emission"), "the rock is lit like any block");
        assertEquals(6, el.get(1).getAsJsonObject().get("light_emission").getAsInt(), "the rim glows a little");
        assertEquals(15, el.get(2).getAsJsonObject().get("light_emission").getAsInt(), "the veins shine at night");
    }
}
