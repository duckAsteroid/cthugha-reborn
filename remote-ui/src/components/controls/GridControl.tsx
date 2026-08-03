import { useEffect, useMemo, useRef, useState } from 'react';
import type { EnumOption } from '../../types';
import { ArrowDownAZ, ArrowUpAZ, Columns2, Columns3, Columns4, ImageOff, Search } from 'lucide-react';
import { SELECT_TAG_EVENT, type SelectTagDetail } from '../../tagSelection';

type SortDir = 'none' | 'asc' | 'desc';
type ThumbSize = 'large' | 'medium' | 'small';

const THUMB_SIZE_KEY = 'cthugha:thumbSize';

const THUMB_SIZE_COLS: Record<ThumbSize, { normal: string; swatch: string }> = {
  large: { normal: 'grid-cols-3', swatch: 'grid-cols-2' },
  medium: { normal: 'grid-cols-4', swatch: 'grid-cols-3' },
  small: { normal: 'grid-cols-6', swatch: 'grid-cols-4' },
};

const NEXT_THUMB_SIZE: Record<ThumbSize, ThumbSize> = {
  large: 'medium',
  medium: 'small',
  small: 'large',
};

const THUMB_SIZE_ICON: Record<ThumbSize, typeof Columns2> = {
  large: Columns2,
  medium: Columns3,
  small: Columns4,
};

function loadThumbSize(): ThumbSize {
  const stored = localStorage.getItem(THUMB_SIZE_KEY);
  return stored === 'small' || stored === 'medium' || stored === 'large' ? stored : 'large';
}

interface GridControlProps {
  value: number;
  options: EnumOption[];
  disabled?: boolean;
  onChange: (v: number) => void;
  /**
   * When `'SWATCH'`, previews are treated as short, wide strips that already encode all their
   * information across the full width (e.g. a palette colour swatch) — rendered two-per-row
   * with `object-fit: fill` instead of the default cropped 1:1 photo-thumbnail tile.
   */
  previewStyle?: string;
  /**
   * This leaf's full param path (e.g. "Videos/Video") — identifies which GridControl instance a
   * `cthugha:select-tag` event (see tagSelection.ts) targets, since more than one GRID-typed
   * ENUM can be mounted at once.
   */
  path?: string;
}

/** Falls back to a single-element list from `group` when an option has no `tags` array. */
function optionTags(opt: EnumOption): string[] {
  return opt.tags ?? (opt.group ? [opt.group] : []);
}

export function GridControl({ value, options, disabled, onChange, previewStyle, path }: GridControlProps) {
  const [search, setSearch] = useState('');
  const [selectedTags, setSelectedTags] = useState<Set<string>>(new Set());
  const [sortDir, setSortDir] = useState<SortDir>('none');
  const [thumbSize, setThumbSize] = useState<ThumbSize>(loadThumbSize);
  const swatch = previewStyle === 'SWATCH';
  const rootRef = useRef<HTMLDivElement>(null);

  // Lets a tag chip rendered elsewhere (e.g. the "Current Video" preview row) drive this
  // instance's own tag filter and bring it into view — see tagSelection.ts.
  useEffect(() => {
    if (!path) return;
    const handler = (event: Event) => {
      const { path: targetPath, tag } = (event as CustomEvent<SelectTagDetail>).detail;
      if (targetPath !== path) return;
      setSearch('');
      setSelectedTags(new Set([tag]));
      rootRef.current?.scrollIntoView({ behavior: 'smooth', block: 'center' });
    };
    window.addEventListener(SELECT_TAG_EVENT, handler);
    return () => window.removeEventListener(SELECT_TAG_EVENT, handler);
  }, [path]);

  const allTags = useMemo(
    () => Array.from(new Set(options.flatMap(optionTags))).sort(),
    [options],
  );

  if (options.length === 0) return null;

  const toggleTag = (tag: string) =>
    setSelectedTags((prev) => {
      const next = new Set(prev);
      if (next.has(tag)) next.delete(tag);
      else next.add(tag);
      return next;
    });

  const query = search.trim().toLowerCase();
  let filtered = options
    .map((opt, idx) => ({ opt, idx }))
    .filter(({ opt }) => {
      const tags = optionTags(opt);
      const matchesQuery =
        !query ||
        opt.label.toLowerCase().includes(query) ||
        tags.some((t) => t.toLowerCase().includes(query));
      const matchesTags = selectedTags.size === 0 || tags.some((t) => selectedTags.has(t));
      return matchesQuery && matchesTags;
    });

  if (sortDir !== 'none') {
    filtered = [...filtered].sort((a, b) => {
      const cmp = a.opt.label.localeCompare(b.opt.label, undefined, { sensitivity: 'base' });
      return sortDir === 'asc' ? cmp : -cmp;
    });
  }

  const cycleSort = () =>
    setSortDir((d) => (d === 'none' ? 'asc' : d === 'asc' ? 'desc' : 'none'));

  const cycleThumbSize = () =>
    setThumbSize((size) => {
      const next = NEXT_THUMB_SIZE[size];
      localStorage.setItem(THUMB_SIZE_KEY, next);
      return next;
    });

  const ThumbSizeIcon = THUMB_SIZE_ICON[thumbSize];

  return (
    <div ref={rootRef} className={`flex flex-col gap-2 ${disabled ? 'opacity-40 pointer-events-none' : ''}`}>
      <div className="flex items-center gap-1.5">
        {allTags.length > 1 && (
          <div className="flex-1 flex items-center gap-1.5 px-2 py-1 bg-neutral-800 border border-neutral-600 rounded">
            <Search className="w-3.5 h-3.5 text-neutral-500 shrink-0" />
            <input
              type="text"
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              placeholder="Search…"
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
              ? 'border-phosphor bg-phosphor/10 text-phosphor'
              : 'border-neutral-600 text-neutral-400 hover:bg-neutral-800'
          }`}
        >
          {sortDir === 'desc' ? (
            <ArrowDownAZ className="w-3.5 h-3.5" />
          ) : (
            <ArrowUpAZ className="w-3.5 h-3.5" />
          )}
        </button>
        <button
          onClick={cycleThumbSize}
          title={`${NEXT_THUMB_SIZE[thumbSize][0].toUpperCase()}${NEXT_THUMB_SIZE[thumbSize].slice(1)} thumbnails`}
          className="shrink-0 p-1.5 rounded border border-neutral-600 text-neutral-400 hover:bg-neutral-800 transition-colors"
        >
          <ThumbSizeIcon className="w-3.5 h-3.5" />
        </button>
      </div>

      {allTags.length > 1 && (
        <div className="flex flex-wrap gap-1.5">
          {allTags.map((tag) => {
            const active = selectedTags.has(tag);
            return (
              <button
                key={tag}
                onClick={() => toggleTag(tag)}
                className={`px-2 py-0.5 rounded-full text-xs border transition-colors ${
                  active
                    ? 'border-phosphor bg-phosphor/10 text-phosphor'
                    : 'border-neutral-600 text-neutral-400 hover:bg-neutral-800'
                }`}
              >
                {tag}
              </button>
            );
          })}
        </div>
      )}

      <div className={`grid gap-2 ${THUMB_SIZE_COLS[thumbSize][swatch ? 'swatch' : 'normal']}`}>
        {filtered.map(({ opt, idx }) => (
          <button
            key={idx}
            onClick={() => onChange(idx)}
            className={`flex flex-col gap-1 p-1 rounded-lg border transition-colors ${
              idx === value
                ? 'border-phosphor bg-phosphor/10'
                : 'border-transparent hover:bg-neutral-800'
            }`}
          >
            {opt.preview ? (
              <img
                src={opt.preview}
                alt={opt.label}
                loading="lazy"
                decoding="async"
                className={
                  swatch
                    ? 'w-full aspect-[4/1] rounded object-fill'
                    : 'w-full aspect-square rounded object-cover'
                }
              />
            ) : (
              <div
                className={`w-full rounded bg-neutral-800 flex items-center justify-center ${
                  swatch ? 'aspect-[4/1]' : 'aspect-square'
                }`}
              >
                <ImageOff className="w-5 h-5 text-neutral-500" />
              </div>
            )}
            <span className="text-xs text-center text-neutral-400 truncate">{opt.label}</span>
          </button>
        ))}
        {filtered.length === 0 && (
          <p className="col-span-full text-xs text-neutral-500 px-2 py-1.5">No matches</p>
        )}
      </div>
    </div>
  );
}
