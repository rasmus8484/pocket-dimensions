package com.pocketdimensions.init;

import com.pocketdimensions.PocketDimensionsMod;
import com.pocketdimensions.menu.SiegeBlockMenu;
import com.pocketdimensions.menu.WorldCoreMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModMenuTypes {

    public static final DeferredRegister<MenuType<?>> MENU_TYPES =
            DeferredRegister.create(ForgeRegistries.MENU_TYPES, PocketDimensionsMod.MODID);

    public static final RegistryObject<MenuType<WorldCoreMenu>> WORLD_CORE =
            MENU_TYPES.register("world_core", () -> IForgeMenuType.create(WorldCoreMenu::new));

    public static final RegistryObject<MenuType<SiegeBlockMenu>> SIEGE_BLOCK =
            MENU_TYPES.register("siege_block", () -> IForgeMenuType.create(SiegeBlockMenu::new));
}
