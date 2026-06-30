import { useCallback, useEffect, useRef, useState } from 'react';
import * as RadixSlider from '@radix-ui/react-slider';

interface SliderControlProps {
  value: number;
  min: number;
  max: number;
  disabled: boolean;
  onChange: (v: number) => void;
}

const DEBOUNCE_MS = 150;

export function SliderControl({ value, min, max, disabled, onChange }: SliderControlProps) {
  const [localValue, setLocalValue] = useState(value);
  const timerRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  useEffect(() => {
    setLocalValue(value);
  }, [value]);

  const handleChange = useCallback(
    (vals: number[]) => {
      const v = vals[0] ?? value;
      setLocalValue(v);
      if (timerRef.current !== null) {
        clearTimeout(timerRef.current);
      }
      timerRef.current = setTimeout(() => {
        onChange(v);
        timerRef.current = null;
      }, DEBOUNCE_MS);
    },
    [onChange, value],
  );

  const displayValue =
    Number.isInteger(localValue) ? localValue.toString() : localValue.toFixed(3);

  return (
    <div className={`flex items-center gap-3 w-full ${disabled ? 'opacity-40 pointer-events-none' : ''}`}>
      <RadixSlider.Root
        className="relative flex items-center select-none touch-none w-full h-5"
        value={[localValue]}
        min={min}
        max={max}
        step={(max - min) / 1000}
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
