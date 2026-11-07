#version 330

#moj_import <minecraft:globals.glsl>

// 与 scope_lens.fsh 完全一致的 LensUniforms 块（std140 跨阶段共享同一 UBO）。
layout(std140) uniform LensUniforms {
    vec2 LensCenter;
    vec2 LensRadius;
    int ContourReady;
    float Distortion;
    float Aberration;
    float EdgeWidth;
    float VignetteStrength;
    float VignetteWidth;
    vec2 ContourSize;
    vec4 ContourRect;
    vec3 LensTint;
    int VisionMode;
    int ThermalReady;
    float VisionGain;
    float VisionNoise;
    float SensorTime;
};

// 镜头合成的几何：gl_VertexID 0..5 → 两个三角形覆盖镜片包围盒。
// 1025 是模型空间 90 边形镜盘 + stencil 裁剪；26.2 的效果门限在 scope_lens.fsh
// 的目镜掩码里，这里只需要把【有片段产出的面积】限制在镜片周围 —— 与源码镜盘
// 的逐帧像素工作量同级，不做全屏三角形那种无谓覆盖。
void main() {
    // 顶点序 -> 角点序：(0,1,2) + (2,1,3)，两个三角形共享对角线。
    int q = gl_VertexID == 0 ? 0
          : gl_VertexID == 1 ? 1
          : gl_VertexID == 2 ? 2
          : gl_VertexID == 3 ? 2
          : gl_VertexID == 4 ? 1
          : 3;
    vec2 corner = vec2(float(q & 1), float((q >> 1) & 1));
    // LensRadius 现在是【像素】半径（Java 侧按官方 80 语义换算，见 ScopeLensRenderer.capture），
    // 而 LensCenter 仍是 UV。quad 要覆盖「镜片正圆 + 一小圈 margin」的屏幕区域，
    // 需把像素半径换算回 UV：半宽 = radiusPx / ScreenSize.x、半高 = radiusPx / ScreenSize.y。
    vec2 radiusUv = LensRadius / ScreenSize;
    vec2 margin = max(radiusUv * 0.02, 3.0 / ScreenSize);
    vec2 minUv = LensCenter - radiusUv - margin;
    vec2 maxUv = LensCenter + radiusUv + margin;
    vec2 uv = mix(minUv, maxUv, corner);
    gl_Position = vec4(uv * 2.0 - 1.0, 0.0, 1.0);
}
