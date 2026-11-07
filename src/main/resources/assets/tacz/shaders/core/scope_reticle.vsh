#version 330

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>
#moj_import <minecraft:sample_lightmap.glsl>

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV2;

uniform sampler2D Sampler2;

out vec2 texCoord;
out vec4 vertexColor;
out vec4 lightMapColor;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    texCoord = UV0;
    vertexColor = Color;
    // 与 scope_body.vsh 同一光照采样约定（UV2 = 量化的 block/sky 光照坐标）。
    lightMapColor = sample_lightmap(Sampler2, UV2);
}
