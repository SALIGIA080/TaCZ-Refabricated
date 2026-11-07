package com.tacz.guns.mixin.client;

import com.tacz.guns.client.renderer.scope.ScopeThermalRenderer;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 热成像的「热源标记」钩子（26.2）。
 *
 * <p>1025 在 {@code EntityRenderDispatcher.render} 里对 {@code MultiBufferSource} 做
 * wrap 并按实体判据（isAlive / !isInvisible / !isSpectator / != camera / isHeatSource）
 * 决定是否包 {@code ThermalBuffers}；26.2 的 {@code render} 已改名为
 * {@code submit(S, ...)}，实体本体不在参数里（只有 render state）。</p>
 *
 * <p>而 {@code extractEntity(E, float)} 是实体与它本帧 render state 同时在手的唯一入口：
 * 在这里按<b>与 1025 完全相同</b>的实体判据标记 state，绘制期
 * （{@code ModelFeatureRendererMixin}）按 state 身份查表做顶点 tee。</p>
 */
@Mixin(EntityRenderDispatcher.class)
public abstract class EntityRenderDispatcherMixin {

    @Inject(method = "extractEntity", at = @At("RETURN"))
    private <E extends Entity> void tacz$markThermalHeatSource(E entity, float partialTick,
                                                              CallbackInfoReturnable<EntityRenderState> cir) {
        ScopeThermalRenderer.markHeatSource(entity, cir.getReturnValue());
    }
}
