package io.github.duckasteroid.cthugha.display;

import com.asteroid.duck.opengl.util.RenderContext;
import com.asteroid.duck.opengl.util.audio.AudioDataSource;
import com.asteroid.duck.opengl.util.audio.AudioReader;
import com.asteroid.duck.opengl.util.audio.AudioSources;
import com.asteroid.duck.opengl.util.audio.LineAcquirer;
import com.asteroid.duck.opengl.util.audio.PboAudioSink;
import com.asteroid.duck.opengl.util.audio.analysis.BeatDetector;
import com.asteroid.duck.opengl.util.audio.analysis.FrequencyProcessor;
import com.asteroid.duck.opengl.util.audio.simulated.SimulatedSources;
import com.asteroid.duck.opengl.util.wave.AudioWave;
import io.github.duckasteroid.cthugha.config.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;

public class AudioPipeline {

    private static final Logger LOG = LoggerFactory.getLogger(AudioPipeline.class);

    public static final String CONFIG_SECTION = "audio";
    public static final String PREFERRED_DEVICE_KEY = "preferred_device";

    /** Name of the always-available simulated fallback source added in {@link #init}. */
    public static final String SIMULATED_SOURCE_NAME = "Simulated: Middle C (panned)";

    /** Guards against a tight crash/restart loop if the capture thread keeps dying immediately. */
    private static final int MAX_RESTARTS_PER_WINDOW = 5;
    private static final long RESTART_WINDOW_MS = 10_000;

    private static final Config CFG = Config.singleton();

    private final AudioSources audioSources = new AudioSources();
    private int selectedIndex = 0;
    private PboAudioSink pboSink;
    private FrequencyProcessor freqProc;
    private BeatDetector beatDetector;
    private AudioReader audioReader;
    private Thread audioThread;
    private int restartCount = 0;
    private long restartWindowStart = 0;

    public void init(RenderContext ctx) throws IOException {
        pboSink = PboAudioSink.create(AudioWave.AUDIO_BUFFER_SIZE, ctx);
        freqProc = new FrequencyProcessor(1024, 128, 48_000f, 20f, 20_000f, -80f, 0f);
        beatDetector = new BeatDetector(freqProc);
        freqProc.addSink(beatDetector);

        // Always-available fallback so capture works even with no usable hardware line.
        audioSources.add(SimulatedSources.middleC(ctx.getClock()));
        LineAcquirer.allLinesMatching(LineAcquirer.IDEAL)
                .map(LineAcquirer.MixerLine::toAudioDataSource)
                .forEach(audioSources::add);

        if (!selectExact(CFG.getConfig(CONFIG_SECTION, PREFERRED_DEVICE_KEY, ""))) {
            selectPreferredSource("pipewire");
        }

        audioReader = new AudioReader(List.of(pboSink, freqProc));
        startAudioThread();
        audioReader.setLine(audioSources.list().get(selectedIndex));
    }

    /**
     * Starts the capture thread with a supervisor that restarts it on any uncaught exception.
     * render-core's {@code AudioReader.run()} only catches {@code InterruptedException}, so a bug
     * in a line implementation (e.g. a buffer over/underflow) can otherwise kill capture for the
     * rest of the process, including the simulated fallback.
     */
    private void startAudioThread() {
        audioThread = Thread.ofVirtual().uncaughtExceptionHandler(this::onAudioThreadDied).start(audioReader);
    }

    private void onAudioThreadDied(Thread thread, Throwable error) {
        LOG.error("Audio capture thread died unexpectedly; restarting", error);

        long now = System.currentTimeMillis();
        if (now - restartWindowStart > RESTART_WINDOW_MS) {
            restartWindowStart = now;
            restartCount = 0;
        }
        if (++restartCount > MAX_RESTARTS_PER_WINDOW) {
            LOG.error("Audio capture crashed {} times within {} ms; giving up on auto-restart",
                    restartCount, RESTART_WINDOW_MS);
            return;
        }

        AudioDataSource current = audioSources.list().get(selectedIndex);
        audioReader = new AudioReader(List.of(pboSink, freqProc));
        startAudioThread();
        audioReader.setLine(current);
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
        selectedIndex = (selectedIndex + 1) % audioSources.size();
        AudioDataSource src = audioSources.list().get(selectedIndex);
        audioReader.setLine(src);
        return src;
    }

    public String getSelectedSourceName() {
        return audioSources.list().get(selectedIndex).getName();
    }

    /**
     * Switches capture to the source with this exact name, if one is currently available.
     *
     * @return {@code true} if a matching source was found and selected
     */
    public boolean selectSource(String name) {
        if (!selectExact(name)) return false;
        audioReader.setLine(audioSources.list().get(selectedIndex));
        return true;
    }

    /** Selects the source with this exact name. */
    private boolean selectExact(String name) {
        if (name == null || name.isBlank()) return false;
        List<AudioDataSource> sources = audioSources.list();
        for (int i = 0; i < sources.size(); i++) {
            if (sources.get(i).getName().equals(name)) {
                selectedIndex = i;
                return true;
            }
        }
        return false;
    }

    private void selectPreferredSource(String nameFragment) {
        List<AudioDataSource> sources = audioSources.list();
        for (int i = 0; i < sources.size(); i++) {
            if (sources.get(i).getName().toLowerCase().contains(nameFragment)) {
                selectedIndex = i;
                return;
            }
        }
    }
}
