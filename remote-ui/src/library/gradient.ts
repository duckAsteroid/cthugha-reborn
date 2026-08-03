export type Curve = 'linear' | 'ease-in' | 'ease-out' | 'ease-in-out';

export interface GradientStop {
  id: string;
  position: number; // 0..1
  color: string; // "#rrggbb"
  curve: Curve; // interpolation used for the segment from this stop to the next
}

function ease(t: number, curve: Curve): number {
  switch (curve) {
    case 'ease-in':
      return t * t;
    case 'ease-out':
      return 1 - (1 - t) * (1 - t);
    case 'ease-in-out':
      return t < 0.5 ? 2 * t * t : 1 - (-2 * t + 2) ** 2 / 2;
    default:
      return t;
  }
}

function hexToRgb(hex: string): [number, number, number] {
  const clean = hex.replace('#', '');
  return [parseInt(clean.slice(0, 2), 16), parseInt(clean.slice(2, 4), 16), parseInt(clean.slice(4, 6), 16)];
}

function rgbToHex(r: number, g: number, b: number): string {
  const c = (n: number) => Math.max(0, Math.min(255, Math.round(n))).toString(16).padStart(2, '0');
  return `#${c(r)}${c(g)}${c(b)}`;
}

function lerpColor(a: string, b: string, t: number): string {
  const [ar, ag, ab] = hexToRgb(a);
  const [br, bg, bb] = hexToRgb(b);
  return rgbToHex(ar + (br - ar) * t, ag + (bg - ag) * t, ab + (bb - ab) * t);
}

/** The gradient's colour at a single position in [0,1], interpolating between the surrounding stops using the earlier stop's outgoing curve. Clamps to the nearest end colour outside the stop range. `stops` must already be sorted by position. */
function colorAt(sorted: GradientStop[], pos: number): string {
  if (pos <= sorted[0].position) return sorted[0].color;
  if (pos >= sorted[sorted.length - 1].position) return sorted[sorted.length - 1].color;
  let segIdx = 0;
  while (segIdx < sorted.length - 2 && pos > sorted[segIdx + 1].position) segIdx++;
  const s0 = sorted[segIdx];
  const s1 = sorted[segIdx + 1];
  const span = s1.position - s0.position;
  const localT = span <= 0 ? 0 : (pos - s0.position) / span;
  return lerpColor(s0.color, s1.color, ease(localT, s0.curve));
}

/** The gradient's colour at a single position in [0,1] — handy when adding a new stop partway along an existing gradient. */
export function sampleGradientAt(stops: GradientStop[], pos: number): string {
  return colorAt([...stops].sort((a, b) => a.position - b.position), pos);
}

/** Samples `count` colours evenly across [0,1] — see {@link sampleGradientAt} for the per-point rule. */
export function sampleGradient(stops: GradientStop[], count: number): string[] {
  const sorted = [...stops].sort((a, b) => a.position - b.position);
  const result: string[] = [];
  for (let i = 0; i < count; i++) {
    result.push(colorAt(sorted, count === 1 ? 0 : i / (count - 1)));
  }
  return result;
}
