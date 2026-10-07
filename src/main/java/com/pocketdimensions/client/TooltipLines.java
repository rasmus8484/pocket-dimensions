package com.pocketdimensions.client;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Which lang keys make up an item's tooltip. Every line lives in the lang file: a summary that always shows
 * ({@code tooltip.pocketdimensions.<item>.0, .1, ...}) and the rules behind it, shown while Shift is held
 * ({@code tooltip.pocketdimensions.<item>.more.0, ...}). Lines are counted up from 0 until a key is missing, so adding a
 * line is a lang-file change only. Pure logic (no Minecraft classes) so it is unit tested.
 */
public final class TooltipLines {

    public static final String HOLD_SHIFT = "tooltip.pocketdimensions.hold_shift";

    private TooltipLines() {}

    public static List<String> keys(String item, boolean shift, Predicate<String> exists) {
        List<String> out = new ArrayList<>();
        String base = "tooltip.pocketdimensions." + item + ".";
        collect(base, exists, out);
        if (exists.test(base + "more.0")) {
            if (shift) collect(base + "more.", exists, out);
            else out.add(HOLD_SHIFT);
        }
        return out;
    }

    private static void collect(String prefix, Predicate<String> exists, List<String> out) {
        for (int i = 0; exists.test(prefix + i); i++) out.add(prefix + i);
    }
}
