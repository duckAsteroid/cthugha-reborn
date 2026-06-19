#version 330 core
in vec2 texCoords;
out vec4 fragColor;

uniform sampler2D tex;      // palette-indexed buffer (R8: 0.0-1.0 maps to index 0-255)
uniform sampler2D palette;  // 256x1 RGBA colour lookup table

void main() {
    float index = texture(tex, texCoords).r;
    fragColor = texture(palette, vec2(index, 0.5));
}
