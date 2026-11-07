package com.tacz.guns.client.renderer.item;

import com.google.common.base.Suppliers;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IAttachment;
import com.tacz.guns.client.model.BedrockAttachmentModel;
import com.tacz.guns.client.model.SlotModel;
import com.tacz.guns.client.resource.index.ClientAttachmentIndex;
import com.tacz.guns.client.resource.index.ClientAttachmentVariantIndex;
import com.tacz.guns.util.RenderDistance;
import cn.sh1rocu.tacz.compat.fabric.BuiltinItemRendererRegistry;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nonnull;
import java.util.function.Supplier;

public class AttachmentItemRenderer implements BuiltinItemRendererRegistry.DynamicItemRenderer {
    public static final SlotModel SLOT_ATTACHMENT_MODEL = new SlotModel();

    public static final Supplier<AttachmentItemRenderer> INSTANCE = Suppliers.memoize(AttachmentItemRenderer::new);

    public AttachmentItemRenderer() {
    }

    @Override
    public void render(ItemStack stack, ItemDisplayContext mode, PoseStack matrices, SubmitNodeCollector collector, int light, int overlay) {
        renderByItem(stack, mode, matrices, collector, light, overlay);
    }

    public void renderByItem(@Nonnull ItemStack stack, @Nonnull ItemDisplayContext transformType, @Nonnull PoseStack poseStack, @Nonnull SubmitNodeCollector collector, int pPackedLight, int pPackedOverlay) {
        if (stack.getItem() instanceof IAttachment iAttachment) {
            Identifier attachmentId = iAttachment.getAttachmentId(stack);
            poseStack.pushPose();
            TimelessAPI.getClientAttachmentIndex(attachmentId).ifPresentOrElse(attachmentIndex -> {
                // GUI 特殊渲染
                if (transformType == ItemDisplayContext.GUI) {
                    poseStack.translate(0.5, 1.5, 0.5);
                    poseStack.mulPose(Axis.ZN.rotationDegrees(180));
                    collector.submitCustomGeometry(poseStack, RenderTypes.entityTranslucent(attachmentIndex.getSlotTexture()), (pose, buffer) -> {
                        // 26.2: 必须使用回调参数 pose（= 提交那一刻 poseStack.last().copy() 的快照），
                        // 而不是外层 poseStack —— 回调执行时它早已被 popPose/复用，
                        // 结果就是图标被画到错误位置（物品栏一片空白）。
                        PoseStack tacz$snapshotPose = new PoseStack();
                        tacz$snapshotPose.last().pose().set(pose.pose());
                        tacz$snapshotPose.last().normal().set(pose.normal());
                        SLOT_ATTACHMENT_MODEL.renderToBuffer(tacz$snapshotPose, buffer, pPackedLight, pPackedOverlay, 1.0F, 1.0F, 1.0F, ItemPreviewRenderContext.alpha());
                    });
                    return;
                }
                poseStack.translate(0.5, 2, 0.5);
                // 反转模型
                poseStack.scale(-1, -1, 1);
                if (transformType == ItemDisplayContext.FIXED) {
                    poseStack.mulPose(Axis.YN.rotationDegrees(90f));
                }
                // 0929：配件变体（replace/overlay 模式）
                ClientAttachmentVariantIndex variant = TimelessAPI.getClientAttachmentVariantIndex(stack).orElse(null);
                this.renderDefaultAttachment(transformType, poseStack, collector, pPackedLight, pPackedOverlay, attachmentIndex, variant);
            }, () -> {
                // 没有这个 attachmentId，渲染黑紫材质以提醒
                poseStack.translate(0.5, 1.5, 0.5);
                poseStack.mulPose(Axis.ZN.rotationDegrees(180));
                collector.submitCustomGeometry(poseStack, RenderTypes.entityTranslucent(MissingTextureAtlasSprite.getLocation()), (pose, buffer) -> {
                    // 26.2: 必须使用回调参数 pose（= 提交那一刻 poseStack.last().copy() 的快照），
                    // 而不是外层 poseStack —— 回调执行时它早已被 popPose/复用，
                    // 结果就是图标被画到错误位置（物品栏一片空白）。
                    PoseStack tacz$snapshotPose = new PoseStack();
                    tacz$snapshotPose.last().pose().set(pose.pose());
                    tacz$snapshotPose.last().normal().set(pose.normal());
                    SLOT_ATTACHMENT_MODEL.renderToBuffer(tacz$snapshotPose, buffer, pPackedLight, pPackedOverlay, 1.0F, 1.0F, 1.0F, ItemPreviewRenderContext.alpha());
                });
            });
            poseStack.popPose();
        }
    }

    private void renderDefaultAttachment(@NotNull ItemDisplayContext transformType, @NotNull PoseStack poseStack, @NotNull SubmitNodeCollector collector, int pPackedLight, int pPackedOverlay, ClientAttachmentIndex attachmentIndex, ClientAttachmentVariantIndex variant) {
        BedrockAttachmentModel model = attachmentIndex.getAttachmentModel();
        Identifier texture = attachmentIndex.getModelTexture();
        // 0929：配件变体（replace mode）—— 整体替换模型与贴图
        if (variant != null && variant.isReplaceMode() && variant.getAttachmentModel() != null && variant.getModelTexture() != null) {
            model = variant.getAttachmentModel();
            texture = variant.getModelTexture();
        }
        // 有模型？正常渲染
        if (model != null && texture != null) {
            // 调用低模
            Pair<BedrockAttachmentModel, Identifier> lodModel = attachmentIndex.getLodModel();
            // 有低模、在高模渲染范围外、不是第一人称
            if (lodModel != null && !RenderDistance.inRenderHighPolyModelDistance(poseStack) && !transformType.firstPerson()) {
                model = lodModel.getLeft();
                texture = lodModel.getRight();
            }
            // 0929：预览态（展示柜/挂钩陈列）用半透明 + alpha，否则不镂空
            float alpha = ItemPreviewRenderContext.alpha();
            RenderType renderType = ItemPreviewRenderContext.isPreview() ? RenderTypes.entityTranslucent(texture) : RenderTypes.entityCutout(texture);
            // 26.2 collector 架构：用基类 BedrockModel 的带 alpha submit 重载。
            // 与 AmmoItemRenderer 的 ammoModel.submit(..., alpha) 一致。
            model.submit(poseStack, transformType, collector, renderType, pPackedLight, pPackedOverlay, 1.0F, 1.0F, 1.0F, alpha);
        }
        // 否则，以 GUI 形式渲染
        else {
            poseStack.translate(0, 0.5, 0);
            // 展示框里显示正常
            if (transformType == ItemDisplayContext.FIXED) {
                poseStack.mulPose(Axis.YP.rotationDegrees(90));
            }
            collector.submitCustomGeometry(poseStack, RenderTypes.entityTranslucent(attachmentIndex.getSlotTexture()), (pose, buffer) -> {
                // 26.2: 必须使用回调参数 pose（= 提交那一刻 poseStack.last().copy() 的快照），
                // 而不是外层 poseStack —— 回调执行时它早已被 popPose/复用，
                // 结果就是图标被画到错误位置（物品栏一片空白）。
                PoseStack tacz$snapshotPose = new PoseStack();
                tacz$snapshotPose.last().pose().set(pose.pose());
                tacz$snapshotPose.last().normal().set(pose.normal());
                SLOT_ATTACHMENT_MODEL.renderToBuffer(tacz$snapshotPose, buffer, pPackedLight, pPackedOverlay, 1.0F, 1.0F, 1.0F, ItemPreviewRenderContext.alpha());
            });
        }
        // 0929：配件变体（overlay mode）—— 叠加渲染变体模型
        if (variant != null && variant.isOverlayMode()) {
            renderVariantOverlay(transformType, poseStack, collector, pPackedLight, pPackedOverlay, variant);
        }
    }

    /**
     * 0929：配件变体（overlay mode）叠加渲染 —— 在原模型之上再画一层变体模型。
     */
    private void renderVariantOverlay(@NotNull ItemDisplayContext transformType, @NotNull PoseStack poseStack, @NotNull SubmitNodeCollector collector, int pPackedLight, int pPackedOverlay, ClientAttachmentVariantIndex variant) {
        BedrockAttachmentModel model = variant.getAttachmentModel();
        Identifier texture = variant.getModelTexture();
        if (model != null && texture != null) {
            float alpha = ItemPreviewRenderContext.alpha();
            RenderType renderType = ItemPreviewRenderContext.isPreview() ? RenderTypes.entityTranslucent(texture) : RenderTypes.entityCutout(texture);
            model.submit(poseStack, transformType, collector, renderType, pPackedLight, pPackedOverlay, 1.0F, 1.0F, 1.0F, alpha);
        }
    }
}
