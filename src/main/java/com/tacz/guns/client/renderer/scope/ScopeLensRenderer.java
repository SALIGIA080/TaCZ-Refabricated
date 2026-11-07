package com.tacz.guns.client.renderer.scope;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.BindGroupLayout;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.tacz.guns.GunMod;
import com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator;
import com.tacz.guns.client.render.scope.ScopeMaskRenderer;
import com.tacz.guns.client.render.scope.ScopeMaskTarget;
import com.tacz.guns.client.resource.pojo.display.attachment.ScopeLensConfig;
import com.tacz.guns.compat.iris.IrisCompat;
import com.tacz.guns.config.client.RenderConfig;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.Projection;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.PreparedRenderType;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector4f;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * 实验性瞄具镜头（镜头畸变 / 色差 / 晕影 / 夜视 / 热成像）——1025 {@code ScopeLensRenderer}
 * 的 26.2 迁移。
 *
 * <h2>1025 原流程（反编译源逐段对照）</h2>
 * <ol>
 *   <li>{@code capture}（renderOcularAndDivision 里）：目镜几何喂 {@code Bounds}
 *       （projection×modelView 投影出屏幕包围盒 centerU/V/radiusU/V）；分划板几何喂
 *       {@code ReticleBuffers}（按 RenderType 收顶点）；随后
 *       {@code glBlitFrameBuffer} 把主画面拷进 capture target。整个 capture FBO 带
 *       stencil，目镜圆盘先前写入的模板值就是「镜片区域」。</li>
 *   <li>{@code render}（帧末）：把 capture FBO 的<b>颜色附件换成主画面纹理</b>，
 *       stencilFunc(EQUAL) 只在镜片区域内画镜头全屏 quad（畸变/色差/晕影/视觉模式，
 *       采样 capture.target = 主画面+分划板），再把分划板按原 RenderType 重画。</li>
 * </ol>
 *
 * <h2>26.2 等价设计（语义等价 + 视觉 1:1，非逐 GL 调用）</h2>
 * <ul>
 *   <li><b>FBO blit / 纹理挂载 / stencil 全部不存在</b>（26.2 硬约束）：
 *       主画面拷贝 → {@code CommandEncoder.copyTextureToTexture}；镜片区域判定 →
 *       {@code scope_lens.fsh} 直采<b>当帧目镜掩码</b>（{@link ScopeMaskRenderer} 在
 *       阶段边界用目镜几何本身画出的 R=1 覆盖区，与 1025 写 stencil 的那套几何同源），
 *       无掩码时回退 Bounds 内切椭圆。</li>
 *   <li>Bounds 的 projection×modelView：手部渲染用的是 {@code hudProjection}
 *       （renderLevel 偏移 428-464：{@code setupPerspective(0.05, 100, hudFov, w, h)}，
 *       字节码实读），这里按同样参数重建 CPU 侧矩阵，再乘
 *       {@code RenderSystem.getModelViewMatrixCopy()} —— 与
 *       {@code ScopeMaskGeometry} 烘焙注释里的变换链
 *       「Projection × ModelView(submit) × itemPose × vertex」逐项对应。</li>
 *   <li>分划板：顶点收集为 POSITION_COLOR_TEX（position+uv+颜色），
 *       {@code render} 时用 {@code scope_reticle} 管线画进 capture target，
 *       再被镜头 shader 采样、随场景一起受畸变 —— 与 1025「分划板画进 capture.target
 *       后被镜头 shader 采样」一致。贴图取自原 RenderType
 *       （{@code PreparedRenderType#textures}），alpha 裁剪同源。</li>
 *   <li>热成像：捕获/重放见 {@link ScopeThermalRenderer}；本类只在合成时把
 *       热成像纹理绑进 Sampler1（1025 的 {@code textureOr} 同位）。</li>
 * </ul>
 *
 * <h2>与 1025 的有界差异</h2>
 * <ul>
 *   <li>1025 的模型空间 90 边形镜盘 + stencil 裁剪 → 镜片包围盒 quad + 目镜掩码门限。
 *       镜外像素<b>直接 discard</b>（不写），与 1025「stencil 不写镜外」等价 ——
 *       这也是镜外不再出现黑框的关键。</li>
 *   <li>分划板重画不乘 lightmap（镜内自发光语境）；颜色/alpha/贴图同 1025。</li>
 *   <li>capture() 不再接收模型空间 center/radius/stencil（屏幕空间设计中无消费方），
 *       开镜门禁由本类内部的 aimingProgress 判定承担（1025 的 rad×aimingProgress 同义）。</li>
 * </ul>
 *
 * <p>调用方：{@code BedrockAttachmentModel}（capture）、{@code GameRendererMixin}
 * （beginFrame / render）、{@code ClientGunTimeResetEvent}（close）。</p>
 */
@Environment(EnvType.CLIENT)
public final class ScopeLensRenderer {

    /**
     * LensUniforms UBO 的 std140 布局总字节数。
     *
     * <p>字段序与偏移（见 {@code scope_lens.fsh}）：
     * LensCenter 0 / LensRadius 8 / ContourReady 16 / Distortion 20 / Aberration 24 /
     * EdgeWidth 28 / VignetteStrength 32 / VignetteWidth 36 / ContourSize 40 /
     * ContourRect 48 / LensTint 64（vec3，<b>只占 12 字节</b>）/ VisionMode 76 /
     * ThermalReady 80 / VisionGain 84 / VisionNoise 88 / SensorTime 92 → 共 96，按 16 对齐取 96。</p>
     *
     * <p><b>ContourSize / ContourRect 现已不参与渲染</b>（掩码直采替代了距离场），
     * 但着色器里的 std140 块仍声明着它们，写 UBO 时按占位写满以把 LensTint 顶到
     * 偏移 64 —— 删掉这 24 字节会让其后所有字段整体前移，热成像/夜视读到的模式
     * 恒为 NORMAL（历史上正是栽在这里）。</p>
     */
    private static final int LENS_UBO_SIZE = 96;

    private static final List<Lens> LENSES = new ArrayList<>();
    /** 跨帧复用的 capture 槽（1025 CAPTURES 同构）：按当帧镜头序号取槽。 */
    private static final List<Capture> CAPTURES = new ArrayList<>();

    private ScopeLensRenderer() {
    }

    // ---- 管线（懒加载，避免 clinit 崩掉整个功能） ----

    private static RenderPipeline lensPipeline;

    /** LensUniforms 的 bind group layout（镜头合成与分划板门限共用同一 UBO）。 */
    private static BindGroupLayout lensUniformLayout() {
        return BindGroupLayout.builder()
                .withUniform("LensUniforms", UniformType.UNIFORM_BUFFER).build();
    }

    private static RenderPipeline lensPipeline() {
        if (lensPipeline == null) {
            BindGroupLayout lensUniform = lensUniformLayout();
            // 用 MATRICES_FOG_SNIPPET 而不是 POST_PROCESSING_SNIPPET：
            // 前者内含 GLOBALS_SNIPPET（Globals/DynamicTransforms/Projection/Fog），
            // core/globals.glsl 的 ScreenSize 正是 Globals 块里的成员。
            // 手拼 POST_PROCESSING + GLOBALS 时 ScreenSize 绑不上、读到 0，
            // 镜片 quad 的 margin = 3.0/ScreenSize 变成 inf，顶点全是 NaN ——
            // pass 不报错、日志照写「已绘制」，屏幕上却一个像素都没有。
            // 掩码管线（ScopeMaskRenderer）用同一片段，一直是好的，照抄它的组合。
            lensPipeline = RenderPipeline.builder(RenderPipelines.MATRICES_FOG_SNIPPET)
                    .withLocation(Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "pipeline/scope_lens"))
                    // MATRICES_FOG_SNIPPET 不像 POST_PROCESSING 那样自带拓扑，必须显式声明。
                    .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
                    .withVertexShader(Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "core/scope_lens_quad"))
                    .withFragmentShader(Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "core/scope_lens"))
                    // Sampler0 主画面拷贝 / Sampler1 热成像 / Sampler2 目镜掩码。
                    // 必须用三个不同的 BindGroupLayout（各自绑定名 Sampler0/1/2），
                    // 连续三个 IN_SAMPLER 会在同一 bind group 里产生重复绑定名。
                    .withBindGroupLayout(BindGroupLayouts.SAMPLER0)
                    .withBindGroupLayout(BindGroupLayouts.SAMPLER1)
                    .withBindGroupLayout(BindGroupLayouts.SAMPLER2)
                    // GLOBALS 由 MATRICES_FOG_SNIPPET 自带，不再手拼。
                    .withBindGroupLayout(lensUniform)
                    .withColorTargetState(new ColorTargetState(
                            Optional.empty(), GpuFormat.RGBA8_UNORM, ColorTargetState.WRITE_ALL))
                    .build();
        }
        return lensPipeline;
    }

    private static RenderPipeline reticlePipeline;

    /** 分划板管线：带贴图的 alpha 裁剪输出（1025 用原 RenderType 实体管线重画的等价）。 */
    private static RenderPipeline reticlePipeline() {
        if (reticlePipeline == null) {
            reticlePipeline = RenderPipeline.builder(RenderPipelines.MATRICES_FOG_SNIPPET)
                    .withLocation(Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "pipeline/scope_reticle"))
                    .withPrimitiveTopology(PrimitiveTopology.QUADS)
                    .withVertexShader(Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "core/scope_reticle"))
                    .withFragmentShader(Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "core/scope_reticle"))
                    .withBindGroupLayout(BindGroupLayouts.SAMPLER0)
                    .withBindGroupLayout(BindGroupLayouts.SAMPLER1)
                    .withBindGroupLayout(BindGroupLayouts.SAMPLER2)
                    // ScreenSize 由 MATRICES_FOG_SNIPPET 的 Globals 提供，
                    // scope_reticle.fsh 的镜内门限直接依赖它。
                    .withBindGroupLayout(lensUniformLayout())
                    .withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP)
                    .withColorTargetState(new ColorTargetState(
                            Optional.empty(), GpuFormat.RGBA8_UNORM, ColorTargetState.WRITE_ALL))
                    // 1025 分划板绘制是 disableDepthTest + depthMask(false)：无深度附件。
                    .withDepthStencilState(Optional.empty())
                    // 分划板是薄片几何，正反面都算。
                    .withCull(false)
                    .build();
        }
        return reticlePipeline;
    }

    private static GpuBuffer lensUbo;

    private static GpuBuffer lensUbo() {
        if (lensUbo == null) {
            lensUbo = RenderSystem.getDevice().createBuffer(
                    () -> "tacz_scope_lens_ubo",
                    GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_MAP_WRITE, LENS_UBO_SIZE);
        }
        return lensUbo;
    }

    // ---- 帧边界 ----

    public static void beginFrame() {
        ScopeThermalRenderer.beginFrame();
        LENSES.clear();
    }

    /**
     * 记录一次镜片渲染（1025 {@code capture} 的 26.2 等价）：
     * <ul>
     *   <li>{@code ocular} 喂目镜几何顶点给 {@link Bounds}（pose 已套用的立方体顶点），
     *       算出镜片在屏幕上的 UV 包围盒；</li>
     *   <li>{@code reticle} 喂分划板几何顶点给 {@link ReticleBuffers}，收成
     *       POSITION_COLOR_TEX 网格；</li>
     *   <li>两者连同 config / modelView 存进 {@link Lens}，待 {@link #render} 帧末合成。</li>
     * </ul>
     *
     * @param reticleType 分划板的原 RenderType（贴图从它解析）
     * @return true 表示已接管分划板 —— 调用方应跳过常规分划绘制；false 走原路径
     */
    public static boolean capture(
            ScopeLensConfig config,
            RenderType reticleType,
            Consumer<VertexConsumer> ocular,
            Consumer<ReticleBuffers> reticle,
            float pivotX,
            float pivotY,
            float pivotZ
    ) {
        boolean expLens = RenderConfig.EXPERIMENTAL_SCOPE_LENS.get();
        boolean isShadow = IrisCompat.isRenderShadow();
        if (config == null || reticleType == null) {
            return false;
        }
        if (!expLens) {
            return false;
        }
        // 【2026-11-04 恢复接管】此前判「主画面拷贝在 26.2 从未成功」是错的：
        // 实测 main 的 color texture usage=15（含 USAGE_COPY_SRC），
        // copyTextureToTexture 全屏拷贝读回 100% 非黑 —— 拷贝链路本身可用。
        // 黑框已由 scope_lens.fsh 的「镜外 distance<=0 → discard」消除，
        // 故恢复实验镜头（含夜视/热成像）接管。
        if (isShadow) {
            return false;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            return false;
        }
        // 1025 的 rad = 80 × scopeViewRadiusModifier × aimingProgress：进度为 0 时
        // capture 直接放弃、分划板走常规绘制。同义门禁。
        float aim = currentAimingProgress();
        if (aim <= 0.001F) {
            return false;
        }
        RenderTarget main = mc.gameRenderer.mainRenderTarget();
        if (main == null || main.width <= 0 || main.height <= 0) {
            return false;
        }

        Matrix4f modelView = RenderSystem.getModelViewMatrixCopy();
        Matrix4f transform = hudProjectionMatrix(mc).mul(modelView);
        Bounds bounds = new Bounds(transform);
        ocular.accept(bounds);
        if (!bounds.isValid()) {
            return false;
        }

        // 【镜片窗口 · 严格 1:1 复刻官方 1.2.0 buildSurface 正圆】
        //
        // 官方 1.2.0 的镜片窗口由 buildSurface 画的【正圆】定义，与目镜 cube 几何无关：
        //   圆心 = 目镜节点 pivot 的屏幕投影（getBedrockPartCenter → viewPosition）
        //   半径 = 80 × scopeViewRadiusModifier × aimingProgress（在「×16×90」参考像素空间）
        // 官方注释明说「80 是一个随便找的大小合适的数值」——其本质是绕开目镜模型里
        // 各种旋转轴偏移的斜切镜框片（AMG10 的 ocular_scope_2 有 4 片 pivot 偏离镜片中心
        // y=+5.6/z=+11 的斜切片，会把目镜 AABB 垂直撑到 7 倍），用节点 pivot + 固定经验
        // 半径得到干净的正圆。
        //
        // 26.2 无 stencil，窗口形状由 scope_lens.fsh 的「正圆门限」承担。这里必须复刻
        // 官方语义，而不是用被污染的目镜 AABB：
        //   center = pivot 的屏幕 UV（bounds.addPivot 投影，与 cube 顶点同一 transform）
        //   radius = 官方 80px 换算到 UV：80/(16×90) 方块 × uvPerBlock
        //            （uvPerBlock = pivot 处「1 模型方块」投影成多少 UV，由 addRadiusProbe 量出）
        bounds.addPivot(pivotX, pivotY, pivotZ);
        float centerU;
        float centerV;
        // 【镜片正圆圆心 · 官方 getBedrockPartCenter 的 1:1 等价 + retro_2x 修正】
        //
        // 官方圆心 = 目镜节点 pivot 的 x/y 投影，z 固定 -90（丢弃深度），
        // 所以对「节点 pivot x/y == 镜片本体中心 x/y」的模型（绝大多数）都正确，
        // 即使 pivot 的 z 与镜片本体差很远（retro_2x 差 8 方块）也不受影响。
        //
        // 26.2 这里 addPivot 用 pivot 的【完整 3D 坐标】做透视投影，把 pivot 的真实
        // 深度带进了投影：retro_2x 的 ocular 节点 pivot z=-6.375，而镜片本体 cube 在
        // z=1.8125，pivot 投影被推到镜筒后方深处对应的屏幕位置 → 正圆圆心错位 → 溢出。
        //
        // 官方丢弃 z 的净效果 = 「pivot 的 x/y 方向投影到镜片本体深度」≈ 镜片本体的
        // 屏幕中心 = AABB 中心。但 AABB 中心会被 AMG10 的 4 片斜切镜框片（pivot 偏离
        // y=+5.6/z=+11）撑偏，所以不能无条件用 AABB 中心。
        //
        // 判别：pivot 投影若落在目镜 AABB 内，说明 pivot 的 z 与镜片本体一致（正常/
        // AMG10 情况），沿用 pivot（规避斜切片污染）；若落在 AABB 外（retro_2x 的
        // pivot z 错位），回退用 AABB 中心（retro_2x 无斜切片，AABB 就是本体投影）。
        float aabbCenterU = (bounds.minU + bounds.maxU) * 0.5F;
        float aabbCenterV = (bounds.minV + bounds.maxV) * 0.5F;
        if (bounds.hasPivot() && bounds.containsPivot()) {
            centerU = bounds.pivotU;
            centerV = bounds.pivotV;
        } else if (bounds.hasPivot()) {
            // pivot 投影落在 AABB 外（z 错位，如 retro_2x）：用 AABB 中心。
            centerU = aabbCenterU;
            centerV = aabbCenterV;
        } else {
            centerU = aabbCenterU;
            centerV = aabbCenterV;
        }

        // 【镜片窗口半径 · 官方固定 80 的严格 1:1 等价（不依赖目镜 AABB）】
        //
        // 官方 1.2.0 buildSurface 画的正圆，顶点视图空间 = (view.x*16*90 + 80cos, ...,
        // view.y*16*90 + 80sin, -90)，其中「×16×90」与「z=-90」是一套自洽的参考像素空间。
        // 圆心 NDC = f × view.x × 16（官方把标准透视的 1/(-view.z) 用固定 ×16 近似，
        // 即假设目镜在固定深度 1/16 方块处），半径 NDC = f × 80/90。
        // 故屏幕半径（像素）= f × (80/90) × (height/2)，其中 f = 1/tan(hudFov/2)。
        //   1. 【只依赖 hudFov 与屏幕高度，不依赖目镜 AABB】—— FMP13 方形镜片、AMG10 斜切片
        //      都不会污染半径，杜绝「AABB 半宽×√2」那种 44~397px 抖动与溢出。
        //   2. 【大圆兜底】该值 ≈ 0.889×height/2（fov70 约 948px），远大于目镜本体
        //      （REAP-IR 约 194px、FMP13 方形约 614px），正圆盖住镜片本体不裁剪；
        //      又远小于 AMG10 斜切片投影（3581px 超屏），斜切片被裁掉。
        //   3. 【不透视抖动】深度用官方固定值，与 pivot 实际深度无关，左右移动不缩放。
        float hudFov = mc.gameRenderer.gameRenderState().levelRenderState.cameraRenderState.hudFov;
        float fovScale = 1.0F / (float) Math.tan(Math.toRadians(hudFov) / 2.0);
        float radiusPx = fovScale * (80.0F / 90.0F) * (main.height / 2.0F) * aim;
        if (radiusPx <= 0.0F || !Float.isFinite(radiusPx)) {
            radiusPx = main.height / 2.0F; // 兜底：极端态给「半屏高」这个安全大值。
        }

        int slot = LENSES.size();
        Capture capture = prepareCapture(slot, main.width, main.height, main.getColorTexture());
        capture.reticle.reset(reticleType);
        try {
            reticle.accept(capture.reticle);
        } catch (RuntimeException e) {
            capture.reticle.discard();
            throw e;
        }
        capture.reticle.finish();

        LENSES.add(new Lens(capture, config, centerU, centerV, radiusPx, modelView));
        return true;
    }

    private static Capture prepareCapture(int slot, int width, int height, GpuTexture mainColor) {
        while (CAPTURES.size() <= slot) {
            CAPTURES.add(null);
        }
        Capture capture = CAPTURES.get(slot);
        if (capture != null && capture.target != null
                && capture.target.width == width && capture.target.height == height
                && capture.format == mainColor.getFormat()) {
            return capture;
        }
        if (capture == null) {
            capture = new Capture();
            CAPTURES.set(slot, capture);
        }
        capture.recreate(width, height, mainColor.getFormat());
        return capture;
    }

    /**
     * 帧末合成：对每个 Lens 把主画面拷一份、叠加分划板、做镜头后处理
     * （畸变/色差/晕影/夜视/热成像），全屏三角形写回主渲染目标。
     *
     * <p>挂在 {@code GameRenderer#renderLevel} 的 RETURN：世界、手部与屏幕效果
     * 均已完成，主画面已是本帧终态；此处拷贝与写回对应 1025 的帧末镜头合成，
     * 不会再把中场的不完整画面覆盖到镜外区域。</p>
     */
    public static void render() {
        if (LENSES.isEmpty()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        RenderTarget main = mc.gameRenderer.mainRenderTarget();
        if (main == null || main.width <= 0 || main.height <= 0) {
            beginFrame();
            return;
        }
        int width = main.width;
        int height = main.height;

        for (Lens lens : LENSES) {
            drawLens(lens, main, width, height);
        }

        beginFrame();
    }

    private static void drawLens(Lens lens, RenderTarget main, int width, int height) {
        try {
            GpuTexture mainColor = main.getColorTexture();
            if (mainColor == null || mainColor.isClosed()) {
                return;
            }
            TextureTarget capture = lens.capture.target;
            if (capture == null || capture.width != width || capture.height != height) {
                return;
            }

            // 1. 主画面 → capture target（1025 glBlitFrameBuffer 的等价）。
            //    必须拷副本：合成 pass 同时读主画面、写主 target，同一 pass 读写同一纹理
            //    是未定义行为。
            CommandEncoder copyEncoder = RenderSystem.getDevice().createCommandEncoder();
            copyEncoder.copyTextureToTexture(mainColor, capture.getColorTexture(), 0, 0, 0, 0, 0, width, height);
            // copyTextureToTexture 只把拷贝命令编码进 backend，必须显式 submit()
            // 才会真正提交到 GPU 队列执行；不 submit 的话 capture 纹理一直是空的，
            // 镜内画面（夜视/热成像/普通镜的畸变采样源）就是黑。这是黑框的真正根因。
            copyEncoder.submit();

            // 2. 镜片形状门限（1025 stencil 的等价数据源）。
            //
            //    直接用当帧目镜掩码（ScopeMaskRenderer 在阶段边界用目镜几何本身
            //    画出来的 R=1 覆盖区），不再烘距离场：那次改造要多 13 个全屏 pass，
            //    还把 gl_FragCoord（左下原点）与 texelFetch（左上原点）混用，实测把
            //    整个包围盒判成「全在镜内」—— 镜片变方形、镜外一圈黑框。
            //
            //    掩码只在「本帧确实画成了」时可用（hasMaskThisFrame）：没画时纹理会
            //    全是黑的，拿来当门限会把整个镜片裁掉。此时退回内切椭圆近似。
            GpuTextureView maskView = null;
            RenderTarget maskTarget = ScopeMaskTarget.current();
            if (maskTarget != null && ScopeMaskRenderer.hasMaskThisFrame()) {
                maskView = maskTarget.getColorTextureView();
            }

            writeLensUbo(lens, maskView, width, height);
            GpuTextureView contourTex = maskView != null ? maskView : capture.getColorTextureView();

            // 3. 分划板画进 capture target（叠加在拷贝之上，随后被镜头 shader 采样、
            //    与场景一起受畸变 —— 1025 语义）。自带镜内门限：1025 的分划板被
            //    stencil 裁在镜片区域内（分划子树含远离目镜的遮光板），等价裁剪在
            //    scope_reticle.fsh 的掩码门限里。
            lens.capture.reticle.drawInto(capture, lens.modelView, contourTex, lensUbo().slice());

            CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
            try (RenderPass pass = encoder.createRenderPass(
                    () -> "tacz_scope_lens",
                    main.getColorTextureView(),
                    Optional.empty())) {
                // 26.2 的 RenderPass 会继承当前的裁剪矩形（1025 对应位置是
                // GlStateManager._enableScissorTest + 显式 enableScissor(镜片框)）。
                // 手部阶段残留的 scissor 会把整块合成画面裁掉 —— 表现为 pass 不报错、
                // 但屏幕上 0 像素。这里显式关掉，镜内门限已由 scope_lens.fsh 的掩码承担。
                pass.disableScissor();
                pass.setPipeline(lensPipeline());
                RenderSystem.bindDefaultUniforms(pass);
                pass.bindTexture("Sampler0", capture.getColorTextureView(),
                        RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
                GpuTextureView thermal = ScopeThermalRenderer.textureOr(capture.getColorTextureView(), width, height);
                pass.bindTexture("Sampler1", thermal,
                        RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
                pass.bindTexture("Sampler2", contourTex,
                        RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
                pass.setUniform("LensUniforms", lensUbo().slice());
                // 镜片包围盒 quad：两个三角形（scope_lens_quad.vsh），
                // 逐帧像素工作量与 1025 的模型空间镜盘 + stencil 同级。
                pass.draw(6, 1, 0, 0);
            }
        } catch (Exception e) {
            GunMod.LOGGER.error("[TACZ Scope] Failed to render experimental scope lens", e);
        }
    }

    /** 把 Lens 的参数写进 LensUniforms UBO（std140 布局，见 scope_lens.fsh）。 */
    private static void writeLensUbo(Lens lens, GpuTextureView mask, int width, int height) {
        ScopeLensConfig config = lens.config;
        try (GpuBufferSlice.MappedView view = lensUbo().map(0, LENS_UBO_SIZE, false, true)) {
            ByteBuffer data = view.data();
            data.putFloat(lens.centerU).putFloat(lens.centerV);       // LensCenter
            data.putFloat(lens.radiusU).putFloat(lens.radiusV);       // LensRadius
            data.putInt(mask != null ? 1 : 0);                       // ContourReady（1 = 目镜掩码可用）
            data.putFloat(config.getDistortion());                    // Distortion
            data.putFloat(config.getAberration());                    // Aberration
            data.putFloat(config.getEdgeWidth());                     // EdgeWidth
            data.putFloat(config.getVignetteStrength());              // VignetteStrength
            data.putFloat(config.getVignetteWidth());                 // VignetteWidth
            // 距离场已移除，ContourSize/ContourRect 在着色器里不再被读取；
            // 仍按 std140 占位把偏移推到 LensTint（64），避免整体错位。
            data.putFloat(1.0f).putFloat(1.0f);                       // ContourSize（占位）
            data.putFloat(0.0f).putFloat(0.0f).putFloat(1.0f).putFloat(1.0f); // ContourRect（占位）
            // LensTint：vec3。
            //
            // 【std140 陷阱 · 2026-11-04 实测修正】vec3 的基对齐是 16，但<b>只占 12 字节</b>；
            // 紧跟着的标量成员的基对齐是 4，所以下一个 int 落在 <b>偏移 76</b>，不是 80。
            // 这里曾多写一个 float 当「vec3 的 std140 填充」，于是 VisionMode 及其后的
            // ThermalReady / VisionGain / VisionNoise / SensorTime 整体错位 4 字节 ——
            // 着色器读到的 VisionMode 永远是 0（NORMAL），夜视/热成像因此从未生效。
            int tint = config.getTint();
            float strength = config.getTintStrength();
            data.putFloat(1.0F + ((tint >> 16 & 0xFF) / 255.0F - 1.0F) * strength);
            data.putFloat(1.0F + ((tint >> 8 & 0xFF) / 255.0F - 1.0F) * strength);
            data.putFloat(1.0F + ((tint & 0xFF) / 255.0F - 1.0F) * strength);
            ScopeVisionMode mode = config.getVisionMode();
            if (mode.isThermal() && !RenderConfig.SCOPE_THERMAL_ENABLE.get()) {
                mode = ScopeVisionMode.NORMAL;
            }
            int modeOrdinal = mode.ordinal();
            data.putInt(modeOrdinal);                                // VisionMode
            boolean thermalReady = ScopeThermalRenderer.isReady(width, height);
            data.putInt(thermalReady ? 1 : 0);                       // ThermalReady
            data.putFloat(config.getVisionGain());                   // VisionGain
            data.putFloat(config.getVisionNoise());                  // VisionNoise
            data.putFloat((float) (Util.getMillis() % 60000L) / 1000.0F); // SensorTime
        }
    }

    public static void close() {
        beginFrame();
        ScopeThermalRenderer.close();
        for (Capture capture : CAPTURES) {
            if (capture != null) {
                capture.destroy();
            }
        }
        CAPTURES.clear();
        if (lensUbo != null) {
            lensUbo.close();
            lensUbo = null;
        }
        lensPipeline = null;
        reticlePipeline = null;
    }

    /** 当前开镜进度（与 ScopeThermalRenderer.enabled 同口径）。 */
    private static float currentAimingProgress() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return 0.0F;
        }
        return IClientPlayerGunOperator.fromLocalPlayer(mc.player)
                .getClientAimingProgress(mc.getDeltaTracker().getGameTimeDeltaPartialTick(false));
    }

    /**
     * 手部渲染所用的投影矩阵（CPU 侧重建）。
     *
     * <p>{@code GameRenderer#renderLevel} 在调 {@code renderItemInHand} 前把投影切成
     * {@code hudProjection.setupPerspective(0.05, 100, hudFov, w, h)}
     * （偏移 428-481 字节码实读），目镜/分划板几何都画在这个投影下。
     * {@code Projection} 是纯值对象，同参重建得到同一矩阵。</p>
     */
    private static Matrix4f hudProjectionMatrix(Minecraft mc) {
        Projection projection = new Projection();
        projection.setupPerspective(
                0.05F,
                100.0F,
                mc.gameRenderer.gameRenderState().levelRenderState.cameraRenderState.hudFov,
                mc.getWindow().getWidth(),
                mc.getWindow().getHeight());
        return projection.getMatrix(new Matrix4f());
    }

    // ---- 内部类 ----

    /**
     * 喂目镜几何顶点，用 projection×modelView 投影到屏幕空间，算出镜片的 UV 包围盒
     * （1025 Bounds 同构；26.2 的 {@code VertexConsumer} 是 addVertex 起链的立即式）。
     */
    private static final class Bounds implements VertexConsumer {
        final Matrix4f transform;
        final Vector4f point = new Vector4f();
        float minU = Float.POSITIVE_INFINITY;
        float minV = Float.POSITIVE_INFINITY;
        float maxU = Float.NEGATIVE_INFINITY;
        float maxV = Float.NEGATIVE_INFINITY;
        boolean behindCamera;
        // 目镜节点 pivot 的屏幕投影（与 cube 顶点同一 transform，单独记录，不参与 AABB）。
        float pivotU = Float.NaN;
        float pivotV = Float.NaN;

        Bounds(Matrix4f transform) {
            this.transform = transform;
        }

        /**
         * 记录目镜节点 pivot（视图空间坐标）的屏幕投影。与 {@link #addVertex} 用同一个
         * transform，但单独存进 pivotU/pivotV，不污染 AABB（pivot 是镜片几何中心，官方
         * buildSurface 用它做圆心）。
         */
        void addPivot(float x, float y, float z) {
            this.point.set(x, y, z, 1.0F).mul(this.transform);
            if (this.point.w > 1.0E-4F && Float.isFinite(this.point.w)) {
                this.pivotU = this.point.x / this.point.w * 0.5F + 0.5F;
                this.pivotV = this.point.y / this.point.w * 0.5F + 0.5F;
            }
        }

        boolean hasPivot() {
            return Float.isFinite(this.pivotU) && Float.isFinite(this.pivotV);
        }

        /**
         * 判别 pivot 的屏幕投影是否落在目镜几何 AABB 内（含少量容差）。
         *
         * <p>用于区分「pivot 深度正确」（投影在镜片本体附近，正常 / AMG10）与
         * 「pivot 深度错位」（投影跑到镜筒后方，retro_2x 的 ocular 节点 pivot z 与
         * 镜片本体差 8 方块）。落在 AABB 内才敢把 pivot 当正圆圆心。</p>
         */
        boolean containsPivot() {
            if (!hasPivot()) {
                return false;
            }
            float tolU = Math.max(this.maxU - this.minU, 1.0E-3F) * 0.5F;
            float tolV = Math.max(this.maxV - this.minV, 1.0E-3F) * 0.5F;
            return this.pivotU >= this.minU - tolU && this.pivotU <= this.maxU + tolU
                    && this.pivotV >= this.minV - tolV && this.pivotV <= this.maxV + tolV;
        }

        /**
         * 量出 pivot 处「1 个模型方块」投影成多少屏幕 UV（沿 x 轴）。
         *
         * <p>官方 1025 的窗口半径是固定的 80px（在 ×16×90 参考像素空间），要把它换算到
         * 26.2 的 UV 空间，需要知道「方块 → UV」的比例。这里在 pivot 和 pivot+(1,0,0)
         * 各投影一次，两点 UV 的欧氏距离即 uvPerBlock。用它乘 80/1440 方块，得到与官方
         * 逐像素等价的屏幕半径。</p>
         *
         * @return pivot 处每模型方块的 UV 长度；量不到（相机背后）返回 NaN
         */
        float uvPerBlock(float x, float y, float z) {
            float u0;
            float v0;
            if (hasPivot()) {
                u0 = this.pivotU;
                v0 = this.pivotV;
            } else {
                this.point.set(x, y, z, 1.0F).mul(this.transform);
                if (!(this.point.w > 1.0E-4F) || !Float.isFinite(this.point.w)) {
                    return Float.NaN;
                }
                u0 = this.point.x / this.point.w * 0.5F + 0.5F;
                v0 = this.point.y / this.point.w * 0.5F + 0.5F;
            }
            this.point.set(x + 1.0F, y, z, 1.0F).mul(this.transform);
            if (!(this.point.w > 1.0E-4F) || !Float.isFinite(this.point.w)) {
                return Float.NaN;
            }
            float u1 = this.point.x / this.point.w * 0.5F + 0.5F;
            float v1 = this.point.y / this.point.w * 0.5F + 0.5F;
            float du = u1 - u0;
            float dv = v1 - v0;
            return (float) Math.sqrt(du * du + dv * dv);
        }

        boolean isValid() {
            return !this.behindCamera
                    && Float.isFinite(this.minU)
                    && Float.isFinite(this.minV)
                    && Float.isFinite(this.maxU)
                    && Float.isFinite(this.maxV)
                    && this.maxU - this.minU > 1.0E-5F
                    && this.maxV - this.minV > 1.0E-5F;
        }

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            this.point.set(x, y, z, 1.0F).mul(this.transform);
            if (this.point.w > 1.0E-4F && Float.isFinite(this.point.w)) {
                float u = this.point.x / this.point.w * 0.5F + 0.5F;
                float v = this.point.y / this.point.w * 0.5F + 0.5F;
                this.minU = Math.min(this.minU, u);
                this.maxU = Math.max(this.maxU, u);
                this.minV = Math.min(this.minV, v);
                this.maxV = Math.max(this.maxV, v);
            } else {
                this.behindCamera = true;
            }
            return this;
        }

        @Override
        public VertexConsumer setColor(int r, int g, int b, int a) {
            return this;
        }

        @Override
        public VertexConsumer setColor(int packed) {
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            return this;
        }

        @Override
        public VertexConsumer setLineWidth(float width) {
            return this;
        }
    }

    /**
     * 分划板顶点收集（1025 ReticleBuffers 的 26.2 等价）：position + uv + 颜色收进
     * POSITION_COLOR_TEX 的 QUADS 缓冲，帧末画进 capture target。
     *
     * <p>顶点由调用方以 {@code cube.compile(pose, ...)} 直接写入
     * （{@link ReticleSink} 做 addVertex 起链的属性归拢）。</p>
     */
    public static final class ReticleBuffers {
        private final ByteBufferBuilder scratch = new ByteBufferBuilder(4096);
        private BufferBuilder builder;
        private ReticleSink sink;
        private RenderType type;

        ReticleBuffers() {
        }

        void reset(RenderType type) {
            this.type = type;
            // 26.2 的 BufferBuilder 无 begin()：构造器即定拓扑/格式，一次性使用。
            this.builder = new BufferBuilder(scratch, PrimitiveTopology.QUADS, DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP);
            this.sink = new ReticleSink(this.builder);
        }

        /** 给调用方的顶点入口（1025 里是 buffers.getBuffer(type)）。 */
        public VertexConsumer vertexConsumer() {
            return this.sink;
        }

        /** 调用方写完分划板顶点后调用：落盘最后一个顶点（跨包调用，须 public）。 */
        public void finish() {
            if (this.sink != null) {
                this.sink.finish();
            }
        }

        void discard() {
            if (builder != null) {
                try {
                    MeshData mesh = builder.build();
                    if (mesh != null) {
                        mesh.close();
                    }
                } catch (Exception ignored) {
                }
                builder = null;
            }
        }

        /**
         * 把分划板网格画进 capture target（叠加在主画面拷贝之上）。
         * 贴图取自原 RenderType；顶点变换用 capture 时的 ModelView。
         */
        void drawInto(TextureTarget target, Matrix4f modelView, GpuTextureView contourView, GpuBufferSlice lensUbo) {
            if (builder == null || type == null) {
                return;
            }
            MeshData mesh;
            try {
                mesh = builder.buildOrThrow();
            } catch (Exception e) {
                GunMod.LOGGER.error("[TACZ Scope] Failed to build reticle mesh", e);
                builder = null;
                return;
            }
            builder = null;
            try (mesh) {
                MeshData.DrawState draw = mesh.drawState();
                if (draw.indexCount() == 0) {
                    return;
                }
                GpuBuffer vertexBuffer = RenderSystem.getDevice().createBuffer(
                        () -> "tacz_scope_reticle_vb",
                        GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_COPY_DST, mesh.vertexBuffer());
                try {
                    RenderSystem.AutoStorageIndexBuffer indices =
                            RenderSystem.getSequentialBuffer(draw.primitiveTopology());
                    GpuBuffer indexBuffer = indices.getBuffer(draw.indexCount());
                    CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
                    try (RenderPass pass = encoder.createRenderPass(
                            () -> "tacz_scope_reticle",
                            target.getColorTextureView(),
                            Optional.empty())) {
                        pass.disableScissor();
                        pass.setPipeline(reticlePipeline());
                        RenderSystem.bindDefaultUniforms(pass);
                        pass.setUniform("DynamicTransforms",
                                RenderSystem.getDynamicUniforms().writeTransform(modelView, WHITE));
                        pass.setUniform("LensUniforms", lensUbo);
                        bindTypeTexture(pass);
                        pass.bindTexture("Sampler1", contourView,
                                RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
                        pass.setVertexBuffer(0, vertexBuffer.slice());
                        pass.setIndexBuffer(indexBuffer, indices.type());
                        pass.drawIndexed(draw.indexCount(), 1, 0, 0, 0);
                    }
                } finally {
                    vertexBuffer.close();
                }
            } catch (Exception e) {
                GunMod.LOGGER.error("[TACZ Scope] Failed to draw reticle into capture target", e);
            }
        }

        private void bindTypeTexture(RenderPass pass) {
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
            // lightmap：1025 用原 RenderType.setupRenderState 的实体管线自带光照；
            // 这里由 scope_reticle.vsh 的 sample_lightmap(Sampler2, UV2) 承担（同 scope_body 约定）。
            pass.bindTexture("Sampler2", Minecraft.getInstance().gameRenderer.lightmap(),
                    RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
        }
    }

    private static final Vector4f WHITE = new Vector4f(1f, 1f, 1f, 1f);

    /**
     * 分划板顶点归拢：26.2 {@code VertexConsumer} 的 addVertex 起链语义下，
     * 缓存一个顶点的全部属性后落进 builder（保留颜色，1025 分划板带色同义）。
     */
    private static final class ReticleSink implements VertexConsumer {
        private final BufferBuilder out;
        private boolean pending;
        private float x;
        private float y;
        private float z;
        private float u;
        private float v;
        private int lu;
        private int lv;
        private boolean packedMode;
        private int packed;
        private int r = 255;
        private int g = 255;
        private int b = 255;
        private int a = 255;

        ReticleSink(BufferBuilder out) {
            this.out = out;
        }

        private void flushPending() {
            if (pending) {
                pending = false;
                VertexConsumer vertex = out.addVertex(x, y, z);
                if (packedMode) {
                    vertex.setColor(packed);
                } else {
                    vertex.setColor(r, g, b, a);
                }
                vertex.setUv(u, v).setUv2(lu, lv);
            }
        }

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            flushPending();
            this.x = x;
            this.y = y;
            this.z = z;
            this.u = 0f;
            this.v = 0f;
            this.lu = 0;
            this.lv = 0;
            this.r = 255;
            this.g = 255;
            this.b = 255;
            this.a = 255;
            this.packedMode = false;
            this.pending = true;
            return this;
        }

        @Override
        public VertexConsumer setColor(int r, int g, int b, int a) {
            if (pending) {
                this.packedMode = false;
                this.r = r;
                this.g = g;
                this.b = b;
                this.a = a;
            }
            return this;
        }

        @Override
        public VertexConsumer setColor(int packed) {
            if (pending) {
                this.packedMode = true;
                this.packed = packed;
            }
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            if (pending) {
                this.u = u;
                this.v = v;
            }
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            if (pending) {
                this.lu = u;
                this.lv = v;
            }
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            return this;
        }

        @Override
        public VertexConsumer setLineWidth(float width) {
            return this;
        }

        /** 调用方写完一批顶点后调用：落盘最后一个顶点。 */
        void finish() {
            flushPending();
        }
    }

    /** 一个镜片的帧数据（1025 Lens 同构）。 */
    private static final class Lens {
        final Capture capture;
        final ScopeLensConfig config;
        final float centerU;
        final float centerV;
        /** 镜片窗口正圆半径（UV 空间）。官方 1025 窗口是正圆，radiusU == radiusV。 */
        final float radiusU;
        final float radiusV;
        final Matrix4f modelView;

        Lens(Capture capture, ScopeLensConfig config, float centerU, float centerV,
             float radius, Matrix4f modelView) {
            this.capture = capture;
            this.config = config;
            this.centerU = centerU;
            this.centerV = centerV;
            // 正圆：UV 半径在 x/y 方向相等（官方 buildSurface 的 cos/sin 同一半径）。
            this.radiusU = radius;
            this.radiusV = radius;
            this.modelView = new Matrix4f(modelView);
        }
    }

    /** capture 槽：离屏 target + 分划板缓冲，跨帧按槽复用（1025 Capture 同构）。 */
    private static final class Capture {
        TextureTarget target;
        GpuFormat format;
        final ReticleBuffers reticle = new ReticleBuffers();

        void recreate(int width, int height, GpuFormat format) {
            destroy();
            this.target = new TextureTarget("tacz_scope_lens_capture", width, height, false, format);
            this.format = format;
        }

        void destroy() {
            if (target != null) {
                target.destroyBuffers();
                target = null;
            }
            reticle.discard();
        }
    }
}
