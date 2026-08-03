import { useMemo, useState } from 'react';

export type SortDir = 'none' | 'asc' | 'desc';

/** Search/sort/tag-filter over a list — mirrors the main app's `GridControl` UX (search matches label or tags, tag filter is OR-of-selected, sort cycles none→A-Z→Z-A). */
export function useLibraryFilter<T>(items: T[], getLabel: (item: T) => string, getTags: (item: T) => string[]) {
  const [search, setSearch] = useState('');
  const [selectedTags, setSelectedTags] = useState<Set<string>>(new Set());
  const [sortDir, setSortDir] = useState<SortDir>('none');

  const allTags = useMemo(() => Array.from(new Set(items.flatMap(getTags))).sort(), [items]);

  function toggleTag(tag: string) {
    setSelectedTags((prev) => {
      const next = new Set(prev);
      if (next.has(tag)) next.delete(tag);
      else next.add(tag);
      return next;
    });
  }

  function cycleSort() {
    setSortDir((d) => (d === 'none' ? 'asc' : d === 'asc' ? 'desc' : 'none'));
  }

  const query = search.trim().toLowerCase();
  let filtered = items.filter((item) => {
    const tags = getTags(item);
    const matchesQuery = !query || getLabel(item).toLowerCase().includes(query) || tags.some((t) => t.toLowerCase().includes(query));
    const matchesTags = selectedTags.size === 0 || tags.some((t) => selectedTags.has(t));
    return matchesQuery && matchesTags;
  });

  if (sortDir !== 'none') {
    filtered = [...filtered].sort((a, b) => {
      const cmp = getLabel(a).localeCompare(getLabel(b), undefined, { sensitivity: 'base' });
      return sortDir === 'asc' ? cmp : -cmp;
    });
  }

  return { search, setSearch, selectedTags, toggleTag, sortDir, cycleSort, allTags, filtered };
}
