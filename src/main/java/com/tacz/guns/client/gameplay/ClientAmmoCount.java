package com.tacz.guns.client.gameplay;

import com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.item.IAmmo;
import com.tacz.guns.api.item.IAmmoBox;
import com.tacz.guns.api.item.IGun;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

public final class ClientAmmoCount {
   public static final int MAX_DISPLAY_COUNT = 9999;

   private ClientAmmoCount() {
   }

   public static int getCached(LocalPlayer player, ItemStack gun, IGun iGun) {
      return IClientPlayerGunOperator.fromLocalPlayer(player).getDataHolder().getCachedAmmoCount(gun, iGun);
   }

   public static int get(LocalPlayer player, ItemStack gun, IGun iGun) {
      if (!IGunOperator.fromLivingEntity(player).needCheckAmmo()) {
         return 9999;
      } else if (iGun.useDummyAmmo(gun)) {
         return Math.max(0, Math.min(iGun.getDummyAmmoAmount(gun), 9999));
      } else {
         int count = 0;
         Inventory inventory = player.getInventory();

         for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.getItem() instanceof IAmmo ammo && ammo.isAmmoOfGun(gun, stack)) {
               count += Math.min(stack.getCount(), 9999 - count);
            }

            if (stack.getItem() instanceof IAmmoBox box && box.isAmmoBoxOfGun(gun, stack)) {
               if (box.isAllTypeCreative(stack) || box.isCreative(stack)) {
                  return 9999;
               }

               count += Math.min(Math.max(box.getAmmoCount(stack), 0), 9999 - count);
            }

            if (count >= 9999) {
               return 9999;
            }
         }

         return count;
      }
   }
}
