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
import java.util.List;

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
    private Texture flashImageTex;
    private volatile boolean flashPending = false;
    private RenderActionQueue renderActions;

    @Override
    public void init(RenderContext ctx) throws IOException {
        // 1×1 white: R=0xFF (palette index 255), A=0xFF
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

    /** Schedules a one-shot bake of the given image on the GL thread. */
    public void requestFlash(Path image) {
        renderActions.enqueue("flashImage", rc -> triggerImage(image));
    }

    @Override
    public void dispose() {
        if (textureBaker  != null) textureBaker.dispose();
        if (flashWhiteTex != null) flashWhiteTex.dispose();
        if (flashImageTex != null) flashImageTex.dispose();
    }

    /** GL thread: loads the given image and bakes it. */
    private void triggerImage(Path image) {
        try {
            BufferedImage img = imageSource.loadImage(image);
            if (flashImageTex != null) flashImageTex.dispose();
            flashImageTex = new Texture();
            flashImageTex.setInternalFormat(GL_RGBA);
            flashImageTex.setImageFormat(GL_RGBA);
            flashImageTex.setDataType(GL_UNSIGNED_BYTE);
            flashImageTex.setFilter(Filter.LINEAR);
            flashImageTex.generate(img.getWidth(), img.getHeight(), toIndexedRGBA(img));
            textureBaker.setTexture(flashImageTex);
            flashPending = true;
        } catch (IOException e) {
            LOG.error("Error loading flash image", e);
        }
    }

    /** GL thread: schedules a white-flash bake. */
    private void triggerWhite() {
        textureBaker.setTexture(flashWhiteTex);
        flashPending = true;
    }

    /** Converts image to RGBA where R = luma (palette index), G=B=0, A=255. */
    private static ByteBuffer toIndexedRGBA(BufferedImage img) {
        int w = img.getWidth();
        int h = img.getHeight();
        ByteBuffer buf = BufferUtils.createByteBuffer(w * h * 4);
        for (int y = h - 1; y >= 0; y--) {
            for (int x = 0; x < w; x++) {
                int rgb = img.getRGB(x, y);
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
