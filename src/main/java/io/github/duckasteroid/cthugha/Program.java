package io.github.duckasteroid.cthugha;

import static com.jogamp.opengl.GL2ES2.GL_FRAGMENT_SHADER;
import static com.jogamp.opengl.GL2ES2.GL_VERTEX_SHADER;

import com.jogamp.common.nio.Buffers;
import com.jogamp.opengl.GL4;
import com.jogamp.opengl.util.glsl.ShaderCode;
import com.jogamp.opengl.util.glsl.ShaderProgram;
import com.jogamp.opengl.util.texture.Texture;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.charset.StandardCharsets;

public class Program {

  public int name = 0;

  public Program(GL4 gl, String root, String vertex, String fragment) {

    ShaderCode
      vertShader = ShaderCode.create(gl, GL_VERTEX_SHADER, this.getClass(), root, null, vertex,
      "vert", null, true);
    ShaderCode fragShader =
      ShaderCode.create(gl, GL_FRAGMENT_SHADER, this.getClass(), root, null, fragment,
        "frag", null, true);

    ShaderProgram shaderProgram = new ShaderProgram();

    shaderProgram.add(vertShader);
    shaderProgram.add(fragShader);

    shaderProgram.init(gl);

    name = shaderProgram.program();

    if (!shaderProgram.link(gl, System.err)) {
      ByteBuffer buffer = Buffers.newDirectByteBuffer(2 * 1024);
      gl.glGetShaderInfoLog(name, 1, IntBuffer.wrap(new int[]{buffer.limit()}), buffer);
      buffer.flip();
      System.err.println(StandardCharsets.UTF_8.decode(buffer));
    }
  }

  public void setUniformTexture(GL4 gl, String name, int v0 ) {
    int uniformLocation = gl.glGetUniformLocation(this.name, name);
    gl.glUniform1i(uniformLocation, v0);
  }

  public void setUniformValue(GL4 gl, String name, float value) {
    int uniformLocation = gl.glGetUniformLocation(this.name, name);
    gl.glUniform1f(uniformLocation, value);
  }
}
