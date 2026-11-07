package com.tacz.guns.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.tacz.guns.client.renderer.scope.ScopeThermalRenderer;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 热成像顶点捕获的绘制期拦截点（26.2）。
 *
 * <p>1025 在 {@code EntityRenderDispatcher.render} 里把 {@code MultiBufferSource} 包成
 * {@code ThermalBuffers} 做顶点 tee；26.2 删除了 MultiBufferSource，实体模型统一经
 * {@code ModelFeatureRenderer#prepareModel} → {@code Model.renderToBuffer} 落笔。
 * 在这一句上 tee 等价于 1025 的 getBuffer 拦截，且此时 {@code setupAnim(state)} 刚按
 * 当前实体摆好姿势（prepareModel 内部先 setupAnim 再 renderToBuffer，字节码实读），
 * 重放出的顶点姿态正确 —— 若在 submit 期提前重放反而会拿到上一个实体的姿势。</p>
 *
 * <p>热源判定见 {@link ScopeThermalRenderer#wrapConsumer}（state 身份查表，
 * 由 {@code EntityRenderDispatcherMixin} 在 extract 期按实体标记）。</p>
 */
@Mixin(ModelFeatureRenderer.class)
public abstract class ModelFeatureRendererMixin {

    /** prepareModel 的当前 Submit 节点（prepareModel 不重入，直接存字段最稳）。 */
    @Unique
    private ModelFeatureRenderer.Submit<?> tacz$currentSubmit;

    @Inject(method = "prepareModel", at = @At("HEAD"))
    private void tacz$trackSubmit(ModelFeatureRenderer.Submit<?> submit, CallbackInfo ci) {
        this.tacz$currentSubmit = submit;
    }

    @WrapOperation(
            method = "prepareModel",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/model/Model;renderToBuffer(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;III)V"
            )
    )
    private void tacz$captureThermalVertices(Model<?> model, PoseStack pose, VertexConsumer consumer,
                                             int light, int overlay, int tint, Operation<Void> original) {
        ModelFeatureRenderer.Submit<?> submit = this.tacz$currentSubmit;
        ScopeThermalRenderer.ThermalVertexSink sink = submit == null
                ? null
                : ScopeThermalRenderer.wrapConsumer(submit.state(), submit.renderType(), consumer);
        if (sink == null) {
            original.call(model, pose, consumer, light, overlay, tint);
            return;
        }
        original.call(model, pose, sink, light, overlay, tint);
        sink.flush();
    }
}
