#version 330

#moj_import <minecraft:globals.glsl>

uniform sampler2D Sampler0;
uniform sampler2D Sampler1;
uniform sampler2D Sampler2;

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

out vec4 fragColor;

vec2 clampUv(vec2 uv) {
    vec2 halfTexel = 0.5 / ScreenSize;
    return clamp(uv, halfTexel, 1.0 - halfTexel);
}

float luminance(vec3 color) {
    return dot(color, vec3(0.2126, 0.7152, 0.0722));
}

float grain() {
    vec2 pixel = floor(gl_FragCoord.xy);
    float frame = floor(SensorTime * 30.0);
    return fract(sin(dot(pixel, vec2(12.9898, 78.233)) + frame * 37.719) * 43758.5453) - 0.5;
}

vec3 ironbow(float heat) {
    vec3 cold = vec3(0.015, 0.01, 0.06);
    vec3 purple = vec3(0.30, 0.025, 0.48);
    vec3 red = vec3(0.90, 0.10, 0.035);
    vec3 yellow = vec3(1.0, 0.82, 0.12);
    if (heat < 0.35) return mix(cold, purple, heat / 0.35);
    if (heat < 0.65) return mix(purple, red, (heat - 0.35) / 0.30);
    if (heat < 0.85) return mix(red, yellow, (heat - 0.65) / 0.20);
    return mix(yellow, vec3(1.0), (heat - 0.85) / 0.15);
}

void main() {
    vec2 uv = gl_FragCoord.xy / ScreenSize;
    vec2 relative = uv - LensCenter;

    // ---- 镜片形状门限（1025 stencilFunc(EQUAL, ...) 的等价）----
    //
    // 【严格 1:1 复刻官方 1.2.0】官方镜片窗口 = 目镜几何 ∩ 正圆（两个 stencil 取交集）：
    //   renderOcularStencil 写目镜几何 stencil 值 i+1；
    //   renderOcularAndDivision 画正圆 stencilOp(INVERT) 在目镜几何区域翻转 → ~(i+1)；
    //   ScopeLensRenderer.capture 用 ~(i+1) 裁剪 = 目镜几何 ∩ 正圆。
    //
    // 26.2 无 stencil，等价裁剪由这里【双门限取交集】承担：
    //   ① 目镜掩码门限：Sampler2 = ScopeMaskRenderer 画的目镜几何（R=1 覆盖区）。
    //      决定镜片的本体形状（方形目镜→方形，圆形目镜→圆形）。
    //   ② 正圆门限：圆心 = 目镜节点 pivot 投影，半径 = 官方 80 语义（Java 侧换算）。
    //      裁掉目镜几何里「远离镜片中心」的斜切镜框片（AMG10 的 4 片 pivot 偏离
    //      y=+5.6/z=+11 的斜切片，投影后远离圆心，被正圆裁掉）。
    //
    // 两者必须同时满足（取交集），缺一会导致：
    //   - 只留正圆（丢掩码）→ 方形目镜四角溢出到镜外（FMP13 微型热成像的 bug）；
    //   - 只留掩码（丢正圆）→ 斜切片撑成漏斗形（早期 AMG10 的 bug）。

    // ① 目镜掩码门限（ContourReady != 0 表示 Sampler2 是本帧目镜掩码）。
    if (ContourReady != 0) {
        if (texture(Sampler2, clamp(uv, 0.0, 1.0)).r < 0.5) {
            discard;
        }
    }

    // ② 正圆门限（像素空间：gl_FragCoord.xy 各向同性，uv 各向异性会变椭圆）。
    vec2 centerPx = LensCenter * ScreenSize;
    float distPx = length(gl_FragCoord.xy - centerPx);
    float radiusPx = LensRadius.x;
    if (distPx > radiusPx) {
        discard;
    }
    // 渐晕/畸变衰减距离：像素空间归一化（distance = 1 在圆心、0 在镜缘）。
    float distance = 1.0 - clamp(distPx / max(radiusPx, 0.00001), 0.0, 1.0);

    float ramp = 0.0;
    float edge = 0.0;
    if (EdgeWidth > 0.0 || (VignetteStrength > 0.0 && VignetteWidth > 0.0)) {
        if (EdgeWidth > 0.0) {
            float width = 0.155 * EdgeWidth;
            ramp = 1.0 - smoothstep(0.0, min(width * 3.0, 1.0), distance);
        }
        if (VignetteStrength > 0.0 && VignetteWidth > 0.0) {
            float falloff = distance / (0.155 * VignetteWidth);
            edge = exp(-falloff * falloff) * VignetteStrength;
        }
    }

    // Sample in screen space so hand FOV cannot magnify the scene a second time.
    vec2 sampleUv = uv - relative * Distortion * ramp;
    // Digital sensors are monochrome before their display palette is applied.
    bool digital = VisionMode == 1 || VisionMode == 2 || (VisionMode >= 3 && ThermalReady != 0);
    vec2 split = digital ? vec2(0.0) : relative * Aberration * ramp;
    vec3 scene = vec3(
        texture(Sampler0, clampUv(sampleUv - split)).r,
        texture(Sampler0, clampUv(sampleUv)).g,
        texture(Sampler0, clampUv(sampleUv + split)).b
    );
    vec3 color = scene * LensTint;
    if (VisionMode == 1 || VisionMode == 2) {
        float signal = 1.0 - exp(-pow(max(luminance(scene), 0.0), 0.65) * VisionGain);
        signal = clamp(signal + grain() * VisionNoise, 0.0, 1.0);
        vec3 phosphor = VisionMode == 1 ? vec3(0.18, 1.0, 0.28) : vec3(0.82, 0.94, 1.0);
        color = phosphor * signal;
    } else if (VisionMode >= 3 && ThermalReady != 0) {
        float detail = clamp(luminance(scene), 0.0, 1.0);
        float target = texture(Sampler1, clampUv(sampleUv)).r;
        float heat = mix(0.06 + detail * 0.24, 0.78 + detail * 0.20, target);
        heat = clamp(heat + grain() * VisionNoise, 0.0, 1.0);
        if (VisionMode == 3) color = vec3(heat);
        else if (VisionMode == 4) color = vec3(1.0 - heat);
        else color = ironbow(heat);
    }
    fragColor = vec4(color * (1.0 - edge), 1.0);
}
