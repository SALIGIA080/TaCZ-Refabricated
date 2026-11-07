package com.tacz.guns.config.util;

import com.tacz.guns.GunMod;
import com.tacz.guns.config.common.AmmoConfig;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

public final class AmmoBlockConfigRead {
   private static volatile AmmoBlockConfigRead.BlockList passThroughBlocks = new AmmoBlockConfigRead.BlockList(Set.of(), List.of());
   private static volatile AmmoBlockConfigRead.BlockList destroyBlocks = new AmmoBlockConfigRead.BlockList(Set.of(), List.of());

   private AmmoBlockConfigRead() {
   }

   public static void init() {
      passThroughBlocks = readBlockList((List<? extends String>)AmmoConfig.PASS_THROUGH_BLOCKS.get(), "PassThroughBlocks");
      destroyBlocks = readBlockList((List<? extends String>)AmmoConfig.DESTROY_BLOCKS.get(), "DestroyBlocks");
   }

   public static boolean canPassThrough(BlockState state) {
      return passThroughBlocks.matches(state);
   }

   public static boolean canDestroy(BlockState state) {
      return destroyBlocks.matches(state);
   }

   private static AmmoBlockConfigRead.BlockList readBlockList(List<? extends String> entries, String configName) {
      Set<Block> blocks = new HashSet<>();
      Set<TagKey<Block>> tags = new LinkedHashSet<>();

      for (String entry : entries) {
         String value = entry.trim();
         boolean isTag = value.startsWith("#");
         String name = isTag ? value.substring(1) : value;
         Identifier id = name.isEmpty() ? null : Identifier.tryParse(name);
         if (id == null) {
            GunMod.LOGGER.warn("Ignoring invalid block ID or tag '{}' in ammo.{}", entry, configName);
         } else if (isTag) {
            tags.add(TagKey.create(Registries.BLOCK, id));
         } else if (BuiltInRegistries.BLOCK.containsKey(id)) {
            blocks.add(BuiltInRegistries.BLOCK.getValue(id));
         } else {
            GunMod.LOGGER.warn("Ignoring unknown block ID '{}' in ammo.{}", entry, configName);
         }
      }

      return new AmmoBlockConfigRead.BlockList(Set.copyOf(blocks), List.copyOf(tags));
   }

   private record BlockList(Set<Block> blocks, List<TagKey<Block>> tags) {
      private boolean matches(BlockState state) {
         if (this.blocks.contains(state.getBlock())) {
            return true;
         } else {
            for (TagKey<Block> tag : this.tags) {
               if (state.is(tag)) {
                  return true;
               }
            }

            return false;
         }
      }
   }
}
