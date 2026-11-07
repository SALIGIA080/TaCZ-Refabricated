package com.tacz.guns.util;

import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IAmmo;
import com.tacz.guns.api.item.builder.AmmoItemBuilder;
import javax.annotation.Nullable;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

public final class AmmoStorageHelper {
   private AmmoStorageHelper() {
   }

   public static boolean isValidAmmo(ItemStack stack) {
      return !stack.isEmpty() && stack.getItem() instanceof IAmmo ammo ? TimelessAPI.getCommonAmmoIndex(ammo.getAmmoId(stack)).isPresent() : false;
   }

   @Nullable
   public static Identifier getAmmoId(ItemStack stack) {
      return stack.getItem() instanceof IAmmo ammo ? ammo.getAmmoId(stack) : null;
   }

   public static int getSort(ItemStack stack) {
      Identifier id = getAmmoId(stack);
      return id == null ? Integer.MAX_VALUE : TimelessAPI.getCommonAmmoIndex(id).map(index -> index.getSort()).orElse(Integer.MAX_VALUE);
   }

   public static int getStackSize(ItemStack stack) {
      Identifier id = getAmmoId(stack);
      return id == null ? stack.getMaxStackSize() : TimelessAPI.getCommonAmmoIndex(id).map(index -> index.getStackSize()).orElse(stack.getMaxStackSize());
   }

   public static int getStorageSlotLimit(ItemStack stack, int slotStacks) {
      long limit = (long)getStackSize(stack) * slotStacks;
      return limit > 2147483647L ? Integer.MAX_VALUE : (int)limit;
   }

   public static ItemStack createAmmoStack(Identifier ammoId, int count) {
      return AmmoItemBuilder.create().setId(ammoId).setCount(count).build();
   }
}
