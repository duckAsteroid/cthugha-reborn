import { useState } from 'react';
import type { StringNode } from '../types';
import { patchStringParam } from '../api';

interface StringLeafProps {
  path: string;
  node: StringNode;
}

export function StringLeaf({ path, node }: StringLeafProps) {
  const [localValue, setLocalValue] = useState(node.value);
  const [committedValue, setCommittedValue] = useState(node.value);
  const [compileError, setCompileError] = useState<string | null>(null);

  const isDirty = localValue !== committedValue;

  const commit = async (value: string) => {
    try {
      const result = await patchStringParam(path, value);
      setCommittedValue(value);
      setCompileError(result.compileError ?? null);
    } catch {
      // error handled by api.ts
    }
  };

  const cancel = () => {
    setLocalValue(committedValue);
    setCompileError(null);
  };

  if (node.uiHints?.['control-type'] === 'CODE_EDITOR') {
    return (
      <div className="flex flex-col gap-1.5 py-2 px-3 rounded-lg bg-neutral-900/50">
        <div className="flex items-center gap-1.5">
          <span className="text-sm text-neutral-300 font-medium">{node.name}</span>
          {isDirty && <span className="w-2 h-2 rounded-full bg-orange-400 shrink-0" aria-label="unsaved changes" />}
        </div>
        <textarea
          value={localValue}
          onChange={(e) => {
            setLocalValue(e.target.value);
            if (compileError) setCompileError(null);
          }}
          rows={3}
          spellCheck={false}
          placeholder="Expression — e.g. (sin(t * 0.31) + 1) / 2  (t = seconds)"
          className={`w-full bg-neutral-800 rounded px-2 py-1.5 text-sm font-mono text-neutral-200 placeholder-neutral-500 focus:outline-none focus:ring-2 resize-y border ${
            isDirty
              ? 'border-orange-500 focus:ring-orange-500'
              : 'border-neutral-600 focus:ring-indigo-500'
          }`}
        />
        <div className="flex justify-end gap-2">
          <button
            onClick={cancel}
            disabled={!isDirty}
            className="px-3 py-1 text-xs rounded border border-neutral-600 text-neutral-300 hover:bg-neutral-700 disabled:opacity-40 disabled:cursor-not-allowed"
          >
            Cancel
          </button>
          <button
            onClick={() => commit(localValue)}
            disabled={!isDirty}
            className="px-3 py-1 text-xs rounded bg-indigo-600 text-white hover:bg-indigo-500 disabled:opacity-40 disabled:cursor-not-allowed"
          >
            Update
          </button>
        </div>
        {compileError && (
          <pre className="text-xs text-red-400 bg-red-950/50 border border-red-800 rounded px-2 py-1.5 overflow-x-auto whitespace-pre-wrap break-all">{compileError}</pre>
        )}
      </div>
    );
  }

  return (
    <div className="flex flex-col gap-1.5 py-2 px-3 rounded-lg bg-neutral-900/50">
      <span className="text-sm text-neutral-300 font-medium">{node.name}</span>
      <input
        type="text"
        value={localValue}
        placeholder="leave blank for auto name"
        onChange={(e) => setLocalValue(e.target.value)}
        onBlur={() => commit(localValue)}
        onKeyDown={(e) => { if (e.key === 'Enter') commit(localValue); }}
        className="w-full bg-neutral-800 border border-neutral-600 rounded px-2 py-1 text-sm text-neutral-200 placeholder-neutral-500 focus:outline-none focus:ring-2 focus:ring-indigo-500"
      />
    </div>
  );
}
