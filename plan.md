# Plan: Migrate Cthugha Reborn from AWT to OpenGL (Phase 1)

## Context

The project renders a 256-colour palettized pixel buffer using AWT (`Frame` + `BufferStrategy`).
`render-core` (LWJGL 3 / GLFW) is already declared in `build.gradle`. Phase 1 keeps all CPU
computation intact and replaces only the display path: AWT window → GLFW window, AWT blit →
palette-lookup shader.

### Caveat on render-core API
The `render-core 0.0.1` jar is not cached locally (Maven local/Gradle cache both empty), so the
API surface below is based on the draft plan. The implementer must verify class names and method
signatures against the actual jar or its source before coding. The structural logic is sound; exact
API spelling may need adjustment.

---

## Architecture (Phase 1)

```
CPU side (unchanged)             GPU side (new)
────────────────────             ──────────────────────────────────────
ScreenBuffer.pixels (byte[])  ──►  screenTexture (GL_R8, GL_RED)
PaletteMap.colors (int[] ARGB) ──► paletteTexture (1×256 RGBA)
                                     │
                              PaletteRenderer.doRender()
                                     │
                              GLFW window / swapBuffers
```

```
CthughaWindow (new, extends GLWindow)
  ├── init()          ─► cthugha.init(Dimension) + create GL textures
  ├── render()        ─► cthugha.doRenderCPU() + upload + PaletteRenderer
  ├── registerKeys()  ─► Keybind list → GLFW char callback
  └── main()

JCthugha (modified)
  ├── init(Dimension)     ─► CPU-only setup (removed AWT args)
  ├── doRenderCPU()       ─► translate + flame + audio + waves + strings
  └── (removed)           ─► run/stop thread loop, main(), AWT display fields
```

---

## Step-by-step changes

### 1. Rename naming conflict

`io.github.duckasteroid.cthugha.display.RenderedItem` (line 5: `render(Display, AudioSample)`)
conflicts with `com.asteroid.duck.opengl.util.RenderedItem` from render-core.

Rename the project interface to `CthughaRenderItem`:
- Rename file `display/RenderedItem.java` → `display/CthughaRenderItem.java`
- Update interface name and any implementors (currently no known implementors, interface is unused)

### 2. Refactor `JCthugha.java`

**Remove AWT display fields** (lines 111–119):
```java
// Remove these:
BufferStrategy bufferStrategy;
private BufferedImage screenImage;
private Frame window;
private boolean debug = true;
private Color debugColor = Color.GREEN;
private Font debugFont = new Font("Courier New", Font.PLAIN, 12);
private final NotificationRenderer notificationRenderer = new NotificationRenderer();
```
Keep `notify` boolean and make `notificationRenderer` calls no-ops for now (or keep the
`notify` field as a flag but remove its AWT-dependent renderer).

**Change `init()` signature** (currently lines 125–154):
```java
// Before:
public void init(Dimension dims, BufferStrategy bufferStrategy,
                 BufferedImage screenImage, Frame window, List<Keybind> keybinds)

// After:
public void init(Dimension dims) throws IOException
```
Remove all assignments to the dropped fields. Keep the animator setup and map loading intact.

**Rename `doRender()` → `doRenderCPU()`** and truncate at line 185 (before the AWT blit):
```java
public synchronized Duration doRenderCPU() {
    final Instant start = Instant.now();
    try {
        frameRate.ping();
        animatorPool.doAnimation(Duration.between(started, start));
        translate.transform(buffer.pixels, buffer.pixels);
        flame.flame(buffer, buffer.getWriteableRaster());   // WritableRaster still fine
        AudioSample audioSample = audioSource.sample(buffer.width);
        wave.wave(audioSample, buffer);
        wave2.wave(audioSample, buffer);
        if (doSpeckles) speckles.wave(audioSample, buffer);
        if (doFFT) fft.wave(audioSample, buffer);
        stringRenderer.show(buffer);                        // AWT Graphics2D into byte[]
        // STOP HERE — everything after is AWT display
    } catch (Throwable t) { LOG.error("Processing main loop", t); }
    return Duration.between(start, Instant.now());
}
```
Note: `StringRenderer.show()` and wave renderers use `buffer.getBufferedImageView()` and
`buffer.getGraphics()` — these create AWT `BufferedImage`/`Graphics2D` backed by `byte[] pixels`
with `IndexColorModel`. This is pure CPU-side color-index painting; it is independent of the
display path and can stay unchanged in Phase 1.

**Remove `run()`, `stop()`, `main()`** (lines 281–404). Keep `close()`.

**Make action methods public** (they already are package/public; verify): `newTranslation()`,
`newPalette()`, `showQuote()`, `toggleAudioSource()`, `toggleSpeckle()`, `changeAmplitude()`,
`rotate()`, `flashImage()`, `toggleNotifications()`.

**AWT imports to keep in JCthugha**: `java.awt.Dimension`, `java.awt.Graphics2D` (for
`flashImage()`). Remove: `Frame`, `BufferStrategy`, `BufferedImage` (screen), `Color` (debug),
`Font` (debug), `Graphics` (debug), AWT display imports.

### 3. Create `display/CthughaWindow.java`

New class extending `com.asteroid.duck.opengl.util.GLWindow` (verify exact FQCN).

**Constructor:**
```java
public CthughaWindow() throws LineUnavailableException {
    super(new ResourceManagerImpl(new PathBasedLoader(Paths.get("."))),
          "Cthugha Reborn", 1280, 720, null);
    this.cthugha = new JCthugha();
}
```
Working dir at runtime is `src/dist/` (set in `build.gradle` `run { workingDir }`) so
`Paths.get(".")` resolves to the correct resource root for maps/config.

**`init()` (GL thread):**
1. Get dims from `getWindow()` (GLWindow method)
2. `cthugha.init(new Dimension(w, h))`
3. Create screen texture (`GL_R8` / `GL_RED` / `GL_UNSIGNED_BYTE`, `NEAREST` filter):
   ```java
   Texture screenTex = new Texture();
   screenTex.setInternalFormat(GL_R8);
   screenTex.setImageFormat(GL_RED);
   screenTex.setDataType(GL_UNSIGNED_BYTE);
   screenTex.setFilter(Filter.NEAREST);
   screenTex.generate(w, h, 0L);
   getResourceManager().putTexture("screen", screenTex);
   ```
4. Create palette texture from `cthugha.buffer.paletteMap.colors` (`int[]` ARGB → RGBA bytes):
   ```java
   ByteBuffer palBuf = buildPaletteBuffer(cthugha.buffer.paletteMap);
   // palTex is 256×1, GL_RGBA
   getResourceManager().putTexture("palette", palTex);
   ```
   `buildPaletteBuffer` converts each `int` (0xAARRGGBB) → 4 bytes (R, G, B, A=255):
   ```java
   private ByteBuffer buildPaletteBuffer(PaletteMap pm) {
       ByteBuffer buf = BufferUtils.createByteBuffer(256 * 4);
       for (int c : pm.colors) {
           buf.put((byte)((c >> 16) & 0xFF)); // R
           buf.put((byte)((c >>  8) & 0xFF)); // G
           buf.put((byte)( c        & 0xFF)); // B
           buf.put((byte) 0xFF);              // A
       }
       buf.flip();
       return buf;
   }
   ```
5. `paletteRenderer = new PaletteRenderer("screen"); paletteRenderer.init(this);`
6. Allocate `pixelBuffer = BufferUtils.createByteBuffer(w * h);`

**`render()` (GL thread):**
1. `cthugha.doRenderCPU()`
2. Upload screen pixels:
   ```java
   pixelBuffer.clear();
   pixelBuffer.put(cthugha.buffer.pixels);
   pixelBuffer.flip();
   screenTex.bind();
   glTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, w, h, GL_RED, GL_UNSIGNED_BYTE, pixelBuffer);
   ```
3. Re-upload palette (1 KB, negligible cost) when it may have changed:
   ```java
   ByteBuffer palBuf = buildPaletteBuffer(cthugha.buffer.paletteMap);
   palTex.bind();
   glTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, 256, 1, GL_RGBA, GL_UNSIGNED_BYTE, palBuf);
   ```
4. `paletteRenderer.doRender(this);`

**`registerKeys()` — GLFW key binding:**
`Keybind.isFired(KeyEvent)` checks `event.getKeyChar()`. GLFW's character callback supplies a
Unicode codepoint. Bridge with a GLFW `glfwSetCharCallback` loop:
```java
// inside registerKeys() or init():
glfwSetCharCallback(getWindow().getHandle(), (window, codepoint) -> {
    char ch = (char) codepoint;
    for (Keybind kb : keybindings) {
        if (kb.getCharacter() == ch || (kb.getChar2() != null && kb.getChar2() == ch)) {
            // invoke the action; note: no KeyEvent available, pass null or adapt
        }
    }
});
```
Because `Keybind.handler` is `Consumer<KeyEvent>` and the shift-state-checking handlers (like
`'t'`/`'T'` and `'u'`/`'U'`) distinguish via `e.isShiftDown()`, the simplest approach is:
- For keys without shift variants: pass `null` (handlers must not NPE) or a dummy `KeyEvent`
- For shift variants (`'T'`, `'U'`, `'J'`, `'<'`, `'>'`): these are separate codepoints in GLFW
  char callbacks, so they arrive as distinct characters — the existing `char2` / `Keybind.isFired`
  logic works as-is.
- Build the keybinding list as a field `List<Keybind> keybindings` in `CthughaWindow` (moved
  from `JCthugha.main()`).

**RenderContext abstract methods** (verify which ones GLWindow leaves abstract):
- `getTimer()` → `new TimerImpl(() -> (double) System.nanoTime() / 1e9)`
- `getDesiredUpdatePeriod()` / `setDesiredUpdatePeriod()` → simple `double` field

**`main()`:**
```java
public static void main(String[] args) throws Exception {
    new CthughaWindow().displayLoop();
}
```

### 4. Update `build.gradle`

```groovy
application {
    mainClass = "io.github.duckasteroid.cthugha.display.CthughaWindow"
}
```

---

## What stays unchanged in Phase 1

| Component | Status |
|---|---|
| `JavaFlame` (CPU convolution on `byte[]`) | Unchanged |
| `Translate.transform()` (CPU array lookup) | Unchanged |
| `StringRenderer.show()` (AWT Graphics2D → byte[]) | Unchanged — writes palette indices |
| `SimpleWave`, `RadialWave`, `SpeckleWave`, `SpectraBars` | Unchanged — write palette indices |
| `flashImage()` (AWT BufferedImage → byte[]) | Unchanged |
| `NotificationRenderer` | Dropped; restore in Phase 2 |
| `renderDebugInfo()` / debug overlay | Dropped; restore in Phase 2 |

---

## Files to create / modify

| File | Action |
|---|---|
| `display/RenderedItem.java` | Rename to `CthughaRenderItem.java` |
| `JCthugha.java` | Refactor: remove AWT display, change `init`, rename `doRender` |
| `display/CthughaWindow.java` | **Create** |
| `build.gradle` | Change `mainClass` |

---

## Verification

```bash
./gradlew run        # workingDir = src/dist/ per build.gradle
```

Confirm:
- A GLFW window appears (not AWT)
- Palettized visuals animate (translate + flame + waveforms)
- Key `t` changes translation, `p` changes palette (palette texture re-uploads)
- Key `q` shows a text quote, `!` quits
- No `NullPointerException` on audio init
- Palette color changes (`p`) are visually reflected immediately

If the build environment lacks Java 25, the `java.toolchain` block in `build.gradle` will need
a temporary downgrade or `JAVA_HOME` override for the test run.