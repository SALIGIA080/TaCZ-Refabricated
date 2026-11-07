#version 330

#moj_import <minecraft:globals.glsl>

uniform sampler2D Sampler0;
uniform sampler2D Sampler1;

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

in vec2 texCoord;
in vec4 vertexColor;
in vec4 lightMapColor;

out vec4 fragColor;

// 镜内门限（与 scope_lens.fsh 同一实现、同一数据源）。
// 1025 的分划板被 stencilFunc(EQUAL, ...) 裁在镜片区域内（分划子树含远离
// 目镜的遮光板，不裁会漏到镜外）；26.2 无 stencil，改用当帧目镜掩码直采。
void main() {
    vec2 uv = gl_FragCoord.xy / ScreenSize;
    if (ContourReady != 0) {
        // 掩码直采：R=1 即镜片通光区，形状与 1025 stencil 完全一致。
        if (texture(Sampler1, clamp(uv, 0.0, 1.0)).r < 0.5) {
            discard;
        }
    } else {
        // 正圆回退（掩码不可用时）：与 scope_lens.fsh 的正圆门限同一实现（像素空间）。
        // LensRadius.x 是像素半径（Java 侧已换算），圆心像素 = LensCenter * ScreenSize。
        vec2 centerPx = LensCenter * ScreenSize;
        float distPx = length(gl_FragCoord.xy - centerPx);
        if (distPx > LensRadius.x) {
            discard;
        }
    }
    vec4 color = texture(Sampler0, texCoord) * vertexColor * lightMapColor;
    if (color.a < 0.1) discard;
    fragColor = color;
}
