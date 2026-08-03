import { useState } from 'react';
import { X } from 'lucide-react';

interface TagEditorProps {
  tags: string[];
  onChange: (tags: string[]) => void;
  disabled?: boolean;
}

export function TagEditor({ tags, onChange, disabled }: TagEditorProps) {
  const [draft, setDraft] = useState('');

  function commitDraft() {
    const value = draft.trim();
    setDraft('');
    if (!value || tags.includes(value)) return;
    onChange([...tags, value]);
  }

  function removeTag(tag: string) {
    onChange(tags.filter((t) => t !== tag));
  }

  return (
    <div className="flex flex-wrap items-center gap-1.5 p-2 rounded border border-neutral-700 bg-neutral-900">
      {tags.map((tag) => (
        <span
          key={tag}
          className="flex items-center gap-1 px-2 py-0.5 rounded-full bg-indigo-500/20 text-indigo-300 text-xs"
        >
          {tag}
          {!disabled && (
            <button
              type="button"
              onClick={() => removeTag(tag)}
              aria-label={`Remove tag ${tag}`}
              className="hover:text-indigo-100"
            >
              <X className="w-3 h-3" />
            </button>
          )}
        </span>
      ))}
      {!disabled && (
        <input
          value={draft}
          onChange={(e) => setDraft(e.target.value)}
          onKeyDown={(e) => {
            if (e.key === 'Enter' || e.key === ',') {
              e.preventDefault();
              commitDraft();
            } else if (e.key === 'Backspace' && draft === '' && tags.length > 0) {
              removeTag(tags[tags.length - 1]);
            }
          }}
          onBlur={commitDraft}
          placeholder="Add tag…"
          className="flex-1 min-w-[6rem] bg-transparent text-sm text-neutral-200 placeholder-neutral-500 outline-none"
        />
      )}
    </div>
  );
}
