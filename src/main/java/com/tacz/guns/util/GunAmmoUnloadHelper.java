package com.tacz.guns.util;

import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.item.IAmmoBox;
import com.tacz.guns.api.item.builder.AmmoItemBuilder;
import com.tacz.guns.api.item.gun.AbstractGunItem;
import com.tacz.guns.config.sync.SyncConfig;
import cn.sh1rocu.tacz.util.itemhandler.IItemHandler;
import cn.sh1rocu.tacz.util.itemhandler.ItemHandlerHelper;
import com.tacz.guns.resource.index.CommonAmmoIndex;
import com.tacz.guns.resource.index.CommonGunIndex;
import com.tacz.guns.resource.pojo.data.gun.Bolt;
import com.tacz.guns.resource.pojo.data.gun.FeedType;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public final class GunAmmoUnloadHelper {
   private GunAmmoUnloadHelper() {
   }

   public static int unloadAmmo(LivingEntity shooter, ItemStack stack, AbstractGunItem gun, CommonGunIndex index, boolean unloadBarrel, int amount) {
      if (amount >= -1 && !shooter.level().isClientSide() && !gun.useInventoryAmmo(stack)) {
         int currentAmmo = Math.max(0, gun.getCurrentAmmoCount(stack));
         int magazineAmmo = amount == -1 ? currentAmmo : Math.min(currentAmmo, amount);
         boolean chambered = unloadBarrel && index.getGunData().getBolt() != Bolt.OPEN_BOLT && gun.hasBulletInBarrel(stack);
         int unloaded = magazineAmmo + (chambered ? 1 : 0);
         if (unloaded == 0) {
            return 0;
         } else {
            boolean returnAmmo = IGunOperator.fromLivingEntity(shooter).needCheckAmmo()
               && !index.getGunData().getReloadData().isInfinite()
               && index.getGunData().getReloadData().getType() != FeedType.FUEL;
            boolean dummyAmmo = gun.useDummyAmmo(stack);
            Identifier ammoId = index.getGunData().getAmmoId();
            CommonAmmoIndex ammoIndex = TimelessAPI.getCommonAmmoIndex(ammoId).orElse(null);
            if (!returnAmmo || dummyAmmo || ammoIndex != null && ammoIndex.getStackSize() > 0) {
               if (magazineAmmo > 0) {
                  gun.setCurrentAmmoCount(stack, currentAmmo - magazineAmmo);
               }

               if (chambered) {
                  gun.setBulletInBarrel(stack, false);
               }

               if (!returnAmmo) {
                  return unloaded;
               } else if (dummyAmmo) {
                  gun.addDummyAmmoAmount(stack, unloaded);
                  return unloaded;
               } else {
                  IItemHandler inventory = shooter.tacz$getItemHandler(null).orElse(null);
                  int remaining = inventory == null ? unloaded : returnToAmmoBoxes(inventory, ammoId, ammoIndex.getStackSize(), unloaded);

                  while (remaining > 0) {
                     int count = Math.min(remaining, ammoIndex.getStackSize());
                     ItemStack ammoStack = AmmoItemBuilder.create().setId(ammoId).setCount(count).build();
                     if (shooter instanceof Player player) {
                        ItemHandlerHelper.giveItemToPlayer(player, ammoStack);
                     } else {
                        ItemStack overflow = inventory == null ? ammoStack : ItemHandlerHelper.insertItemStacked(inventory, ammoStack, false);
                        if (!overflow.isEmpty()) {
                           shooter.spawnAtLocation((net.minecraft.server.level.ServerLevel)shooter.level(), overflow);
                        }
                     }

                     remaining -= count;
                  }

                  return unloaded;
               }
            } else {
               return 0;
            }
         }
      } else {
         return 0;
      }
   }

   private static int returnToAmmoBoxes(IItemHandler inventory, Identifier ammoId, int stackSize, int amount) {
      int remaining = amount;

      for (int slot = 0; slot < inventory.getSlots() && remaining > 0; slot++) {
         ItemStack boxStack = inventory.getStackInSlot(slot);
         if (boxStack.getItem() instanceof IAmmoBox box
            && !box.isCreative(boxStack)
            && !box.isAllTypeCreative(boxStack)
            && ammoId.equals(box.getAmmoId(boxStack))) {
            int current = box.getAmmoCount(boxStack);
            if (current >= 0) {
               long capacity = (long)stackSize * ((Integer)SyncConfig.AMMO_BOX_STACK_SIZE.get()).intValue() * (box.getAmmoLevel(boxStack) + 1L);
               long space = Math.max(0L, Math.min(capacity, 2147483647L) - current);
               int accepted = (int)Math.min(space, (long)remaining);
               if (accepted > 0) {
                  box.setAmmoCount(boxStack, current + accepted);
                  remaining -= accepted;
               }
            }
         }
      }

      return remaining;
   }
}
