package com.pocketdimensions.client;

import com.pocketdimensions.PocketDimensionsMod;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;

import java.util.List;

/** Adds each mod item's tooltip lines from the lang file ({@link TooltipLines}): grey summary, darker rules on Shift. */
public final class ItemTooltips {

    private ItemTooltips() {}

    public static void register() {
        ItemTooltipEvent.BUS.addListener(ItemTooltips::onTooltip);
    }

    private static void onTooltip(ItemTooltipEvent event) {
        Identifier id = BuiltInRegistries.ITEM.getKey(event.getItemStack().getItem());
        if (!PocketDimensionsMod.MODID.equals(id.getNamespace())) return;
        List<String> keys = TooltipLines.keys(id.getPath(), Minecraft.getInstance().hasShiftDown(), I18n::exists);
        for (String key : keys) {
            ChatFormatting style = key.equals(TooltipLines.HOLD_SHIFT) || key.contains(".more.")
                    ? ChatFormatting.DARK_GRAY : ChatFormatting.GRAY;
            event.getToolTip().add(Component.translatable(key).withStyle(style));
        }
    }
}
