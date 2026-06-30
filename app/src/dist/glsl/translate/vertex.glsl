#version 330 core
in vec2 screenPosition;
in vec2 texturePosition;
out vec2 texCoords;

void main() {
    texCoords = texturePosition;
    gl_Position = vec4(screenPosition, 0.0, 1.0);
}