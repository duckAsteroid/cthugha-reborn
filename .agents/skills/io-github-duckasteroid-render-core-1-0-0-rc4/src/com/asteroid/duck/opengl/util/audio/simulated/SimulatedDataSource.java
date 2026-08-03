package com.asteroid.duck.opengl.util.audio.simulated;

import com.asteroid.duck.opengl.util.audio.AudioDataSource;
import com.asteroid.duck.opengl.util.audio.AudioLine;
import com.asteroid.duck.opengl.util.audio.AudioSourceUnavailableException;
import com.asteroid.duck.opengl.util.stats.Stats;
import com.asteroid.duck.opengl.util.stats.StatsFactory;
import com.asteroid.duck.opengl.util.timer.TimeSource;
import com.asteroid.duck.opengl.util.timer.Clock;
import com.asteroid.duck.opengl.util.timer.ClockImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sound.sampled.*;
import java.io.IOException;

import static com.asteroid.duck.opengl.util.audio.LineAcquirer.IDEAL;

/**
 * A synthetic {@link AudioDataSource} that samples a {@link StereoDataSource} against a
 * {@link Clock} instead of reading from real hardware. Only supports
 * {@link com.asteroid.duck.opengl.util.audio.LineAcquirer#IDEAL}.
 */
public class SimulatedDataSource implements AudioDataSource {

	private static final Logger LOG = LoggerFactory.getLogger(SimulatedDataSource.class);

	private final String name;
	private final Clock timer;
	private final StereoDataSource source;

	/**
	 * @param name   display name for this source, used in logs and UI
	 * @param timer  clock driving sample generation
	 * @param source the waveform/positioning to sample
	 */
	public SimulatedDataSource(String name, Clock timer, StereoDataSource source) {
		this.name = name;
		this.timer = timer;
		this.source = source;
	}

	@Override
	public String getName() {
		return name;
	}

	@Override
	public AudioFormat[] getSupportedFormats() {
		return new AudioFormat[]{IDEAL};
	}

	@Override
	public AudioLine open(AudioFormat format, int bufferSize) throws AudioSourceUnavailableException {
		if (!IDEAL.equals(format)) {
			throw new AudioSourceUnavailableException("Unsupported format: " + format);
		}
		return new SimulatedAudioLine(timer, source, format, bufferSize);
	}

	public static void main(String[] args) throws LineUnavailableException, AudioSourceUnavailableException {
		DataLine.Info info = new DataLine.Info(SourceDataLine.class, IDEAL);
		// TODO Lets try to play our simulated audio via output to hear it...
		ClockImpl t = new ClockImpl(TimeSource.systemNanoTimeInstance());
		t.reset();
		t.setPaused(false);
		com.asteroid.duck.opengl.util.audio.LineAcquirer.MixerLine mixerLine =
				com.asteroid.duck.opengl.util.audio.LineAcquirer.allLinesMatching(info).toList().get(0);
		SimulatedDataSource simulated = SimulatedSources.middleC(t);
		try(Mixer mixer = mixerLine.mixer()) {
			byte[] audioBuffer = new byte[32];
			SourceDataLine output = (SourceDataLine) mixer.getLine(info);
			LOG.info("Running on {}", mixerLine);
			output.open(IDEAL);
			try (AudioLine line = simulated.open(IDEAL, audioBuffer.length)) {
				output.start();
				line.start();
				Stats readStats = StatsFactory.stats("read.depth");
				while (!exitKeyPressed()) {
					t.update();

					int read = line.read(audioBuffer, 0, audioBuffer.length);
					readStats.add(read);
					output.write(audioBuffer, 0, read);
				}
				LOG.info("{}", readStats);
			}
			output.close();
		}
	}

	private static boolean exitKeyPressed() {
		try {
			while (System.in.available() > 0) {
				// Read the input character
				int key = System.in.read();

				// Check if the input is 'q' (or your desired exit key)
				if (key == 'q' || key == 'Q') {
					LOG.info("Exiting...");
					return true; // Exit the loop
				}
			}
			return false;
		}
		catch(IOException ioe) {
			LOG.error("Error reading stdin", ioe);
			return true;
		}
	}
}
