package com.tacz.guns.event;

import cn.sh1rocu.tacz.api.LogicalSide;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.event.common.GunFireEvent;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.config.common.GunConfig;
import com.tacz.guns.resource.modifier.custom.SilenceModifier;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import com.tacz.guns.util.AttachmentDataUtils;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * 枪声惊动生物。26.2 移植说明：去掉 Forge 事件注解，
 * 在 {@code TaCZFabric#subscribeEvents} 里注册到 {@code GunFireEvent.CALLBACK}；
 * Fabric 没有 FakePlayer 概念，该检查移除。
 */
public class GunSoundAngerEvent {
   public static void onGunFire(GunFireEvent event) {
      if (!event.isCanceled() && GunConfig.ENABLE_GUN_SOUND_ANGER.get()) {
         if (event.getLogicalSide() == LogicalSide.SERVER && event.getShooter() instanceof ServerPlayer player) {
            if (!player.isCreative() && !player.isSpectator()) {
               ItemStack gunItem = event.getGunItemStack();
               IGun gun = IGun.getIGunOrNull(gunItem);
               if (gun != null) {
                  TimelessAPI.getCommonGunIndex(gun.getGunId(gunItem)).ifPresent(gunIndex -> {
                     GunData gunData = gunIndex.getGunData();
                     if (!hasSilencer(gunItem, gunData)) {
                        float fireMultiplier = gunData.getFireSound().getFireMultiplier();
                        int neutralRadius = getCappedRadius(GunConfig.DEFAULT_GUN_SOUND_NEUTRAL_MOB_ANGER_DISTANCE.get(), fireMultiplier);
                        int hostileRadius = getCappedRadius(GunConfig.DEFAULT_GUN_SOUND_HOSTILE_MOB_ANGER_DISTANCE.get(), fireMultiplier);
                        if (neutralRadius > 0 || hostileRadius > 0) {
                           angerMobs(player, neutralRadius, hostileRadius);
                        }
                     }
                  });
               }
            }
         }
      }
   }

   private static boolean hasSilencer(ItemStack gunItem, GunData gunData) {
      boolean[] found = new boolean[]{false};
      AttachmentDataUtils.getAllAttachmentData(gunItem, gunData, attachmentData -> {
         if (attachmentData.getModifier().containsKey(SilenceModifier.ID)) {
            found[0] = true;
         }
      });
      return found[0];
   }

   private static int getCappedRadius(int baseRadius, float multiplier) {
      return Math.min(Math.round(baseRadius * multiplier), 64);
   }

   private static void angerMobs(ServerPlayer player, int neutralRadius, int hostileRadius) {
      Level level = player.level();
      int searchRadius = Math.max(neutralRadius, hostileRadius);
      double neutralRadiusSqr = (double)neutralRadius * neutralRadius;
      double hostileRadiusSqr = (double)hostileRadius * hostileRadius;
      level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(searchRadius), mob -> shouldAnger(player, mob, neutralRadiusSqr, hostileRadiusSqr)).forEach(mob -> {
         if (mob instanceof NeutralMob) {
            mob.setLastHurtByMob(player);
         } else {
            mob.setTarget(player);
         }
      });
   }

   private static boolean shouldAnger(ServerPlayer player, Mob mob, double neutralRadiusSqr, double hostileRadiusSqr) {
      if (!mob.isAlive() || mob.getTarget() != null) {
         return false;
      } else if (mob.isAlliedTo(player) || !mob.canAttack(player)) {
         return false;
      } else if (mob instanceof NeutralMob) {
         return mob instanceof TamableAnimal tamable && tamable.isTame()
            ? false
            : !(mob instanceof IronGolem ironGolem && ironGolem.isPlayerCreated()) && mob.distanceToSqr(player) <= neutralRadiusSqr;
      } else {
         return mob instanceof Enemy && mob.distanceToSqr(player) <= hostileRadiusSqr;
      }
   }
}
