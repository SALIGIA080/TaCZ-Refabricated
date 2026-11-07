package com.tacz.guns.client.renderer.block;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.tacz.guns.GunMod;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 展示柜/外部枪「发光件」的 RenderType（1025 DisplayGunRenderType 的 26.2 等价）。
 *
 * <h2>1025 原始配方（decomp/src-1025 实读）</h2>
 * <pre>
 * RenderType.create(
 *   "tacz_display_gun_emissive",
 *   DefaultVertexFormat.NEW_ENTITY, QUADS, 256, true, false,
 *   CompositeState.builder()
 *     .setShaderState(RENDERTYPE_ENERGY_SWIRL_SHADER)   // energy_swirl 满亮 shader
 *     .setTextureState(TextureStateShard(texture, false, false))
 *     .setTransparencyState(NO_TRANSPARENCY)            // 不透明，非混合
 *     .setCullState(CULL)
 *     .setTexturingState(identity_uv)                    // resetTextureMatrix：无 UV 滚动
 *     .setLightmapState(LIGHTMAP)
 *     .setOverlayState(OVERLAY)
 *     .setOutputState(ITEM_ENTITY_TARGET)
 *     .setWriteMaskState(COLOR_DEPTH_WRITE)
 *     .createCompositeState(true))
 * </pre>
 *
 * <h2>26.2 精确等价</h2>
 * 26.2 把 energy_swirl 的 shader 折叠回通用 {@code core/entity}（jar 内已无独立
 * rendertype_energy_swirl.fsh），管线配方见 {@code RenderPipelines.&lt;clinit&gt;}
 * 里 {@code ENERGY_SWIRL} 的字节码（见 {@code ScopeBodyRenderTypes.FLASH_SWIRL_CLIPPED_PIPELINE}
 * 的注释）。相对 vanilla {@code ENERGY_SWIRL}，本类只改两处，逐项对齐 1025：
 * <ul>
 *   <li><b>混合</b>：1025 是 {@code NO_TRANSPARENCY}（不透明），而 vanilla
 *       {@code ENERGY_SWIRL} 是 {@code BlendFunction.ADDITIVE}（叠加发光）。
 *       展示柜发光件画的是枪身发光骨骼（瞄准镜发光准星等），不是火光的叠加辉光，
 *       必须用<b>无混合</b>的 {@code ColorTargetState}（{@code Optional.empty()}
 *       + {@code WRITE_ALL}）等价 1025 的 NO_TRANSPARENCY；</li>
 *   <li><b>UV</b>：1025 是 identity UV（{@code resetTextureMatrix}，无滚动），
 *       vanilla {@code ENERGY_SWIRL} 的 {@code APPLY_TEXTURE_MATRIX} +
 *       {@code OffsetTextureTransform} 是给火光辉光滚动用的，这里<b>不加</b>
 *       {@code APPLY_TEXTURE_MATRIX}，从而得到无滚动的纯贴图发光。</li>
 * </ul>
 * 其余（EMISSIVE 满亮、NO_OVERLAY、NO_CARDINAL_LIGHTING 不受方向光、Cull(false)、
 * ENTITY 顶点绑定、QUADS 拓扑、DEFAULT 深度）与 vanilla ENERGY_SWIRL 逐状态一致。
 *
 * <p>该通道只喂给 {@code GeometryCollector} 的发光骨骼批（illuminated bones）。</p>
 */
public class DisplayGunRenderType {
    private static final Map<Identifier, RenderType> CACHE = new HashMap<>();

    /** 展示柜发光件管线：energy_swirl shader + 无混合 + identity UV（见类注释）。 */
    private static final RenderPipeline EMISSIVE_PIPELINE =
            RenderPipeline.builder(RenderPipelines.MATRICES_FOG_SNIPPET)
                    .withLocation(Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "pipeline/tacz_display_gun_emissive"))
                    .withVertexShader("core/entity")
                    .withFragmentShader("core/entity")
                    .withShaderDefine("ALPHA_CUTOUT", 0.1F)
                    .withShaderDefine("EMISSIVE")
                    .withShaderDefine("NO_OVERLAY")
                    .withShaderDefine("NO_CARDINAL_LIGHTING")
                    .withBindGroupLayout(BindGroupLayouts.SAMPLER0)
                    .withColorTargetState(new ColorTargetState(
                            Optional.empty(), GpuFormat.RGBA8_UNORM, ColorTargetState.WRITE_ALL))
                    .withCull(false)
                    .withVertexBinding(0, DefaultVertexFormat.ENTITY)
                    .withPrimitiveTopology(PrimitiveTopology.QUADS)
                    .withDepthStencilState(DepthStencilState.DEFAULT)
                    .build();

    private DisplayGunRenderType() {
    }

    public static RenderType emissive(Identifier texture) {
        return CACHE.computeIfAbsent(texture, tex -> RenderType.create("tacz_display_gun_emissive",
                RenderSetup.builder(EMISSIVE_PIPELINE)
                        .withTexture("Sampler0", tex)
                        .useLightmap()
                        .useOverlay()
                        .sortOnUpload()
                        .createRenderSetup()));
    }
}
