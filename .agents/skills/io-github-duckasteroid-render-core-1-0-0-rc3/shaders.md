# Shaders

## Inline source (preferred for self-contained components)

```java
// language=GLSL  (IDE hint for syntax highlighting)
private static final String VERT = """
    #version 330 core
    in vec2 position;
    uniform float uTime;
    void main() {
        gl_Position = vec4(position, 0.0, 1.0);
    }
    """;

private static final String FRAG = """
    #version 330 core
    uniform vec4 uColour;
    out vec4 fragColor;
    void main() { fragColor = uColour; }
    """;

// In init():
shader = ShaderProgram.compile(
    ShaderSource.fromClass(VERT, MyClass.class),
    ShaderSource.fromClass(FRAG, MyClass.class),
    null);  // null = no geometry shader
```

## File-based source

Place files in `src/main/resources/glsl/<name>/vertex.glsl` and `frag.glsl`, then:

```java
ShaderProgram shader = ctx.getResourceManager()
    .getShader("myShader", "glsl/name/vertex.glsl", "glsl/name/frag.glsl", null);
```

## Uniforms

Cache handles once in `init()`, set each frame in `doRender()`:

```java
// init():
Uniform<Float>    uTime   = shader.uniforms().get("uTime",   Float.class);
Uniform<Integer>  uMode   = shader.uniforms().get("uMode",   Integer.class);
Uniform<Vector2f> uOffset = shader.uniforms().get("uOffset", Vector2f.class);
Uniform<Vector4f> uColour = shader.uniforms().get("uColour", Vector4f.class);

// doRender():
shader.use(ctx);
uTime.set((float) ctx.getTimer().elapsed());
uColour.set(new Vector4f(1f, 0.5f, 0f, 1f));
```

Supported Java types for `Uniform<T>`: `Float`, `Integer`, `Vector2f`, `Vector3f`, `Vector4f`,
`Matrix4f`.

**Constraint:** GLSL `uniform` names must match exactly what is passed to `shader.uniforms().get(name, …)`.

---

## Constraints

| Constraint | Detail |
|---|---|
| **Uniform caching** | Obtain `Uniform<T>` handles once in `init()` after `shader.use(ctx)`. Getting them every frame is harmless but wasteful. |
