import { useMemo, useState } from 'react';
import type { EnumOption } from '../../types';
import { ArrowDownAZ, ArrowUpAZ, ImageOff, Search } from 'lucide-react';

type SortDir = 'none' | 'asc' | 'desc';

interface GridControlProps {
  value: number;
  options: EnumOption[];
  disabled?: boolean;
  onChange: (v: number) => void;
}

export function GridControl({ value, options, disabled, onChange }: GridControlProps) {
  const [search, setSearch] = useState('');
  const [sortDir, setSortDir] = useState<SortDir>('none');

  const groups = useMemo(
    () => Array.from(new Set(options.map((o) => o.group).filter((g): g is string => !!g))).sort(),
    [options],
  );

  if (options.length === 0) return null;

  const query = search.trim().toLowerCase();
  let filtered = options
    .map((opt, idx) => ({ opt, idx }))
    .filter(
      ({ opt }) =>
        !query ||
        opt.label.toLowerCase().includes(query) ||
        (opt.group ?? '').toLowerCase().includes(query),
    );

  if (sortDir !== 'none') {
    filtered = [...filtered].sort((a, b) => {
      const cmp = a.opt.label.localeCompare(b.opt.label, undefined, { sensitivity: 'base' });
      return sortDir === 'asc' ? cmp : -cmp;
    });
  }

  const cycleSort = () =>
    setSortDir((d) => (d === 'none' ? 'asc' : d === 'asc' ? 'desc' : 'none'));

  return (
    <div className={`flex flex-col gap-2 ${disabled ? 'opacity-40 pointer-events-none' : ''}`}>
      <div className="flex items-center gap-1.5">
        {groups.length > 1 && (
          <div className="flex-1 flex items-center gap-1.5 px-2 py-1 bg-neutral-800 border border-neutral-600 rounded">
            <Search className="w-3.5 h-3.5 text-neutral-500 shrink-0" />
            <input
              type="text"
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              placeholder="Search or filter by tag…"
              className="w-full bg-transparent text-sm text-neutral-200 placeholder-neutral-500 focus:outline-none"
            />
          </div>
        )}
        <button
          onClick={cycleSort}
          title={
            sortDir === 'none'
              ? 'Sort A→Z'
              : sortDir === 'asc'
                ? 'Sort Z→A'
                : 'Clear sort'
          }
          className={`shrink-0 p-1.5 rounded border transition-colors ${
            sortDir !== 'none'
              ? 'border-indigo-400 bg-indigo-950/40 text-indigo-300'
              : 'border-neutral-600 text-neutral-400 hover:bg-neutral-800'
          }`}
        >
          {sortDir === 'desc' ? (
            <ArrowDownAZ className="w-3.5 h-3.5" />
          ) : (
            <ArrowUpAZ className="w-3.5 h-3.5" />
          )}
        </button>
      </div>

      {groups.length > 1 && (
        <div className="flex flex-wrap gap-1.5">
          {groups.map((group) => {
            const active = query === group.toLowerCase();
            return (
              <button
                key={group}
                onClick={() => setSearch(active ? '' : group)}
                className={`px-2 py-0.5 rounded-full text-xs border transition-colors ${
                  active
                    ? 'border-indigo-400 bg-indigo-950/40 text-indigo-300'
                    : 'border-neutral-600 text-neutral-400 hover:bg-neutral-800'
                }`}
              >
                {group}
              </button>
            );
          })}
        </div>
      )}

      <div className="grid grid-cols-3 gap-2">
        {filtered.map(({ opt, idx }) => (
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
        {filtered.length === 0 && (
          <p className="text-xs text-neutral-500 px-2 py-1.5 col-span-3">No matches</p>
        )}
      </div>
    </div>
  );
}
