package com.tacz.guns.advancement;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.advancements.predicates.ContextAwarePredicate;
import net.minecraft.advancements.predicates.MinMaxBounds;
import net.minecraft.advancements.triggers.SimpleCriterionTrigger;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

import java.util.Optional;

public class TargetHitTrigger extends SimpleCriterionTrigger<TargetHitTrigger.TriggerInstance> {
   public static final TargetHitTrigger INSTANCE = new TargetHitTrigger();
   static final Identifier ID = Identifier.fromNamespaceAndPath("tacz", "target_hit");

   public static void register() {
      Registry.register(BuiltInRegistries.TRIGGER_TYPES, ID, INSTANCE);
   }

   public Codec<TargetHitTrigger.TriggerInstance> codec() {
      return TriggerInstance.CODEC;
   }

   public void trigger(ServerPlayer player, double distance) {
      this.trigger(player, instance -> instance.matches(distance));
   }

   public record TriggerInstance(Optional<ContextAwarePredicate> player,
                                 MinMaxBounds.Doubles distance) implements SimpleCriterionTrigger.SimpleInstance {
      public static final Codec<TargetHitTrigger.TriggerInstance> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                  ContextAwarePredicate.CODEC.optionalFieldOf("player").forGetter(TargetHitTrigger.TriggerInstance::player),
                  MinMaxBounds.Doubles.CODEC.optionalFieldOf("distance", MinMaxBounds.Doubles.ANY).forGetter(TargetHitTrigger.TriggerInstance::distance)
            ).apply(instance, TargetHitTrigger.TriggerInstance::new));

      public boolean matches(double distance) {
         return this.distance.matches(distance);
      }
   }
}
