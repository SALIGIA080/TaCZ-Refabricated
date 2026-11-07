package com.tacz.guns.advancement;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.advancements.predicates.ContextAwarePredicate;
import net.minecraft.advancements.triggers.SimpleCriterionTrigger;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;

public class GunRefitTrigger extends SimpleCriterionTrigger<GunRefitTrigger.TriggerInstance> {
    public static final GunRefitTrigger INSTANCE = new GunRefitTrigger();
    private static final Identifier ID = Identifier.fromNamespaceAndPath("tacz", "gun_refit");

    public static void register() {
        Registry.register(BuiltInRegistries.TRIGGER_TYPES, ID, INSTANCE);
    }

    @Override
    public Codec<GunRefitTrigger.TriggerInstance> codec() {
        return TriggerInstance.CODEC;
    }

    public void trigger(ServerPlayer player, ItemStack before, ItemStack after) {
        if (!ItemStack.matches(before, after)) {
            this.trigger(player, instance -> true);
        }
    }

    public record TriggerInstance(Optional<ContextAwarePredicate> player) implements SimpleCriterionTrigger.SimpleInstance {
        public static final Codec<GunRefitTrigger.TriggerInstance> CODEC = RecordCodecBuilder.create(instance ->
                instance.group(
                        ContextAwarePredicate.CODEC.optionalFieldOf("player").forGetter(GunRefitTrigger.TriggerInstance::player)
                ).apply(instance, GunRefitTrigger.TriggerInstance::new));
    }
}
