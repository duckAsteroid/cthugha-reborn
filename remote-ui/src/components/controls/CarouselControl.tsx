import { ChevronLeft, ChevronRight } from 'lucide-react';
import type { EnumOption } from '../../types';

interface CarouselControlProps {
  value: number;
  options: EnumOption[];
  disabled?: boolean;
  onChange: (v: number) => void;
}

export function CarouselControl({ value, options, disabled, onChange }: CarouselControlProps) {
  if (options.length === 0) return null;
  const idx = Math.min(Math.max(value, 0), options.length - 1);
  const current = options[idx];
  const prev = () => onChange(((idx - 1) + options.length) % options.length);
  const next = () => onChange((idx + 1) % options.length);

  return (
    <div className={`flex items-center gap-2 ${disabled ? 'opacity-40 pointer-events-none' : ''}`}>
      <button
        onClick={prev}
        className="p-1 rounded hover:bg-neutral-700 text-neutral-400 hover:text-neutral-200 transition-colors shrink-0"
        aria-label="Previous"
      >
        <ChevronLeft className="w-4 h-4" />
      </button>

      <div className="flex-1 min-w-0 flex flex-col gap-0.5">
        {current.preview && (
          <img
            src={current.preview}
            alt={current.label}
            className="w-full h-8 rounded object-fill"
          />
        )}
        <span className="text-xs text-center text-neutral-400 truncate">{current.label}</span>
      </div>

      <button
        onClick={next}
        className="p-1 rounded hover:bg-neutral-700 text-neutral-400 hover:text-neutral-200 transition-colors shrink-0"
        aria-label="Next"
      >
        <ChevronRight className="w-4 h-4" />
      </button>
    </div>
  );
}
