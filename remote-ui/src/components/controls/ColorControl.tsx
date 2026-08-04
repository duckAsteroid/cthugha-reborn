interface ColorControlProps {
  r: number;
  g: number;
  b: number;
  disabled: boolean;
  onChange: (r: number, g: number, b: number) => void;
}

function toHex(r: number, g: number, b: number): string {
  const c = (n: number) => Math.max(0, Math.min(255, Math.round(n * 255))).toString(16).padStart(2, '0');
  return `#${c(r)}${c(g)}${c(b)}`;
}

function fromHex(hex: string): [number, number, number] {
  const clean = hex.replace('#', '');
  return [
    parseInt(clean.slice(0, 2), 16) / 255,
    parseInt(clean.slice(2, 4), 16) / 255,
    parseInt(clean.slice(4, 6), 16) / 255,
  ];
}

/** Native colour-swatch picker for a {@code ColorParam}'s R/G/B triple (each normalised [0, 1]). */
export function ColorControl({ r, g, b, disabled, onChange }: ColorControlProps) {
  return (
    <input
      type="color"
      value={toHex(r, g, b)}
      disabled={disabled}
      onChange={(e) => onChange(...fromHex(e.target.value))}
      aria-label="Colour"
      className="w-16 h-8 rounded bg-transparent border border-neutral-700 cursor-pointer disabled:opacity-40 disabled:cursor-not-allowed"
    />
  );
}
