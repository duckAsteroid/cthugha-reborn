import { useCallback, useEffect, useRef, useState } from 'react';
import * as RadixSlider from '@radix-ui/react-slider';
import { Check, X } from 'lucide-react';

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
  const [editing, setEditing] = useState(false);
  const [editValue, setEditValue] = useState('');
  const editInputRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    setLocalValue(value);
  }, [value]);

  useEffect(() => {
    if (editing) {
      editInputRef.current?.focus();
      editInputRef.current?.select();
    }
  }, [editing]);

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

  const startEdit = () => {
    if (timerRef.current !== null) {
      clearTimeout(timerRef.current);
      timerRef.current = null;
    }
    setEditValue(displayValue);
    setEditing(true);
  };

  const commitEdit = () => {
    const parsed = Number(editValue);
    if (!Number.isNaN(parsed)) {
      const clamped = Math.min(max, Math.max(min, parsed));
      const v = integer ? Math.round(clamped) : clamped;
      setLocalValue(v);
      onChange(v);
    }
    setEditing(false);
  };

  const cancelEdit = () => {
    setEditing(false);
  };

  return (
    <div className={`flex items-center gap-3 w-full ${disabled ? 'opacity-40 pointer-events-none' : ''}`}>
      {editing ? (
        <>
          <input
            ref={editInputRef}
            type="number"
            inputMode="decimal"
            min={min}
            max={max}
            step={integer ? 1 : 'any'}
            value={editValue}
            onChange={(e) => setEditValue(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === 'Enter') commitEdit();
              if (e.key === 'Escape') cancelEdit();
            }}
            className="flex-1 min-w-0 bg-neutral-800 border border-indigo-500 rounded px-2 py-1 text-sm text-neutral-100 tabular-nums focus:outline-none focus:ring-2 focus:ring-indigo-500"
          />
          <button
            onClick={commitEdit}
            aria-label="Confirm value"
            className="p-1 rounded text-emerald-400 hover:text-emerald-300 hover:bg-neutral-800 shrink-0"
          >
            <Check className="w-4 h-4" />
          </button>
          <button
            onClick={cancelEdit}
            aria-label="Cancel edit"
            className="p-1 rounded text-neutral-400 hover:text-neutral-200 hover:bg-neutral-800 shrink-0"
          >
            <X className="w-4 h-4" />
          </button>
        </>
      ) : (
        <>
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
          <button
            onClick={startEdit}
            disabled={disabled}
            aria-label="Enter exact value"
            className="text-neutral-400 text-xs w-16 text-right tabular-nums shrink-0 hover:text-neutral-200 focus:outline-none focus:text-indigo-300"
          >
            {displayValue}
          </button>
        </>
      )}
    </div>
  );
}
