package com.tacz.guns.client.renderer.external;

import com.mojang.blaze3d.vertex.PoseStack;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.client.model.DisplayGunModelInstance;
import com.tacz.guns.client.model.ExternalGunModel;
import com.tacz.guns.client.model.functional.BeamRenderer;
import com.tacz.guns.client.particle.ThirdPersonMuzzleParticleManager;
import com.tacz.guns.client.renderer.block.DisplayGunGeometry;
import com.tacz.guns.client.renderer.item.GunItemRendererWrapper;
import com.tacz.guns.client.renderer.item.SbmGunTransforms;
import com.tacz.guns.client.resource.GunDisplayInstance;
import com.tacz.guns.client.resource.index.ClientAttachmentIndex;
import com.tacz.guns.client.resource.index.ClientGunIndex;
import java.util.EnumMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

public final class ExternalGunEffects {
   private ExternalGunEffects() {
   }

   public static void renderThirdPerson(ExternalGunAppearance.Snapshot snapshot, PoseStack pose, SubmitNodeCollector buffers, int packedLight, int packedOverlay) {
      ItemStack stack = snapshot.stack();
      IGun gun = IGun.getIGunOrNull(stack);
      if (gun != null) {
         EnumMap<AttachmentType, ClientAttachmentIndex> attachments = DisplayGunGeometry.resolveAttachments(gun, stack);
         if (attachments != null) {
            DisplayGunModelInstance instance = snapshot.model().displayInstance();
            instance.configureGun(gun, stack, attachments);
            pose.pushPose();
            DisplayGunGeometry.applyTransform(snapshot, ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, true, pose);
            renderLasers(snapshot.model(), stack, pose, buffers);
            DisplayGunGeometry.forEachAttachmentModel(
               instance,
               pose,
               snapshot.useLod(),
               gun,
               stack,
               attachments,
               (model, texture, attached, modelPose) -> renderLasers(model, attached, modelPose, buffers)
            );
            renderShells(snapshot, gun, pose, buffers, packedLight, packedOverlay);
            int muzzle = instance.muzzleFlashPosition();
            if (muzzle >= 0) {
               SbmGunTransforms.applyBonePivot(pose, instance, muzzle);
               pose.scale(-1.0F, -1.0F, 1.0F);
               GunItemRendererWrapper.captureThirdPersonMuzzle(pose.last().pose());
            }

            pose.popPose();
         }
      }
   }

   private static void renderShells(
      ExternalGunAppearance.Snapshot snapshot, IGun gun, PoseStack pose, SubmitNodeCollector buffers, int packedLight, int packedOverlay
   ) {
      LocalPlayer player = Minecraft.getInstance().player;
      if (player != null && ThirdPersonMuzzleParticleManager.currentEntity == player) {
         GunDisplayInstance display = snapshot.display();
         ClientGunIndex gunIndex = TimelessAPI.getClientGunIndex(gun.getGunId(snapshot.stack())).orElse(null);
         if (gunIndex != null) {
            display.getExternalShellRenderer()
               .render(display, gunIndex.getGunData(), snapshot.model().displayInstance(), pose, buffers, packedLight, packedOverlay);
         }
      }
   }

   private static void renderLasers(ExternalGunModel model, ItemStack stack, PoseStack pose, SubmitNodeCollector buffers) {
      DisplayGunModelInstance instance = model.displayInstance();

      for (int laser : instance.laserBeamBones()) {
         pose.pushPose();
         SbmGunTransforms.applyBonePivot(pose, instance, laser);
         pose.scale(-1.0F, -1.0F, 1.0F);
         BeamRenderer.renderLaserBeamUnaccelerated(stack, pose, ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, buffers);
         pose.popPose();
      }
   }
}