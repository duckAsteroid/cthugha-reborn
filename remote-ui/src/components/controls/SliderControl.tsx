import { useCallback, useEffect, useRef, useState } from 'react';
import * as RadixSlider from '@radix-ui/react-slider';

interface SliderControlProps {
  value: number;
  min: number;
  max: number;
  disabled: boolean;
  onChange: (v: number) => void;
  scale?: string;
  integer?: boolean;
}

const DEBOUNCE_MS = 150;
const LOG_EXPONENT = 3;

function toSlider(v: number, min: number, max: number, scale?: string): number {
  const t = (v - min) / (max - min);
  if (scale === 'log') return 1 - Math.pow(1 - t, 1 / LOG_EXPONENT);
  return t;
}

function fromSlider(t: number, min: number, max: number, scale?: string, integer?: boolean): number {
  let v: number;
  if (scale === 'log') v = min + (max - min) * (1 - Math.pow(1 - t, LOG_EXPONENT));
  else v = min + (max - min) * t;
  return integer ? Math.round(v) : v;
}

export function SliderControl({ value, min, max, disabled, onChange, scale, integer }: SliderControlProps) {
  const [localValue, setLocalValue] = useState(value);
  const timerRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  useEffect(() => {
    setLocalValue(value);
  }, [value]);

  const handleChange = useCallback(
    (vals: number[]) => {
      const v = fromSlider(vals[0] ?? 0, min, max, scale, integer);
      setLocalValue(v);
      if (timerRef.current !== null) {
        clearTimeout(timerRef.current);
      }
      timerRef.current = setTimeout(() => {
        onChange(v);
        timerRef.current = null;
      }, DEBOUNCE_MS);
    },
    [onChange, min, max, scale, integer],
  );

  const displayValue =
    Number.isInteger(localValue) ? localValue.toString() : localValue.toFixed(3);

  return (
    <div className={`flex items-center gap-3 w-full ${disabled ? 'opacity-40 pointer-events-none' : ''}`}>
      <RadixSlider.Root
        className="relative flex items-center select-none touch-none w-full h-5"
        value={[toSlider(localValue, min, max, scale)]}
        min={0}
        max={1}
        step={integer ? 1 / (max - min) : 0.001}
        onValueChange={handleChange}
        disabled={disabled}
        aria-label="Parameter value"
      >
        <RadixSlider.Track className="bg-neutral-700 relative grow rounded-full h-1">
          <RadixSlider.Range className="absolute bg-indigo-500 rounded-full h-full" />
        </RadixSlider.Track>
        <RadixSlider.Thumb className="block w-4 h-4 bg-indigo-400 rounded-full shadow-md hover:bg-indigo-300 focus:outline-none focus:ring-2 focus:ring-indigo-500" />
      </RadixSlider.Root>
      <span className="text-neutral-400 text-xs w-16 text-right tabular-nums shrink-0">
        {displayValue}
      </span>
    </div>
  );
}
