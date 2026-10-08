package com.pocketdimensions.advancement;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.advancements.criterion.ContextAwarePredicate;
import net.minecraft.advancements.criterion.EntityPredicate;
import net.minecraft.advancements.criterion.SimpleCriterionTrigger;
import net.minecraft.server.level.ServerPlayer;

import java.util.Optional;

/**
 * The mod's one advancement trigger, {@code pocketdimensions:event}: code fires it with an event name (Milestones) and
 * an advancement listens for one, e.g. {"trigger": "pocketdimensions:event", "conditions": {"event": "steal_anchor"}}.
 */
public class PocketEventTrigger extends SimpleCriterionTrigger<PocketEventTrigger.TriggerInstance> {

    @Override
    public Codec<TriggerInstance> codec() {
        return TriggerInstance.CODEC;
    }

    public void trigger(ServerPlayer player, String event) {
        this.trigger(player, instance -> instance.event().equals(event));
    }

    public record TriggerInstance(Optional<ContextAwarePredicate> player, String event)
            implements SimpleCriterionTrigger.SimpleInstance {
        public static final Codec<TriggerInstance> CODEC = RecordCodecBuilder.create(i -> i.group(
                EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("player").forGetter(TriggerInstance::player),
                Codec.STRING.fieldOf("event").forGetter(TriggerInstance::event)
        ).apply(i, TriggerInstance::new));
    }
}
