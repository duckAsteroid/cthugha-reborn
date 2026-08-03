package io.github.duckasteroid.cthugha.display.phase;

import com.asteroid.duck.opengl.util.RenderContext;
import com.asteroid.duck.opengl.util.audio.analysis.BeatDetector;
import com.asteroid.duck.opengl.util.audio.analysis.FrequencyBand;
import com.asteroid.duck.opengl.util.color.StandardColors;
import com.asteroid.duck.opengl.util.resources.font.FontTexture;
import com.asteroid.duck.opengl.util.resources.font.FontTextureFactory;
import com.asteroid.duck.opengl.util.text.StringRenderer;
import io.github.duckasteroid.cthugha.JCthugha;
import io.github.duckasteroid.cthugha.config.Config;
import org.joml.Matrix4f;
import org.joml.Vector4f;

import java.awt.Font;
import java.awt.Rectangle;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.opengl.GL11.*;

/**
 * HUD overlay listing each beat-detection band's live name, a filled bar, and its numeric
 * strength — a debug aid for tuning {@code [beats]} band ranges and binding-script thresholds
 * (e.g. {@code "bass() > 0.7"}) without needing the remote UI.
 *
 * <p>Shown only while {@link io.github.duckasteroid.cthugha.display.AudioSourceNode#debugBeats}
 * is enabled (toggle on the "Audio" tab, or the {@code D} key by default). Bands, their order,
 * and count come from {@link JCthugha#beatDetector} once it's initialised by {@link WavePhase};
 * this phase is placed after {@code wavePhase} in {@link JCthugha#createPhases()} so the detector
 * — and therefore row layout — is already available by the time {@link #init} runs.
 *
 * <p>Configurable in the {@code [DebugBeats]} INI section: {@code font}, {@code size},
 * {@code style}, {@code color}, {@code location}, {@code padding}, {@code bar_width},
 * {@code row_gap}, {@code gap}. A monospaced font is recommended so the name/bar/number columns
 * line up.
 */
public class DebugBeatsPhase implements RenderPhase {

    private static final String SECTION = "DebugBeats";
    private static final Config CFG = Config.singleton();
    private static final Vector4f PANEL_COLOR = new Vector4f(0f, 0f, 0f, 0.55f);
    private static final Vector4f TRACK_COLOR = new Vector4f(1f, 1f, 1f, 0.18f);

    private final JCthugha cthugha;

    private List<FrequencyBand> bands = List.of();
    private List<StringRenderer> labelRenderers = List.of();
    private List<StringRenderer> valueRenderers = List.of();
    private SolidQuad quad;
    private Vector4f barColor;

    private float panelX, panelY, panelW, panelH;
    private float[] rowBarX, rowBarY;
    private float barWidthPx, barHeightPx;

    public DebugBeatsPhase(JCthugha cthugha) {
        this.cthugha = cthugha;
    }

    @Override
    public void init(RenderContext ctx) throws IOException {
        BeatDetector bd = cthugha.beatDetector;
        bands = bd != null ? bd.getBands() : List.of();
        if (bands.isEmpty()) return;

        Rectangle win = ctx.getWindow();

        String fontName = CFG.getConfig(SECTION, "font",  "Monospaced");
        String sizeStr  = CFG.getConfig(SECTION, "size",  "18");
        String styleStr = CFG.getConfig(SECTION, "style", "BOLD");
        int size  = PhaseConfig.parseFontSize(sizeStr, win.height);
        int style = PhaseConfig.parseFontStyle(styleStr);
        barColor = PhaseConfig.parseColor(CFG.getConfig(SECTION, "color", "CYAN"), StandardColors.CYAN.color);

        String location = CFG.getConfig(SECTION, "location", "TOP_RIGHT").trim().toUpperCase();
        int padding = PhaseConfig.parseDimension(CFG.getConfig(SECTION, "padding", "1%"), win.height);
        int rowGap  = PhaseConfig.parseDimension(CFG.getConfig(SECTION, "row_gap", "4"), win.height);
        int gap     = PhaseConfig.parseDimension(CFG.getConfig(SECTION, "gap", "10"), win.width);
        barWidthPx  = PhaseConfig.parseDimension(CFG.getConfig(SECTION, "bar_width", "160"), win.width);

        FontTexture font = new FontTextureFactory(new Font(fontName, style, size), true).createFontTexture();
        int fontHeight = font.getFontHeight();
        barHeightPx = fontHeight * 0.6f;
        int rowHeight = fontHeight + rowGap;

        int maxNameLen = bands.stream().mapToInt(b -> b.name().length()).max().orElse(4);
        String labelFormat = "%-" + maxNameLen + "s";
        int labelWidthPx = font.getWidth("X".repeat(maxNameLen));
        int valueWidthPx = font.getWidth("0.00");

        int contentW = labelWidthPx + gap + (int) barWidthPx + gap + valueWidthPx;
        int contentH = bands.size() * rowHeight - rowGap;
        int panelPad = Math.max(4, rowGap);
        panelW = contentW + panelPad * 2;
        panelH = contentH + panelPad * 2;

        boolean right  = location.contains("RIGHT");
        boolean bottom = location.contains("BOTTOM");
        panelX = right  ? win.width  - padding - panelW : padding;
        panelY = bottom ? win.height - padding - panelH : padding;
        float contentX = panelX + panelPad;
        float contentY = panelY + panelPad;

        quad = new SolidQuad();
        quad.init(ctx);

        List<StringRenderer> labels = new ArrayList<>(bands.size());
        List<StringRenderer> values = new ArrayList<>(bands.size());
        rowBarX = new float[bands.size()];
        rowBarY = new float[bands.size()];

        for (int i = 0; i < bands.size(); i++) {
            float rowTop = contentY + i * rowHeight;
            float baselineY = rowTop + fontHeight;

            StringRenderer label = new StringRenderer(font);
            label.init(ctx);
            label.setText(String.format(labelFormat, bands.get(i).name()));
            label.setTextColor(barColor);
            label.setTransform(new Matrix4f().translate(contentX, baselineY, 0f));
            labels.add(label);

            rowBarX[i] = contentX + labelWidthPx + gap;
            rowBarY[i] = rowTop + (fontHeight - barHeightPx) / 2f;

            StringRenderer value = new StringRenderer(font);
            value.init(ctx);
            value.setText("0.00");
            value.setTextColor(barColor);
            value.setTransform(new Matrix4f().translate(rowBarX[i] + barWidthPx + gap, baselineY, 0f));
            values.add(value);
        }
        labelRenderers = labels;
        valueRenderers = values;
    }

    @Override
    public void screenRender(RenderContext ctx) {
        if (bands.isEmpty() || !cthugha.audioSource.debugBeats.value) return;
        BeatDetector bd = cthugha.beatDetector;
        if (bd == null) return;

        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

        quad.render(ctx, panelX, panelY, panelW, panelH, PANEL_COLOR);

        for (int i = 0; i < bands.size(); i++) {
            float strength = bd.getBeatStrength(i);
            quad.render(ctx, rowBarX[i], rowBarY[i], barWidthPx, barHeightPx, TRACK_COLOR);
            if (strength > 0f) {
                quad.render(ctx, rowBarX[i], rowBarY[i], barWidthPx * Math.min(1f, strength), barHeightPx, barColor);
            }
            labelRenderers.get(i).doRender(ctx);
            valueRenderers.get(i).setText(String.format("%.2f", strength));
            valueRenderers.get(i).doRender(ctx);
        }

        glDisable(GL_BLEND);
    }

    @Override
    public void dispose() {
        if (quad != null) quad.dispose();
        labelRenderers.forEach(StringRenderer::dispose);
        valueRenderers.forEach(StringRenderer::dispose);
    }
}
