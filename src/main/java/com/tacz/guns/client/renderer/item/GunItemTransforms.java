package com.tacz.guns.client.renderer.item;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.tacz.guns.client.model.BedrockGunModel;
import com.tacz.guns.client.model.bedrock.BedrockPart;
import com.tacz.guns.client.resource.pojo.TransformScale;
import java.util.List;
import net.minecraft.world.item.ItemDisplayContext;
import org.joml.Vector3f;

public final class GunItemTransforms {
   private GunItemTransforms() {
   }

   public static void apply(ItemDisplayContext context, TransformScale scale, BedrockGunModel model, PoseStack pose) {
      pose.translate(0.5, 2.0, 0.5);
      pose.scale(-1.0F, -1.0F, 1.0F);
      switch (context) {
         case FIXED:
            applyPositioningNode(model.getFixedOriginPath(), pose, scale.getFixed());
            break;
         case GROUND:
            applyPositioningNode(model.getGroundOriginPath(), pose, scale.getGround());
            break;
         case THIRD_PERSON_RIGHT_HAND:
         case THIRD_PERSON_LEFT_HAND:
            applyPositioningNode(model.getThirdPersonHandOriginPath(), pose, scale.getThirdPerson());
      }

      applyScale(context, scale, pose);
   }

   private static void applyScale(ItemDisplayContext context, TransformScale scale, PoseStack pose) {
      if (scale != null) {
         Vector3f value = switch (context) {
            case FIXED -> scale.getFixed();
            case GROUND -> scale.getGround();
            case THIRD_PERSON_RIGHT_HAND, THIRD_PERSON_LEFT_HAND -> scale.getThirdPerson();
            default -> null;
         };
         if (value != null) {
            pose.translate(0.0, 1.5, 0.0);
            pose.scale(value.x(), value.y(), value.z());
            pose.translate(0.0, -1.5, 0.0);
         }
      }
   }

   private static void applyPositioningNode(List<BedrockPart> path, PoseStack pose, Vector3f scale) {
      if (path != null) {
         if (scale == null) {
            scale = new Vector3f(1.0F, 1.0F, 1.0F);
         }

         pose.translate(0.0, 1.5, 0.0);

         for (int i = path.size() - 1; i >= 0; i--) {
            BedrockPart part = path.get(i);
            pose.mulPose(Axis.XN.rotation(part.xRot));
            pose.mulPose(Axis.YN.rotation(part.yRot));
            pose.mulPose(Axis.ZN.rotation(part.zRot));
            if (part.getParent() != null) {
               pose.translate(-part.x * scale.x() / 16.0F, -part.y * scale.y() / 16.0F, -part.z * scale.z() / 16.0F);
            } else {
               pose.translate(-part.x * scale.x() / 16.0F, (1.5F - part.y / 16.0F) * scale.y(), -part.z * scale.z() / 16.0F);
            }
         }

         pose.translate(0.0, -1.5, 0.0);
      }
   }
}
