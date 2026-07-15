import { useState } from 'react';
import { Search } from 'lucide-react';
import type { EnumOption } from '../../types';

interface SearchListControlProps {
  value: number;
  options: EnumOption[];
  disabled?: boolean;
  onChange: (v: number) => void;
}

export function SearchListControl({ value, options, disabled, onChange }: SearchListControlProps) {
  const [search, setSearch] = useState('');

  if (options.length === 0) return null;

  const filtered = options
    .map((opt, idx) => ({ opt, idx }))
    .filter(({ opt }) => opt.label.toLowerCase().includes(search.toLowerCase()));

  return (
    <div className={`flex flex-col gap-1.5 ${disabled ? 'opacity-40 pointer-events-none' : ''}`}>
      <div className="flex items-center gap-1.5 px-2 py-1 bg-neutral-800 border border-neutral-600 rounded">
        <Search className="w-3.5 h-3.5 text-neutral-500 shrink-0" />
        <input
          type="text"
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          placeholder="Search…"
          className="w-full bg-transparent text-sm text-neutral-200 placeholder-neutral-500 focus:outline-none"
        />
      </div>

      <div className="max-h-64 overflow-y-auto flex flex-col gap-0.5">
        {filtered.map(({ opt, idx }) => (
          <button
            key={idx}
            onClick={() => onChange(idx)}
            className={`text-left px-2 py-1.5 rounded text-sm truncate transition-colors ${
              idx === value
                ? 'bg-indigo-950/40 text-indigo-300'
                : 'text-neutral-300 hover:bg-neutral-800'
            }`}
          >
            {opt.label}
          </button>
        ))}
        {filtered.length === 0 && (
          <p className="text-xs text-neutral-500 px-2 py-1.5">No matches</p>
        )}
      </div>
    </div>
  );
}
