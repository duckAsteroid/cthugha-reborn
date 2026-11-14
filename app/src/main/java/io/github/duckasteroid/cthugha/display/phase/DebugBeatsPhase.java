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
import io.github.duckasteroid.cthugha.display.BeatClock;
import org.joml.Matrix4f;
import org.joml.Vector4f;

import java.awt.Font;
import java.awt.Rectangle;
import java.io.IOException;
import java.util.List;

import static org.lwjgl.opengl.GL11.*;

/**
 * HUD overlay for tuning beat detection without the remote UI, in two sections:
 *
 * <ul>
 *   <li><b>Bands</b> — each beat-detection band's name, a filled bar and its numeric strength,
 *   for tuning {@code [beats]} band ranges and binding-script thresholds (e.g.
 *   {@code "bass() > 0.7"}).</li>
 *   <li><b>Tracker</b> — the {@link BeatClock}: {@code kick} ({@code kick()}), {@code pulse}
 *   ({@code beatPulse()}), {@code conf} ({@code beatConfidence()}) and {@code beat}, four boxes
 *   for the beats of a bar ({@code beatCount() % 4}), the current one filling with
 *   {@code beatPhase()} and flashing on the beat, next to the tempo in BPM ({@code --} until
 *   the tracker locks on).</li>
 * </ul>
 *
 * <p>Shown only while {@link io.github.duckasteroid.cthugha.display.AudioSourceNode#debugBeats}
 * is enabled (toggle on the "Audio" tab, or the {@code D} key by default). Bands, their order,
 * and count come from {@link JCthugha#beatDetector} once it's initialised by {@link WavePhase};
 * this phase is placed after {@code wavePhase} in {@link JCthugha#createPhases()} so the detector
 * — and therefore row layout — is already available by the time {@link #init} runs.
 *
 * <p>Configurable in the {@code [DebugBeats]} INI section: {@code font}, {@code size},
 * {@code style}, {@code color} (band rows), {@code tempo_color} (tracker rows), {@code location},
 * {@code padding}, {@code bar_width}, {@code gap}. A monospaced font is recommended so the
 * name/bar/number columns line up.
 *
 * <p>Each section's labels and values are drawn as one multi-line {@link StringRenderer} apiece
 * (rows are one line height apart), since every renderer holds a texture unit for the whole run
 * and there are few to spare.</p>
 */
public class DebugBeatsPhase implements RenderPhase {

    private static final String SECTION = "DebugBeats";
    private static final Config CFG = Config.singleton();
    private static final Vector4f PANEL_COLOR = new Vector4f(0f, 0f, 0f, 0.55f);
    private static final Vector4f TRACK_COLOR = new Vector4f(1f, 1f, 1f, 0.18f);
    private static final List<String> TRACKER_LABELS = List.of("kick", "pulse", "conf", "beat");
    private static final int BEATS_PER_BAR = 4;

    private final JCthugha cthugha;

    private List<FrequencyBand> bands = List.of();
    private SolidQuad quad;
    private Vector4f bandColor;
    private Vector4f tempoColor;
    private final Vector4f dimTempoColor = new Vector4f();
    private final Vector4f flashColor = new Vector4f();
    private Section bandSection;
    private Section trackerSection;

    private float panelX, panelY, panelW, panelH;
    private float barX, barWidthPx, barHeightPx, beatGapPx;

    /** A block of rows: one multi-line renderer for the labels, one for the values. */
    private static final class Section {
        final StringRenderer labels;
        final StringRenderer values;
        final float[] barY;
        final StringBuilder text = new StringBuilder();
        String shown = "";

        Section(StringRenderer labels, StringRenderer values, float[] barY) {
            this.labels = labels;
            this.values = values;
            this.barY = barY;
        }

        /** Starts collecting this frame's values, one line per row. */
        void beginValues() {
            text.setLength(0);
        }

        void addValue(String value) {
            if (!text.isEmpty()) text.append('\n');
            text.append(value);
        }

        /** Re-lays out the value text only when it has changed. */
        void render(RenderContext ctx) {
            if (!shown.contentEquals(text)) {
                shown = text.toString();
                values.setText(shown);
            }
            labels.doRender(ctx);
            values.doRender(ctx);
        }

        void dispose() {
            labels.dispose();
            values.dispose();
        }
    }

    public DebugBeatsPhase(JCthugha cthugha) {
        this.cthugha = cthugha;
    }

    @Override
    public void init(RenderContext ctx) throws IOException {
        BeatDetector bd = cthugha.beatDetector;
        bands = bd != null ? bd.getBands() : List.of();

        Rectangle win = ctx.getWindow();

        String fontName = CFG.getConfig(SECTION, "font",  "Monospaced");
        String sizeStr  = CFG.getConfig(SECTION, "size",  "18");
        String styleStr = CFG.getConfig(SECTION, "style", "BOLD");
        int size  = PhaseConfig.parseFontSize(sizeStr, win.height);
        int style = PhaseConfig.parseFontStyle(styleStr);
        bandColor  = PhaseConfig.parseColor(CFG.getConfig(SECTION, "color", "CYAN"), StandardColors.CYAN.color);
        tempoColor = PhaseConfig.parseColor(CFG.getConfig(SECTION, "tempo_color", "GOLD"), StandardColors.GOLD.color);
        dimTempoColor.set(tempoColor.x, tempoColor.y, tempoColor.z, tempoColor.w * 0.4f);

        String location = CFG.getConfig(SECTION, "location", "TOP_RIGHT").trim().toUpperCase();
        int padding = PhaseConfig.parseDimension(CFG.getConfig(SECTION, "padding", "1%"), win.height);
        int gap     = PhaseConfig.parseDimension(CFG.getConfig(SECTION, "gap", "10"), win.width);
        barWidthPx  = PhaseConfig.parseDimension(CFG.getConfig(SECTION, "bar_width", "160"), win.width);

        FontTexture font = new FontTextureFactory(new Font(fontName, style, size), true).createFontTexture();
        int lineHeight = font.getFontHeight();
        barHeightPx = lineHeight * 0.6f;
        beatGapPx = Math.max(2f, barWidthPx * 0.03f);
        // Between the band rows and the tracker rows
        int sectionGap = bands.isEmpty() ? 0 : lineHeight / 2;

        List<String> bandNames = bands.stream().map(FrequencyBand::name).toList();
        int maxNameLen = 0;
        for (String name : bandNames) maxNameLen = Math.max(maxNameLen, name.length());
        for (String name : TRACKER_LABELS) maxNameLen = Math.max(maxNameLen, name.length());
        int labelWidthPx = font.getWidth("X".repeat(maxNameLen));
        int valueWidthPx = font.getWidth("000.0");

        int contentW = labelWidthPx + gap + (int) barWidthPx + gap + valueWidthPx;
        int contentH = (bands.size() + TRACKER_LABELS.size()) * lineHeight + sectionGap;
        int panelPad = Math.max(4, lineHeight / 4);
        panelW = contentW + panelPad * 2;
        panelH = contentH + panelPad * 2;

        boolean right  = location.contains("RIGHT");
        boolean bottom = location.contains("BOTTOM");
        panelX = right  ? win.width  - padding - panelW : padding;
        panelY = bottom ? win.height - padding - panelH : padding;
        float contentX = panelX + panelPad;
        float contentY = panelY + panelPad;
        barX = contentX + labelWidthPx + gap;
        float valueX = barX + barWidthPx + gap;

        quad = new SolidQuad();
        quad.init(ctx);

        if (!bands.isEmpty()) {
            bandSection = section(ctx, font, bandNames, bandColor, contentX, valueX, contentY, lineHeight);
        }
        float trackerTop = contentY + bands.size() * lineHeight + sectionGap;
        trackerSection = section(ctx, font, TRACKER_LABELS, tempoColor, contentX, valueX, trackerTop, lineHeight);
    }

    private Section section(RenderContext ctx, FontTexture font, List<String> names, Vector4f color,
                            float labelX, float valueX, float top, int lineHeight) throws IOException {
        float baselineY = top + lineHeight;
        StringRenderer labels = new StringRenderer(font);
        labels.init(ctx);
        labels.setText(String.join("\n", names));
        labels.setTextColor(color);
        labels.setTransform(new Matrix4f().translate(labelX, baselineY, 0f));

        StringRenderer values = new StringRenderer(font);
        values.init(ctx);
        values.setTextColor(color);
        values.setTransform(new Matrix4f().translate(valueX, baselineY, 0f));

        float[] barY = new float[names.size()];
        for (int i = 0; i < barY.length; i++) {
            barY[i] = top + i * lineHeight + (lineHeight - barHeightPx) / 2f;
        }
        return new Section(labels, values, barY);
    }

    @Override
    public void screenRender(RenderContext ctx) {
        if (trackerSection == null || !cthugha.audioSource.debugBeats.value) return;

        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

        quad.render(ctx, panelX, panelY, panelW, panelH, PANEL_COLOR);

        if (bandSection != null) {
            BeatDetector bd = cthugha.beatDetector;
            bandSection.beginValues();
            for (int i = 0; i < bands.size(); i++) {
                renderBar(ctx, bandSection, i, bd != null ? bd.getBeatStrength(i) : 0f, bandColor);
            }
            bandSection.render(ctx);
        }

        BeatClock clock = cthugha.beatClock;
        float pulse = clock != null ? (float) clock.beatPulse() : 0f;
        trackerSection.beginValues();
        renderBar(ctx, trackerSection, 0, clock != null ? clock.kickStrength() : 0f, tempoColor);
        renderBar(ctx, trackerSection, 1, pulse, tempoColor);
        renderBar(ctx, trackerSection, 2, clock != null ? clock.confidence() : 0f, tempoColor);
        renderBeats(ctx, trackerSection, 3, clock, pulse);
        trackerSection.render(ctx);

        glDisable(GL_BLEND);
    }

    private void renderBar(RenderContext ctx, Section section, int row, float strength, Vector4f color) {
        float y = section.barY[row];
        quad.render(ctx, barX, y, barWidthPx, barHeightPx, TRACK_COLOR);
        if (strength > 0f) {
            quad.render(ctx, barX, y, barWidthPx * Math.min(1f, strength), barHeightPx, color);
        }
        section.addValue(formatHundredths(Math.round(strength * 100)));
    }

    /**
     * Four boxes, one per beat of the bar: beats already past this bar dim, the current one
     * filling with the beat phase and flashing with the pulse; the tempo in the value column.
     */
    private void renderBeats(RenderContext ctx, Section section, int row, BeatClock clock, float pulse) {
        double bpm = clock != null ? clock.bpm() : Double.NaN;
        boolean locked = !Double.isNaN(bpm) && clock.confidence() > 0f;
        int current = locked ? (int) Math.floorMod(clock.beatCount(), BEATS_PER_BAR) : -1;
        float phase = locked ? (float) clock.beatPhase() : 0f;

        float y = section.barY[row];
        float boxW = (barWidthPx - beatGapPx * (BEATS_PER_BAR - 1)) / BEATS_PER_BAR;
        for (int beat = 0; beat < BEATS_PER_BAR; beat++) {
            float x = barX + beat * (boxW + beatGapPx);
            quad.render(ctx, x, y, boxW, barHeightPx, TRACK_COLOR);
            if (beat < current) {
                quad.render(ctx, x, y, boxW, barHeightPx, dimTempoColor);
            } else if (beat == current) {
                quad.render(ctx, x, y, boxW * phase, barHeightPx, tempoColor);
                if (pulse > 0.01f) {
                    flashColor.set(1f, 1f, 1f, 0.8f * pulse);
                    quad.render(ctx, x, y, boxW, barHeightPx, flashColor);
                }
            }
        }
        section.addValue(locked ? formatTenths(Math.round(bpm * 10)) : "  --");
    }

    /** {@code 0.00}-style, without the per-frame cost of {@code String.format}. */
    private static String formatHundredths(int hundredths) {
        int h = Math.max(0, hundredths);
        return (h / 100) + "." + (h % 100 < 10 ? "0" : "") + (h % 100);
    }

    /** Right-aligned {@code 000.0}-style. */
    private static String formatTenths(long tenths) {
        String s = (tenths / 10) + "." + (tenths % 10);
        return " ".repeat(Math.max(0, 5 - s.length())) + s;
    }

    @Override
    public void dispose() {
        if (quad != null) quad.dispose();
        if (bandSection != null) bandSection.dispose();
        if (trackerSection != null) trackerSection.dispose();
        bandSection = null;
        trackerSection = null;
    }
}
