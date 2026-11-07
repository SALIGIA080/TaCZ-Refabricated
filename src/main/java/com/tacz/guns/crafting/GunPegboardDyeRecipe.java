package com.tacz.guns.crafting;

import com.mojang.serialization.MapCodec;
import com.tacz.guns.init.ModRecipe;
import com.tacz.guns.item.GunPegboardItem;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.PlacementInfo;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

public class GunPegboardDyeRecipe extends CustomRecipe {
   public GunPegboardDyeRecipe() {
   }

   @Override
   public boolean matches(CraftingInput input, Level level) {
      boolean pegboard = false;
      boolean dye = false;

      for (int slot = 0; slot < input.size(); slot++) {
         ItemStack stack = input.getItem(slot);
         if (!stack.isEmpty()) {
            if (stack.getItem() instanceof GunPegboardItem) {
               if (pegboard) {
                  return false;
               }
               pegboard = true;
            } else {
               if (!(stack.getItem() instanceof DyeItem)) {
                  return false;
               }
               dye = true;
            }
         }
      }

      return pegboard && dye;
   }

   @Override
   public ItemStack assemble(CraftingInput input) {
      ItemStack pegboard = ItemStack.EMPTY;
      List<DyeColor> dyes = new ArrayList<>();

      for (int slot = 0; slot < input.size(); slot++) {
         ItemStack stack = input.getItem(slot);
         if (!stack.isEmpty()) {
            if (stack.getItem() instanceof GunPegboardItem && pegboard.isEmpty()) {
               pegboard = stack;
            } else {
               if (!(stack.getItem() instanceof DyeItem)) {
                  return ItemStack.EMPTY;
               }
               dyes.add(stack.getOrDefault(DataComponents.DYE, DyeColor.WHITE));
            }
         }
      }

      return !pegboard.isEmpty() && !dyes.isEmpty() ? DyedItemColor.applyDyes(pegboard, dyes) : ItemStack.EMPTY;
   }

   @Override
   public CraftingBookCategory category() {
      return CraftingBookCategory.MISC;
   }

   @Override
   public RecipeSerializer<? extends CustomRecipe> getSerializer() {
      return ModRecipe.GUN_PEGBOARD_DYE_RECIPE_SERIALIZER;
   }

   public static final MapCodec<GunPegboardDyeRecipe> CODEC =
         MapCodec.unit(new GunPegboardDyeRecipe());

   public static final StreamCodec<RegistryFriendlyByteBuf, GunPegboardDyeRecipe> STREAM_CODEC =
         StreamCodec.unit(new GunPegboardDyeRecipe());
}
