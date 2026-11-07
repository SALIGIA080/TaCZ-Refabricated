package com.tacz.guns.mixin.client.ar;

import com.github.argon4w.acceleratedrendering.core.buffers.accelerated.builders.IAcceleratedVertexConsumer;
import com.github.argon4w.acceleratedrendering.core.buffers.accelerated.builders.IBufferGraph;
import com.github.argon4w.acceleratedrendering.core.buffers.accelerated.builders.VertexConsumerExtension;
import com.github.argon4w.acceleratedrendering.core.buffers.accelerated.renderers.IAcceleratedRenderer;
import com.github.argon4w.acceleratedrendering.core.meshes.IMesh;
import com.github.argon4w.acceleratedrendering.core.meshes.collectors.CulledMeshCollector;
import com.github.argon4w.acceleratedrendering.features.entities.AcceleratedEntityRenderingFeature;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.tacz.guns.client.model.bedrock.BedrockCube;
import com.tacz.guns.client.model.bedrock.BedrockPart;
import com.tacz.guns.client.model.bedrock.UnshadedVertexConsumer;
import com.tacz.guns.compat.ar.ARCompat;
import com.tacz.guns.compat.ar.BedrockPartLightingMode;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectList;
import net.minecraft.util.FastColor;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;

@Mixin(BedrockPart.class)
public class BedrockPartMixin implements IAcceleratedRenderer<BedrockPartLightingMode> {

    @Unique
    private static final PoseStack.Pose POSE = new PoseStack().last();

    @Unique
    private final Map<IBufferGraph, IMesh> meshes = new Object2ObjectOpenHashMap<>();

    /** 0929：无光照材质的独立网格缓存（与有光照材质分开，避免顶点法线互相污染）。 */
    @Unique
    private final Map<IBufferGraph, IMesh> unshadedMeshes = new Object2ObjectOpenHashMap<>();

    @Shadow(remap = false)
    @Final
    public ObjectList<BedrockCube> cubes;

    @Inject(
            method = "compile",
            at = @At("HEAD"),
            cancellable = true,
            remap = false
    )
    public void compileFast(
            PoseStack.Pose pose,
            VertexConsumer consumer,
            int texU,
            int texV,
            float red,
            float green,
            float blue,
            float alpha,
            CallbackInfo ci
    ) {
        // 0929：无光照材质（UnshadedVertexConsumer 包裹）时切到 UNSHADED 模式，
        // 用其自定义法线矩阵与底层 delegate 走加速渲染。
        BedrockPartLightingMode lightingMode = BedrockPartLightingMode.SHADED;
        VertexConsumer acceleratedConsumer = consumer;
        Matrix3f normalMatrix = pose.normal();
        if (consumer instanceof UnshadedVertexConsumer unshadedConsumer) {
            lightingMode = BedrockPartLightingMode.UNSHADED;
            acceleratedConsumer = unshadedConsumer.getDelegate();
            normalMatrix = unshadedConsumer.createNormalMatrix();
        }

        IAcceleratedVertexConsumer extension = VertexConsumerExtension.getAccelerated(acceleratedConsumer);

        if (ARCompat.shouldAccelerate() && extension.isAccelerated()) {
            ci.cancel();

            int color = FastColor.ARGB32.color(
                    (int) (alpha * 255.0f),
                    (int) (red * 255.0f),
                    (int) (green * 255.0f),
                    (int) (blue * 255.0f)
            );

            extension.doRender(
                    this,
                    lightingMode,
                    pose.pose(),
                    normalMatrix,
                    texU,
                    texV,
                    color
            );
        }
    }

    @Unique
    @Override
    public void render(
            VertexConsumer consumer,
            BedrockPartLightingMode lightingMode,
            Matrix4f transform,
            Matrix3f normal,
            int light,
            int overlay,
            int color
    ) {
        IAcceleratedVertexConsumer extension = VertexConsumerExtension.getAccelerated(consumer);
        // 0929：按光照模式选择独立网格缓存
        Map<IBufferGraph, IMesh> meshCache = lightingMode == BedrockPartLightingMode.UNSHADED ? unshadedMeshes : meshes;
        IMesh mesh = meshCache.get(extension);

        extension.beginTransform(transform, normal);

        if (mesh != null) {
            mesh.write(extension, color, light, overlay);
            extension.endTransform();
            return;
        }

        CulledMeshCollector culledMeshCollector = new CulledMeshCollector(extension);
        VertexConsumer meshBuilder = extension.decorate(culledMeshCollector);
        // 0929：无光照模式固定法线，避免光照影响
        if (lightingMode == BedrockPartLightingMode.UNSHADED) {
            meshBuilder = UnshadedVertexConsumer.wrapFixedNormal(meshBuilder);
        }

        for (BedrockCube cube : cubes) {
            cube.compile(
                    POSE,
                    meshBuilder,
                    0,
                    overlay,
                    1.0f,
                    1.0f,
                    1.0f,
                    1.0f
            );
        }

        culledMeshCollector.flush();

        mesh = AcceleratedEntityRenderingFeature
                .getMeshType()
                .getBuilder()
                .build(culledMeshCollector);

        meshCache.put(extension, mesh);
        mesh.write(extension, color, light, overlay);

        extension.endTransform();
    }
}
