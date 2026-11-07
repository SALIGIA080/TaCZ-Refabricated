package com.tacz.guns.entity.shooter;

import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.gun.AbstractGunItem;
import com.tacz.guns.resource.index.CommonGunIndex;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

public class LivingEntityUnload {
   private final LivingEntity shooter;
   private final ShooterDataHolder data;
   private final LivingEntityDrawGun draw;
   private final LivingEntityShoot shoot;
   private final LivingEntityMelee melee;

   public LivingEntityUnload(LivingEntity shooter, ShooterDataHolder data, LivingEntityDrawGun draw, LivingEntityShoot shoot, LivingEntityMelee melee) {
      this.shooter = shooter;
      this.data = data;
      this.draw = draw;
      this.shoot = shoot;
      this.melee = melee;
   }

   public void unloadAmmo() {
      if (!this.shooter.level().isClientSide()
         && this.shooter.isAlive()
         && !this.shooter.isSpectator()
         && this.data.currentGunItem != null
         && !this.data.reloadStateType.isReloading()
         && !this.data.isBolting
         && this.data.meleePrepTickCount < 0
         && this.draw.getDrawCoolDown() == 0L
         && this.shoot.getShootCoolDown() == 0L
         && this.melee.getMeleeCoolDown() == 0L
         && !this.data.isAiming
         && !(this.data.aimingProgress > 0.0F)
         && !(this.data.sprintTimeS > 0.0F)
         && !this.shooter.isSprinting()) {
         ItemStack stack = this.shooter.getMainHandItem();
         if (stack == this.data.currentGunItem.get() && stack.getItem() instanceof AbstractGunItem gun && !gun.useInventoryAmmo(stack)) {
            CommonGunIndex index = TimelessAPI.getCommonGunIndex(gun.getGunId(stack)).orElse(null);
            if (index != null && index.canUnloadAmmo()) {
               gun.unloadAmmo(this.data, stack, this.shooter);
            }
         }
      }
   }
}
