package com.tacz.guns.client.renderer.block;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.core.Direction;

public class DisplayItemTransforms {
   private DisplayItemTransforms() {
   }

   public static void apply(PoseStack pose, Direction facing, double x, double y, double offset, int rotation) {
      pose.translate(0.5, 0.5, 0.5);
      pose.mulPose(Axis.YP.rotationDegrees(180.0F - facing.get2DDataValue() * 90.0F));
      pose.translate(x, y, offset);
      pose.mulPose(Axis.ZP.rotationDegrees(rotation * 45.0F));
      pose.scale(0.5F, 0.5F, 0.5F);
   }
}
