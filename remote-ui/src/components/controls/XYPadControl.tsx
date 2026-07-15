import { useCallback, useEffect, useRef, useState } from 'react';

interface XYPadControlProps {
  x: number;
  y: number;
  minX: number;
  maxX: number;
  minY: number;
  maxY: number;
  disabled: boolean;
  onChange: (x: number, y: number) => void;
}

const DEBOUNCE_MS = 150;

function clamp01(t: number): number {
  return Math.min(1, Math.max(0, t));
}

/**
 * A square pad representing the parameter's own coordinate space (X and Y share the same
 * min/max by convention — see XYParam), with a crosshair marking the current position.
 * Tap or drag anywhere on the pad to set both axes at once.
 */
export function XYPadControl({ x, y, minX, maxX, minY, maxY, disabled, onChange }: XYPadControlProps) {
  const [local, setLocal] = useState({ x, y });
  const padRef = useRef<HTMLDivElement | null>(null);
  const timerRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  useEffect(() => {
    setLocal({ x, y });
  }, [x, y]);

  const commit = useCallback(
    (nx: number, ny: number) => {
      if (timerRef.current !== null) clearTimeout(timerRef.current);
      timerRef.current = setTimeout(() => {
        onChange(nx, ny);
        timerRef.current = null;
      }, DEBOUNCE_MS);
    },
    [onChange],
  );

  const setFromClientPoint = useCallback(
    (clientX: number, clientY: number) => {
      const el = padRef.current;
      if (!el) return;
      const rect = el.getBoundingClientRect();
      const tx = clamp01((clientX - rect.left) / rect.width);
      const ty = clamp01((clientY - rect.top) / rect.height);
      const nx = minX + tx * (maxX - minX);
      const ny = minY + ty * (maxY - minY);
      setLocal({ x: nx, y: ny });
      commit(nx, ny);
    },
    [minX, maxX, minY, maxY, commit],
  );

  const handlePointerDown = (e: React.PointerEvent<HTMLDivElement>) => {
    if (disabled) return;
    e.currentTarget.setPointerCapture(e.pointerId);
    setFromClientPoint(e.clientX, e.clientY);
  };

  const handlePointerMove = (e: React.PointerEvent<HTMLDivElement>) => {
    if (disabled || e.buttons === 0) return;
    setFromClientPoint(e.clientX, e.clientY);
  };

  const tx = clamp01((local.x - minX) / (maxX - minX)) * 100;
  const ty = clamp01((local.y - minY) / (maxY - minY)) * 100;

  return (
    <div className={`flex flex-col items-center gap-1.5 ${disabled ? 'opacity-40 pointer-events-none' : ''}`}>
      <div
        ref={padRef}
        onPointerDown={handlePointerDown}
        onPointerMove={handlePointerMove}
        className="relative w-full max-w-40 aspect-square rounded-lg bg-neutral-800 border border-neutral-700
                   touch-none select-none cursor-crosshair"
        role="slider"
        aria-label="X/Y position"
        aria-valuetext={`${local.x.toFixed(2)}, ${local.y.toFixed(2)}`}
      >
        <div className="absolute inset-0 border border-neutral-700/50 m-[25%]" aria-hidden="true" />
        <div
          className="absolute w-3 h-3 -ml-1.5 -mt-1.5 rounded-full bg-indigo-400 shadow-md
                     ring-2 ring-indigo-400/30"
          style={{ left: `${tx}%`, top: `${ty}%` }}
          aria-hidden="true"
        />
      </div>
      <span className="text-neutral-400 text-xs tabular-nums">
        {local.x.toFixed(2)}, {local.y.toFixed(2)}
      </span>
    </div>
  );
}
