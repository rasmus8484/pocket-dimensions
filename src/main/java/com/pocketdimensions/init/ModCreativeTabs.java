package com.pocketdimensions.init;

import com.pocketdimensions.PocketDimensionsMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public class ModCreativeTabs {

    public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, PocketDimensionsMod.MODID);

    public static final RegistryObject<CreativeModeTab> POCKET_DIMENSIONS_TAB =
            CREATIVE_TABS.register("pocket_dimensions_tab",
                    () -> CreativeModeTab.builder()
                            .title(Component.translatable("itemGroup.pocketdimensions"))
                            .icon(() -> new ItemStack(ModItems.POCKET_ITEM.get()))
                            .displayItems((params, output) -> {
                                output.accept(ModItems.POCKET_ITEM.get());
                                output.accept(ModItems.WORLD_SEED.get());
                                output.accept(ModItems.POCKET_ANCHOR_ITEM.get());
                                output.accept(ModItems.WORLD_ANCHOR_ITEM.get());
                                output.accept(ModItems.WORLD_BREACHER_ITEM.get());
                                output.accept(ModItems.ANCHOR_BREAKER_ITEM.get());
                                output.accept(ModItems.WORLD_CORE_ITEM.get());
                                output.accept(ModItems.BOUNDARY_BLOCK_ITEM.get());
                            })
                            .build());
}
