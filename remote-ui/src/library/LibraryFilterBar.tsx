import { ArrowDownAZ, ArrowUpAZ, Search } from 'lucide-react';
import type { SortDir } from './useLibraryFilter';

interface LibraryFilterBarProps {
  search: string;
  onSearchChange: (v: string) => void;
  sortDir: SortDir;
  onCycleSort: () => void;
  allTags: string[];
  selectedTags: Set<string>;
  onToggleTag: (tag: string) => void;
}

export function LibraryFilterBar({
  search,
  onSearchChange,
  sortDir,
  onCycleSort,
  allTags,
  selectedTags,
  onToggleTag,
}: LibraryFilterBarProps) {
  return (
    <div className="flex flex-col gap-2 mb-4">
      <div className="flex items-center gap-1.5">
        <div className="flex-1 flex items-center gap-1.5 px-2 py-1 bg-neutral-900 border border-neutral-700 rounded">
          <Search className="w-3.5 h-3.5 text-neutral-500 shrink-0" />
          <input
            type="text"
            value={search}
            onChange={(e) => onSearchChange(e.target.value)}
            placeholder="Search…"
            className="w-full bg-transparent text-sm text-neutral-200 placeholder-neutral-500 focus:outline-none"
          />
        </div>
        <button
          onClick={onCycleSort}
          title={sortDir === 'none' ? 'Sort A→Z' : sortDir === 'asc' ? 'Sort Z→A' : 'Clear sort'}
          className={`shrink-0 p-1.5 rounded border transition-colors ${
            sortDir !== 'none'
              ? 'border-phosphor bg-phosphor/10 text-phosphor'
              : 'border-neutral-700 text-neutral-400 hover:bg-neutral-800'
          }`}
        >
          {sortDir === 'desc' ? <ArrowDownAZ className="w-3.5 h-3.5" /> : <ArrowUpAZ className="w-3.5 h-3.5" />}
        </button>
      </div>

      {allTags.length > 1 && (
        <div className="flex flex-wrap gap-1.5">
          {allTags.map((tag) => {
            const active = selectedTags.has(tag);
            return (
              <button
                key={tag}
                onClick={() => onToggleTag(tag)}
                className={`px-2 py-0.5 rounded-full text-xs border transition-colors ${
                  active
                    ? 'border-phosphor bg-phosphor/10 text-phosphor'
                    : 'border-neutral-700 text-neutral-400 hover:bg-neutral-800'
                }`}
              >
                {tag}
              </button>
            );
          })}
        </div>
      )}
    </div>
  );
}
