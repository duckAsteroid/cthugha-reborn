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

import java.io.IOException;
import java.util.List;

public class AudioPipeline {

    public static final String CONFIG_SECTION = "audio";
    public static final String PREFERRED_DEVICE_KEY = "preferred_device";

    /** Name of the always-available simulated fallback source added in {@link #init}. */
    public static final String SIMULATED_SOURCE_NAME = "Simulated: Middle C (panned)";

    private static final Config CFG = Config.singleton();

    private final AudioSources audioSources = new AudioSources();
    private int selectedIndex = 0;
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

        // Always-available fallback so capture works even with no usable hardware line.
        audioSources.add(SimulatedSources.middleC(ctx.getClock()));
        LineAcquirer.allLinesMatching(LineAcquirer.IDEAL)
                .map(LineAcquirer.MixerLine::toAudioDataSource)
                .forEach(audioSources::add);

        if (!selectExact(CFG.getConfig(CONFIG_SECTION, PREFERRED_DEVICE_KEY, ""))) {
            selectPreferredSource("pipewire");
        }

        audioReader = new AudioReader(List.of(pboSink, freqProc));
        audioThread = Thread.ofVirtual().start(audioReader);
        audioReader.setLine(audioSources.list().get(selectedIndex));
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
