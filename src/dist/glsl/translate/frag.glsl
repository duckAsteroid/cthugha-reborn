#version 330 core
in vec2 texCoords;
out vec4 fragColor;

uniform sampler2D tex;      // source R8 texture (palette indices 0..1 mapped from 0..255)
uniform usampler2D map;     // RG16UI translation map: (srcX, srcY) as unsigned shorts per pixel
uniform vec2 dimensions;    // source texture size in pixels (set by TranslateTextureRenderer)

void main() {
    uvec2 src = texture(map, texCoords).rg;
    vec2 uv = (vec2(src) + 0.5) / dimensions;
    fragColor = texture(tex, uv);
}
