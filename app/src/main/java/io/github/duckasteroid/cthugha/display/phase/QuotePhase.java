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
import io.github.duckasteroid.cthugha.params.ContainerNode;
import io.github.duckasteroid.cthugha.params.UiHint;
import io.github.duckasteroid.cthugha.params.action.AbstractAction;
import io.github.duckasteroid.cthugha.strings.Constants;
import io.github.duckasteroid.cthugha.strings.Quote;
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

    private final JCthugha cthugha;
    private boolean quoteInBuffer = false;

    // Screen overlay renderers
    private StringRenderer quoteRenderer;
    private StringRenderer attrRenderer;
    private Quote lastQuote = null;
    private String attrPosition;
    private String attrAlign;

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
        FontTexture quoteFont = makeFontTexture("quote", "Serif", Font.ITALIC,  36);
        FontTexture attrFont  = makeFontTexture("attr",  "Serif", Font.PLAIN,   22);
        attrPosition = CFG.getConfig(Constants.SECTION, Constants.KEY_ATTR_POSITION, "below");
        attrAlign    = CFG.getConfig(Constants.SECTION, Constants.KEY_ATTR_ALIGN,    "center");

        quoteRenderer = new StringRenderer(quoteFont);
        quoteRenderer.init(ctx);
        quoteRenderer.setTextColor(new Vector4f(1f, 1f, 1f, 1f));

        attrRenderer = new StringRenderer(attrFont);
        attrRenderer.init(ctx);
        attrRenderer.setTextColor(new Vector4f(0.85f, 0.85f, 0.85f, 1f));

        // In-buffer bake resources: RGBA offscreen FBO + bake renderer
        java.awt.Rectangle win = ctx.getWindow();
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
        if (!quoteInBuffer) return;
        Quote quote = cthugha.getCurrentQuote();
        if (quote == null) return;

        syncQuoteText(quote, ctx);

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
        if (quoteInBuffer) return;
        Quote quote = cthugha.getCurrentQuote();
        syncQuoteText(quote, ctx);
        if (quote == null) return;

        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        quoteRenderer.doRender(ctx);
        attrRenderer.doRender(ctx);
        glDisable(GL_BLEND);
    }

    @Override
    public void registerActions(ContainerNode generalGroup, RenderActionQueue renderActions) {
        AbstractAction showQuote = new AbstractAction("Show Quote",
                ctx -> cthugha.showQuote());
        showQuote.withUiHint(UiHint.ICON, "quote");
        generalGroup.addChild(showQuote);

        AbstractAction toggleMode = new AbstractAction("Toggle Quote Mode", ctx -> {
            quoteInBuffer = !quoteInBuffer;
            cthugha.notify("quote: " + (quoteInBuffer ? "in buffer" : "overlay"));
        });
        toggleMode.withUiHint(UiHint.ICON, "message-square");
        generalGroup.addChild(toggleMode);
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

    private void updateLayout(Quote quote, int w, int h) {
        String quoteText = quote.quote();
        String attrText  = "— " + quote.author();
        quoteRenderer.setText(quoteText);
        attrRenderer.setText(attrText);

        float quoteY = h / 2.0f;
        quoteRenderer.setTransform(new Matrix4f().translate(40.0f, quoteY, 0.0f));

        FontTexture attrFont  = attrRenderer.getFontTexture();
        FontTexture quoteFont = quoteRenderer.getFontTexture();
        int gap = 8;

        float attrY;
        if ("above".equalsIgnoreCase(attrPosition)) {
            attrY = quoteY - attrFont.getFontHeight() - gap;
        } else {
            attrY = quoteY + quoteFont.getHeight(quoteText) + gap;
        }

        float attrX = switch (attrAlign.toLowerCase()) {
            case "center" -> (w - attrFont.getWidth(attrText)) / 2.0f;
            case "right"  -> w - attrFont.getWidth(attrText) - 40.0f;
            default       -> 40.0f;
        };
        attrRenderer.setTransform(new Matrix4f().translate(attrX, attrY, 0.0f));
    }

    private static FontTexture makeFontTexture(String prefix, String defaultName,
                                               int defaultStyle, int defaultSize) {
        String name  = CFG.getConfig(Constants.SECTION, prefix + "_font",  defaultName);
        int    size  = CFG.getConfigAs(Constants.SECTION, prefix + "_size",
                String.valueOf(defaultSize), Integer::parseInt);
        int    style = parseFontStyle(CFG.getConfig(Constants.SECTION, prefix + "_style",
                fontStyleName(defaultStyle)));
        return new FontTextureFactory(new Font(name, style, size), true).createFontTexture();
    }

    private static int parseFontStyle(String s) {
        return switch (s.toUpperCase()) {
            case "BOLD"        -> Font.BOLD;
            case "ITALIC"      -> Font.ITALIC;
            case "BOLD_ITALIC" -> Font.BOLD | Font.ITALIC;
            default            -> Font.PLAIN;
        };
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
