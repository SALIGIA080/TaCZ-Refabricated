package com.tacz.guns.client.renderer.item;

import com.github.mcmodderanchor.simplebedrockmodel.v2.common.model.baked.BakedBoneDefinition;
import com.github.mcmodderanchor.simplebedrockmodel.v2.common.model.runtime.BoneDefinition;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.tacz.guns.client.model.DisplayGunModelInstance;
import com.tacz.guns.client.model.ExternalGunModel;
import org.joml.Vector3f;

public class SbmGunTransforms {
   private SbmGunTransforms() {
   }

   public static void applyFixed(PoseStack pose, ExternalGunModel external, Vector3f configuredScale) {
      apply(pose, external, "fixed", configuredScale);
   }

   public static void applyGround(PoseStack pose, ExternalGunModel external, Vector3f configuredScale) {
      apply(pose, external, "ground", configuredScale);
   }

   public static void applyThirdPerson(PoseStack pose, ExternalGunModel external, Vector3f configuredScale) {
      apply(pose, external, "thirdperson_hand", configuredScale);
   }

   public static void apply(PoseStack pose, ExternalGunModel external, String anchor, Vector3f configuredScale) {
      Vector3f scale = configuredScale == null ? new Vector3f(1.0F, 1.0F, 1.0F) : configuredScale;
      bedrockToSbm(pose);
      pose.translate(0.0, 1.5, 0.0);
      BakedBoneDefinition[] bones = external.model().bones();
      int index = external.model().getIndex(anchor);

      while (index >= 0) {
         BakedBoneDefinition bone = bones[index];
         Vector3f rotation = bone.bindEulerRotation();
         pose.mulPose(Axis.XP.rotation(rotation.x()));
         pose.mulPose(Axis.YP.rotation(rotation.y()));
         pose.mulPose(Axis.ZN.rotation(rotation.z()));
         if (bone.parentIndex() >= 0) {
            pose.translate(bone.bindX() * scale.x() / 16.0F, bone.bindY() * scale.y() / 16.0F, -bone.bindZ() * scale.z() / 16.0F);
         } else {
            pose.translate(bone.pivotX() * scale.x(), bone.pivotY() * scale.y(), -bone.pivotZ() * scale.z());
         }

         index = bone.parentIndex();
      }

      if (configuredScale != null) {
         pose.scale(scale.x(), scale.y(), scale.z());
      }

      pose.translate(0.0, -1.5, 0.0);
      bedrockToSbm(pose);
   }

   public static void bedrockToSbm(PoseStack pose) {
      pose.translate(0.0, 1.5, 0.0);
      pose.scale(-1.0F, -1.0F, 1.0F);
   }

   public static void applyBonePivot(PoseStack pose, DisplayGunModelInstance instance, int index) {
      instance.mulGlobalTransform(pose, index);
      BoneDefinition bone = instance.getBone(index).definition();
      pose.translate(bone.pivotX(), bone.pivotY(), bone.pivotZ());
   }
}