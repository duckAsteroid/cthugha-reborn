# Beat, Tempo and Kick Detection

Package `com.asteroid.duck.opengl.util.audio.beat`. Tracks the kick drum, the tempo and the beat
of live music, and predicts beats so visuals land *on* them rather than after them.

| Use | Class |
|---|---|
| Live visuals: poll the beat from the render thread | `BeatTrackerSink` |
| Callbacks for every beat, kick and tempo change, on your own thread or offline | `BeatTracker` + `BeatListener` |
| Kick drum onsets only | `KickDetector` + `BeatListener` |
| Per-band energy flashes (not timing) | `BeatDetector` — see [audio.md](audio.md) |

---

## BeatTrackerSink — live use (start here)

An `AudioSink`: hand it to `AudioInput` with your other sinks. It analyses on the audio thread
and publishes an immutable snapshot; every getter is safe on the GL thread and extrapolates the
snapshot to the current moment, so the phase advances smoothly every frame.

```java
private static final float SAMPLE_RATE = 48_000f;
private final BeatTrackerSink beats = new BeatTrackerSink(SAMPLE_RATE);
private AudioInput audioInput;

@Override
public void init(RenderContext ctx) {
    audioInput = AudioInput.start(ctx, List.of(freqProc, beats));
    audioInput.bindKeys(ctx);
}

@Override
public void doRender(RenderContext ctx) {
    float pulse = (float) Math.exp(-6 * beats.beatPhase()) * beats.confidence(); // hits on the beat
    float kick  = beats.kickStrength();
    renderer.setTransform(transform.identity().scale(1 + 0.06f * pulse + 0.04f * kick));
}

@Override
public void dispose() {
    audioInput.close();
}
```

| Method | Returns |
|---|---|
| `bpm()` | Tempo in BPM; `NaN` until the first estimate (~3 s) and after silence |
| `confidence()` | `[0, 1]`; falls when beats stop landing on onsets, 0 after silence |
| `beatPhase()` | `[0, 1)`: 0 on the beat, rising to 1 just before the next |
| `beatCount()` | Beats so far; `% 4` cycles a bar, but which beat is the downbeat is not known |
| `kickStrength()` | Last kick's strength `[0, 1]`, decaying with a 150 ms time constant |
| `setLatency(Duration)` / `latency()` | How far the visuals lead the analysis (default 0) |

- **Animate from `beatPhase()`**, not from discrete events: the phase is predicted, so motion
  stays on the beat even when the kick is buried in the mix or briefly drops out.
- **Scale pulses by `confidence()`**: they then fade through percussion-free breakdowns and stop
  when the music stops, instead of pulsing at the old tempo.
- **Latency:** sound reaches the listener later than the samples reach the sink (output buffer,
  display lag). A positive latency (typically 20–80 ms) moves the visuals earlier to compensate.
  The spectrum experiments bind `.` / `,` to ±10 ms.
- **Input format:** `write()` expects 16-bit little-endian stereo PCM (what `AudioInput`
  captures), mixed to mono. The constructor's sample rate must match the capture rate.
- **Cost:** about 1% of real time on the audio thread; the worst single write is ~0.3 ms.

---

## BeatTracker + BeatListener — events

`BeatTracker` is the analyser behind the sink. Feed it mono floats; it calls the listener on the
same thread before `process()` returns.

```java
BeatTracker tracker = new BeatTracker(sampleRate, new BeatListener() {
    @Override public void onBeat(double time) { ... }
    @Override public void onKick(double time, float strength) { ... }
    @Override public void onTempo(double time, double bpm, float confidence) { ... }
});

tracker.process(samples, offset, length);   // mono, [-1, 1], any block size
```

`BeatListener` methods all default to no-ops:

| Callback | When |
|---|---|
| `onBeat(time)` | A beat, reported as the sample clock reaches its *predicted* time (never revised) |
| `onKick(time, strength)` | A kick onset, timed at the start of its rise; `strength` in `[0, 1]` |
| `onTempo(time, bpm, confidence)` | Every 0.5 s once there is an estimate. `NaN` bpm with confidence 0 = beat lost (silence) |
| `onDownbeat(time)` | Reserved for analysers that find bars; `BeatTracker` does not call it |

- **Time is the sample clock**: seconds = samples consumed / sample rate. Nothing reads the wall
  clock, so running a file through faster than real time gives exactly the events a live run would.
- **Single-threaded**: call `process()` from one thread only.
- **Tempo prior**: `new BeatTracker(sampleRate, listener, priorBpm, priorOctaves)` changes the
  preferred tempo (default 120 BPM, ±1 octave). Use it to bias against double/half-tempo locks
  for music outside dance tempos.
- `period()` gives the current beat period in seconds (`NaN` before the first estimate).
- After 1.5 s below −60 dBFS the tracker forgets tempo and phase and locks on afresh when sound
  returns (~3–5 s).

### How it works

| Stage | Class | Method |
|---|---|---|
| Kick onsets | `KickDetector` | 80–250 Hz band-pass, 20 ms level every 5 ms, peak-picked dB rise |
| Onset strength | `SpectralFlux` | Log-banded spectral flux, ~100 frames/s (`frameRate()` is exact) |
| Tempo | `TempoEstimator` | Autocorrelation of 8 s of flux with a log-normal tempo prior |
| Beat phase | `BeatPhaseTracker` | Phase-locked loop on flux + kicks, predicting the next beat |

---

## KickDetector — kicks only

```java
KickDetector kicks = new KickDetector(sampleRate, new BeatListener() {
    @Override public void onKick(double time, float strength) { ... }
});
kicks.process(samples, offset, length);
```

`KickDetector.Settings(lowHz, highHz, k, minRiseDb)` tunes it; `Settings.DEFAULT` is
`(80, 250, 2.0, 10.0)`. The 80–250 Hz band is deliberately above the sub-bass: a sustained sub
masks a 40–150 Hz detector. It reports any sharp low-frequency attack, so off-beat bass plucks
in house music also register as kicks.

---

## Accuracy

Measured with the `beatEval` harness (`./gradlew :render-core:beatEval`) on GTZAN with
GTZAN-Rhythm annotations, scored after a 5 s warm-up. Details and method:
`docs/beat-tracking-plan.md` in the repository.

| | Beat F | Tempo Acc1 / Acc2 |
|---|--:|--:|
| `BeatTracker`, disco | 0.94 | 94% / 98% |
| `BeatTracker`, all genres | 0.74 | 70% / 89% |
| `BeatDetector`-based beats, all genres | 0.32 | — |

Known limits: roughly one non-dance track in five locks at double or half tempo (the Acc1/Acc2
gap); bar position (downbeat) is not detected.
