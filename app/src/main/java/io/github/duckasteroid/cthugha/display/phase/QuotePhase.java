package io.github.duckasteroid.cthugha.display.phase;

import com.asteroid.duck.opengl.util.RenderContext;
import com.asteroid.duck.opengl.util.renderaction.RenderActionQueue;
import com.asteroid.duck.opengl.util.resources.font.FontTexture;
import com.asteroid.duck.opengl.util.resources.font.FontTextureFactory;
import com.asteroid.duck.opengl.util.resources.framebuffer.FrameBuffer;
import com.asteroid.duck.opengl.util.resources.texture.Filter;
import com.asteroid.duck.opengl.util.resources.texture.Texture;
import com.asteroid.duck.opengl.util.resources.texture.TextureUnit;
import com.asteroid.duck.opengl.util.text.StringRenderer;
import io.github.duckasteroid.cthugha.JCthugha;
import io.github.duckasteroid.cthugha.config.Config;
import io.github.duckasteroid.cthugha.display.TextureBakeRenderer;
import io.github.duckasteroid.cthugha.params.ParamNode;
import io.github.duckasteroid.cthugha.params.UiHint;
import io.github.duckasteroid.cthugha.params.action.AbstractAction;
import io.github.duckasteroid.cthugha.params.transform.TransformParams;
import io.github.duckasteroid.cthugha.quote.Constants;
import io.github.duckasteroid.cthugha.quote.Quote;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.lwjgl.BufferUtils;

import java.awt.Font;
import java.io.IOException;
import java.nio.IntBuffer;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL30.GL_FRAMEBUFFER;
import static org.lwjgl.opengl.GL30.GL_FRAMEBUFFER_BINDING;
import static org.lwjgl.opengl.GL30.GL_RGBA8;
import static org.lwjgl.opengl.GL30.glBindFramebuffer;

/**
 * Renders the current quote as a screen overlay (default) or baked into the indexed buffer.
 *
 * In indexed-buffer mode ({@code quoteInBuffer=true}) the GL framebuffer binding is saved
 * before rendering text to an internal RGBA offscreen FBO, then restored so the bake
 * renderer can write palette indices into the render texture — no renderFBO reference needed.
 */
public class QuotePhase implements RenderPhase {

    private static final Config CFG = Config.singleton();

    public enum Mode { OVERLAY, BUFFER, BOTH }

    private final JCthugha cthugha;
    private Mode mode = Mode.OVERLAY;

    // Shared rigid transform applied to both the quote and its attribution, pivoting around the
    // quote's own computed anchor position (see updateLayout()/applyTransform()). Animatable via
    // the existing animation system, same as wave renderer transforms.
    public final TransformParams transform = new TransformParams("Transform");

    // Screen overlay renderers
    private StringRenderer quoteRenderer;
    private StringRenderer attrRenderer;
    private Quote lastQuote = null;
    private String attrPosition;
    private String attrAlign;

    // Auto-computed layout: quote anchor position (pixels) and the attribution's offset relative
    // to that anchor. Recomputed only when the quote text changes; the shared transform above is
    // re-applied every frame on top of this fixed anchor/offset pair.
    private float quoteX;
    private float quoteY;
    private float attrOffsetX;
    private float attrOffsetY;

    // In-buffer rendering resources
    private Texture textOverlayTex;
    private FrameBuffer textFBO;
    private TextureBakeRenderer textBaker;
    private TextureUnit textBakerUnit;

    public QuotePhase(JCthugha cthugha) {
        this.cthugha = cthugha;
    }

    @Override
    public void init(RenderContext ctx) throws IOException {
        java.awt.Rectangle win = ctx.getWindow();
        FontTexture quoteFont = makeFontTexture("quote", "Serif", Font.ITALIC, Constants.DEFAULT_QUOTE_SIZE, win.height);
        FontTexture attrFont  = makeFontTexture("attr",  "Serif", Font.PLAIN,  Constants.DEFAULT_ATTR_SIZE,  win.height);
        attrPosition = CFG.getConfig(Constants.SECTION, Constants.KEY_ATTR_POSITION, "below");
        attrAlign    = CFG.getConfig(Constants.SECTION, Constants.KEY_ATTR_ALIGN,    "center");

        quoteRenderer = new StringRenderer(quoteFont);
        quoteRenderer.init(ctx);
        quoteRenderer.setTextColor(new Vector4f(1f, 1f, 1f, 1f));

        attrRenderer = new StringRenderer(attrFont);
        attrRenderer.init(ctx);
        attrRenderer.setTextColor(new Vector4f(0.85f, 0.85f, 0.85f, 1f));

        // In-buffer bake resources: RGBA offscreen FBO + bake renderer
        textOverlayTex = new Texture();
        textOverlayTex.setInternalFormat(GL_RGBA8);
        textOverlayTex.setImageFormat(GL_RGBA);
        textOverlayTex.setDataType(GL_UNSIGNED_BYTE);
        textOverlayTex.setFilter(Filter.LINEAR);
        textOverlayTex.generate(win.width, win.height, 0L);
        textFBO = new FrameBuffer(textOverlayTex);

        textBaker = new TextureBakeRenderer();
        textBaker.init(ctx);
        textBakerUnit = ctx.getResourceManager().nextTextureUnit();
        textBakerUnit.bind(textOverlayTex);
        textBaker.setTexture(textOverlayTex);
    }

    @Override
    public void indexedRender(RenderContext ctx) {
        if (mode == Mode.OVERLAY) return;
        Quote quote = cthugha.getCurrentQuote();
        if (quote == null) return;

        syncQuoteText(quote, ctx);
        applyTransform();

        // Save current FBO binding (renderFBO bound by CthughaWindow)
        IntBuffer savedFbo = BufferUtils.createIntBuffer(1);
        glGetIntegerv(GL_FRAMEBUFFER_BINDING, savedFbo);
        int savedFboId = savedFbo.get(0);

        // Render RGBA text into our offscreen FBO
        textFBO.bind();
        glClearColor(0f, 0f, 0f, 0f);
        glClear(GL_COLOR_BUFFER_BIT);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        quoteRenderer.doRender(ctx);
        attrRenderer.doRender(ctx);
        glDisable(GL_BLEND);
        textFBO.unbind();

        // Restore renderFBO and bake RGBA text → palette indices
        glBindFramebuffer(GL_FRAMEBUFFER, savedFboId);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        textBaker.doRender(ctx);
        glDisable(GL_BLEND);
    }

    @Override
    public void screenRender(RenderContext ctx) {
        if (mode == Mode.BUFFER) return;
        Quote quote = cthugha.getCurrentQuote();
        syncQuoteText(quote, ctx);
        if (quote == null) return;
        applyTransform();

        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        quoteRenderer.doRender(ctx);
        attrRenderer.doRender(ctx);
        glDisable(GL_BLEND);
    }

    @Override
    public void registerActions(ParamNode generalGroup, RenderActionQueue renderActions) {
        AbstractAction toggleMode = new AbstractAction("Toggle Quote Mode", ctx -> {
            Mode[] values = Mode.values();
            mode = values[(mode.ordinal() + 1) % values.length];
            cthugha.notify("quote: " + (mode == Mode.BUFFER ? "in buffer" : mode.name().toLowerCase()));
        });
        toggleMode.withUiHint(UiHint.ICON, "message-square");
        generalGroup.addChild(toggleMode);

        transform.withDescription("Spin, scale, or skew the quote and its attribution together as "
                + "a single rigid unit, pivoting around the quote's own auto-computed position.");
        generalGroup.addChild(transform);
    }

    @Override
    public void dispose() {
        if (quoteRenderer  != null) quoteRenderer.dispose();
        if (attrRenderer   != null) attrRenderer.dispose();
        if (textBaker      != null) textBaker.dispose();
        if (textBakerUnit  != null) textBakerUnit.dispose();
        if (textFBO        != null) textFBO.dispose();
        if (textOverlayTex != null) textOverlayTex.dispose();
    }

    private void syncQuoteText(Quote quote, RenderContext ctx) {
        if (quote == lastQuote) return;
        lastQuote = quote;
        if (quote != null) {
            java.awt.Rectangle win = ctx.getWindow();
            updateLayout(quote, win.width, win.height);
        }
    }

    /**
     * Recomputes the quote's anchor position and the attribution's offset relative to that
     * anchor, from window size and text metrics. Does not touch the renderers' transforms — that
     * happens every frame in {@link #applyTransform()} so the shared, animatable {@link #transform}
     * is honoured even when the quote text itself hasn't changed.
     */
    private void updateLayout(Quote quote, int w, int h) {
        String quoteText = quote.quote();
        String attrText  = "— " + quote.author();
        quoteRenderer.setText(quoteText);
        attrRenderer.setText(attrText);

        quoteX = 40.0f;
        quoteY = h / 2.0f;

        FontTexture attrFont  = attrRenderer.getFontTexture();
        FontTexture quoteFont = quoteRenderer.getFontTexture();
        int gap = quoteFont.getFontHeight() / 3;

        // Attribution offset relative to the quote anchor (not an absolute position) so it can be
        // applied *after* the shared transform below and move rigidly with the quote.
        if ("above".equalsIgnoreCase(attrPosition)) {
            attrOffsetY = -attrFont.getFontHeight() - gap;
        } else {
            attrOffsetY = quoteFont.getHeight(quoteText) + gap;
        }

        float quoteW = quoteFont.getWidth(quoteText);
        attrOffsetX = switch (attrAlign.toLowerCase()) {
            case "center" -> (quoteW - attrFont.getWidth(attrText)) / 2.0f;
            case "right"  -> quoteW - attrFont.getWidth(attrText);
            default       -> 0.0f;
        };
    }

    /**
     * Applies the shared {@link #transform} to both renderers as a single rigid unit, pivoting
     * around the quote's own anchor position ({@link #quoteX}, {@link #quoteY}).
     * <p>
     * The anchor translate is applied first (establishing the pivot as the new local origin),
     * then {@code transform}'s scale/shear/rotate are composed on top of it — so they act in the
     * quote's local space and the quote's own origin never moves. The attribution's position is
     * then a further local-space translate by its quote-relative offset on top of that same
     * composed matrix, so it rotates/scales/shears together with the quote around the identical
     * pivot rather than computing its own absolute, independent placement.
     */
    private void applyTransform() {
        Matrix4f quoteModel = transform.applyTo(new Matrix4f().translate(quoteX, quoteY, 0.0f));
        quoteRenderer.setTransform(quoteModel);

        Matrix4f attrModel = new Matrix4f(quoteModel).translate(attrOffsetX, attrOffsetY, 0.0f);
        attrRenderer.setTransform(attrModel);
    }

    private static FontTexture makeFontTexture(String prefix, String defaultName,
                                               int defaultStyle, String defaultSize, int refHeight) {
        String name     = CFG.getConfig(Constants.SECTION, prefix + "_font", defaultName);
        String sizeStr  = CFG.getConfig(Constants.SECTION, prefix + "_size", defaultSize);
        int    size     = PhaseConfig.parseFontSize(sizeStr, refHeight);
        int    style    = PhaseConfig.parseFontStyle(CFG.getConfig(Constants.SECTION, prefix + "_style",
                fontStyleName(defaultStyle)));
        return new FontTextureFactory(new Font(name, style, size), true).createFontTexture();
    }

    private static String fontStyleName(int style) {
        return switch (style) {
            case Font.BOLD               -> "BOLD";
            case Font.ITALIC             -> "ITALIC";
            case Font.BOLD | Font.ITALIC -> "BOLD_ITALIC";
            default                      -> "PLAIN";
        };
    }
}
