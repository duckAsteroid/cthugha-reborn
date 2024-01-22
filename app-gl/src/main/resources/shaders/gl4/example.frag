#version 330

precision mediump float;

uniform sampler2D background;
uniform float millis;

in vec2 texCoord;

void main() {
  vec2 newPos = texCoord;

  newPos = newPos + (sin(newPos * 12.)/12.) * (sin(millis/800.)/2. + 0.5);
  // read colour from image
  vec4 col = texture2D(background, newPos);

  gl_FragColor = col;
}
