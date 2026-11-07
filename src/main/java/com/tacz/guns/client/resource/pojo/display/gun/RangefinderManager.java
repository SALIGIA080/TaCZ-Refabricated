package com.tacz.guns.client.resource.pojo.display.gun;

import com.tacz.guns.api.GunProperties;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.client.resource.index.ClientGunIndex;
import com.tacz.guns.config.common.AmmoConfig;
import com.tacz.guns.resource.modifier.AttachmentCacheProperty;
import com.tacz.guns.resource.pojo.data.gun.BulletData;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import com.tacz.guns.util.HitboxHelper;
import com.tacz.guns.util.block.BlockRayTrace;
import com.tacz.guns.util.math.Easing;
import com.tacz.guns.util.math.MathUtil;
import com.tacz.guns.util.time.GunTime;
import java.util.Comparator;
import java.util.Optional;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.ClipContext.Block;
import net.minecraft.world.level.ClipContext.Fluid;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult.Type;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public final class RangefinderManager {
   private static final String MISS_TEXT = "---";
   private static final double ENTITY_XZ_EXPAND = 0.25;
   private static final double ENTITY_Y_EXPAND = 8.0;
   private static final long MILLIS_PER_TICK = 50L;
   private static final Predicate<Entity> RANGEFINDER_TARGETS = entity -> entity != null && entity.isPickable() && !entity.isSpectator() && entity.isAlive();
   private static Rangefinder currentConfig = null;
   private static long lastUpdateTimestamp = 0L;
   private static int lastRefreshTick = 2;
   private static String text = "---";
   private static boolean hitEntity = false;
   private static double hitDistance = 0.0;
   private static float impactProjectionY = 0.0F;
   private static int impactPredictionColor = -1;
   private static float impactPredictionScale = 1.0F;
   private static boolean showImpactPrediction = false;

   private RangefinderManager() {
   }

   public static void resetTimestamps() {
      currentConfig = null;
      lastUpdateTimestamp = 0L;
      lastRefreshTick = 2;
      clearResult();
   }

   public static void setCurrentConfig(@Nullable Rangefinder config) {
      currentConfig = config;
   }

   public static void clearCurrentConfig() {
      currentConfig = null;
   }

   public static String getText(ItemStack stack) {
      if (currentConfig == null) {
         return "";
      } else {
         Minecraft mc = Minecraft.getInstance();
         LocalPlayer player = mc.player;
         if (player != null && stack != null && !stack.isEmpty()) {
            int refreshTick = currentConfig.getRefreshTick();
            long now = GunTime.nowMillis();
            if (lastUpdateTimestamp != 0L && GunTime.isWithin(lastUpdateTimestamp, refreshTick * 50L) && refreshTick == lastRefreshTick) {
               return text;
            } else {
               lastUpdateTimestamp = now;
               lastRefreshTick = refreshTick;
               updateResult(player, stack, currentConfig);
               return text;
            }
         } else {
            lastUpdateTimestamp = 0L;
            clearResult();
            return "---";
         }
      }
   }

   public static boolean shouldRenderImpactPrediction() {
      if (!showImpactPrediction) {
         return false;
      } else {
         Minecraft mc = Minecraft.getInstance();
         LocalPlayer player = mc.player;
         if (player != null && GunTime.isWithin(lastUpdateTimestamp, (lastRefreshTick + 1L) * 50L)) {
            if (player instanceof IClientPlayerGunOperator operator && !(operator.getClientAimingProgress(mc.getDeltaTracker().getGameTimeDeltaPartialTick(true)) < 0.9F)) {
               return true;
            } else {
               showImpactPrediction = false;
               return false;
            }
         } else {
            showImpactPrediction = false;
            return false;
         }
      }
   }

   public static float getImpactProjectionY() {
      return impactProjectionY;
   }

   public static int getImpactPredictionColor() {
      return impactPredictionColor;
   }

   public static float getImpactPredictionScale() {
      return impactPredictionScale;
   }

   private static void updateResult(LocalPlayer player, ItemStack stack, Rangefinder config) {
      impactPredictionColor = config.getImpactPredictionColor();
      impactPredictionScale = config.getImpactPredictionScale();
      if (!(stack.getItem() instanceof IGun iGun && player instanceof IClientPlayerGunOperator operator)) {
         clearResult();
      } else if (operator.getClientAimingProgress(Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(true)) < 0.9F) {
         clearResult();
      } else {
         float maxDistance = config.getMaxDistance();
         Vec3 start = player.getEyePosition(Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(true));
         Vec3 look = player.getViewVector(Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(true));
         Vec3 end = start.add(look.scale(maxDistance));
         BlockHitResult blockResult = BlockRayTrace.rayTraceBlocks(player.level(), new ClipContext(start, end, Block.COLLIDER, Fluid.NONE, player));
         double blockDistance = blockResult.getType() == Type.MISS ? Double.MAX_VALUE : start.distanceTo(blockResult.getLocation());
         RangefinderManager.EntityHit entityHit = findEntityHit(player, start, end, blockDistance).orElse(null);
         if (entityHit != null) {
            double distance = start.distanceTo(entityHit.hitPos());
            setDistanceText(distance, true);
            updateImpactPrediction(player, stack, iGun, config, distance);
         } else if (blockDistance != Double.MAX_VALUE) {
            setDistanceText(blockDistance, false);
            showImpactPrediction = false;
         } else {
            clearResult();
         }
      }
   }

   private static Optional<RangefinderManager.EntityHit> findEntityHit(LocalPlayer player, Vec3 start, Vec3 end, double maxDistance) {
      AABB scanBox = new AABB(start, end).inflate(0.25, 8.0, 0.25);
      return player.level()
         .getEntities(player, scanBox, RANGEFINDER_TARGETS)
         .stream()
         .filter(entity -> !entity.equals(player))
         .filter(entity -> !entity.isPassengerOfSameVehicle(player))
         .map(
            entity -> HitboxHelper.getFixedBoundingBox(entity, player)
               .inflate(0.25, 8.0, 0.25)
               .clip(start, end)
               .map(RangefinderManager.EntityHit::new)
               .orElse(null)
         )
         .filter(hit -> hit != null && start.distanceTo(hit.hitPos()) <= maxDistance)
         .min(Comparator.comparingDouble(hit -> start.distanceTo(hit.hitPos())));
   }

   private static void setDistanceText(double distance, boolean entityHit) {
      hitDistance = distance;
      hitEntity = entityHit;
      int roundedDistance = Mth.floor(distance + 0.5);
      text = roundedDistance > 999 ? "---" : Integer.toString(roundedDistance);
      if ("---".equals(text)) {
         showImpactPrediction = false;
      }
   }

   private static void clearResult() {
      text = "---";
      hitEntity = false;
      hitDistance = 0.0;
      impactProjectionY = 0.0F;
      showImpactPrediction = false;
   }

   private static void updateImpactPrediction(LocalPlayer player, ItemStack stack, IGun iGun, Rangefinder config, double distance) {
      showImpactPrediction = false;
      if (hitEntity && config.isShowImpactPrediction() && !"---".equals(text) && !(distance <= 0.0)) {
         Identifier gunId = iGun.getGunId(stack);
         ClientGunIndex gunIndex = TimelessAPI.getClientGunIndex(gunId).orElse(null);
         if (gunIndex != null) {
            GunData gunData = gunIndex.getGunData();
            BulletData bulletData = gunData != null ? gunData.getBulletData() : null;
            if (gunData != null && bulletData != null) {
               float speed = getAmmoSpeed(player, stack, bulletData);
               if (!(speed <= 0.0F)) {
                  Vec3 look = player.getViewVector(Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(true));
                  double drop = calculateDrop(distance, look.y, speed / 20.0F, bulletData.getGravity(), bulletData.getFriction());
                  if (Double.isFinite(drop)) {
                     float fov = getCurrentFov(player, stack, iGun);
                     double angle = Math.atan2(-drop, Math.max(distance, 0.001));
                     impactProjectionY = (float)(Math.tan(angle) / Math.tan(Math.toRadians(fov) / 2.0));
                     showImpactPrediction = Float.isFinite(impactProjectionY);
                  }
               }
            }
         }
      }
   }

   private static float getAmmoSpeed(LocalPlayer player, ItemStack stack, BulletData bulletData) {
      float speed = bulletData.getSpeed();
      AttachmentCacheProperty cacheProperty = IGunOperator.fromLivingEntity(player).getCacheProperty();
      if (cacheProperty != null) {
         try {
            speed = cacheProperty.getCache(GunProperties.AMMO_SPEED);
         } catch (RuntimeException var6) {
            speed = bulletData.getSpeed();
         }
      }

      return Math.max(0.0F, speed * ((Double)AmmoConfig.GLOBAL_BULLET_SPEED_MODIFIER.get()).floatValue());
   }

   private static float getCurrentFov(LocalPlayer player, ItemStack stack, IGun iGun) {
      float configuredFov = ((Integer)Minecraft.getInstance().options.fov().get()).floatValue();
      float aimingProgress = IClientPlayerGunOperator.fromLocalPlayer(player).getClientAimingProgress(Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(true));
      float fovProgress = (float)Easing.easeInExpo(aimingProgress);
      float zoom = iGun.getAimingZoom(stack);
      return (float)MathUtil.magnificationToFov(1.0F + (zoom - 1.0F) * fovProgress, configuredFov);
   }

   private static double calculateDrop(double distance, double lookY, float speedPerTick, float gravity, float friction) {
      double traveled = 0.0;
      double vertical = 0.0;
      double speed = speedPerTick;
      double verticalVelocity = lookY * speedPerTick;
      int maxTicks = 600;

      for (int i = 0; i < maxTicks && traveled < distance && speed > 1.0E-4; i++) {
         double step = Math.min(speed, distance - traveled);
         double ratio = step / speed;
         vertical += verticalVelocity * ratio;
         traveled += step;
         speed *= 1.0F - friction;
         verticalVelocity = verticalVelocity * (1.0F - friction) - gravity;
      }

      return vertical - lookY * distance;
   }

   private record EntityHit(Vec3 hitPos) {
   }
}
