package com.tacz.guns.client.particle;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.client.resource.GunDisplayInstance;
import com.tacz.guns.client.resource.pojo.display.ammo.HitSurfaceParticle;
import com.tacz.guns.config.client.RenderConfig;
import com.tacz.guns.util.ColorHex;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.commands.arguments.ParticleArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustColorTransitionOptions;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.apache.commons.lang3.StringUtils;
import org.joml.Vector3f;

public final class HitSurfaceParticleSpawner {
   private static final int MAX_BLOCK_PARTICLE_COUNT = 32;
   private static final int MAX_DISPLAY_PARTICLE_COUNT_PER_HIT = 64;
   private static final float DEFAULT_SURFACE_OFFSET = 0.03F;
   private static final float DEFAULT_RANDOM_OFFSET = 0.01F;
   private static final int DEFAULT_BLOCK_PARTICLE_COUNT = 3;
   private static final float DEFAULT_BLOCK_NORMAL_SPEED = 0.14F;
   private static final float DEFAULT_BLOCK_RANDOM_SPEED = 0.015F;
   private static final float DEFAULT_BLOCK_SPREAD_ANGLE = 30.0F;
   private static final String DUST = "minecraft:dust";
   private static final String DUST_COLOR_TRANSITION = "minecraft:dust_color_transition";
   private static final String BLOCK = "minecraft:block";
   private static final String FALLING_DUST = "minecraft:falling_dust";
   private static final String ITEM = "minecraft:item";

   private HitSurfaceParticleSpawner() {
   }

   public static void spawn(
      ClientLevel level, Vec3 hitPos, Direction direction, BlockPos blockPos, String ammoId, String gunId, String gunDisplayId, boolean incendiary
   ) {
      if ((Boolean)RenderConfig.HIT_PARTICLE_ENABLE.get() && isCloseEnough(hitPos)) {
         BlockState blockState = level.getBlockState(blockPos);
         ParticleEngine particleEngine = Minecraft.getInstance().particleEngine;
         RandomSource random = level.getRandom();
         Integer blockParticleCount = getAmmoBlockParticleCount(ammoId);
         spawnBlockParticles(particleEngine, random, blockState, hitPos, direction, blockParticleCount, incendiary);
         if ((Boolean)RenderConfig.HIT_EXTRA_PARTICLE_ENABLE.get()) {
            List<HitSurfaceParticle> particles = getDisplayParticles(ammoId, gunId, gunDisplayId);
            if (particles != null && !particles.isEmpty()) {
               int remainingCount = 64;

               for (HitSurfaceParticle particle : particles) {
                  remainingCount -= spawnDisplayParticle(particleEngine, random, blockState, hitPos, direction, particle, remainingCount);
                  if (remainingCount <= 0) {
                     return;
                  }
               }
            }
         }
      }
   }

   private static boolean isCloseEnough(Vec3 hitPos) {
      int maxDistance = (Integer)RenderConfig.HIT_PARTICLE_MAX_DISTANCE.get();
      if (maxDistance <= 0) {
         return true;
      } else {
         Player player = Minecraft.getInstance().player;
         double maxDistanceSqr = (double)maxDistance * maxDistance;
         return player != null && player.distanceToSqr(hitPos) <= maxDistanceSqr;
      }
   }

   private static void spawnBlockParticles(
      ParticleEngine particleEngine,
      RandomSource random,
      BlockState blockState,
      Vec3 hitPos,
      Direction direction,
      @Nullable Integer configuredCount,
      boolean incendiary
   ) {
      int count = configuredCount == null ? 3 : Math.min(configuredCount, 32);
      if (count > 0) {
         ParticleOptions options = (ParticleOptions)(incendiary ? ParticleTypes.LAVA : new BlockParticleOption(ParticleTypes.BLOCK, blockState));

         for (int i = 0; i < count; i++) {
            Vec3 pos = randomPosition(random, hitPos, direction, 0.03F, 0.01F);
            Vec3 speed = randomVelocity(random, direction, 0.14F, 0.015F, 30.0F);
            particleEngine.createParticle(options, pos.x, pos.y, pos.z, speed.x, speed.y, speed.z);
         }
      }
   }

   @Nullable
   private static Integer getAmmoBlockParticleCount(String ammoId) {
      try {
         return TimelessAPI.getClientAmmoIndex(Identifier.parse(ammoId)).map(ammoIndex -> ammoIndex.getHitBlockParticleCount()).orElse(null);
      } catch (Exception var2) {
         return null;
      }
   }

   @Nullable
   private static List<HitSurfaceParticle> getDisplayParticles(String ammoId, String gunId, String gunDisplayId) {
      try {
         Identifier gunDisplayLocation = Identifier.parse(gunDisplayId);
         Identifier gunLocation = Identifier.parse(gunId);
         Optional<GunDisplayInstance> gunDisplay = TimelessAPI.getGunDisplay(gunDisplayLocation, gunLocation);
         return gunDisplay.isPresent() && gunDisplay.get().getHitSurfaceParticles() != null
            ? gunDisplay.get().getHitSurfaceParticles()
            : TimelessAPI.getClientAmmoIndex(Identifier.parse(ammoId)).map(ammoIndex -> ammoIndex.getHitSurfaceParticles()).orElse(null);
      } catch (Exception var6) {
         return null;
      }
   }

   private static int spawnDisplayParticle(
      ParticleEngine particleEngine, RandomSource random, BlockState blockState, Vec3 hitPos, Direction direction, HitSurfaceParticle particle, int maxCount
   ) {
      ParticleOptions options = parseParticle(blockState, particle);
      if (options != null && maxCount > 0) {
         int count = Math.min(randomCount(random, particle), maxCount);
         int spawned = 0;

         for (int i = 0; i < count; i++) {
            Vec3 pos = randomPosition(random, hitPos, direction, particle.getSurfaceOffset(), particle.getRandomOffset());
            Vec3 speed = randomVelocity(random, direction, particle.getNormalSpeed(), particle.getRandomSpeed(), particle.getSpreadAngle());
            Particle result = particleEngine.createParticle(options, pos.x, pos.y, pos.z, speed.x, speed.y, speed.z);
            if (result != null) {
               applyParticleOverrides(result, particle);
               spawned++;
            }
         }

         return spawned;
      } else {
         return 0;
      }
   }

   private static int randomCount(RandomSource random, HitSurfaceParticle particle) {
      int min = particle.getCountMin();
      int max = particle.getCountMax();
      if (max < min) {
         int swap = min;
         min = max;
         max = swap;
      }

      return max == min ? min : min + random.nextInt(max - min + 1);
   }

   @Nullable
   private static ParticleOptions parseParticle(BlockState blockState, HitSurfaceParticle particle) {
      String name = particle.getName();
      if (StringUtils.isBlank(name)) {
         return null;
      } else {
         String trimmedName = name.trim();
         boolean hasArguments = trimmedName.split("\\s+", 2).length > 1;
         if (hasArguments) {
            try {
               return ParticleArgument.readParticle(new StringReader(trimmedName), net.minecraft.core.RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
            } catch (CommandSyntaxException var9) {
               return null;
            }
         } else {
            String particleId = normalizeParticleId(trimmedName);
            if ("minecraft:block".equals(particleId)) {
               return new BlockParticleOption(ParticleTypes.BLOCK, blockState);
            } else if ("minecraft:falling_dust".equals(particleId)) {
               return new BlockParticleOption(ParticleTypes.FALLING_DUST, blockState);
            } else if ("minecraft:dust".equals(particleId)) {
               float[] color = colorOrDefault(particle.getColor(), 1.0F, 0.35F, 0.1F);
               return new DustParticleOptions(net.minecraft.util.ARGB.colorFromFloat(1.0F, color[0], color[1], color[2]), positiveScale(particle));
            } else if ("minecraft:dust_color_transition".equals(particleId)) {
               float[] color = colorOrDefault(particle.getColor(), 1.0F, 0.35F, 0.1F);
               int fromColor = net.minecraft.util.ARGB.colorFromFloat(1.0F, color[0], color[1], color[2]);
               int toColor = net.minecraft.util.ARGB.colorFromFloat(1.0F, color[0] * 0.25F, color[1] * 0.25F, color[2] * 0.25F);
               return new DustColorTransitionOptions(fromColor, toColor, positiveScale(particle));
            } else if ("minecraft:item".equals(particleId)) {
               return null;
            } else {
               try {
                  return ParticleArgument.readParticle(new StringReader(trimmedName), net.minecraft.core.RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
               } catch (CommandSyntaxException var10) {
                  return null;
               }
            }
         }
      }
   }

   private static String normalizeParticleId(String particleName) {
      String firstToken = particleName.split("\\s+", 2)[0];
      Identifier id = Identifier.tryParse(firstToken);
      return id == null ? firstToken : id.toString();
   }

   private static float positiveScale(HitSurfaceParticle particle) {
      Float scale = particle.getScale();
      return scale == null ? 1.0F : Math.max(scale, 0.0F);
   }

   private static float[] colorOrDefault(@Nullable String color, float red, float green, float blue) {
      return StringUtils.isBlank(color) ? new float[]{red, green, blue} : ColorHex.colorTextToRbgFloatArray(color);
   }

   private static void applyParticleOverrides(Particle result, HitSurfaceParticle particle) {
      result.setLifetime(particle.getLifeTime());
      // 26.2 的 Particle 移除了 setColor：染色已并入 ParticleOptions，
      // 自定义颜色请在粒子参数（dust 等）里指定，此处不再事后覆盖。

      Float scale = particle.getScale();
      if (scale != null && scale > 0.0F && !usesScaleInParticleOptions(particle)) {
         result.scale(scale);
      }
   }

   private static boolean usesScaleInParticleOptions(HitSurfaceParticle particle) {
      String name = particle.getName();
      if (StringUtils.isBlank(name)) {
         return false;
      } else {
         String particleId = normalizeParticleId(name.trim());
         return "minecraft:dust".equals(particleId) || "minecraft:dust_color_transition".equals(particleId);
      }
   }

   private static Vec3 randomPosition(RandomSource random, Vec3 hitPos, Direction direction, float surfaceOffset, float randomOffset) {
      Vec3 normal = Vec3.atLowerCornerOf(direction.getUnitVec3i());
      double offsetX = randomOffset == 0.0F ? 0.0 : random.nextGaussian() * randomOffset;
      double offsetY = randomOffset == 0.0F ? 0.0 : random.nextGaussian() * randomOffset;
      double offsetZ = randomOffset == 0.0F ? 0.0 : random.nextGaussian() * randomOffset;
      return hitPos.add(normal.scale(surfaceOffset)).add(offsetX, offsetY, offsetZ);
   }

   private static Vec3 randomVelocity(RandomSource random, Direction direction, float normalSpeed, float randomSpeed, float spreadAngle) {
      Vec3 normal = Vec3.atLowerCornerOf(direction.getUnitVec3i()).normalize();
      Vec3 tangent = Math.abs(normal.y) > 0.9 ? new Vec3(1.0, 0.0, 0.0) : normal.cross(new Vec3(0.0, 1.0, 0.0)).normalize();
      Vec3 bitangent = normal.cross(tangent).normalize();
      double spread = Math.tan(Math.toRadians(spreadAngle));
      Vec3 cone = normal.add(tangent.scale(random.nextGaussian() * spread))
         .add(bitangent.scale(random.nextGaussian() * spread))
         .normalize()
         .scale(normalSpeed);
      Vec3 randomVector = new Vec3(random.nextGaussian(), random.nextGaussian(), random.nextGaussian()).scale(randomSpeed);
      return cone.add(randomVector);
   }
}
