package com.tacz.guns.client.model.functional;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.tacz.guns.client.model.IFunctionalSubmitter;
import com.tacz.guns.client.model.bedrock.BedrockModel;
import com.tacz.guns.client.resource.pojo.display.gun.RangefinderManager;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.rendertype.RenderTypes;

/**
 * 测距仪弹道落点预测框。26.2 迁移说明：
 * <ul>
 *   <li>旧即时渲染链（delegateRender + MultiBufferSource）在 26.2 已废弃，
 *       改走 IFunctionalSubmitter.extract → SubmitNodeCollector.submitCustomGeometry。</li>
 *   <li>自定义 RenderType（RenderStateShard 组合）在 26.2 是包私有，无法自建；
 *       用 RenderTypes.entityTranslucentEmissive(激光贴图) 代替，与 BeamRenderer 同方案。</li>
 *   <li>投影矩阵逐像素校准依赖 RenderSystem.getProjectionMatrix()，26.2 已移除该 API，
 *       退化为 fallback（projectionY × 80），落点框位置随配置投影 Y 线性变化，
 *       不再有屏幕像素级精确对齐。</li>
 * </ul>
 */
@Environment(EnvType.CLIENT)
public class RangefinderImpactPredictionRender implements IFunctionalSubmitter {
   private static final float MODEL_TEXT_SCALE = 0.006666667F;
   private static final float FALLBACK_MODEL_PROJECTION_HALF_HEIGHT = 80.0F;
   // 与 1025 原版一致：HALF_SIZE=3、线宽 1。落点框应是一个贴准星中心的紧凑小框，
   // 过大反而失去弹道落点预测的精确意义。
   private static final float HALF_SIZE = 3.0F;
   private static final float LINE_WIDTH = 1.0F;
   private final BedrockModel bedrockModel;

   public RangefinderImpactPredictionRender(BedrockModel bedrockModel) {
      this.bedrockModel = bedrockModel;
   }

   @Override
   public void extract(ExtractionContext context) {
      if (!context.displayContext().firstPerson() || !RangefinderManager.shouldRenderImpactPrediction()) {
         return;
      }
      PoseStack frozenPose = context.poseStack();
      frozenPose.mulPose(Axis.ZP.rotationDegrees(180.0F));
      int color = RangefinderManager.getImpactPredictionColor();
      float scale = RangefinderManager.getImpactPredictionScale();
      float yOffset = RangefinderManager.getImpactProjectionY() * FALLBACK_MODEL_PROJECTION_HALF_HEIGHT;
      int light = context.light();
      int overlay = context.overlay();
      context.add(collector -> {
         PoseStack taskPose = new PoseStack();
         taskPose.last().pose().set(frozenPose.last().pose());
         taskPose.last().normal().set(frozenPose.last().normal());
         taskPose.scale(MODEL_TEXT_SCALE, -MODEL_TEXT_SCALE, -MODEL_TEXT_SCALE);
         collector.submitCustomGeometry(taskPose, RenderTypes.entityTranslucentEmissive(BeamRenderer.LASER_BEAM_TEXTURE),
               (pose, consumer) -> renderBox(consumer, pose, yOffset, color, scale, light, overlay));
      });
   }

   private static void renderBox(VertexConsumer consumer, PoseStack.Pose pose, float yOffset, int color, float scale, int light, int overlay) {
      int a = color >>> 24 & 0xFF;
      int r = color >>> 16 & 0xFF;
      int g = color >>> 8 & 0xFF;
      int b = color & 0xFF;
      float halfSize = HALF_SIZE * scale;
      float left = -halfSize;
      float top = yOffset - halfSize;
      float bottom = yOffset + halfSize;
      quad(consumer, pose, left, top, halfSize, top + LINE_WIDTH, r, g, b, a, light, overlay);
      quad(consumer, pose, left, bottom - LINE_WIDTH, halfSize, bottom, r, g, b, a, light, overlay);
      quad(consumer, pose, left, top, left + LINE_WIDTH, bottom, r, g, b, a, light, overlay);
      quad(consumer, pose, halfSize - LINE_WIDTH, top, halfSize, bottom, r, g, b, a, light, overlay);
   }

   private static void quad(VertexConsumer consumer, PoseStack.Pose pose, float minX, float minY, float maxX, float maxY, int r, int g, int b, int a, int light, int overlay) {
      consumer.addVertex(pose.pose(), minX, minY, 0.0F).setColor(r, g, b, a).setUv(0.0F, 0.0F).setOverlay(overlay).setLight(light).setNormal(pose, 0.0F, 0.0F, 1.0F);
      consumer.addVertex(pose.pose(), minX, maxY, 0.0F).setColor(r, g, b, a).setUv(0.0F, 1.0F).setOverlay(overlay).setLight(light).setNormal(pose, 0.0F, 0.0F, 1.0F);
      consumer.addVertex(pose.pose(), maxX, maxY, 0.0F).setColor(r, g, b, a).setUv(1.0F, 1.0F).setOverlay(overlay).setLight(light).setNormal(pose, 0.0F, 0.0F, 1.0F);
      consumer.addVertex(pose.pose(), maxX, minY, 0.0F).setColor(r, g, b, a).setUv(1.0F, 0.0F).setOverlay(overlay).setLight(light).setNormal(pose, 0.0F, 0.0F, 1.0F);
   }
}
