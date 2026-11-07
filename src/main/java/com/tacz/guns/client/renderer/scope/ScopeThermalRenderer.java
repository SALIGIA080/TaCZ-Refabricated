package com.tacz.guns.client.renderer.scope;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.pipeline.BindGroupLayout;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.tacz.guns.GunMod;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator;
import com.tacz.guns.api.item.IAttachment;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.client.resource.index.ClientAttachmentIndex;
import com.tacz.guns.client.resource.index.ClientAttachmentVariantIndex;
import com.tacz.guns.compat.iris.IrisCompat;
import com.tacz.guns.config.client.RenderConfig;
import com.tacz.guns.util.ThermalImagingHelper;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.PreparedRenderType;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;

/**
 * 热成像镜片的「热源剪影」捕获与重放（1025 {@code ScopeThermalRenderer} 的 26.2 迁移）。
 *
 * <h2>1025 原语义（逐行对照反编译源）</h2>
 * <ol>
 *   <li>捕获：{@code EntityRenderDispatcherMixin} 把实体渲染的 {@code MultiBufferSource}
 *       包成 {@code ThermalBuffers}；{@code getBuffer(RenderType)} 把非描边类型的顶点经
 *       {@code VertexMultiConsumer} 同时写进原 buffer 与 {@code POSITION_COLOR_TEX} 的
 *       {@code BufferBuilder}（按 RenderType 分组，存进 ACTIVE）。</li>
 *   <li>重绘：{@code AFTER_PARTICLES} 阶段把 ACTIVE 顶点重画进离屏 {@code TextureTarget}
 *       —— 附上<b>场景深度</b>（{@code attachSceneDepth}）、depthFunc LEQUAL、
 *       depthMask false、polygonOffset(-1,-2)、无混合 —— 于是被墙挡住的热源不会显形。</li>
 *   <li>合成：{@code ScopeLensRenderer.textureOr(thermalTarget, ...)} 把热成像纹理
 *       作 Sampler1 绑进镜头后处理，由 {@code scope_lens.fsh} 按 VisionMode 调色。</li>
 * </ol>
 *
 * <h2>26.2 等价落点（语义等价 + 视觉 1:1，非逐 GL 调用）</h2>
 * <ul>
 *   <li>{@code MultiBufferSource} 顶点拦截（已删除的 API）→ 在
 *       {@code ModelFeatureRenderer#prepareModel} 的 {@code Model.renderToBuffer} 调用处
 *       拦截（{@code ModelFeatureRendererMixin}）：绘制期 {@code setupAnim(state)} 已按
 *       <b>当前实体</b>摆好姿势，此刻重放顶点与 1025 的 tee 时机等价且姿态正确。
 *       热源判定用 {@code EntityRenderDispatcher.extractEntity} 钩子
 *       （{@code EntityRenderDispatcherMixin}）按<b>实体</b>逐个标记（同 1025
 *       {@code wrap} 的 isAlive/!isInvisible/!isSpectator/!=camera/isHeatSource 判据），
 *       绘制期按 state 身份查表。</li>
 *   <li>{@code BufferUploader.drawWithShader} → {@code MeshData + CommandEncoder + RenderPass}。</li>
 *   <li>场景深度挂载（{@code glFramebufferTexture}/{@code glFramebufferRenderbuffer}）→
 *       {@code CommandEncoder.createRenderPass(..., depthView, OptionalDouble.empty())}
 *       把主 target 的深度纹理当<b>只读深度附件</b>挂上（writeDepth=false），遮挡关系
 *       与 1025 完全一致。</li>
 *   <li>stencil/描边类型过滤 → {@code RenderType.isOutline()} 跳过（同 1025 的
 *       「非描边类型才捕获」）。</li>
 *   <li>1025 的 {@code RenderSystem.getModelViewMatrix()/getProjectionMatrix()} 捕获 →
 *       首个顶点落笔时快照 {@code RenderSystem.getModelViewMatrixCopy()}（与
 *       GPU 网格绘制同一约定：顶点先套 pose 再进相机系，重放时
 *       ModelViewMat 用这份快照、ProjMat 走 {@code bindDefaultUniforms} 的当前 UBO）。</li>
 * </ul>
 *
 * <h2>已知的有界差异</h2>
 * <ul>
 *   <li>1025 的 {@code polygonOffset(-1,-2)} 由 {@code DepthStencilState} 的
 *       depthBias(scaleFactor=-1, constant=-2) 等价承担。</li>
 *   <li>1025 重放按 RenderType {@code setupRenderState()}；这里统一用
 *       {@code scope_thermal_mask} 管线 + 各 RenderType 自己的贴图
 *       （{@code PreparedRenderType#textures}），alpha 裁剪语义由
 *       {@code scope_thermal_mask.fsh} 承担。</li>
 *   <li>只拦截 {@code submitModel} 路径（实体模型）；实体的 custom geometry 不进
 *       热成像剪影 —— 1025 的 MultiBufferSource tee 会覆盖它们，属可观察的边缘差异。</li>
 * </ul>
 */
@Environment(EnvType.CLIENT)
public final class ScopeThermalRenderer {
    static final float DEPTH_BIAS_FACTOR = -1.0F;
    static final float DEPTH_BIAS_UNITS = -2.0F;

    /** 帧内热源 state 集合（identity）：extract 期标记，绘制期查表。 */
    private static final Set<Object> HOT_STATES = Collections.newSetFromMap(new IdentityHashMap<>());

    /** 本帧已捕获的批次（按 RenderType 分组，插入序）。 */
    private static final Map<RenderType, ThermalBatch> ACTIVE = new LinkedHashMap<>();

    /** 重放用 ModelViewMat：首个顶点落笔时的 RenderSystem modelview 快照。 */
    private static Matrix4f drawModelView;

    private static TextureTarget target;
    private static boolean collecting;
    private static boolean ready;
    private static Boolean thermalScope;

    private static final Vector4f WHITE = new Vector4f(1f, 1f, 1f, 1f);

    private ScopeThermalRenderer() {
    }

    // ---- 管线（懒加载，避免 clinit 崩掉整个功能） ----

    private static RenderPipeline thermalPipeline;

    private static RenderPipeline thermalPipeline() {
        if (thermalPipeline == null) {
            thermalPipeline = RenderPipeline.builder(RenderPipelines.POST_PROCESSING_SNIPPET)
                    .withLocation(Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "pipeline/scope_thermal_mask"))
                    .withPrimitiveTopology(PrimitiveTopology.QUADS)
                    .withVertexShader(Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "core/scope_thermal_mask"))
                    .withFragmentShader(Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "core/scope_thermal_mask"))
                    .withBindGroupLayout(BindGroupLayouts.MATRICES_PROJECTION)
                    .withBindGroupLayout(BindGroupLayouts.SAMPLER0)
                    .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_COLOR)
                    .withColorTargetState(new ColorTargetState(
                            Optional.empty(), GpuFormat.RGBA8_UNORM, ColorTargetState.WRITE_ALL))
                    // LEQUAL + 不写深度 + 深度偏置(-1,-2)：1025 的
                    // depthFunc(GL_LEQUAL)/depthMask(false)/polygonOffset(-1,-2) 等价。
                    .withDepthStencilState(new DepthStencilState(
                            CompareOp.LESS_THAN_OR_EQUAL, false, DEPTH_BIAS_FACTOR, DEPTH_BIAS_UNITS))
                    .withCull(true)
                    .build();
        }
        return thermalPipeline;
    }

    // ---- 帧边界 ----

    public static void beginFrame() {
        for (ThermalBatch batch : ACTIVE.values()) {
            batch.discard();
        }
        ACTIVE.clear();
        HOT_STATES.clear();
        drawModelView = null;
        collecting = true;
        ready = false;
        thermalScope = null;
    }

    /** 是否处于热成像工作状态（与 1025 {@code enabled()} 同判据）。 */
    public static boolean enabled() {
        Minecraft mc = Minecraft.getInstance();
        if (!RenderConfig.EXPERIMENTAL_SCOPE_LENS.get()
                || !RenderConfig.SCOPE_THERMAL_ENABLE.get()
                || mc.level == null
                || mc.player == null
                || mc.getCameraEntity() != mc.player
                || !mc.options.getCameraType().isFirstPerson()
                || mc.gameRenderer.gameRenderState().guiRenderState.isHudHidden
                || IrisCompat.isRenderShadow()) {
            return false;
        }
        if (IClientPlayerGunOperator.fromLocalPlayer(mc.player)
                .getClientAimingProgress(mc.getDeltaTracker().getGameTimeDeltaPartialTick(false)) <= 0.001F) {
            return false;
        }
        if (thermalScope == null) {
            thermalScope = hasThermalScope(mc.player.getMainHandItem());
        }
        return thermalScope;
    }

    private static boolean hasThermalScope(ItemStack gunItem) {
        IGun gun = IGun.getIGunOrNull(gunItem);
        if (gun == null) {
            return false;
        }
        ItemStack scope = gun.getAttachment(gunItem, AttachmentType.SCOPE);
        if (scope.isEmpty()) {
            scope = gun.getBuiltinAttachment(gunItem, AttachmentType.SCOPE);
        }
        IAttachment attachment = IAttachment.getIAttachmentOrNull(scope);
        if (attachment == null) {
            return false;
        }
        ClientAttachmentIndex index = TimelessAPI.getClientAttachmentIndex(attachment.getAttachmentId(scope)).orElse(null);
        if (index == null) {
            return false;
        }
        ClientAttachmentVariantIndex variant = TimelessAPI.getClientAttachmentVariantIndex(scope).orElse(null);
        if (variant != null && variant.getAttachmentModel() != null && variant.getModelTexture() != null) {
            boolean variantThermal = variant.isScope() && variant.getScopeLens().getVisionMode().isThermal();
            if (variant.isReplaceMode()) {
                return variantThermal;
            }
            if (variantThermal) {
                return true;
            }
        }
        return index.isScope() && index.getScopeLens().getVisionMode().isThermal();
    }

    // ---- 捕获（extract 期标记 / 绘制期 tee） ----

    /**
     * {@code EntityRenderDispatcher.extractEntity} 钩子：按 1025 {@code wrap()} 的判据
     * 标记本帧热源（此时实体与 state 同时在手，判据可完全照抄 1025）。
     */
    public static void markHeatSource(Entity entity, Object renderState) {
        if (!collecting || renderState == null) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (enabled()
                && entity.isAlive()
                && !entity.isInvisible()
                && !entity.isSpectator()
                && entity != mc.getCameraEntity()
                && ThermalImagingHelper.isHeatSource(entity)) {
            HOT_STATES.add(renderState);
        }
    }

    /**
     * 绘制期（{@code ModelFeatureRenderer#prepareModel}）：该 state 是本帧标记的热源时，
     * 返回同时写入热成像批次的 tee 消费者；否则返回 {@code null} 表示不拦截。
     */
    @Nullable
    public static ThermalVertexSink wrapConsumer(Object renderState, RenderType type, VertexConsumer real) {
        if (!collecting || renderState == null || !HOT_STATES.contains(renderState)
                || type == null || type.isOutline()) {
            return null;
        }
        if (drawModelView == null) {
            drawModelView = new Matrix4f(RenderSystem.getModelViewMatrixCopy());
        }
        // 26.2 的 BufferBuilder 是一次性对象（构造器定拓扑/格式，无 begin()），
        // 每帧每批新建；底层 ByteBufferBuilder 随 builder 释放。
        ThermalBatch batch = ACTIVE.computeIfAbsent(type, ThermalBatch::new);
        return new ThermalVertexSink(real, batch);
    }

    // ---- 重放（世界画完、主深度被清之前） ----

    /**
     * 把本帧捕获的热源剪影重画进离屏热成像纹理（附场景深度做遮挡）。
     *
     * <p>必须卡在 {@code LevelRenderer#render} 之后、{@code renderItemInHand} 之前：
     * 前者保证捕获完整且场景深度仍在主深度纹理里；后者在开头会把主深度清零
     * （GameRenderer#renderLevel 偏移 484-500 实读），晚一步就失去了遮挡依据。</p>
     */
    public static void renderOffscreen(RenderTarget main) {
        collecting = false;
        if (!enabled() || ACTIVE.isEmpty() || main == null || main.width <= 0 || main.height <= 0) {
            return;
        }
        GpuTextureView depth = main.getDepthTextureView();
        if (depth == null) {
            return;
        }
        List<RenderType> done = new ArrayList<>(ACTIVE.size());
        try {
            if (target == null || target.width != main.width || target.height != main.height) {
                if (target != null) {
                    target.destroyBuffers();
                }
                target = new TextureTarget("tacz_scope_thermal", main.width, main.height, false, GpuFormat.RGBA8_UNORM);
            }
            CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
            boolean first = true;
            for (Map.Entry<RenderType, ThermalBatch> entry : ACTIVE.entrySet()) {
                RenderType type = entry.getKey();
                ThermalBatch batch = entry.getValue();
                MeshData mesh = batch.build();
                if (mesh == null) {
                    done.add(type);
                    continue;
                }
                try (mesh) {
                    MeshData.DrawState draw = mesh.drawState();
                    if (draw.indexCount() == 0) {
                        done.add(type);
                        continue;
                    }
                    GpuBuffer vertexBuffer = RenderSystem.getDevice().createBuffer(
                            () -> "tacz_scope_thermal_vb", GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_COPY_DST,
                            mesh.vertexBuffer());
                    try {
                        RenderSystem.AutoStorageIndexBuffer indices =
                                RenderSystem.getSequentialBuffer(draw.primitiveTopology());
                        GpuBuffer indexBuffer = indices.getBuffer(draw.indexCount());
                        try (RenderPass pass = encoder.createRenderPass(
                                () -> "tacz_scope_thermal",
                                target.getColorTextureView(),
                                first ? Optional.of(new Vector4f(0f, 0f, 0f, 0f)) : Optional.empty(),
                                depth,
                                OptionalDouble.empty())) {
                            pass.setPipeline(thermalPipeline());
                            RenderSystem.bindDefaultUniforms(pass);
                            pass.setUniform("DynamicTransforms",
                                    RenderSystem.getDynamicUniforms().writeTransform(drawModelView, WHITE));
                            bindTypeTexture(pass, type);
                            pass.setVertexBuffer(0, vertexBuffer.slice());
                            pass.setIndexBuffer(indexBuffer, indices.type());
                            pass.drawIndexed(draw.indexCount(), 1, 0, 0, 0);
                        }
                    } finally {
                        vertexBuffer.close();
                    }
                }
                first = false;
                done.add(type);
            }
            ready = !first;
        } catch (Exception e) {
            ready = false;
            GunMod.LOGGER.error("[TACZ Scope] Failed to render thermal silhouettes; thermal vision falls back to the normal lens", e);
        } finally {
            for (RenderType type : done) {
                ThermalBatch batch = ACTIVE.remove(type);
                if (batch != null) {
                    batch.discard();
                }
            }
            ACTIVE.clear();
        }
    }

    /** 把 RenderType 自己的贴图绑成 {@code Sampler0}（alpha 裁剪由 scope_thermal_mask.fsh 做）。 */
    private static void bindTypeTexture(RenderPass pass, RenderType type) {
        PreparedRenderType prepared = type.prepare();
        List<PreparedRenderType.Texture> textures = prepared.textures();
        PreparedRenderType.Texture chosen = null;
        for (PreparedRenderType.Texture texture : textures) {
            if ("Sampler0".equals(texture.name())) {
                chosen = texture;
                break;
            }
        }
        if (chosen == null && !textures.isEmpty()) {
            chosen = textures.get(0);
        }
        if (chosen != null) {
            pass.bindTexture("Sampler0", chosen.textureView(), chosen.sampler());
        }
    }

    // ---- 给 ScopeLensRenderer 的查询 ----

    public static boolean isReady(int width, int height) {
        return ready && target != null && target.width == width && target.height == height;
    }

    public static GpuTextureView textureOr(GpuTextureView fallback, int width, int height) {
        return isReady(width, height) ? target.getColorTextureView() : fallback;
    }

    public static void close() {
        beginFrame();
        if (target != null) {
            target.destroyBuffers();
            target = null;
        }
    }

    // ---- 内部类 ----

    /** 一个 RenderType 的热成像批次：POSITION_COLOR_TEX 的 QUADS 缓冲。 */
    private static final class ThermalBatch {
        final RenderType type;
        final BufferBuilder builder;

        ThermalBatch(RenderType type) {
            this.type = type;
            this.builder = new BufferBuilder(new ByteBufferBuilder(4096),
                    PrimitiveTopology.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        }

        @Nullable
        MeshData build() {
            try {
                return builder.buildOrThrow();
            } catch (Exception e) {
                GunMod.LOGGER.error("[TACZ Scope] Failed to build thermal batch for {}", type, e);
                return null;
            }
        }

        void discard() {
            try {
                MeshData mesh = builder.build();
                if (mesh != null) {
                    mesh.close();
                }
            } catch (Exception ignored) {
            }
        }
    }

    /**
     * 顶点 tee：把 {@code Model.renderToBuffer} 写出的顶点（pose 已套用）同时转记进
     * 热成像批次（position + uv + 纯白）—— 1025 {@code MaskVertices} 的 26.2 等价。
     *
     * <p>26.2 的 {@code VertexConsumer} 是 {@code addVertex} 起链的立即式，
     * 但 uv/颜色等属性在 {@code addVertex} <b>之后</b>补写；因此按「缓存一个顶点、
     * 下一个 addVertex 或 flush 时落盘」实现，与链式顺序无关。</p>
     */
    public static final class ThermalVertexSink implements VertexConsumer {
        private final VertexConsumer real;
        private final ThermalBatch batch;
        private boolean pending;
        private float x;
        private float y;
        private float z;
        private float u;
        private float v;

        ThermalVertexSink(VertexConsumer real, ThermalBatch batch) {
            this.real = real;
            this.batch = batch;
        }

        private void flushPending() {
            if (pending) {
                pending = false;
                batch.builder.addVertex(x, y, z).setColor(255, 255, 255, 255).setUv(u, v);
            }
        }

        /** 模型写完后调用：落盘最后一个顶点。 */
        public void flush() {
            flushPending();
        }

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            flushPending();
            this.x = x;
            this.y = y;
            this.z = z;
            this.u = 0f;
            this.v = 0f;
            this.pending = true;
            return real.addVertex(x, y, z);
        }

        @Override
        public VertexConsumer setColor(int r, int g, int b, int a) {
            return real.setColor(r, g, b, a);
        }

        @Override
        public VertexConsumer setColor(int packed) {
            return real.setColor(packed);
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            if (pending) {
                this.u = u;
                this.v = v;
            }
            return real.setUv(u, v);
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            return real.setUv1(u, v);
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            return real.setUv2(u, v);
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            return real.setNormal(x, y, z);
        }

        @Override
        public VertexConsumer setLineWidth(float width) {
            return real.setLineWidth(width);
        }
    }
}
