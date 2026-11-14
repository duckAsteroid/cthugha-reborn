export const SCRIPT_HELP: { section: string; items: { name: string; desc: string }[] }[] = [
  {
    section: 'Variables',
    items: [
      { name: 't',       desc: 'elapsed seconds since start' },
      { name: 'TWO_PI',  desc: '2π ≈ 6.283' },
    ],
  },
  {
    section: 'Wave helpers — return [0, 1]',
    items: [
      { name: 'sine(hz)',         desc: 'smooth sine wave' },
      { name: 'cosine(hz)',       desc: 'smooth cosine wave' },
      { name: 'saw(hz)',          desc: 'sawtooth ramp 0→1' },
      { name: 'tri(hz)',          desc: 'triangle 0→1→0' },
      { name: 'pulse(hz, duty)',  desc: 'square wave; duty 0–1 e.g. 0.5' },
      { name: 'phase(hz)',        desc: 'raw angular phase in radians' },
    ],
  },
  {
    section: 'Beat detection — return [0, 1]',
    items: [
      { name: 'bass()',        desc: 'kick drum / sub-bass beat strength' },
      { name: 'snare()',       desc: 'snare / clap / attack beat strength' },
      { name: 'hihat()',       desc: 'hi-hat / cymbal beat strength' },
      { name: 'beat(name)',    desc: 'strength for any named band; 0 if unknown' },
    ],
  },
  {
    section: 'Tempo — locked to the predicted beat',
    items: [
      { name: 'beatPulse()',          desc: '1 exactly on each beat, decaying; fades when the beat is lost' },
      { name: 'beatPulse(sharpness)', desc: 'as beatPulse(), higher = snappier (default 6)' },
      { name: 'beatPhase()',          desc: '0 on the beat, rising to 1 before the next' },
      { name: 'beatRamp(beats)',      desc: 'ramp 0→1 every n beats; beatRamp(4) once per bar' },
      { name: 'beatSine(beats)',      desc: 'smooth wave every n beats, 1 on the beat' },
      { name: 'beatCount()',          desc: 'beats so far; beatCount() % 4 == 0 every 4th beat' },
      { name: 'kick()',               desc: 'kick drum strength [0, 1], decaying' },
      { name: 'bpm()',                desc: 'tempo in BPM; 0 until locked on (~3 s)' },
      { name: 'beatConfidence()',     desc: 'confidence [0, 1] in the tempo' },
    ],
  },
  {
    section: 'Random',
    items: [
      { name: 'random()',        desc: 'uniform random value in [0, 1)' },
      { name: 'random(lo, hi)',  desc: 'uniform random value in [lo, hi)' },
    ],
  },
  {
    section: 'Script state — ephemeral, resets on restart, not saved with configs',
    items: [
      { name: 'state.get(key, default)',  desc: 'per-binding value; default if unset' },
      { name: 'state.set(key, value)',    desc: 'store a per-binding value; returns it, so it composes in an expression' },
      { name: 'global.get(key, default)', desc: 'value shared across every binding' },
      { name: 'global.set(key, value)',   desc: 'store a shared value; returns it, same as state.set' },
    ],
  },
  {
    section: 'Also in scope',
    items: [
      { name: 'Math.*', desc: 'sin, cos, abs, min, max, pow, sqrt…' },
    ],
  },
];
