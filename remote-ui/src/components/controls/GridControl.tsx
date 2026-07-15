import type { EnumOption } from '../../types';
import { ImageOff } from 'lucide-react';

interface GridControlProps {
  value: number;
  options: EnumOption[];
  disabled?: boolean;
  onChange: (v: number) => void;
}

export function GridControl({ value, options, disabled, onChange }: GridControlProps) {
  if (options.length === 0) return null;

  return (
    <div
      className={`grid grid-cols-3 gap-2 ${disabled ? 'opacity-40 pointer-events-none' : ''}`}
    >
      {options.map((opt, idx) => (
        <button
          key={idx}
          onClick={() => onChange(idx)}
          className={`flex flex-col gap-1 p-1 rounded-lg border transition-colors ${
            idx === value
              ? 'border-indigo-400 bg-indigo-950/40'
              : 'border-transparent hover:bg-neutral-800'
          }`}
        >
          {opt.preview ? (
            <img
              src={opt.preview}
              alt={opt.label}
              className="w-full aspect-square rounded object-cover"
            />
          ) : (
            <div className="w-full aspect-square rounded bg-neutral-800 flex items-center justify-center">
              <ImageOff className="w-5 h-5 text-neutral-500" />
            </div>
          )}
          <span className="text-xs text-center text-neutral-400 truncate">{opt.label}</span>
        </button>
      ))}
    </div>
  );
}
