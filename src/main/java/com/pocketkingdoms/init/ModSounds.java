package com.pocketkingdoms.init;

import com.pocketkingdoms.PocketKingdomsMod;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUND_EVENTS =
            DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, PocketKingdomsMod.MODID);

    /** Distant-thunder crack played as reality splits around an anchor under an Anchor Breaker. */
    public static final RegistryObject<SoundEvent> REALITY_CRACK = SOUND_EVENTS.register("reality_crack",
            () -> SoundEvent.createVariableRangeEvent(Identifier.fromNamespaceAndPath(PocketKingdomsMod.MODID, "reality_crack")));
}
