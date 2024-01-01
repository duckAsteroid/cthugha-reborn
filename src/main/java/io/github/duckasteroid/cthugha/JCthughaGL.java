package io.github.duckasteroid.cthugha;

import static com.jogamp.opengl.GL.GL_DONT_CARE;
import static com.jogamp.opengl.GL2ES2.GL_DEBUG_SEVERITY_HIGH;
import static com.jogamp.opengl.GL2ES2.GL_DEBUG_SEVERITY_MEDIUM;
import static com.jogamp.opengl.GL2ES3.GL_COLOR;

import com.jogamp.common.nio.Buffers;
import com.jogamp.newt.event.*;
import com.jogamp.newt.opengl.GLWindow;
import com.jogamp.opengl.*;
import com.jogamp.opengl.util.Animator;
import com.jogamp.opengl.util.texture.Texture;
import com.jogamp.opengl.util.texture.TextureIO;
import java.io.IOException;
import java.io.InputStream;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;
import java.nio.file.Files;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalUnit;

public class JCthughaGL implements GLEventListener {
  private static GLWindow window;

  private Program program;

  private long start;
  private Animator animator;

  private final float freq = 1; // Hz
  private FloatBuffer bkgBuffer;
  private short[] indices;

  private int[] vbo;
  private int[] ibo;


  private void setup() {

    GLProfile glProfile = GLProfile.getGL4ES3();
    GLCapabilities glCapabilities = new GLCapabilities(glProfile);

    window = GLWindow.create(glCapabilities);

    window.setTitle("Shader basics");
    window.setSize(600, 600);
    //window.setFullscreen(true);
    window.setContextCreationFlags(GLContext.CTX_OPTION_DEBUG);
    window.setVisible(true);

    window.addGLEventListener(this);

    animator = new Animator(window);
    animator.setUpdateFPSFrames(1, null);
    animator.setRunAsFastAsPossible(true);
    animator.start();

    window.addWindowListener(new WindowAdapter() {
      @Override
      public void windowDestroyed(WindowEvent e) {
        animator.stop();
        System.exit(0);
      }
    });

    window.addKeyListener(new KeyAdapter() {
      @Override
      public void keyPressed(KeyEvent e) {
        if (KeyEvent.VK_ESCAPE == e.getKeyCode()) {
          System.out.println("Exit via ESC key");
          window.destroy();
        }
      }
    });
  }


  @Override
  public void init(GLAutoDrawable drawable) {
    GL4 gl = drawable.getGL().getGL4();
    start = System.currentTimeMillis();

    initDebug(gl);

    bkgBuffer = Buffers.newDirectFloatBuffer(4);
    bkgBuffer.put(0, new float[] {0.0f, 0.0f, 0.0f, 0.0f});

    this.program = new Program(gl, "shaders/gl4", "example", "example");
    gl.glUseProgram(this.program.name);


    try(InputStream dogJpeg = JCthughaGL.class.getResourceAsStream("/shaders/gl4/dog.jpeg")) {
      Texture texture = TextureIO.newTexture(dogJpeg, false, "jpeg");
      // Bind the texture to texture unit 0
      gl.glActiveTexture(GL.GL_TEXTURE0);
      texture.bind(gl);

      // Set the uniform variable in the shader to texture unit 0
      this.program.setUniformTexture(gl, "background", 0);
    }
    catch (IOException ioe) {
      ioe.printStackTrace();
    }

    // Define the vertices of the rectangle
    float[] vertices = {
      -1.0f, -1.0f, // bottom left
      1.0f, -1.0f, // bottom right
      1.0f, 1.0f, // top right
      -1.0f, 1.0f // top left
    };

    // Create a VBO and bind it
    vbo = new int[1];
    gl.glGenBuffers(1, vbo, 0);
    gl.glBindBuffer(GL.GL_ARRAY_BUFFER, vbo[0]);

    // Store the vertex data in the VBO
    FloatBuffer vertexBuffer = Buffers.newDirectFloatBuffer(vertices);
    gl.glBufferData(GL.GL_ARRAY_BUFFER, vertexBuffer.limit() * 4, vertexBuffer, GL.GL_STATIC_DRAW);

    // Enable the vertex attribute array and specify the vertex attribute pointer
    int positionAttribute = gl.glGetAttribLocation(this.program.name, "position");
    gl.glEnableVertexAttribArray(positionAttribute);
    gl.glVertexAttribPointer(positionAttribute, 2, GL.GL_FLOAT, false, 0, 0);


    // Create an IBO and bind it
    ibo = new int[1];
    gl.glGenBuffers(1, ibo, 0);
    gl.glBindBuffer(GL.GL_ELEMENT_ARRAY_BUFFER, ibo[0]);

    // Store the index data in the IBO
    indices = new short[] {0, 1, 2, 0, 2, 3};
    ShortBuffer indexBuffer = Buffers.newDirectShortBuffer(indices);
    gl.glBufferData(GL.GL_ELEMENT_ARRAY_BUFFER, indexBuffer.limit() * 2, indexBuffer, GL.GL_STATIC_DRAW);

  }

  @Override
  public void dispose(GLAutoDrawable glAutoDrawable) {
    GL4 gl = glAutoDrawable.getGL().getGL4();
  }

  @Override
  public void display(GLAutoDrawable drawable) {
    GL4 gl = (GL4) GLContext.getCurrentGL();
    gl.glClearBufferfv(GL_COLOR, 0, bkgBuffer);
    gl.glUseProgram(program.name);
    this.program.setUniformValue(gl, "millis", start - System.currentTimeMillis());
    gl.glBindBuffer(GL.GL_ARRAY_BUFFER, vbo[0]);
    gl.glBindBuffer(GL.GL_ELEMENT_ARRAY_BUFFER, ibo[0]);
    gl.glDrawElements(GL.GL_TRIANGLES, indices.length, GL.GL_UNSIGNED_SHORT, 0);
    gl.glUseProgram(0);
  }

  @Override
  public void reshape(GLAutoDrawable drawable, int x, int y, int width, int height) {
    GL4 gl = drawable.getGL().getGL4();
    gl.glViewport(x, y, width, height);
  }

  public static void main(String[] args) {
    JCthughaGL jCthughaGL = new JCthughaGL();
    jCthughaGL.setup();
  }


  private void initDebug(GL4 gl) {

    window.getContext().addGLDebugListener(new GLDebugListener() {
      @Override
      public void messageSent(GLDebugMessage event) {
        System.out.println(event);
        throw new RuntimeException(event.getDbgMsg());
      }
    });

    gl.glDebugMessageControl(
      GL_DONT_CARE,
      GL_DONT_CARE,
      GL_DONT_CARE,
      0,
      null,
      false);

    gl.glDebugMessageControl(
      GL_DONT_CARE,
      GL_DONT_CARE,
      GL_DEBUG_SEVERITY_HIGH,
      0,
      null,
      true);

    gl.glDebugMessageControl(
      GL_DONT_CARE,
      GL_DONT_CARE,
      GL_DEBUG_SEVERITY_MEDIUM,
      0,
      null,
      true);
  }

}
