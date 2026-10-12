package com.pocketkingdoms.init;

import com.pocketkingdoms.PocketKingdomsMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public class ModCreativeTabs {

    public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, PocketKingdomsMod.MODID);

    public static final RegistryObject<CreativeModeTab> POCKET_KINGDOMS_TAB =
            CREATIVE_TABS.register("pocket_kingdoms_tab",
                    () -> CreativeModeTab.builder()
                            .title(Component.translatable("itemGroup.pocketkingdoms"))
                            .icon(() -> new ItemStack(ModItems.POCKET_ANCHOR_ITEM.get()))
                            .displayItems((params, output) -> {
                                output.accept(ModItems.POCKET_ANCHOR_ITEM.get());
                                output.accept(ModItems.WORLD_SEED.get());
                                output.accept(ModItems.WORLD_ANCHOR_ITEM.get());
                                output.accept(ModItems.WORLD_BREACHER_ITEM.get());
                                output.accept(ModItems.ANCHOR_BREAKER_ITEM.get());
                                output.accept(ModItems.WORLD_CORE_ITEM.get());
                                output.accept(ModItems.BOUNDARY_BLOCK_ITEM.get());
                                output.accept(ModItems.AURORA_STONE_ITEM.get());
                                output.accept(ModItems.AURORA_SLATE_ITEM.get());
                            })
                            .build());
}
