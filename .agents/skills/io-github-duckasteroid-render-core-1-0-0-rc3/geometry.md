# Geometry (VAO / VBO)

## Vertex layout

```java
// Declare elements — the name() is used verbatim as the GLSL "in" variable name
VertexElement POSITION = new VertexElement(VertexElementType.VEC_2F, "position");
VertexElement COLOR    = new VertexElement(VertexElementType.VEC_3F, "color");
```

Available `VertexElementType` constants: `FLOAT`, `VEC_2F`, `VEC_3F`, `VEC_4F`.

## Building and filling a VBO

```java
// init():
VertexDataStructure layout = new VertexDataStructure(POSITION, COLOR);

VertexArrayObject vao = new VertexArrayObject();
vao.setDrawMode(BufferDrawMode.TRIANGLES);   // or LINE_STRIP, LINES, TRIANGLE_FAN, …
vao.init(ctx);

VertexBufferObject vbo = vao.createVbo(layout, vertexCount);
vbo.init(ctx);

// populate
vbo.setElement(0, POSITION, new Vector2f( 0f,  0.5f));
vbo.setElement(0, COLOR,    new Vector3f( 1f,  0f,   0f));
vbo.setElement(1, POSITION, new Vector2f(-0.5f,-0.5f));
// ...

vbo.update(UpdateHint.STATIC);  // STATIC = never changes; DYNAMIC = changes every frame

// Link VBO attribute locations to shader inputs (do this after shader is compiled):
vao.getVbo().setup(shader);
```

`UpdateHint` values: `STATIC` (write once), `DYNAMIC` (write many), `STREAM` (write once, use few).

## Rendering

```java
// doRender():
shader.use(ctx);
vao.bind(ctx);
vao.doRender(ctx);
```

## Disposal

```java
// dispose():
vao.dispose();   // disposes VBO and EBO too
shader.dispose();
```

---

## Constraints

| Constraint | Detail |
|---|---|
| **Vertex name contract** | `VertexElement.name()` must match the GLSL `in` variable name exactly. A mismatch silently produces a black screen. |
| **UpdateHint.STATIC** | Call `vbo.update(UpdateHint.STATIC)` after initial population. If vertex data changes at runtime, use `UpdateHint.DYNAMIC` and call `update()` again after each write. |
| **VBO bound before setup()** | `VertexBufferObject.setup(shader)` calls `glVertexAttribPointer` without binding the VBO first — it relies on the VBO already being bound to `GL_ARRAY_BUFFER`. When initialising multiple VAO/VBO pairs, call `vbo.update(UpdateHint.STATIC)` (which rebinds the VBO) immediately before each `vbo.setup(shader)` call to avoid recording the wrong VBO in the VAO's attribute state. |
