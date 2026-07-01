import { useState } from 'react';
import type { StringNode } from '../types';
import { patchStringParam } from '../api';

interface StringLeafProps {
  path: string;
  node: StringNode;
}

export function StringLeaf({ path, node }: StringLeafProps) {
  const [localValue, setLocalValue] = useState(node.value);

  const commit = async (value: string) => {
    try {
      await patchStringParam(path, value);
    } catch {
      // error handled by api.ts
    }
  };

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
