# Resource Manager

`ResourceManager` (owned by `GLWindow`) is the single owner of all GL handles. Load textures,
shaders, and buffers through it — it calls `dispose()` on all resources at shutdown.

```java
ResourceManager rm = ctx.getResourceManager();

// Named textures — loaded once, cached by name
Texture tex = rm.getTexture("myTex", "textures/foo.png");
Texture tex2 = rm.getTexture("myTex", "textures/foo.png", ImageLoadingOptions.DEFAULT.withFlip());

// Named shaders loaded from classpath
ShaderProgram sh = rm.getShader("blur", "glsl/blur/vertex.glsl", "glsl/blur/frag.glsl", null);

// Texture units — allocated sequentially, avoids unit conflicts between components
TextureUnit unit = rm.nextTextureUnit();
unit.activate();
unit.bind(texture);
shader.uniforms().get("uTex", Integer.class).set(unit.index());

// Arbitrary lifecycle tracking — dispose() will be called at shutdown
rm.register(() -> { glDeleteBuffers(myPboId); });    // Resource is a @FunctionalInterface
```

---

## Texture formats (`DataFormat`)

Pass a `DataFormat` via `ImageLoadingOptions.DEFAULT.withType(...)` to control how image data is uploaded to the GPU.

| Enum value | GL internal format | Bytes/pixel | Use case |
|---|---|---|---|
| `RGBA` | `GL_RGBA8` | 4 | Default; standard colour images. |
| `GRAY` | `GL_R32F` | 4 | Single-channel 32-bit float luminance. Legacy; use `GRAY_16` for palette indexing. |
| `GRAY_16` | `GL_R16` | 2 | Single-channel 16-bit normalised luminance (0–65 535 → 0.0–1.0). Use as the indexed texture for `PaletteRenderer` to address up to 65 535 palette entries. |
| `TWO_CHANNEL_16_BIT` | `GL_RG16UI` | 4 | Two-channel 16-bit unsigned integer; no image-load support (programmatic use only). |

All textures are created as **2D** (`GL_TEXTURE_2D`) regardless of image height, so `sampler2D` is always the correct GLSL type.

```java
// Indexed (greyscale) source texture for PaletteRenderer — 16-bit precision:
Texture indexed = rm.getTexture("gray", "window.jpeg",
    ImageLoadingOptions.DEFAULT.withType(DataFormat.GRAY_16));

// Palette image — full 2D RGBA; any width × height (width × height = total entries):
Texture palette = rm.getTexture("palette", "palettes/FIRE2.MAP.png",
    ImageLoadingOptions.DEFAULT);
```

---

## Constraints

| Constraint | Detail |
|---|---|
| **PBO persistent mapping** | A persistently-mapped PBO (`GL_MAP_PERSISTENT_BIT`) must stay mapped for the life of the object. Do not wrap it in `VertexBufferObject`; manage it with raw GL calls and register cleanup via `rm.register(...)`. |
| **ExclusivityGroup** | VAOs, EBOs, shader programs, and standard textures are bound through `ExclusivityGroup` wrappers. Always use the wrapper (`vao.bind(ctx)`, `shader.use(ctx)`) rather than raw `glBind*` calls to avoid silent state corruption. |
