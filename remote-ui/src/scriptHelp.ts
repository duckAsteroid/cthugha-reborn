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
    section: 'Random',
    items: [
      { name: 'random()',        desc: 'uniform random value in [0, 1)' },
      { name: 'random(lo, hi)',  desc: 'uniform random value in [lo, hi)' },
    ],
  },
  {
    section: 'Also in scope',
    items: [
      { name: 'Math.*', desc: 'sin, cos, abs, min, max, pow, sqrt…' },
    ],
  },
];
