package com.tacz.guns.client.particle;

import com.github.mcmodderanchor.simplebedrockmodel.v1.common.resource.pojo.ParticleEffectData;
import com.github.mcmodderanchor.simplebedrockmodel.v1.particle.data.ParticleEffectDefinition;
import com.github.mcmodderanchor.simplebedrockmodel.v1.particle.firstperson.FirstPersonParticleSystem;
import com.github.mcmodderanchor.simplebedrockmodel.v1.particle.render.CameraStateCache;
import com.github.mcmodderanchor.simplebedrockmodel.v1.particle.resource.ParticleDefinitionLoader;
import com.github.mcmodderanchor.simplebedrockmodel.v1.particle.runtime.ParticleEmitterInstance;
import com.mojang.blaze3d.vertex.PoseStack;
import com.tacz.guns.GunMod;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IAttachment;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.api.modifier.JsonProperty;
import com.tacz.guns.client.model.BedrockGunModel;
import com.tacz.guns.client.model.bedrock.BedrockPart;
import com.tacz.guns.client.resource.GunDisplayInstance;
import com.tacz.guns.client.resource.index.ClientAttachmentIndex;
import com.tacz.guns.client.resource.pojo.display.gun.MuzzleFlash;
import com.tacz.guns.config.client.RenderConfig;
import com.tacz.guns.resource.index.CommonAttachmentIndex;
import com.tacz.guns.resource.modifier.custom.SilenceModifier;
import com.tacz.guns.resource.pojo.data.SmokeDirectionData;
import com.tacz.guns.resource.pojo.data.attachment.AttachmentData;
import com.tacz.guns.util.time.GunTime;
import it.unimi.dsi.fastutil.Pair;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;

import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

public final class MuzzleParticleManager {
   private static final long FLASH_LIGHT_TIME_MS = 50L;
   private static final EnumMap<InteractionHand, List<MuzzleParticleManager.PendingShoot>> PENDING = new EnumMap<>(InteractionHand.class);
   private static final EnumMap<InteractionHand, List<MuzzleParticleManager.BoundEmitter>> BOUND = new EnumMap<>(InteractionHand.class);
   private static final EnumMap<InteractionHand, Matrix4f> BASE_POSE = new EnumMap<>(InteractionHand.class);
   private static GunDisplayInstance lightDisplay;
   private static long lightShootTime = -1L;
   private static int flashLightLevel;
   private static final float BUILTIN_SMOKE_PARTICLE_COUNT = 15.0F;
   private static final float BUILTIN_SMOKE_SIZE = 1.0F;

   private MuzzleParticleManager() {
   }

   @Nullable
   public static MuzzleParticleManager.MuzzleShot resolveShot(ItemStack gunItem) {
      GunDisplayInstance display = TimelessAPI.getGunDisplay(gunItem).orElse(null);
      if (display == null) {
         return null;
      } else {
         MuzzleFlash muzzleFlash = display.getMuzzleFlash();
         if (muzzleFlash != null && muzzleFlash.isRenew()) {
            MuzzleParticleManager.MuzzleAttachmentEffect effect = resolveAttachmentEffect(gunItem);
            float gunScale = muzzleFlash.getScale();
            float flashScale = gunScale * effect.flashScale();
            if (effect.silenced() && !effect.hasFlashHider()) {
               flashScale = 0.0F;
            }

            int flashLightLevel = Math.max(0, Math.min(15, (int)Math.ceil(muzzleFlash.getFlashLightLevel() * effect.flashScale())));
            float smokeSize = applyAttachmentScale(muzzleFlash.getSmokeScale(), 1.0F, effect.smokeScale());
            return new MuzzleParticleManager.MuzzleShot(
               muzzleFlash.getFirstPersonFlashId(),
               flashScale,
               flashLightLevel,
               muzzleFlash.getFirstPersonSmokeId(),
               gunScale * effect.smokeScale(),
               effect.offsetBb(),
               muzzleFlash.getThirdPersonFlashId(),
               muzzleFlash.getThirdPersonSmokeId(),
               muzzleFlash.getInitialSpeedScale() * effect.smokeSpeedScale(),
               applyAttachmentScale(muzzleFlash.getSmokeParticleCount(), 15.0F, effect.smokeCountScale()),
               smokeSize,
               resolveSmokeDirection(muzzleFlash, effect)
            );
         } else {
            return null;
         }
      }
   }

   @Nullable
   private static MuzzleParticleManager.SmokeDirection resolveSmokeDirection(MuzzleFlash muzzleFlash, MuzzleParticleManager.MuzzleAttachmentEffect effect) {
      SmokeDirectionData.Config config = effect.smokeDirectionConfig() != null ? effect.smokeDirectionConfig() : muzzleFlash.getSmokeDirectionConfig();
      if (config == null) {
         return null;
      } else {
         float[][] raw = config.directions();
         float[][] fixed = new float[raw.length][];

         for (int i = 0; i < raw.length; i++) {
            fixed[i] = new float[]{-raw[i][0], -raw[i][1], raw[i][2]};
         }

         return new MuzzleParticleManager.SmokeDirection(fixed, config.spread(), config.angleMode());
      }
   }

   private static float applyAttachmentScale(float gunValue, float builtinDefault, float scale) {
      return scale == 1.0F ? gunValue : (gunValue > 0.0F ? gunValue : builtinDefault) * scale;
   }

   public static void onShoot(ItemStack gunItem) {
      if (Minecraft.getInstance().options.getCameraType().isFirstPerson()) {
         enqueueShoot(gunItem);
      }
   }

   public static void enqueueShoot(ItemStack gunItem) {
      MuzzleParticleManager.MuzzleShot shot = resolveShot(gunItem);
      if (shot == null) {
         resetMuzzleLight();
      } else {
         lightDisplay = TimelessAPI.getGunDisplay(gunItem).orElse(null);
         lightShootTime = GunTime.nowMillis();
         flashLightLevel = shot.flashId() != null && shot.flashScale() > 0.0F ? shot.flashLightLevel() : 0;
         PENDING.computeIfAbsent(InteractionHand.MAIN_HAND, h -> new ArrayList())
            .add(
               new MuzzleParticleManager.PendingShoot(
                  shot.flashId(),
                  shot.flashScale(),
                  shot.smokeId(),
                  shot.smokeScale(),
                  shot.offsetBb(),
                  shot.speedScale(),
                  shot.smokeParticleCount(),
                  shot.smokeSize(),
                  shot.smokeDirection()
               )
            );
      }
   }

   public static int getFirstPersonModelLight(ItemStack gunItem, int environmentLight) {
      if (lightDisplay != null
         && flashLightLevel > 0
         && GunTime.isWithin(lightShootTime, 50L)
         && TimelessAPI.getGunDisplay(gunItem).orElse(null) == lightDisplay) {
         // 26.2：LightTexture 已移除，block/sky/pack 按经典打包公式内联。
         int environmentBlockLight = (environmentLight >> 4) & 0xF;
         int environmentSkyLight = (environmentLight >> 20) & 0xF;
         if (environmentBlockLight >= 12) {
            return environmentLight;
         } else {
            int flashLightLimit = Math.min(12, environmentBlockLight + 10);
            int blockLight = Math.max(environmentBlockLight, Math.min(flashLightLevel, flashLightLimit));
            return (environmentSkyLight << 20) | (blockLight << 4);
         }
      } else {
         return environmentLight;
      }
   }

   public static void resetMuzzleLight() {
      lightDisplay = null;
      lightShootTime = -1L;
      flashLightLevel = 0;
   }

   public static void captureBasePose(InteractionHand hand, Matrix4f pose) {
      BASE_POSE.put(hand, new Matrix4f(pose));
   }

   @Nullable
   public static Matrix4f getBasePose(InteractionHand hand) {
      Matrix4f pose = BASE_POSE.get(hand);
      return pose != null ? new Matrix4f(pose) : null;
   }

   public static void spawnAndBind(
      FirstPersonParticleSystem system, PoseStack poseStack, BedrockGunModel model, InteractionHand hand, @Nullable List<ParticleEffectData> animationParticles
   ) {
      Matrix4f basePose = BASE_POSE.get(hand);
      if (basePose != null) {
         if (model.getMuzzleFlashPosPath() == null) {
            PENDING.remove(hand);
         } else {
            Matrix4f basePoseInv = new Matrix4f(basePose).invert();
            List<MuzzleParticleManager.BoundEmitter> bound = BOUND.computeIfAbsent(hand, h -> new ArrayList());
            List<ParticleEmitterInstance> newEmitters = new ArrayList<>();
            List<MuzzleParticleManager.PendingShoot> pending = PENDING.remove(hand);
            if (pending != null) {
               for (MuzzleParticleManager.PendingShoot req : pending) {
                  if (req.flashId() != null && req.flashScale() > 0.0F) {
                     createFlashEmitter(system, hand, bound, newEmitters, req.flashId(), req.flashScale(), "muzzle_flash", req.offsetBb(), req.speedScale());
                  }

                  if ((Boolean)RenderConfig.RENDER_MUZZLE_SMOKE.get() && req.smokeId() != null && req.smokeScale() > 0.0F) {
                     createSmokeEmitter(
                        system,
                        hand,
                        bound,
                        newEmitters,
                        req.smokeId(),
                        req.smokeScale(),
                        "muzzle_flash",
                        req.offsetBb(),
                        req.speedScale(),
                        req.smokeSize(),
                        req.smokeParticleCount(),
                        req.smokeDirection()
                     );
                  }
               }
            }

            if (animationParticles != null) {
               for (ParticleEffectData data : animationParticles) {
                  String locator = data.locator() != null && !data.locator().isEmpty() ? data.locator() : "muzzle_flash";
                  createFlashEmitter(system, hand, bound, newEmitters, data.effect(), 1.0F, locator, 0.0F, 1.0F);
               }
            }

            poseStack.pushPose();
            List<BedrockPart> muzzlePath = model.getMuzzleFlashPosPath();
            if (muzzlePath != null) {
               for (BedrockPart part : muzzlePath) {
                  part.translateAndRotateAndScale(poseStack);
               }
            }

            Matrix4f muzzleBone = new Matrix4f(poseStack.last().pose());
            poseStack.popPose();
            Camera camera = Minecraft.getInstance().gameRenderer.mainCamera();
            float cameraRoll = CameraStateCache.getCameraRollRadians();
            // 【烟雾位置偏移根因 · 2026-11-07】官方 1025 这里用手工欧拉
            // rotationX(xRot)·rotationY(yRot+180)·rotationZ(roll).invert() 把 muzzleView 从
            // 「视图空间」转到「世界空间」——因为官方 1.20/1.21 手部渲染的 poseStack 基座是
            // 单位阵（顶点在视图空间）。
            //
            // 但 26.2 的手部渲染架构变了：GameRenderer.renderItemInHand 里先
            // poseStack.mulPose(viewRotationMatrix.invert())（= R⁻¹，字节码已确认），
            // 故 muzzleBone = poseStack.last().pose() 已经落在「世界轴空间」
            // （= R⁻¹·视图空间，原点在相机、轴已对齐世界），无需再乘任何 R⁻¹。
            //
            // 此前两版都错：官方手工欧拉（26.2 欧拉顺序/roll 已变）和上一轮改的
            // getViewRotationMatrix().invert()（= R⁻¹）都会把已对齐世界的 muzzleView
            // 再反向转一次 → 双重旋转 → 烟雾「从枪口上方凭空出现、只在特定角度可见」。
            //
            // 正确：cameraRotationInv = 单位阵。muzzleView 已在世界轴空间，投递时
            // FirstPersonParticleSystem.deliverWorldSpaceParticle 会再加相机位置转回世界空间。
            Matrix4f cameraRotationInv = new Matrix4f();

            for (MuzzleParticleManager.BoundEmitter be : bound) {
               ParticleEmitterInstance emitter = be.emitter();
               if (!emitter.isFinished()) {
                  Matrix4f muzzleView = new Matrix4f(muzzleBone);
                  if (be.offsetBb() != 0.0F) {
                     muzzleView.translate(0.0F, 0.0F, -be.offsetBb() / 16.0F);
                  }

                  emitter.setWorldParticleScale(getWorldParticleScale());
                  emitter.setEmitterTransform(new Matrix4f(basePoseInv).mul(muzzleView), new Matrix4f(cameraRotationInv).mul(toWorldMuzzleView(muzzleView)));
               }
            }

            for (ParticleEmitterInstance emitter : newEmitters) {
               emitter.tick(0.0F);
            }

            bound.removeIf(bex -> bex.emitter().isFinished());
         }
      }
   }

   private static float getHandToWorldTanRatio() {
      return CameraStateCache.getFirstPersonToWorldDepthScale();
   }

   private static Matrix4f toWorldMuzzleView(Matrix4f muzzleView) {
      float fovRatio = getHandToWorldTanRatio();
      if (!(fovRatio <= 1.0E-4F) && !(Math.abs(fovRatio - 1.0F) <= 1.0E-4F)) {
         float lateral = 1.0F / fovRatio;
         return new Matrix4f().scaling(lateral, lateral, 1.0F).mul(muzzleView);
      } else {
         return new Matrix4f(muzzleView);
      }
   }

   private static float getWorldParticleScale() {
      float ratio = getHandToWorldTanRatio();
      return ratio > 1.0E-4F ? 1.0F / ratio : 1.0F;
   }

   private static void createFlashEmitter(
      FirstPersonParticleSystem system,
      InteractionHand hand,
      List<MuzzleParticleManager.BoundEmitter> bound,
      List<ParticleEmitterInstance> newEmitters,
      Identifier definitionId,
      float scale,
      String locator,
      float offsetBb,
      float speedScale
   ) {
      ParticleEffectDefinition definition = ParticleDefinitionLoader.getInstance().getDefinition(definitionId);
      if (definition == null) {
         GunMod.LOGGER.warn("Muzzle particle definition {} not found, skipped", definitionId);
      } else {
         ParticleEmitterInstance emitter = system.addEmitter(definition, hand);
         emitter.setVariable("muzzle_scale", scale);
         emitter.setVariable("muzzle_speed", speedScale);
         bound.add(new MuzzleParticleManager.BoundEmitter(emitter, locator, offsetBb));
         newEmitters.add(emitter);
      }
   }

   private static void createSmokeEmitter(
      FirstPersonParticleSystem system,
      InteractionHand hand,
      List<MuzzleParticleManager.BoundEmitter> bound,
      List<ParticleEmitterInstance> newEmitters,
      Identifier definitionId,
      float scale,
      String locator,
      float offsetBb,
      float speedScale,
      float smokeSize,
      float smokeParticleCount,
      @Nullable MuzzleParticleManager.SmokeDirection smokeDirection
   ) {
      ParticleEffectDefinition definition = ParticleDefinitionLoader.getInstance().getDefinition(definitionId);
      if (definition == null) {
         GunMod.LOGGER.warn("Muzzle particle definition {} not found, skipped", definitionId);
      } else {
         ParticleEmitterInstance emitter = system.addEmitter(definition, hand);
         emitter.setVariable("smoke_size", smokeSize);
         emitter.setVariable("smoke_vel", speedScale);
         emitter.setVariable("smoke_count", smokeParticleCount);
         writeSmokeDirectionVariables(emitter, smokeDirection);
         bound.add(new MuzzleParticleManager.BoundEmitter(emitter, locator, offsetBb));
         newEmitters.add(emitter);
      }
   }

   static void writeSmokeDirectionVariables(ParticleEmitterInstance emitter, @Nullable MuzzleParticleManager.SmokeDirection direction) {
      int count = direction == null ? 0 : direction.dirs().length;
      emitter.setVariable("smoke_dir_n", count);
      emitter.setVariable("smoke_spread", direction == null ? 0.0F : direction.spread());
      emitter.setVariable("smoke_angle_mode", direction != null && direction.angleMode() ? 1.0F : 0.0F);

      for (int i = 0; i < 4; i++) {
         float x = 0.0F;
         float y = 0.0F;
         float z = 0.0F;
         float ux = 0.0F;
         float uy = 0.0F;
         float uz = 0.0F;
         float vx = 0.0F;
         float vy = 0.0F;
         float vz = 0.0F;
         if (direction != null && i < count) {
            float[] dir = direction.dirs()[i];
            x = dir[0];
            y = dir[1];
            z = dir[2];
            float lateral = (float)Math.hypot(x, y);
            if (lateral > 1.0E-6F) {
               ux = -y / lateral;
               uy = x / lateral;
            } else {
               ux = 1.0F;
            }

            vx = y * uz - z * uy;
            vy = z * ux - x * uz;
            vz = x * uy - y * ux;
         }

         emitter.setVariable("smoke_dir" + i + "_x", x);
         emitter.setVariable("smoke_dir" + i + "_y", y);
         emitter.setVariable("smoke_dir" + i + "_z", z);
         emitter.setVariable("smoke_u" + i + "_x", ux);
         emitter.setVariable("smoke_u" + i + "_y", uy);
         emitter.setVariable("smoke_u" + i + "_z", uz);
         emitter.setVariable("smoke_v" + i + "_x", vx);
         emitter.setVariable("smoke_v" + i + "_y", vy);
         emitter.setVariable("smoke_v" + i + "_z", vz);
      }
   }

   private static MuzzleParticleManager.MuzzleAttachmentEffect resolveAttachmentEffect(ItemStack gunItem) {
      IGun gun = IGun.getIGunOrNull(gunItem);
      if (gun == null) {
         return MuzzleParticleManager.MuzzleAttachmentEffect.NONE;
      } else {
         ItemStack muzzleAttachment = gun.getAttachment(gunItem, AttachmentType.MUZZLE);
         if (muzzleAttachment.isEmpty()) {
            muzzleAttachment = gun.getBuiltinAttachment(gunItem, AttachmentType.MUZZLE);
         }

         IAttachment attachment = IAttachment.getIAttachmentOrNull(muzzleAttachment);
         if (attachment == null) {
            return MuzzleParticleManager.MuzzleAttachmentEffect.NONE;
         } else {
            Identifier attachmentId = attachment.getAttachmentId(muzzleAttachment);
            float offsetBb = TimelessAPI.getClientAttachmentIndex(attachmentId).map(ClientAttachmentIndex::getMuzzleFlashOffset).orElse(0.0F);
            AttachmentData data = TimelessAPI.getCommonGunIndex(gun.getGunId(gunItem))
               .map(index -> index.getGunData().getExclusiveAttachments().get(attachmentId))
               .orElse(null);
            if (data == null) {
               data = TimelessAPI.getCommonAttachmentIndex(attachmentId).map(CommonAttachmentIndex::getData).orElse(null);
            }

            boolean hasFlashHider = data != null && data.hasFlashHider();
            float flashScale = hasFlashHider ? data.getFlashHider() : 1.0F;
            float smokeScale = data != null ? data.getSmokeHider() : 1.0F;
            float smokeSpeedScale = data != null ? data.getSmokeSpeedScale() : 1.0F;
            float smokeCountScale = data != null ? data.getSmokeCountScale() : 1.0F;
            SmokeDirectionData.Config smokeDirectionConfig = data != null ? data.getSmokeDirectionConfig() : null;
            return new MuzzleParticleManager.MuzzleAttachmentEffect(
               offsetBb, flashScale, smokeScale, smokeSpeedScale, smokeCountScale, smokeDirectionConfig, isSilenced(data), hasFlashHider
            );
         }
      }
   }

   private static boolean isSilenced(@Nullable AttachmentData data) {
      if (data == null) {
         return false;
      } else {
         JsonProperty<?> silence = data.getModifier().get(SilenceModifier.ID);
         return silence != null && silence.getValue() instanceof Pair<?, ?> pair && pair.right() instanceof Boolean value && value;
      }
   }

   private record BoundEmitter(ParticleEmitterInstance emitter, String locator, float offsetBb) {
   }

   private record MuzzleAttachmentEffect(
      float offsetBb,
      float flashScale,
      float smokeScale,
      float smokeSpeedScale,
      float smokeCountScale,
      @Nullable SmokeDirectionData.Config smokeDirectionConfig,
      boolean silenced,
      boolean hasFlashHider
   ) {
      private static final MuzzleParticleManager.MuzzleAttachmentEffect NONE = new MuzzleParticleManager.MuzzleAttachmentEffect(
         0.0F, 1.0F, 1.0F, 1.0F, 1.0F, null, false, false
      );
   }

   public record MuzzleShot(
      @Nullable Identifier flashId,
      float flashScale,
      int flashLightLevel,
      @Nullable Identifier smokeId,
      float smokeScale,
      float offsetBb,
      @Nullable Identifier thirdPersonFlashId,
      @Nullable Identifier thirdPersonSmokeId,
      float speedScale,
      float smokeParticleCount,
      float smokeSize,
      @Nullable MuzzleParticleManager.SmokeDirection smokeDirection
   ) {
   }

   private record PendingShoot(
      @Nullable Identifier flashId,
      float flashScale,
      @Nullable Identifier smokeId,
      float smokeScale,
      float offsetBb,
      float speedScale,
      float smokeParticleCount,
      float smokeSize,
      @Nullable MuzzleParticleManager.SmokeDirection smokeDirection
   ) {
   }

   record SmokeDirection(float[][] dirs, float spread, boolean angleMode) {
   }
}