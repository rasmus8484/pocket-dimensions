package com.pocketdimensions.init;

import com.pocketdimensions.PocketDimensionsMod;
import com.pocketdimensions.advancement.PocketEventTrigger;
import net.minecraft.advancements.CriterionTrigger;
import net.minecraft.core.registries.Registries;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public class ModTriggers {
    public static final DeferredRegister<CriterionTrigger<?>> TRIGGERS =
            DeferredRegister.create(Registries.TRIGGER_TYPE, PocketDimensionsMod.MODID);

    /** pocketdimensions:event, the trigger behind the mod's own advancement moments (advancement/Milestones). */
    public static final RegistryObject<PocketEventTrigger> EVENT = TRIGGERS.register("event", PocketEventTrigger::new);
}
