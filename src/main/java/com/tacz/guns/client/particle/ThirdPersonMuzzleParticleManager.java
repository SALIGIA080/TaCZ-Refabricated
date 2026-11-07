package com.tacz.guns.client.particle;

import com.github.mcmodderanchor.simplebedrockmodel.v1.particle.data.ParticleEffectDefinition;
import com.github.mcmodderanchor.simplebedrockmodel.v1.particle.resource.ParticleDefinitionLoader;
import com.github.mcmodderanchor.simplebedrockmodel.v1.particle.runtime.ParticleEmitterInstance;
import com.github.mcmodderanchor.simplebedrockmodel.v1.particle.world.WorldEmitterManager;
import com.tacz.guns.config.client.RenderConfig;
import com.tacz.guns.util.time.GunTime;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

public final class ThirdPersonMuzzleParticleManager {
   private static final long FLASH_WINDOW_MS = 150L;
   private static final Map<LivingEntity, MuzzleParticleManager.MuzzleShot> PENDING = new WeakHashMap<>();
   private static final Map<LivingEntity, MuzzleParticleManager.MuzzleShot> FLASH_PARAMS = new WeakHashMap<>();
   private static final Map<LivingEntity, ParticleEmitterInstance> FLASH_EMITTERS = new WeakHashMap<>();
   private static final Map<LivingEntity, Long> FLASH_START = new WeakHashMap<>();
   @Nullable
   public static LivingEntity currentEntity = null;

   private ThirdPersonMuzzleParticleManager() {
   }

   public static void resetTimestamps() {
      PENDING.clear();
      FLASH_PARAMS.clear();
      FLASH_EMITTERS.clear();
      FLASH_START.clear();
      currentEntity = null;
   }

   public static void onShoot(LivingEntity shooter, ItemStack gunItem) {
      LocalPlayer player = Minecraft.getInstance().player;
      if (!shooter.equals(player) || !Minecraft.getInstance().options.getCameraType().isFirstPerson()) {
         MuzzleParticleManager.MuzzleShot shot = MuzzleParticleManager.resolveShot(gunItem);
         if (shot != null) {
            PENDING.put(shooter, shot);
            FLASH_PARAMS.put(shooter, shot);
            FLASH_START.put(shooter, GunTime.nowMillis());
         }
      }
   }

   public static void onMuzzleRendered(LivingEntity entity, Matrix4f muzzleWorld) {
      ClientLevel level = Minecraft.getInstance().level;
      if (level != null) {
         long now = GunTime.nowMillis();
         Long flashStart = FLASH_START.get(entity);
         if (flashStart != null) {
            if (now >= flashStart && now - flashStart < 150L) {
               ParticleEmitterInstance flashEmitter = FLASH_EMITTERS.get(entity);
               if (flashEmitter == null) {
                  MuzzleParticleManager.MuzzleShot shot = FLASH_PARAMS.get(entity);
                  if (shot != null && shot.thirdPersonFlashId() != null && shot.flashScale() > 0.0F) {
                     ParticleEffectDefinition def = ParticleDefinitionLoader.getInstance().getDefinition(shot.thirdPersonFlashId());
                     if (def != null) {
                        Vec3 spawnPos = new Vec3(muzzleWorld.m30(), muzzleWorld.m31(), muzzleWorld.m32());
                        flashEmitter = WorldEmitterManager.getInstance().addEmitter(level, spawnPos, Vec3.ZERO, def);
                        if (flashEmitter != null) {
                           flashEmitter.setVariable("muzzle_scale", shot.flashScale());
                           flashEmitter.setVariable("muzzle_speed", shot.speedScale());
                           flashEmitter.setFollowAnchor(true);
                           FLASH_EMITTERS.put(entity, flashEmitter);
                           WorldEmitterManager.getInstance().updateEmitterAnchor(flashEmitter, muzzleWorld);
                        }
                     }
                  }
               } else if (!WorldEmitterManager.getInstance().updateEmitterAnchor(flashEmitter, muzzleWorld)) {
                  FLASH_EMITTERS.remove(entity);
               }
            } else {
               FLASH_START.remove(entity);
               FLASH_PARAMS.remove(entity);
               FLASH_EMITTERS.remove(entity);
            }
         }

         MuzzleParticleManager.MuzzleShot shot = PENDING.remove(entity);
         if (shot != null && (Boolean)RenderConfig.RENDER_MUZZLE_SMOKE.get() && shot.thirdPersonSmokeId() != null && shot.smokeScale() > 0.0F) {
            spawnEffect(
               level,
               muzzleWorld,
               shot.thirdPersonSmokeId(),
               shot.smokeScale(),
               shot.speedScale(),
               shot.smokeParticleCount(),
               shot.smokeSize(),
               shot.smokeDirection()
            );
         }
      }
   }

   private static void spawnEffect(
      ClientLevel level,
      Matrix4f muzzleWorld,
      Identifier definitionId,
      float scale,
      float speedScale,
      float smokeParticleCount,
      float smokeSize,
      @Nullable MuzzleParticleManager.SmokeDirection smokeDirection
   ) {
      ParticleEffectDefinition definition = ParticleDefinitionLoader.getInstance().getDefinition(definitionId);
      if (definition != null) {
         ParticleEmitterInstance emitter = WorldEmitterManager.getInstance()
            .addEmitter(level, new Vec3(muzzleWorld.m30(), muzzleWorld.m31(), muzzleWorld.m32()), Vec3.ZERO, definition);
         if (emitter != null) {
            WorldEmitterManager.getInstance().updateEmitterAnchor(emitter, muzzleWorld);
            emitter.setVariable("smoke_size", smokeSize);
            emitter.setVariable("smoke_vel", speedScale);
            emitter.setVariable("smoke_count", smokeParticleCount);
            MuzzleParticleManager.writeSmokeDirectionVariables(emitter, smokeDirection);
         }
      }
   }
}