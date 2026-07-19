package io.github.duckasteroid.cthugha.display;

import com.asteroid.duck.opengl.util.RenderContext;
import com.asteroid.duck.opengl.util.audio.AudioDataSource;
import com.asteroid.duck.opengl.util.audio.AudioReader;
import com.asteroid.duck.opengl.util.audio.LineAcquirer;
import com.asteroid.duck.opengl.util.audio.PboAudioSink;
import com.asteroid.duck.opengl.util.audio.analysis.BeatDetector;
import com.asteroid.duck.opengl.util.audio.analysis.FrequencyProcessor;
import com.asteroid.duck.opengl.util.wave.AudioWave;
import io.github.duckasteroid.cthugha.config.Config;

import java.io.IOException;
import java.util.List;

public class AudioPipeline {

    public static final String CONFIG_SECTION = "audio";
    public static final String PREFERRED_DEVICE_KEY = "preferred_device";

    private static final Config CFG = Config.singleton();

    private LineAcquirer lineAcquirer;
    private PboAudioSink pboSink;
    private FrequencyProcessor freqProc;
    private BeatDetector beatDetector;
    private AudioReader audioReader;
    private Thread audioThread;

    public void init(RenderContext ctx) throws IOException {
        pboSink = PboAudioSink.create(AudioWave.AUDIO_BUFFER_SIZE, ctx);
        freqProc = new FrequencyProcessor(1024, 128, 48_000f, 20f, 20_000f, -80f, 0f);
        beatDetector = new BeatDetector(freqProc);
        freqProc.addSink(beatDetector);

        lineAcquirer = new LineAcquirer();
        lineAcquirer.init(ctx, LineAcquirer.IDEAL);
        if (!selectExact(CFG.getConfig(CONFIG_SECTION, PREFERRED_DEVICE_KEY, ""))) {
            selectPreferredSource("pipewire");
        }

        audioReader = new AudioReader(List.of(pboSink, freqProc));
        audioThread = Thread.ofVirtual().start(audioReader);
        audioReader.setLine(lineAcquirer.getSelectedSource());
    }

    public void update() {
        pboSink.upload();
        freqProc.process();
    }

    public void dispose() {
        if (audioReader != null) {
            audioReader.setRunning(false);
            audioReader.setLine(null);
        }
        if (audioThread != null) {
            audioThread.interrupt();
            try { audioThread.join(2000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }
    }

    public PboAudioSink getPboSink() { return pboSink; }

    public FrequencyProcessor getFreqProc() { return freqProc; }

    public BeatDetector getBeatDetector() { return beatDetector; }

    public AudioDataSource cycleSource() {
        lineAcquirer.next();
        AudioDataSource src = lineAcquirer.getSelectedSource();
        audioReader.setLine(src);
        return src;
    }

    public String getSelectedSourceName() {
        return lineAcquirer.getSelectedSource().getName();
    }

    /**
     * Switches capture to the source with this exact name, if one is currently available.
     * Cycles through every source at most once; leaves the selection unchanged if not found.
     *
     * @return {@code true} if a matching source was found and selected
     */
    public boolean selectSource(String name) {
        if (!selectExact(name)) return false;
        audioReader.setLine(lineAcquirer.getSelectedSource());
        return true;
    }

    /** Selects the source with this exact name, cycling through every source at most once. */
    private boolean selectExact(String name) {
        if (name == null || name.isBlank()) return false;
        AudioDataSource first = lineAcquirer.getSelectedSource();
        if (first.getName().equals(name)) return true;
        AudioDataSource s;
        do {
            lineAcquirer.next();
            s = lineAcquirer.getSelectedSource();
            if (s.getName().equals(name)) return true;
        } while (s != first);
        return false;
    }

    private void selectPreferredSource(String nameFragment) {
        AudioDataSource first = lineAcquirer.getSelectedSource();
        if (first.getName().toLowerCase().contains(nameFragment)) return;
        do {
            lineAcquirer.next();
            AudioDataSource s = lineAcquirer.getSelectedSource();
            if (s == first) return;
            if (s.getName().toLowerCase().contains(nameFragment)) return;
        } while (true);
    }
}
