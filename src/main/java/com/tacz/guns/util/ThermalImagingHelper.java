package com.tacz.guns.util;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;

public final class ThermalImagingHelper {
   public static final TagKey<EntityType<?>> BLACKLIST = TagKey.create(Registries.ENTITY_TYPE, Identifier.fromNamespaceAndPath("tacz", "thermal_imaging/blacklist"));
   public static final TagKey<EntityType<?>> WHITELIST = TagKey.create(Registries.ENTITY_TYPE, Identifier.fromNamespaceAndPath("tacz", "thermal_imaging/whitelist"));

   private ThermalImagingHelper() {
   }

   public static boolean isHeatSource(Entity entity) {
      EntityType<?> type = entity.getType();
      return !BuiltInRegistries.ENTITY_TYPE.wrapAsHolder(type).is(BLACKLIST) && (entity instanceof LivingEntity || BuiltInRegistries.ENTITY_TYPE.wrapAsHolder(type).is(WHITELIST));
   }
}
