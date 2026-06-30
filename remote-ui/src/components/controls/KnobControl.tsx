import { useCallback, useEffect, useRef, useState } from 'react';

interface KnobControlProps {
  value: number;
  min: number;
  max: number;
  disabled: boolean;
  onChange: (v: number) => void;
}

const DEBOUNCE_MS = 150;
// Pixels of vertical drag for a full 0→1 sweep
const DRAG_SENSITIVITY = 200;

export function KnobControl({ value, min, max, disabled, onChange }: KnobControlProps) {
  const [localValue, setLocalValue] = useState(value);
  const timerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const dragRef = useRef<{ startY: number; startValue: number } | null>(null);

  useEffect(() => {
    setLocalValue(value);
  }, [value]);

  const commit = useCallback(
    (v: number) => {
      const clamped = Math.min(max, Math.max(min, v));
      setLocalValue(clamped);
      if (timerRef.current !== null) {
        clearTimeout(timerRef.current);
      }
      timerRef.current = setTimeout(() => {
        onChange(clamped);
        timerRef.current = null;
      }, DEBOUNCE_MS);
    },
    [min, max, onChange],
  );

  const handlePointerDown = useCallback(
    (e: React.PointerEvent<HTMLDivElement>) => {
      if (disabled) return;
      e.currentTarget.setPointerCapture(e.pointerId);
      dragRef.current = { startY: e.clientY, startValue: localValue };
    },
    [disabled, localValue],
  );

  const handlePointerMove = useCallback(
    (e: React.PointerEvent<HTMLDivElement>) => {
      if (!dragRef.current) return;
      const dy = dragRef.current.startY - e.clientY; // up = increase
      const delta = (dy / DRAG_SENSITIVITY) * (max - min);
      commit(dragRef.current.startValue + delta);
    },
    [commit, min, max],
  );

  const handlePointerUp = useCallback(() => {
    dragRef.current = null;
  }, []);

  // Normalise value to [0, 1] for the arc SVG
  const norm = max > min ? (localValue - min) / (max - min) : 0;
  // Arc from -135 deg to +135 deg (270 deg sweep)
  const startAngle = -135;
  const sweepAngle = 270;
  const angle = startAngle + norm * sweepAngle;

  const cx = 24;
  const cy = 24;
  const r = 18;

  function polarToXY(deg: number) {
    const rad = ((deg - 90) * Math.PI) / 180;
    return { x: cx + r * Math.cos(rad), y: cy + r * Math.sin(rad) };
  }

  const arcStart = polarToXY(startAngle);
  const arcEnd = polarToXY(angle);
  const largeArc = norm * sweepAngle > 180 ? 1 : 0;

  const trackStart = polarToXY(startAngle);
  const trackEnd = polarToXY(startAngle + sweepAngle);

  const displayValue =
    Number.isInteger(localValue) ? localValue.toString() : localValue.toFixed(3);

  return (
    <div className={`flex flex-col items-center gap-1 ${disabled ? 'opacity-40 pointer-events-none' : ''}`}>
      <div
        role="slider"
        aria-valuemin={min}
        aria-valuemax={max}
        aria-valuenow={localValue}
        aria-label="Parameter knob"
        tabIndex={disabled ? -1 : 0}
        className="w-12 h-12 touch-none cursor-ns-resize select-none"
        onPointerDown={handlePointerDown}
        onPointerMove={handlePointerMove}
        onPointerUp={handlePointerUp}
        onKeyDown={(e) => {
          const step = (max - min) / 100;
          if (e.key === 'ArrowUp') commit(localValue + step);
          if (e.key === 'ArrowDown') commit(localValue - step);
        }}
      >
        <svg viewBox="0 0 48 48" className="w-12 h-12 pointer-events-none">
          {/* Track */}
          <path
            d={`M ${trackStart.x} ${trackStart.y} A ${r} ${r} 0 1 1 ${trackEnd.x} ${trackEnd.y}`}
            fill="none"
            stroke="#404040"
            strokeWidth="3"
            strokeLinecap="round"
          />
          {/* Fill */}
          {norm > 0 && (
            <path
              d={`M ${arcStart.x} ${arcStart.y} A ${r} ${r} 0 ${largeArc} 1 ${arcEnd.x} ${arcEnd.y}`}
              fill="none"
              stroke="#6366f1"
              strokeWidth="3"
              strokeLinecap="round"
            />
          )}
          {/* Thumb dot */}
          <circle cx={arcEnd.x} cy={arcEnd.y} r={3} fill="#818cf8" />
        </svg>
      </div>
      <span className="text-neutral-400 text-xs tabular-nums">{displayValue}</span>
    </div>
  );
}
