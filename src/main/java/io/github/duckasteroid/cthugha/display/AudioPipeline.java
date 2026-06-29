package io.github.duckasteroid.cthugha.display;

import com.asteroid.duck.opengl.util.RenderContext;
import com.asteroid.duck.opengl.util.audio.AudioDataSource;
import com.asteroid.duck.opengl.util.audio.AudioReader;
import com.asteroid.duck.opengl.util.audio.LineAcquirer;
import com.asteroid.duck.opengl.util.audio.PboAudioSink;
import com.asteroid.duck.opengl.util.audio.analysis.FrequencyProcessor;
import com.asteroid.duck.opengl.util.wave.AudioWave;

import java.io.IOException;
import java.util.List;

public class AudioPipeline {

    private LineAcquirer lineAcquirer;
    private PboAudioSink pboSink;
    private FrequencyProcessor freqProc;
    private AudioReader audioReader;
    private Thread audioThread;

    public void init(RenderContext ctx) throws IOException {
        pboSink = PboAudioSink.create(AudioWave.AUDIO_BUFFER_SIZE, ctx);
        freqProc = new FrequencyProcessor(1024, 128, 48_000f, 20f, 20_000f, -80f, 0f);

        lineAcquirer = new LineAcquirer();
        lineAcquirer.init(ctx, LineAcquirer.IDEAL);
        selectPreferredSource("pipewire");

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

    public AudioDataSource cycleSource() {
        lineAcquirer.next();
        AudioDataSource src = lineAcquirer.getSelectedSource();
        audioReader.setLine(src);
        return src;
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
