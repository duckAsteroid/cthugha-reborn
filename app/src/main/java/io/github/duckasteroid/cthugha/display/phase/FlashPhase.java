package io.github.duckasteroid.cthugha.display.phase;

import com.asteroid.duck.opengl.util.RenderContext;
import com.asteroid.duck.opengl.util.renderaction.RenderActionQueue;
import com.asteroid.duck.opengl.util.resources.texture.Filter;
import com.asteroid.duck.opengl.util.resources.texture.Texture;
import io.github.duckasteroid.cthugha.display.TextureBakeRenderer;
import io.github.duckasteroid.cthugha.img.RandomImageSource;
import io.github.duckasteroid.cthugha.params.ParamNode;
import io.github.duckasteroid.cthugha.params.UiHint;
import io.github.duckasteroid.cthugha.params.action.AbstractAction;
import org.lwjgl.BufferUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.lwjgl.opengl.GL11.GL_RGBA;
import static org.lwjgl.opengl.GL11.GL_UNSIGNED_BYTE;

/**
 * One-shot texture flash effects baked into the R16 indexed buffer.
 *
 * The flash image texture stores luma as a normalised float in the R channel so
 * TextureBakeRenderer can write it directly into renderTex without an RGBA→palette
 * conversion step. The white flash uses R = 1.0 (the last palette entry).
 */
public class FlashPhase implements RenderPhase {

    private static final Logger LOG = LoggerFactory.getLogger(FlashPhase.class);

    private final RandomImageSource imageSource = new RandomImageSource(Paths.get("images"));
    private TextureBakeRenderer textureBaker;
    private Texture flashWhiteTex;
    /**
     * Uploaded flash textures by image, LRU-evicted (GL thread only). Creating a texture costs a
     * near-constant ~15ms of driver time regardless of image size — a dropped frame per flash, and
     * flashes are often beat-triggered — so each image is uploaded once and then reused.
     */
    private final Map<Path, Texture> flashCache = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Path, Texture> eldest) {
            if (size() <= MAX_CACHED_FLASHES) return false;
            eldest.getValue().dispose();
            return true;
        }
    };
    private static final int MAX_CACHED_FLASHES = 48;
    private volatile boolean flashPending = false;
    private RenderActionQueue renderActions;

    @Override
    public void init(RenderContext ctx) throws IOException {
        // 1×1 white: R=0xFF (normalised 1.0, the last palette entry), A=0xFF
        ByteBuffer whitePx = BufferUtils.createByteBuffer(4);
        whitePx.put((byte) 0xFF).put((byte) 0).put((byte) 0).put((byte) 0xFF).flip();
        flashWhiteTex = new Texture();
        flashWhiteTex.setInternalFormat(GL_RGBA);
        flashWhiteTex.setImageFormat(GL_RGBA);
        flashWhiteTex.setDataType(GL_UNSIGNED_BYTE);
        flashWhiteTex.setFilter(Filter.NEAREST);
        flashWhiteTex.generate(1, 1, whitePx);

        textureBaker = new TextureBakeRenderer();
        textureBaker.init(ctx);
        textureBaker.setTexture(flashWhiteTex);
    }

    @Override
    public void indexedRender(RenderContext ctx) {
        if (flashPending) {
            textureBaker.doRender(ctx);
            flashPending = false;
        }
    }

    @Override
    public void registerActions(ParamNode generalGroup, RenderActionQueue renderActions) {
        this.renderActions = renderActions;

        AbstractAction flashWhite = new AbstractAction("Flash White",
                ctx -> renderActions.enqueue("flashWhite", rc -> triggerWhite()));
        flashWhite.withUiHint(UiHint.ICON, "sun");
        generalGroup.addChild(flashWhite);
    }

    /** Lists the available flash images. */
    public List<Path> imageFiles() throws IOException {
        return imageSource.imageFiles();
    }

    /** The theme folder an image belongs to, e.g. "cthugha-old-skool". */
    public String groupOf(Path image) {
        return imageSource.groupOf(image);
    }

    /**
     * Bakes the image's cached texture if it has one; otherwise decodes and converts it on a
     * background thread and schedules only the texture upload + bake on the GL thread. Decoding a
     * PNG on the GL thread stalled whole frames for hundreds of ms — very visible when flashes are
     * beat-triggered by a binding.
     */
    public void requestFlash(Path image) {
        renderActions.enqueue("flashImage", rc -> {
            Texture cached = flashCache.get(image);
            if (cached != null) {
                bake(cached);
            } else {
                loadInBackground(image);
            }
        });
    }

    private void loadInBackground(Path image) {
        Thread.ofVirtual().name("flash-load").start(() -> {
            BufferedImage img;
            try {
                img = imageSource.loadImage(image);
            } catch (IOException e) {
                LOG.error("Error loading flash image", e);
                return;
            }
            int w = img.getWidth();
            int h = img.getHeight();
            ByteBuffer pixels = toIndexedRGBA(img);
            renderActions.enqueue("flashImageLoaded", rc -> triggerImage(image, w, h, pixels));
        });
    }

    @Override
    public void dispose() {
        if (textureBaker  != null) textureBaker.dispose();
        if (flashWhiteTex != null) flashWhiteTex.dispose();
        flashCache.values().forEach(Texture::dispose);
        flashCache.clear();
    }

    /** GL thread: uploads pre-converted pixels, caches the texture, and bakes it. */
    private void triggerImage(Path image, int width, int height, ByteBuffer pixels) {
        Texture tex = flashCache.get(image); // a concurrent load of the same image may have won
        if (tex == null) {
            tex = new Texture();
            tex.setInternalFormat(GL_RGBA);
            tex.setImageFormat(GL_RGBA);
            tex.setDataType(GL_UNSIGNED_BYTE);
            tex.setFilter(Filter.LINEAR);
            tex.generate(width, height, pixels);
            flashCache.put(image, tex);
        }
        bake(tex);
    }

    private void bake(Texture tex) {
        textureBaker.setTexture(tex);
        flashPending = true;
    }

    /** GL thread: schedules a white-flash bake. */
    private void triggerWhite() {
        bake(flashWhiteTex);
    }

    /** Converts image to RGBA where R = luma (palette index), G=B=0, A=255. */
    private static ByteBuffer toIndexedRGBA(BufferedImage img) {
        int w = img.getWidth();
        int h = img.getHeight();
        ByteBuffer buf = BufferUtils.createByteBuffer(w * h * 4);
        int[] row = new int[w];
        for (int y = h - 1; y >= 0; y--) {
            img.getRGB(0, y, w, 1, row, 0, w); // bulk row read; per-pixel getRGB is far slower
            for (int x = 0; x < w; x++) {
                int rgb = row[x];
                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >>  8) & 0xFF;
                int b =  rgb        & 0xFF;
                int luma = (r * 299 + g * 587 + b * 114) / 1000;
                buf.put((byte) luma);
                buf.put((byte) 0);
                buf.put((byte) 0);
                buf.put((byte) 0xFF);
            }
        }
        buf.flip();
        return buf;
    }
}
